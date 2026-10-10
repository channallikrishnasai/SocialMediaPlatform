package com.socialmedia.platform.controller;

import com.socialmedia.platform.dto.PagedResponse;
import com.socialmedia.platform.dto.PostResponse;
import com.socialmedia.platform.service.FeedService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
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
@RequestMapping("/api/feed")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Feed", description = "Personalized feed endpoints")
public class FeedController {

    private final FeedService feedService;

    @GetMapping
    @RateLimiter(name = "feedApi")
    @Operation(summary = "Get personalized feed")
    public ResponseEntity<PagedResponse<PostResponse>> getPersonalizedFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(feedService.getPersonalizedFeed(authentication, pageable));
    }
}
