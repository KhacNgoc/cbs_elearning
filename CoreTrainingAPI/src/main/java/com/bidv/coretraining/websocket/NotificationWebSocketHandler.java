package com.bidv.coretraining.websocket;

import com.bidv.coretraining.dto.NotificationDto;
import com.bidv.coretraining.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    private static final long AUTH_TIMEOUT_MS = 10_000;
    private static final long IDLE_TIMEOUT_MS = 60_000;

    private final JwtTokenProvider tokenProvider;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebSocketSession> pendingSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastSeen = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(JwtTokenProvider tokenProvider, ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        long now = Instant.now().toEpochMilli();
        session.getAttributes().put("connectedAt", now);
        pendingSessions.put(session.getId(), session);
        lastSeen.put(session.getId(), now);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        lastSeen.put(session.getId(), Instant.now().toEpochMilli());
        JsonNode payload = objectMapper.readTree(message.getPayload());
        String type = payload.path("type").asText();
        if ("AUTH".equals(type)) {
            authenticate(session, payload.path("token").asText());
        } else if ("PING".equals(type) && session.getAttributes().containsKey("username")) {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
        }
    }

    private void authenticate(WebSocketSession session, String token) throws IOException {
        if (!tokenProvider.validateToken(token)) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Invalid token"));
            return;
        }
        String username = tokenProvider.getUsernameFromJWT(token);
        session.getAttributes().put("username", username);
        pendingSessions.remove(session.getId());
        sessionsByUser.computeIfAbsent(username, ignored -> ConcurrentHashMap.newKeySet())
                .add(session);
        session.sendMessage(new TextMessage("{\"type\":\"AUTHENTICATED\"}"));
    }

    public void sendToUser(String username, NotificationDto notification) {
        Set<WebSocketSession> sessions = sessionsByUser.get(username);
        if (sessions == null) return;
        try {
            String payload = objectMapper.writeValueAsString(
                    java.util.Map.of("type", "NOTIFICATION", "data", notification));
            sessions.removeIf(session -> !send(session, payload));
        } catch (Exception ignored) {
            // Persistence remains the source of truth if realtime delivery fails.
        }
    }

    private boolean send(WebSocketSession session, String payload) {
        try {
            if (!session.isOpen()) return false;
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException exception) {
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
            boolean authenticated = session.getAttributes().containsKey("username");
            long timeout = authenticated ? IDLE_TIMEOUT_MS : AUTH_TIMEOUT_MS;
            if (now - seenAt > timeout) closeQuietly(session, CloseStatus.SESSION_NOT_RELIABLE);
        });
    }

    private WebSocketSession findSession(String id) {
        WebSocketSession pending = pendingSessions.get(id);
        if (pending != null) return pending;
        return sessionsByUser.values().stream()
                .flatMap(Set::stream)
                .filter(session -> session.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) session.close(status);
        } catch (IOException ignored) {
        } finally {
            removeSession(session);
        }
    }

    private void removeSession(WebSocketSession session) {
        lastSeen.remove(session.getId());
        pendingSessions.remove(session.getId());
        Object username = session.getAttributes().get("username");
        if (username != null) {
            Set<WebSocketSession> sessions = sessionsByUser.get(username.toString());
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) sessionsByUser.remove(username.toString());
            }
        }
    }
}
