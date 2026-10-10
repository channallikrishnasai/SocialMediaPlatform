package com.socialmedia.platform.service;

import com.socialmedia.platform.dto.*;
import com.socialmedia.platform.entity.*;
import com.socialmedia.platform.event.NotificationEvent;
import com.socialmedia.platform.exception.ForbiddenException;
import com.socialmedia.platform.exception.ResourceNotFoundException;
import com.socialmedia.platform.repository.*;
import com.socialmedia.platform.security.UserPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.socialmedia.platform.config.KafkaConfig.NOTIFICATION_TOPIC;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final UserService userService;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Transactional
    @CacheEvict(value = "userFeed", allEntries = true)
    public PostResponse createPost(PostRequest request, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));

        Post post = Post.builder()
                .content(request.getContent())
                .imageUrl(request.getImageUrl())
                .user(user)
                .shareCount(0L)
                .build();

        if (request.getSharedPostId() != null) {
            Post sharedPost = postRepository.findById(request.getSharedPostId())
                    .orElseThrow(() -> new ResourceNotFoundException("Post", "id", request.getSharedPostId()));
            post.setSharedPost(sharedPost);
            sharedPost.setShareCount(sharedPost.getShareCount() + 1);
            postRepository.save(sharedPost);

            // Send notification to original post author
            sendNotification(sharedPost.getUser().getId(), "POST_SHARED",
                    user.getUsername() + " shared your post", user.getId(), sharedPost.getId());
        }

        post = postRepository.save(post);
        log.info("Post created by user: {}", user.getUsername());
        meterRegistry.counter("posts.created").increment();

        return mapToPostResponse(post, authentication);
    }

    @Transactional(readOnly = true)
    public PostResponse getPostById(Long postId, Authentication authentication) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        return mapToPostResponse(post, authentication);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getUserPosts(Long userId, Pageable pageable, Authentication authentication) {
        Page<Post> posts = postRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return createPagedResponse(posts, pageable, authentication);
    }

    @Transactional
    public PostResponse updatePost(Long postId, PostRequest request, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        if (!post.getUser().getId().equals(userPrincipal.getId())) {
            throw new ForbiddenException("Unauthorized to update this post");
        }

        post.setContent(request.getContent());
        if (request.getImageUrl() != null) {
            post.setImageUrl(request.getImageUrl());
        }

        post = postRepository.save(post);
        return mapToPostResponse(post, authentication);
    }

    @Transactional
    public void deletePost(Long postId, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        if (!post.getUser().getId().equals(userPrincipal.getId())) {
            throw new ForbiddenException("Unauthorized to delete this post");
        }

        postRepository.delete(post);
        log.info("Post deleted: {}", postId);
    }

    /**
     * Idempotent by design: the existsBy check is a fast path, the DB unique constraint on
     * (post_id, user_id) is the real guard against concurrent duplicate likes. A losing
     * concurrent insert is treated as a no-op success rather than an error, and only the
     * winning insert emits a notification.
     */
    @Transactional
    public void likePost(Long postId, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        if (likeRepository.existsByPostIdAndUserId(postId, user.getId())) {
            return;
        }

        Like like = Like.builder()
                .post(post)
                .user(user)
                .build();

        try {
            likeRepository.saveAndFlush(like);
        } catch (DataIntegrityViolationException e) {
            log.info("Like on post {} by user {} already exists (concurrent request), treating as success",
                    postId, user.getId());
            return;
        }
        meterRegistry.counter("likes.created").increment();

        // Send notification to post author
        if (!post.getUser().getId().equals(user.getId())) {
            sendNotification(post.getUser().getId(), "POST_LIKED",
                    user.getUsername() + " liked your post", user.getId(), postId);
        }
    }

    @Transactional
    public void unlikePost(Long postId, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        likeRepository.deleteByPostIdAndUserId(postId, userPrincipal.getId());
    }

    @Transactional
    public CommentResponse addComment(Long postId, CommentRequest request, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));

        Comment comment = Comment.builder()
                .content(request.getContent())
                .post(post)
                .user(user)
                .build();

        comment = commentRepository.save(comment);

        // Send notification to post author
        if (!post.getUser().getId().equals(user.getId())) {
            sendNotification(post.getUser().getId(), "POST_COMMENTED",
                    user.getUsername() + " commented on your post", user.getId(), postId);
        }

        return mapToCommentResponse(comment, authentication);
    }

    @Transactional(readOnly = true)
    public PagedResponse<CommentResponse> getPostComments(Long postId, Pageable pageable, Authentication authentication) {
        Page<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtDesc(postId, pageable);

        return PagedResponse.<CommentResponse>builder()
                .content(comments.getContent().stream()
                        .map(comment -> mapToCommentResponse(comment, authentication))
                        .toList())
                .pageNumber(comments.getNumber())
                .pageSize(comments.getSize())
                .totalElements(comments.getTotalElements())
                .totalPages(comments.getTotalPages())
                .last(comments.isLast())
                .build();
    }

    public PostResponse mapToPostResponse(Post post, Authentication authentication) {
        Long likesCount = postRepository.countLikes(post.getId());
        Long commentsCount = postRepository.countComments(post.getId());

        Boolean isLiked = false;
        if (authentication != null) {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            isLiked = likeRepository.existsByPostIdAndUserId(post.getId(), userPrincipal.getId());
        }

        PostResponse sharedPostResponse = null;
        if (post.getSharedPost() != null) {
            sharedPostResponse = mapToPostResponse(post.getSharedPost(), authentication);
        }

        return PostResponse.builder()
                .id(post.getId())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .user(userService.mapToUserResponse(post.getUser(), authentication))
                .sharedPost(sharedPostResponse)
                .likesCount(likesCount)
                .commentsCount(commentsCount)
                .shareCount(post.getShareCount())
                .isLiked(isLiked)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private CommentResponse mapToCommentResponse(Comment comment, Authentication authentication) {
        return CommentResponse.builder()
                .id(comment.getId())
                .content(comment.getContent())
                .user(userService.mapToUserResponse(comment.getUser(), authentication))
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

    private PagedResponse<PostResponse> createPagedResponse(Page<Post> posts, Pageable pageable, Authentication authentication) {
        return PagedResponse.<PostResponse>builder()
                .content(posts.getContent().stream()
                        .map(post -> mapToPostResponse(post, authentication))
                        .toList())
                .pageNumber(posts.getNumber())
                .pageSize(posts.getSize())
                .totalElements(posts.getTotalElements())
                .totalPages(posts.getTotalPages())
                .last(posts.isLast())
                .build();
    }

    private void sendNotification(Long userId, String type, String message, Long relatedUserId, Long relatedPostId) {
        NotificationEvent event = NotificationEvent.builder()
                .userId(userId)
                .type(type)
                .message(message)
                .relatedUserId(relatedUserId)
                .relatedPostId(relatedPostId)
                .build();

        kafkaTemplate.send(NOTIFICATION_TOPIC, event);
        meterRegistry.counter("kafka.events.produced", "topic", NOTIFICATION_TOPIC).increment();
    }
}
