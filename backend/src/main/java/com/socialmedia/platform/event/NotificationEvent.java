package com.socialmedia.platform.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    private Long userId;
    private String type;
    private String message;
    private Long relatedUserId;
    private Long relatedPostId;
}
