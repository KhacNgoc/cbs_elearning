package com.bidv.coretraining.websocket;

import com.bidv.coretraining.notification.dto.NotificationDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    private static final long IDLE_TIMEOUT_MS = 60_000;

    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.security.UserDetailsServiceImpl detailsService;
    @org.springframework.beans.factory.annotation.Autowired
    private com.bidv.coretraining.security.JwtTokenProvider tokens;

    private boolean authorized(WebSocketSession session) {
        Object token = session.getAttributes().get("token");
        Object username = session.getAttributes().get(JwtHandshakeInterceptor.USERNAME_ATTRIBUTE);
        if (token == null || username == null)
            return false;
        try {
            return tokens.matchesUser(token.toString(),
                    ((com.bidv.coretraining.security.CustomUserDetails) detailsService
                            .loadUserById(tokens.getUserIdFromJWT(token.toString()))).getUser());
        } catch (Exception ex) {
            return false;
        }
    }

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebSocketSession> sessionsById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastSeen = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        long now = Instant.now().toEpochMilli();
        String username = (String) session.getAttributes()
                .get(JwtHandshakeInterceptor.USERNAME_ATTRIBUTE);
        if (username == null) {
            log.warn("Closing unauthenticated WebSocket sessionId={}", session.getId());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Unauthenticated"));
            return;
        }

        String payload = objectMapper.writeValueAsString(
                Map.of("type", "AUTHENTICATED"));
        sessionsByUser.computeIfAbsent(username, ignored -> ConcurrentHashMap.newKeySet())
                .add(session);
        sessionsById.put(session.getId(), session);
        lastSeen.put(session.getId(), now);
        session.sendMessage(new TextMessage(payload));
        log.info("WebSocket connected user={} sessionId={} activeConnections={}",
                username, session.getId(), sessionsById.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (!authorized(session)) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        lastSeen.put(session.getId(), Instant.now().toEpochMilli());
        String payload = objectMapper.writeValueAsString(
                Map.of("type", "PONG"));
        if ("PING".equals(objectMapper.readTree(message.getPayload()).path("type").asText())) {
            session.sendMessage(new TextMessage(payload));
        }
    }

    public void sendToUser(String username, NotificationDto notification) {
        Set<WebSocketSession> sessions = sessionsByUser.get(username);
        if (sessions == null)
            return;
        try {
            String payload = objectMapper.writeValueAsString(
                    java.util.Map.of("type", "NOTIFICATION", "data", notification));
            sessions.forEach(session -> {
                if (!send(session, payload)) {
                    closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
                }
            });
        } catch (Exception exception) {
            log.error("Could not serialize notification for user={} notificationId={}",
                    username, notification.getId(), exception);
            // Persistence remains the source of truth if realtime delivery fails.
        }
    }

    private boolean send(WebSocketSession session, String payload) {
        try {
            if (!session.isOpen() || !authorized(session))
                return false;
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException exception) {
            log.debug("WebSocket send failed sessionId={}", session.getId(), exception);
            return false;
        }
    }

    @Scheduled(fixedDelay = 15_000)
    public void closeStaleConnections() {
        long now = Instant.now().toEpochMilli();
        lastSeen.forEach((sessionId, seenAt) -> {
            WebSocketSession session = findSession(sessionId);
            if (session == null) {
                lastSeen.remove(sessionId);
                return;
            }
            if (!authorized(session) || now - seenAt > IDLE_TIMEOUT_MS) {
                log.warn("Closing stale WebSocket sessionId={} idleMs={}",
                        sessionId, now - seenAt);
                closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
            }
        });
    }

    private WebSocketSession findSession(String id) {
        return sessionsById.get(id);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session);
        log.info("WebSocket disconnected sessionId={} status={} activeConnections={}",
                session.getId(), status.getCode(), sessionsById.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket transport error sessionId={}", session.getId(), exception);
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen())
                session.close(status);
        } catch (IOException ignored) {
            log.debug("Could not close WebSocket sessionId={}", session.getId(), ignored);
        } finally {
            removeSession(session);
        }
    }

    private void removeSession(WebSocketSession session) {
        String sessionId = session.getId();
        lastSeen.remove(sessionId);
        sessionsById.remove(sessionId, session);
        Object username = session.getAttributes().get(JwtHandshakeInterceptor.USERNAME_ATTRIBUTE);
        if (username != null) {
            sessionsByUser.computeIfPresent(username.toString(), (key, sessions) -> {
                sessions.remove(session);
                return sessions.isEmpty() ? null : sessions;
            });
        }
    }
}
