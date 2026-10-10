package com.socialmedia.platform.service;

import com.socialmedia.platform.dto.UserResponse;
import com.socialmedia.platform.entity.User;
import com.socialmedia.platform.exception.ForbiddenException;
import com.socialmedia.platform.exception.ResourceNotFoundException;
import com.socialmedia.platform.repository.FollowRepository;
import com.socialmedia.platform.repository.UserRepository;
import com.socialmedia.platform.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId, Authentication authentication) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        return mapToUserResponse(user, authentication);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByUsername(String username, Authentication authentication) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));

        return mapToUserResponse(user, authentication);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));

        return mapToUserResponse(user, authentication);
    }

    @Transactional
    public UserResponse updateProfile(Long userId, String bio, String avatarUrl, Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        if (!userId.equals(userPrincipal.getId())) {
            throw new ForbiddenException("Unauthorized to update this profile");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (bio != null) {
            user.setBio(bio);
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl);
        }

        user = userRepository.save(user);
        return mapToUserResponse(user, authentication);
    }

    public UserResponse mapToUserResponse(User user, Authentication authentication) {
        Long followersCount = userRepository.countFollowers(user.getId());
        Long followingCount = userRepository.countFollowing(user.getId());

        Boolean isFollowing = false;
        if (authentication != null) {
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            isFollowing = followRepository.existsByFollowerIdAndFollowingId(
                    userPrincipal.getId(), user.getId()
            );
        }

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .bio(user.getBio())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole().name())
                .followersCount(followersCount)
                .followingCount(followingCount)
                .isFollowing(isFollowing)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
