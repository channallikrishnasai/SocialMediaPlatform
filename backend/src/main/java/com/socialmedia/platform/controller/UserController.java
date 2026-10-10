package com.socialmedia.platform.controller;

import com.socialmedia.platform.dto.UserResponse;
import com.socialmedia.platform.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Users", description = "User management endpoints")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<UserResponse> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(userService.getCurrentUser(authentication));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long userId, Authentication authentication) {
        return ResponseEntity.ok(userService.getUserById(userId, authentication));
    }

    @GetMapping("/username/{username}")
    @Operation(summary = "Get user by username")
    public ResponseEntity<UserResponse> getUserByUsername(@PathVariable String username, Authentication authentication) {
        return ResponseEntity.ok(userService.getUserByUsername(username, authentication));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update user profile")
    public ResponseEntity<UserResponse> updateProfile(
            @PathVariable Long userId,
            @RequestParam(required = false) String bio,
            @RequestParam(required = false) String avatarUrl,
            Authentication authentication) {
        return ResponseEntity.ok(userService.updateProfile(userId, bio, avatarUrl, authentication));
    }
}
