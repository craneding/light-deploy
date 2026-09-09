package com.lightdeploy.backend.websocket;

import org.springframework.scheduling.annotation.Scheduled;
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

    /**
     * 应用层心跳帧：慢任务长时间无输出时，靠它产生流量，
     * 防止中间代理（Nginx proxy_read_timeout 默认 60s）掐断空闲连接。
     * 心跳走 handler 直发，不经过 DeployLogger，因此不会落盘污染日志文件；
     * 前端 onmessage 负责过滤丢弃。
     */
    public static final String HEARTBEAT_PAYLOAD = "__light_deploy_heartbeat__";

    /** 心跳间隔：小于 Nginx 默认 60s 并留余量 */
    private static final long HEARTBEAT_INTERVAL_MS = 25000;

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

    /**
     * 定时心跳：向所有 open 会话发送心跳帧保活，顺手清理已关闭会话。
     * 需要 {@code @EnableScheduling}（见 BackendApplication）。
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void sendHeartbeat() {
        for (Map.Entry<String, CopyOnWriteArrayList<WebSocketSession>> entry : taskSessions.entrySet()) {
            CopyOnWriteArrayList<WebSocketSession> sessions = entry.getValue();
            sessions.removeIf(session -> !session.isOpen());
            if (sessions.isEmpty()) {
                taskSessions.remove(entry.getKey(), sessions);
                continue;
            }
            for (WebSocketSession session : sessions) {
                try {
                    session.sendMessage(new TextMessage(HEARTBEAT_PAYLOAD));
                } catch (IOException e) {
                    e.printStackTrace();
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
