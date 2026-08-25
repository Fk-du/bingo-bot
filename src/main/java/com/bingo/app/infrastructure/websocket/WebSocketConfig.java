package com.bingo.app.infrastructure.websocket;

import com.bingo.app.infrastructure.security.TelegramAuthService;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.master.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TelegramAuthService telegramAuthService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new WebSocketAuthHandshakeInterceptor(telegramAuthService))
                .withSockJS();

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new WebSocketAuthHandshakeInterceptor(telegramAuthService));
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(new StompConnectAuthInterceptor(telegramAuthService));
    }

    /**
     * HTTP handshake interceptor — authenticates the initial WebSocket upgrade request.
     * Extracts Telegram initData from the "token" query parameter.
     */
    @RequiredArgsConstructor
    private static class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {
        private final TelegramAuthService telegramAuthService;

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            try {
                String token = extractTokenFromUri(request.getURI());
                if (token != null) {
                    User user = telegramAuthService.authenticate(token);
                    if (user != null) {
                        UserPrincipal principal = new UserPrincipal(user);
                        attributes.put("userPrincipal", principal);
                        attributes.put("userPrincipal_auth",
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                        log.debug("WebSocket handshake authenticated: {}", user.getTelegramId());
                        return true;
                    }
                }
                log.debug("WebSocket handshake: no valid token — allowing connection (auth required on STOMP CONNECT)");
                return true;
            } catch (Exception e) {
                log.error("WebSocket handshake auth error: {}", e.getMessage());
                return true;
            }
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }

        private String extractTokenFromUri(URI uri) {
            String query = uri.getQuery();
            if (query == null) return null;
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    return kv[1];
                }
            }
            return null;
        }
    }

    /**
     * STOMP CONNECT interceptor — authenticates from "Authorization: tma <token>" header.
     * This is the primary auth path for STOMP connections.
     */
    @RequiredArgsConstructor
    private static class StompConnectAuthInterceptor implements ChannelInterceptor {
        private final TelegramAuthService telegramAuthService;

        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

            if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                String token = extractToken(accessor);

                if (token != null) {
                    try {
                        User user = telegramAuthService.authenticate(token);
                        if (user != null) {
                            UserPrincipal principal = new UserPrincipal(user);
                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                            accessor.setUser(auth);
                            log.debug("WebSocket STOMP CONNECT authenticated: {}", user.getTelegramId());
                            return message;
                        }
                    } catch (Exception e) {
                        log.error("WebSocket STOMP CONNECT auth error: {}", e.getMessage());
                    }
                }

                log.warn("WebSocket STOMP CONNECT rejected: invalid or missing auth token");
                accessor.setLeaveMutable(true);
                return message;
            }

            return message;
        }

        private String extractToken(StompHeaderAccessor accessor) {
            List<String> authHeaders = accessor.getNativeHeader("Authorization");
            if (authHeaders != null && !authHeaders.isEmpty()) {
                String authHeader = authHeaders.get(0);
                if (authHeader.startsWith("tma ")) {
                    return authHeader.substring(4);
                }
            }

            List<String> tokenHeaders = accessor.getNativeHeader("token");
            if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
                return tokenHeaders.get(0);
            }

            return null;
        }
    }
}