package com.socialmedia.platform.service;

import com.socialmedia.platform.dto.NotificationResponse;
import com.socialmedia.platform.dto.PagedResponse;
import com.socialmedia.platform.entity.Notification;
import com.socialmedia.platform.entity.User;
import com.socialmedia.platform.event.NotificationEvent;
import com.socialmedia.platform.repository.NotificationRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    private NotificationService notificationService;

    private User user;
    private UserPrincipal userPrincipal;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).username("testuser").build();
        userPrincipal = UserPrincipal.create(user);
        authentication = mock(Authentication.class);
        lenient().when(authentication.getPrincipal()).thenReturn(userPrincipal);
    }

    @Test
    void consumeNotificationEvent_ShouldSaveAndSend() {
        NotificationEvent event = NotificationEvent.builder()
                .userId(1L)
                .type("POST_LIKED")
                .message("liked your post")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> {
            Notification n = i.getArgument(0);
            n.setId(100L);
            return n;
        });

        notificationService.consumeNotificationEvent(event);

        verify(notificationRepository).save(any(Notification.class));
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void getUserNotifications_ShouldReturnPagedResponse() {
        Notification notification = Notification.builder()
                .id(100L)
                .user(user)
                .type(Notification.NotificationType.POST_LIKED)
                .message("liked your post")
                .isRead(false)
                .build();
        Page<Notification> page = new PageImpl<>(List.of(notification));

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(1L), any())).thenReturn(page);

        PagedResponse<NotificationResponse> response = notificationService.getUserNotifications(authentication, PageRequest.of(0, 10));

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(100L, response.getContent().get(0).getId());
    }

    @Test
    void markAsRead_ShouldUpdateStatus() {
        Notification notification = Notification.builder().id(100L).user(user).isRead(false).build();
        when(notificationRepository.findById(100L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(100L, authentication);

        assertTrue(notification.getIsRead());
        verify(notificationRepository).save(notification);
    }
}
