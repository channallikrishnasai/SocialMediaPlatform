package com.socialmedia.platform.security;

import java.security.Principal;

/**
 * Identifies a STOMP session by user id (as a string) so that
 * SimpMessagingTemplate.convertAndSendToUser(userId.toString(), ...) can route to it -
 * see WebSocketAuthChannelInterceptor, which is what actually attaches this to the session.
 */
public record StompPrincipal(String name) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
