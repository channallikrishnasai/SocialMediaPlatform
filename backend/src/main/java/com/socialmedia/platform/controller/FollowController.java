package com.socialmedia.platform.controller;

import com.socialmedia.platform.dto.PagedResponse;
import com.socialmedia.platform.dto.UserResponse;
import com.socialmedia.platform.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Follow", description = "Follow/Unfollow endpoints")
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{userId}/follow")
    @Operation(summary = "Follow a user")
    public ResponseEntity<Void> followUser(
            @PathVariable Long userId,
            Authentication authentication) {
        followService.followUser(userId, authentication);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}/follow")
    @Operation(summary = "Unfollow a user")
    public ResponseEntity<Void> unfollowUser(
            @PathVariable Long userId,
            Authentication authentication) {
        followService.unfollowUser(userId, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/followers")
    @Operation(summary = "Get user's followers")
    public ResponseEntity<PagedResponse<UserResponse>> getFollowers(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(followService.getFollowers(userId, pageable, authentication));
    }

    @GetMapping("/{userId}/following")
    @Operation(summary = "Get users that this user is following")
    public ResponseEntity<PagedResponse<UserResponse>> getFollowing(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(followService.getFollowing(userId, pageable, authentication));
    }
}
