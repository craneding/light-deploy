package com.lightdeploy.backend.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class DeployLogWebSocketHandler extends TextWebSocketHandler {

    // Store sessions by task ID: taskId -> list of sessions
    private final Map<String, CopyOnWriteArrayList<WebSocketSession>> taskSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String query = session.getUri().getQuery();
        String taskId = extractTaskId(query);
        if (taskId != null) {
            taskSessions.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Can handle incoming messages from client if needed (e.g., ping)
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String query = session.getUri().getQuery();
        String taskId = extractTaskId(query);
        if (taskId != null) {
            CopyOnWriteArrayList<WebSocketSession> sessions = taskSessions.get(taskId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    taskSessions.remove(taskId);
                }
            }
        }
    }

    public void sendLog(String taskId, String logMessage) {
        CopyOnWriteArrayList<WebSocketSession> sessions = taskSessions.get(taskId);
        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(logMessage));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private String extractTaskId(String query) {
        if (query == null) return null;
        String[] params = query.split("&");
        for (String param : params) {
            String[] keyValue = param.split("=");
            if (keyValue.length == 2 && "taskId".equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }
}
