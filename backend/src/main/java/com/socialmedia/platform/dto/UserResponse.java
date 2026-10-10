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
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String bio;
    private String avatarUrl;
    private String role;
    private Long followersCount;
    private Long followingCount;
    private Boolean isFollowing;
    private LocalDateTime createdAt;
}
