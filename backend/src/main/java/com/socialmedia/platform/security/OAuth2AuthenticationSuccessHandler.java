package com.socialmedia.platform.security;

import com.socialmedia.platform.entity.User;
import com.socialmedia.platform.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

/**
 * Issues an app JWT for a successful Google OAuth2 login and redirects to the frontend with the
 * tokens as query parameters. Requires real GOOGLE_CLIENT_ID/GOOGLE_CLIENT_SECRET to exercise
 * end-to-end; not runtime-verified in this environment for lack of real Google credentials.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;

    @Value("${app.oauth2.redirect-uri:http://localhost:3000/oauth2/redirect}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String sub = oAuth2User.getAttribute("sub");

        if (email == null || sub == null) {
            log.error("OAuth2 login succeeded but provider did not return email/sub attributes");
            response.sendRedirect(UriComponentsBuilder.fromUriString(redirectUri)
                    .queryParam("error", "oauth2_missing_attributes")
                    .build().toUriString());
            return;
        }

        User user = userRepository.findByOauthProviderAndOauthId("google", sub)
                .or(() -> userRepository.findByEmail(email))
                .map(existing -> linkGoogleAccount(existing, sub))
                .orElseGet(() -> createOAuthUser(email, sub));

        user = userRepository.save(user);

        UserPrincipal principal = UserPrincipal.create(user);
        Authentication appAuthentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        String accessToken = tokenProvider.generateToken(appAuthentication);
        String refreshToken = tokenProvider.generateRefreshToken(appAuthentication);

        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        response.sendRedirect(targetUrl);
    }

    private User linkGoogleAccount(User existing, String sub) {
        if (existing.getOauthId() == null) {
            existing.setOauthProvider("google");
            existing.setOauthId(sub);
        }
        return existing;
    }

    private User createOAuthUser(String email, String sub) {
        return User.builder()
                .username(generateUniqueUsername(email))
                .email(email)
                .oauthProvider("google")
                .oauthId(sub)
                .role(User.Role.USER)
                .enabled(true)
                .emailVerified(true)
                .build();
    }

    private String generateUniqueUsername(String email) {
        String base = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9_]", "");
        if (base.isBlank()) {
            base = "user";
        }
        String candidate = base;
        int suffix = 0;
        while (userRepository.existsByUsername(candidate)) {
            suffix++;
            candidate = base + suffix;
        }
        return candidate;
    }
}
