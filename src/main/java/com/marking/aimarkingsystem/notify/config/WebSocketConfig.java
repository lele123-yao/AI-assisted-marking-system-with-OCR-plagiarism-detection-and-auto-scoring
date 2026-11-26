package com.marking.aimarkingsystem.notify.config;

import com.marking.aimarkingsystem.notify.websocket.JwtHandshakeInterceptor;
import com.marking.aimarkingsystem.notify.websocket.MarkNotifyWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 端点注册。
 *
 * - 路径 /ws/mark：前端用 ws(s)://host/ws/mark?token=xxx 连接；
 * - 裸 WebSocket，不使用 SockJS：本项目只面向现代浏览器与微信内置浏览器，
 *   且 SockJS 会额外引入 /ws/mark/** 的 HTTP 回退路径，增加安全面；
 * - allowedOriginPatterns("*")：开发环境前端 5173、后端 8080 不同源，
 *   生产环境若前后端同域则天然满足；鉴权由握手拦截器负责，不依赖 Origin。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MarkNotifyWebSocketHandler markNotifyWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public WebSocketConfig(MarkNotifyWebSocketHandler markNotifyWebSocketHandler,
                           JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.markNotifyWebSocketHandler = markNotifyWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(markNotifyWebSocketHandler, "/ws/mark")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
