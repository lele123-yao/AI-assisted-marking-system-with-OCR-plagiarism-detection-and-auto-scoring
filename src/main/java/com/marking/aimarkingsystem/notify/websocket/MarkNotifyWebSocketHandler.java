package com.marking.aimarkingsystem.notify.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 阅卷完成通知的 WebSocket 端点处理器。
 *
 * 职责很薄：
 * - 连接建立：把 session 登记到本实例注册表（userId 由握手拦截器写入 attributes）；
 * - 收到 "ping"：回 "pong"，用于前端心跳保活（防止 Nginx/网关空闲超时断连）；
 * - 连接关闭/异常：从注册表移除，避免向死连接推送。
 */
@Component
public class MarkNotifyWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MarkNotifyWebSocketHandler.class);

    private final WsSessionRegistry sessionRegistry;

    public MarkNotifyWebSocketHandler(WsSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = resolveUserId(session);
        if (userId == null) {
            // 理论上不会发生：握手成功一定写入了 userId；兜底关闭非法连接
            log.warn("WebSocket 连接缺少 userId，主动关闭: sessionId={}", session.getId());
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {
                // 关闭失败无需处理
            }
            return;
        }
        sessionRegistry.add(userId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 只处理心跳，其余内容忽略（本通道是单向推送，不接收业务指令）
        if ("ping".equalsIgnoreCase(message.getPayload())) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage("pong"));
                }
            } catch (Exception e) {
                log.debug("回复心跳失败: sessionId={}, error={}", session.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionRegistry.remove(resolveUserId(session), session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WebSocket 传输异常: sessionId={}, error={}", session.getId(), exception.getMessage());
        sessionRegistry.remove(resolveUserId(session), session);
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (Exception ignored) {
            // 连接已断开，关闭失败无需处理
        }
    }

    private Long resolveUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_ID);
        return value instanceof Long id ? id : null;
    }
}
