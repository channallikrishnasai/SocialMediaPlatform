package com.socialmedia.platform.service;

import com.socialmedia.platform.dto.AuthRequest;
import com.socialmedia.platform.dto.AuthResponse;
import com.socialmedia.platform.dto.RegisterRequest;
import com.socialmedia.platform.dto.UserResponse;
import com.socialmedia.platform.entity.User;
import com.socialmedia.platform.exception.BadRequestException;
import com.socialmedia.platform.repository.UserRepository;
import com.socialmedia.platform.security.JwtTokenProvider;
import com.socialmedia.platform.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private User user;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");

        user = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("hashedPassword")
                .role(User.Role.USER)
                .build();
    }

    @Test
    void register_ShouldSucceed_WhenValidRequest() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashedPassword");
        when(userRepository.save(any(User.class))).thenReturn(user);

        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(tokenProvider.generateToken(any())).thenReturn("accessToken");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("refreshToken");

        UserResponse userResponse = UserResponse.builder().id(1L).username("testuser").build();
        when(userService.mapToUserResponse(any(), any())).thenReturn(userResponse);

        AuthResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals("accessToken", response.getAccessToken());
        assertEquals("testuser", response.getUser().getUsername());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_ShouldThrowException_WhenEmailExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(registerRequest));
    }

    @Test
    void login_ShouldSucceed_WhenValidCredentials() {
        AuthRequest authRequest = new AuthRequest();
        authRequest.setEmail("test@example.com");
        authRequest.setPassword("password123");

        UserPrincipal userPrincipal = UserPrincipal.create(user);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userPrincipal);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(tokenProvider.generateToken(any())).thenReturn("accessToken");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("refreshToken");
        when(userRepository.findById(anyLong())).thenReturn(Optional.of(user));

        UserResponse userResponse = UserResponse.builder().id(1L).username("testuser").build();
        when(userService.mapToUserResponse(any(), any())).thenReturn(userResponse);

        AuthResponse response = authService.login(authRequest);

        assertNotNull(response);
        assertEquals("accessToken", response.getAccessToken());
        verify(authenticationManager, times(1)).authenticate(any());
    }

    @Test
    void refreshToken_ShouldSucceed_WhenValidToken() {
        String refreshToken = "validRefreshToken";
        when(tokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(refreshToken)).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(tokenProvider.generateToken(any())).thenReturn("newAccessToken");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("newRefreshToken");

        UserResponse userResponse = UserResponse.builder().id(1L).username("testuser").build();
        when(userService.mapToUserResponse(any(), any())).thenReturn(userResponse);

        AuthResponse response = authService.refreshToken(refreshToken);

        assertNotNull(response);
        assertEquals("newAccessToken", response.getAccessToken());
    }

    @Test
    void refreshToken_ShouldThrowException_WhenInvalidToken() {
        String refreshToken = "invalidRefreshToken";
        when(tokenProvider.validateToken(refreshToken)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> authService.refreshToken(refreshToken));
    }
}
