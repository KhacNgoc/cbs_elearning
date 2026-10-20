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
    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.security.UserDetailsServiceImpl detailsService;
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

        com.bidv.coretraining.entity.User user;
        try {
            user = ((com.bidv.coretraining.security.CustomUserDetails) detailsService
                    .loadUserById(tokenProvider.getUserIdFromJWT(token))).getUser();
        } catch (Exception ex) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String username = user.getUsername();
        if (!tokenProvider.matchesUser(token, user)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put("token", token);
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
                .flatMap(header -> java.util.Arrays.stream(header.split(";")))
                .map(String::trim)
                .filter(cookie -> cookie.startsWith(JwtCookieService.COOKIE_NAME + "="))
                .map(cookie -> cookie.substring(cookie.indexOf('=') + 1))
                .findFirst()
                .orElse(null);
    }
}
