package com.bidv.coretraining.websocket;

import com.bidv.coretraining.security.JwtTokenProvider;
import com.bidv.coretraining.security.JwtCookieService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    public static final String USERNAME_ATTRIBUTE = "username";
    private final JwtTokenProvider tokenProvider;

    public JwtHandshakeInterceptor(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token) || !tokenProvider.validateToken(token)) {
            log.warn("Rejected WebSocket handshake remote={} reason=invalid_or_missing_token",
                    request.getRemoteAddress());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String username = tokenProvider.getUsernameFromJWT(token);
        attributes.put(USERNAME_ATTRIBUTE, username);
        log.debug("Accepted WebSocket handshake user={} remote={}",
                username, request.getRemoteAddress());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        if (exception != null) {
            log.warn("WebSocket handshake failed remote={}", request.getRemoteAddress(), exception);
        }
    }

    private String resolveToken(ServerHttpRequest request) {
        return request.getHeaders().getOrEmpty("Cookie").stream()
                .flatMap(header -> java.net.HttpCookie.parse(header).stream())
                .filter(cookie -> JwtCookieService.COOKIE_NAME.equals(cookie.getName()))
                .map(java.net.HttpCookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
