package com.socialmedia.platform.service;

import com.socialmedia.platform.dto.PostRequest;
import com.socialmedia.platform.dto.PostResponse;
import com.socialmedia.platform.entity.Post;
import com.socialmedia.platform.entity.User;
import com.socialmedia.platform.exception.ResourceNotFoundException;
import com.socialmedia.platform.repository.CommentRepository;
import com.socialmedia.platform.repository.LikeRepository;
import com.socialmedia.platform.repository.PostRepository;
import com.socialmedia.platform.repository.UserRepository;
import com.socialmedia.platform.security.UserPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LikeRepository likeRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private UserService userService;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private PostService postService;

    private User user;
    private UserPrincipal userPrincipal;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("testuser")
                .build();
        userPrincipal = UserPrincipal.create(user);
        authentication = mock(Authentication.class);
        lenient().when(authentication.getPrincipal()).thenReturn(userPrincipal);
    }

    @Test
    void createPost_ShouldSucceed() {
        PostRequest request = new PostRequest();
        request.setContent("Test content");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            p.setId(100L);
            return p;
        });

        PostResponse response = postService.createPost(request, authentication);

        assertNotNull(response);
        assertEquals("Test content", response.getContent());
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void getPostById_ShouldReturnPost() {
        Post post = Post.builder().id(100L).content("Content").user(user).build();
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        PostResponse response = postService.getPostById(100L, authentication);

        assertNotNull(response);
        assertEquals(100L, response.getId());
    }

    @Test
    void getPostById_ShouldThrowException_WhenNotFound() {
        when(postRepository.findById(100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> postService.getPostById(100L, authentication));
    }

    @Test
    void deletePost_ShouldSucceed_WhenOwner() {
        Post post = Post.builder().id(100L).user(user).build();
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        postService.deletePost(100L, authentication);

        verify(postRepository).delete(post);
    }

    @Test
    void deletePost_ShouldThrowException_WhenNotOwner() {
        User otherUser = User.builder().id(2L).build();
        Post post = Post.builder().id(100L).user(otherUser).build();
        when(postRepository.findById(100L)).thenReturn(Optional.of(post));

        assertThrows(RuntimeException.class, () -> postService.deletePost(100L, authentication));
    }
}
