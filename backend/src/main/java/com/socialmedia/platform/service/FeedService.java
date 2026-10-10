package com.socialmedia.platform.service;

import com.socialmedia.platform.dto.PagedResponse;
import com.socialmedia.platform.dto.PostResponse;
import com.socialmedia.platform.entity.Post;
import com.socialmedia.platform.repository.FollowRepository;
import com.socialmedia.platform.repository.PostRepository;
import com.socialmedia.platform.security.UserPrincipal;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The feed cache is populated/read manually (rather than via @Cacheable) so that hits and
 * misses can be counted for real observability - see feed.cache.hit / feed.cache.miss in
 * /actuator/prometheus.
 */
@Service
@Slf4j
public class FeedService {

    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final PostService postService;
    private final CacheManager cacheManager;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;

    public FeedService(PostRepository postRepository, FollowRepository followRepository,
                        PostService postService, CacheManager cacheManager, MeterRegistry meterRegistry) {
        this.postRepository = postRepository;
        this.followRepository = followRepository;
        this.postService = postService;
        this.cacheManager = cacheManager;
        this.cacheHitCounter = Counter.builder("feed.cache.hit")
                .description("Personalized feed requests served from Redis")
                .register(meterRegistry);
        this.cacheMissCounter = Counter.builder("feed.cache.miss")
                .description("Personalized feed requests that had to query PostgreSQL")
                .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PostResponse> getPersonalizedFeed(Authentication authentication, Pageable pageable) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        String cacheKey = userPrincipal.getId() + "-" + pageable.getPageNumber();
        Cache cache = cacheManager.getCache("userFeed");

        if (cache != null) {
            Cache.ValueWrapper cached = cache.get(cacheKey);
            if (cached != null) {
                cacheHitCounter.increment();
                log.info("Feed cache HIT for user: {} page: {}", userPrincipal.getId(), pageable.getPageNumber());
                return (PagedResponse<PostResponse>) cached.get();
            }
        }

        cacheMissCounter.increment();
        log.info("Feed cache MISS - fetching personalized feed for user: {} page: {}",
                userPrincipal.getId(), pageable.getPageNumber());

        // Get IDs of users that the current user follows
        List<Long> followingIds = new ArrayList<>(followRepository.findFollowingIdsByFollowerId(userPrincipal.getId()));

        // Include the user's own posts
        followingIds.add(userPrincipal.getId());

        Page<Post> posts;
        if (followingIds.isEmpty()) {
            // If not following anyone, show all posts
            posts = postRepository.findAll(pageable);
        } else {
            posts = postRepository.findByUserIdInOrderByCreatedAtDesc(followingIds, pageable);
        }

        PagedResponse<PostResponse> response = createPagedResponse(posts, pageable, authentication);

        if (cache != null) {
            cache.put(cacheKey, response);
        }

        return response;
    }

    private PagedResponse<PostResponse> createPagedResponse(Page<Post> posts, Pageable pageable, Authentication authentication) {
        List<PostResponse> postResponses = new ArrayList<>();

        for (Post post : posts.getContent()) {
            try {
                PostResponse postResponse = postService.mapToPostResponse(post, authentication);
                postResponses.add(postResponse);
            } catch (Exception e) {
                log.error("Error mapping post to response", e);
            }
        }

        return PagedResponse.<PostResponse>builder()
                .content(postResponses)
                .pageNumber(posts.getNumber())
                .pageSize(posts.getSize())
                .totalElements(posts.getTotalElements())
                .totalPages(posts.getTotalPages())
                .last(posts.isLast())
                .build();
    }
}
