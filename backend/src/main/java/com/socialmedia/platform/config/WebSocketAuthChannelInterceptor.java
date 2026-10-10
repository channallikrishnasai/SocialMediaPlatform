package com.socialmedia.platform.config;

import com.socialmedia.platform.security.JwtTokenProvider;
import com.socialmedia.platform.security.StompPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Without this, the STOMP session has no authenticated Principal at all (the "/ws/**"
 * handshake is permitAll at the HTTP layer, since the browser can't attach a Bearer header
 * to a WebSocket upgrade request), so SimpMessagingTemplate.convertAndSendToUser(userId, ...)
 * in NotificationService has nothing to route to and the message is silently dropped.
 * The JWT is instead passed as a STOMP CONNECT header by the client and validated here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider tokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token != null && tokenProvider.validateToken(token)) {
                Long userId = tokenProvider.getUserIdFromToken(token);
                accessor.setUser(new StompPrincipal(userId.toString()));
            } else {
                log.warn("Rejecting STOMP CONNECT with missing/invalid JWT");
            }
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            return null;
        }
        String header = authHeaders.get(0);
        return header.startsWith("Bearer ") ? header.substring(7) : header;
    }
}
