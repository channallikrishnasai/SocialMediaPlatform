package com.socialmedia.platform.service;

import com.socialmedia.platform.dto.PagedResponse;
import com.socialmedia.platform.dto.PostResponse;
import com.socialmedia.platform.entity.Post;
import com.socialmedia.platform.repository.FollowRepository;
import com.socialmedia.platform.repository.PostRepository;
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
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private PostService postService;
    @Mock
    private CacheManager cacheManager;
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private FeedService feedService;

    private UserPrincipal userPrincipal;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        userPrincipal = UserPrincipal.builder().id(1L).build();
        authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
    }

    @Test
    void getPersonalizedFeed_ShouldReturnPosts() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Long> followingIds = List.of(2L);
        when(followRepository.findFollowingIdsByFollowerId(1L)).thenReturn(followingIds);

        Post post = Post.builder().id(100L).build();
        Page<Post> postPage = new PageImpl<>(List.of(post));
        when(postRepository.findByUserIdInOrderByCreatedAtDesc(anyList(), eq(pageable))).thenReturn(postPage);

        PostResponse postResponse = PostResponse.builder().id(100L).build();
        when(postService.mapToPostResponse(any(Post.class), any())).thenReturn(postResponse);

        PagedResponse<PostResponse> response = feedService.getPersonalizedFeed(authentication, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(100L, response.getContent().get(0).getId());
    }
}
