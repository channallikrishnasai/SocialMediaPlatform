package com.socialmedia.platform.service;

import com.socialmedia.platform.entity.User;
import com.socialmedia.platform.exception.BadRequestException;
import com.socialmedia.platform.repository.FollowRepository;
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

import static com.socialmedia.platform.config.KafkaConfig.NOTIFICATION_TOPIC;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock
    private FollowRepository followRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private FollowService followService;

    private User follower;
    private User following;
    private UserPrincipal followerPrincipal;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        follower = User.builder().id(1L).username("follower").build();
        following = User.builder().id(2L).username("following").build();
        followerPrincipal = UserPrincipal.create(follower);
        authentication = mock(Authentication.class);
        lenient().when(authentication.getPrincipal()).thenReturn(followerPrincipal);
    }

    @Test
    void followUser_ShouldSucceed() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(follower));
        when(userRepository.findById(2L)).thenReturn(Optional.of(following));
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(false);

        followService.followUser(2L, authentication);

        verify(followRepository).saveAndFlush(any());
        verify(kafkaTemplate).send(eq(NOTIFICATION_TOPIC), any());
    }

    @Test
    void followUser_ShouldThrowException_WhenSelfFollow() {
        assertThrows(BadRequestException.class, () -> followService.followUser(1L, authentication));
    }

    @Test
    void unfollowUser_ShouldSucceed() {
        followService.unfollowUser(2L, authentication);

        verify(followRepository).deleteByFollowerIdAndFollowingId(1L, 2L);
    }

    @Test
    void followUser_ShouldBeIdempotent_WhenAlreadyFollowing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(follower));
        when(userRepository.findById(2L)).thenReturn(Optional.of(following));
        when(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).thenReturn(true);

        followService.followUser(2L, authentication);

        verify(followRepository, never()).saveAndFlush(any());
        verify(kafkaTemplate, never()).send(eq(NOTIFICATION_TOPIC), any());
    }
}
