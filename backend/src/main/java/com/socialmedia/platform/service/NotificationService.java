package com.socialmedia.platform.service;

import com.socialmedia.platform.dto.NotificationResponse;
import com.socialmedia.platform.dto.PagedResponse;
import com.socialmedia.platform.entity.Notification;
import com.socialmedia.platform.entity.User;
import com.socialmedia.platform.event.NotificationEvent;
import com.socialmedia.platform.exception.ForbiddenException;
import com.socialmedia.platform.exception.ResourceNotFoundException;
import com.socialmedia.platform.repository.NotificationRepository;
import com.socialmedia.platform.repository.UserRepository;
import com.socialmedia.platform.security.UserPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.socialmedia.platform.config.KafkaConfig.NOTIFICATION_TOPIC;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = NOTIFICATION_TOPIC, groupId = "social-media-group")
    @Transactional
    public void consumeNotificationEvent(NotificationEvent event) {
        log.info("Received notification event for user: {}", event.getUserId());
        meterRegistry.counter("kafka.notifications.consumed").increment();

        User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", event.getUserId()));

        Notification notification = Notification.builder()
                .user(user)
                .type(Notification.NotificationType.valueOf(event.getType()))
                .message(event.getMessage())
                .relatedUserId(event.getRelatedUserId())
                .relatedPostId(event.getRelatedPostId())
                .isRead(false)
                .build();

        notification = notificationRepository.save(notification);

        // Send real-time notification via WebSocket
        NotificationResponse response = mapToNotificationResponse(notification);
        messagingTemplate.convertAndSendToUser(
                event.getUserId().toString(),
                "/queue/notifications",
                response
        );
        meterRegistry.counter("websocket.notifications.sent").increment();
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getUserNotifications(Authentication authentication, Pageable pageable) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Page<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userPrincipal.getId(), pageable
        );

        return PagedResponse.<NotificationResponse>builder()
                .content(notifications.getContent().stream()
                        .map(this::mapToNotificationResponse)
                        .toList())
                .pageNumber(notifications.getNumber())
                .pageSize(notifications.getSize())
                .totalElements(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages())
                .last(notifications.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getUnreadNotifications(Authentication authentication, Pageable pageable) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Page<Notification> notifications = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(
                userPrincipal.getId(), false, pageable
        );

        return PagedResponse.<NotificationResponse>builder()
                .content(notifications.getContent().stream()
                        .map(this::mapToNotificationResponse)
                        .toList())
                .pageNumber(notifications.getNumber())
                .pageSize(notifications.getSize())
                .totalElements(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages())
                .last(notifications.isLast())
                .build();
    }

    @Transactional
    public void markAsRead(Long notificationId, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notificationId));

        if (!notification.getUser().getId().equals(userPrincipal.getId())) {
            throw new ForbiddenException("Unauthorized to mark this notification as read");
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        Page<Notification> notifications = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(
                userPrincipal.getId(), false, Pageable.unpaged()
        );

        notifications.forEach(notification -> notification.setIsRead(true));
        notificationRepository.saveAll(notifications.getContent());
    }

    @Transactional(readOnly = true)
    public Long getUnreadCount(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return notificationRepository.countByUserIdAndIsRead(userPrincipal.getId(), false);
    }

    private NotificationResponse mapToNotificationResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .type(notification.getType().name())
                .message(notification.getMessage())
                .relatedUserId(notification.getRelatedUserId())
                .relatedPostId(notification.getRelatedPostId())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
