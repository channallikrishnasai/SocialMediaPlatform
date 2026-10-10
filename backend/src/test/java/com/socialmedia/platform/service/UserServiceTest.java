package com.socialmedia.platform.service;

import com.socialmedia.platform.dto.UserResponse;
import com.socialmedia.platform.entity.User;
import com.socialmedia.platform.repository.FollowRepository;
import com.socialmedia.platform.repository.UserRepository;
import com.socialmedia.platform.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FollowRepository followRepository;

    @InjectMocks
    private UserService userService;

    private User user;
    private UserPrincipal userPrincipal;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .role(User.Role.USER)
                .build();
        userPrincipal = UserPrincipal.create(user);
        authentication = mock(Authentication.class);
    }

    @Test
    void getCurrentUser_ShouldReturnUser() {
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.countFollowers(1L)).thenReturn(10L);
        when(userRepository.countFollowing(1L)).thenReturn(5L);

        UserResponse response = userService.getCurrentUser(authentication);

        assertNotNull(response);
        assertEquals("testuser", response.getUsername());
        assertEquals(10L, response.getFollowersCount());
        assertEquals(5L, response.getFollowingCount());
    }

    @Test
    void updateProfile_ShouldSucceed() {
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponse response = userService.updateProfile(1L, "New Bio", "new-avatar.jpg", authentication);

        assertNotNull(response);
        verify(userRepository).save(user);
        assertEquals("New Bio", user.getBio());
        assertEquals("new-avatar.jpg", user.getAvatarUrl());
    }
}
