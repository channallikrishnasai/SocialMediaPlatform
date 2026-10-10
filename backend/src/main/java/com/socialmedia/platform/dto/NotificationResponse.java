package com.socialmedia.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private String type;
    private String message;
    private Long relatedUserId;
    private Long relatedPostId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
