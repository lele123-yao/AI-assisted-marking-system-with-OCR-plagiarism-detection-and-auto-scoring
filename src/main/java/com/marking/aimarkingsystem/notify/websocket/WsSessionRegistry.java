package com.marking.aimarkingsystem.notify.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本实例的 WebSocket 会话注册表。
 *
 * 为什么按 userId 分组：一个用户可能同时打开桌面端、手机端、多个标签页，
 * 推送时要发给他当前所有的连接（Set 去重），任一连接关闭只移除自己。
 *
 * 线程安全：
 * - 会话集合用 ConcurrentHashMap.newKeySet()，注册/注销无锁；
 * - WebSocketSession.sendMessage 本身不是线程安全的（同一个会话并发写会报
 *   "TEXT_PARTIAL_WRITING" 之类的状态错误），因此发送时对单个 session 加锁。
 *
 * 注意：本注册表只保存"连在本实例"的会话。集群下其余实例的会话由各自的注册表负责，
 * 这正是通知事件要用 Fanout 广播的原因。
 */
@Component
public class WsSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(WsSessionRegistry.class);

    /** userId → 该用户的全部在线会话 */
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public WsSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 新建连接时登记 */
    public void add(Long userId, WebSocketSession session) {
        if (userId == null) {
            return;
        }
        userSessions.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket 已连接: userId={}, sessionId={}, 本实例该用户会话数={}",
                userId, session.getId(), userSessions.get(userId).size());
    }

    /** 连接关闭时注销 */
    public void remove(Long userId, WebSocketSession session) {
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        // 空集合及时移除，避免长期运行后 map 里堆积大量空 Set
        if (sessions.isEmpty()) {
            userSessions.remove(userId, sessions);
        }
    }

    /**
     * 向指定用户在本实例的所有会话推送 JSON 消息。
     * 用户不在本实例（或已下线）时直接返回，不做任何重试——站内信已落库可补查。
     */
    public void sendToUser(Long userId, Object payload) {
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("通知消息序列化失败: userId={}, error={}", userId, e.getMessage());
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                remove(userId, session);
                continue;
            }
            try {
                // 同一 session 可能被多个 RabbitMQ 监听线程并发推送，必须串行写
                synchronized (session) {
                    session.sendMessage(message);
                }
            } catch (Exception e) {
                log.warn("WebSocket 推送失败，主动关闭会话: userId={}, sessionId={}, error={}",
                        userId, session.getId(), e.getMessage());
                closeQuietly(session);
                remove(userId, session);
            }
        }
    }

    /** 本实例当前在线用户数（仅用于日志/排查） */
    public int onlineUserCount() {
        return userSessions.size();
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (Exception ignored) {
            // 连接已损坏，关闭失败无需处理
        }
    }
}
