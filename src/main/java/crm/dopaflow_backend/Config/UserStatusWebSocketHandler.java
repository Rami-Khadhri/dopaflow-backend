package crm.dopaflow_backend.Config;

import crm.dopaflow_backend.Model.User;
import crm.dopaflow_backend.Service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserStatusWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(UserStatusWebSocketHandler.class);
    private final Map<Long, List<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final UserService userService;

    @Autowired
    public UserStatusWebSocketHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = extractUserId(session);
        if (userId != null) {
            sessions.computeIfAbsent(userId, k -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(session);
            userService.updateLastActive(userId, Instant.now());
            broadcastActivity(userId, "online");
            logger.info("User {} connected. Total sessions: {}", userId, sessions.get(userId).size());
        } else {
            session.close(CloseStatus.SERVER_ERROR.withReason("Invalid userId"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Long userId = extractUserId(session);
        if (userId != null) {
            List<WebSocketSession> userSessions = sessions.get(userId);
            if (userSessions != null) {
                userSessions.remove(session);
                if (userSessions.isEmpty()) {
                    sessions.remove(userId);
                    userService.updateLastActive(userId, Instant.now());
                    broadcastActivity(userId, "offline");
                }
            }
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = extractUserId(session);
        if (userId != null) {
            userService.updateLastActive(userId, Instant.now());
            broadcastActivity(userId, "online");
        }
    }

    private Long extractUserId(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query == null || !query.contains("userId=")) {
            logger.warn("Query string is missing or doesn't contain userId parameter");
            return null;
        }

        String userIdParam = query.split("userId=")[1].split("&")[0];
        try {
            return Long.parseLong(userIdParam);
        } catch (NumberFormatException e) {
            logger.debug("userId is not a number, trying to find user by email: {}", userIdParam);
            User user = userService.getUserByEmail(userIdParam);
            if (user == null) {
                logger.warn("No user found for email: {}", userIdParam);
                return null;
            }
            return user.getId();
        }
    }

    private void broadcastActivity(Long userId, String activity) {
        User user = userService.getUserByHisId(userId);
        Instant lastActiveTime = user.getLastActive();
        String message = String.format("{\"userId\": %d, \"activity\": \"%s\", \"lastActive\": %s}",
                userId, activity, lastActiveTime != null ? lastActiveTime.toEpochMilli() : "null");

        sessions.values().stream()
                .flatMap(List::stream)
                .filter(WebSocketSession::isOpen)
                .forEach(session -> {
                    try {
                        session.sendMessage(new TextMessage(message));
                    } catch (Exception e) {
                        logger.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
                    }
                });
    }

    public boolean isUserOnline(Long userId) {
        return sessions.containsKey(userId) && !sessions.get(userId).isEmpty();
    }
}