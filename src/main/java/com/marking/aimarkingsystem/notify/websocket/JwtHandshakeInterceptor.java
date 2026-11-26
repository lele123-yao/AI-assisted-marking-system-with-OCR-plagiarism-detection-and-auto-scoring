package com.marking.aimarkingsystem.notify.websocket;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.common.constant.RedisKeyConstant;
import com.marking.aimarkingsystem.common.util.JwtUtil;
import com.marking.aimarkingsystem.common.util.RedisUtil;
import com.marking.aimarkingsystem.user.entity.User;
import com.marking.aimarkingsystem.user.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * WebSocket 握手鉴权拦截器。
 *
 * 为什么 token 走 query 参数而不是 Authorization 头：
 * 浏览器原生 WebSocket API 不支持自定义请求头，只能把 token 拼在 URL 上。
 *
 * 校验与 HTTP 请求保持一致（不能只验签名，否则被"后登录挤掉"的旧 token 仍能连上）：
 * 1. JWT 签名与有效期；
 * 2. Redis 中 login:token:{username} 与本次 token 比对（Redis 不可用时降级为信任 JWT）；
 * 3. 账号必须存在于 sys_user。
 * 任一步失败 → HTTP 401 拒绝握手，前端 onclose 后按退避策略重连。
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    /** 握手成功后写入会话 attributes 的键：用户ID */
    public static final String ATTR_USER_ID = "userId";
    /** 握手成功后写入会话 attributes 的键：用户名 */
    public static final String ATTR_USERNAME = "username";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final UserMapper userMapper;

    public JwtHandshakeInterceptor(JwtUtil jwtUtil, RedisUtil redisUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
        this.userMapper = userMapper;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token) || !jwtUtil.validateToken(token)) {
            log.info("WebSocket 握手被拒绝：token 缺失或无效");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        String username;
        try {
            username = jwtUtil.getUsername(token);
        } catch (Exception e) {
            // validateToken 已校验过，这里理论上不会发生；兜底防止异常冒泡成 500
            log.warn("WebSocket 握手解析 token 失败: {}", e.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // 单账号单会话：与 HTTP 一致，旧 token 被新登录挤掉后不允许再建连
        String currentToken = redisUtil.get(RedisKeyConstant.LOGIN_TOKEN + username);
        if (currentToken != null && !currentToken.equals(token)) {
            log.info("WebSocket 握手被拒绝：token 已失效（被新登录挤掉或已退出）: username={}", username);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (user == null) {
            log.info("WebSocket 握手被拒绝：账号不存在: username={}", username);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(ATTR_USER_ID, user.getId());
        attributes.put(ATTR_USERNAME, username);
        log.debug("WebSocket 握手通过: userId={}, username={}", user.getId(), username);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需额外处理
    }

    /**
     * 优先取 URL 上的 token（浏览器 WebSocket），
     * 兜底取 Authorization 头（服务端/脚本客户端可自行设置）。
     */
    private String resolveToken(ServerHttpRequest request) {
        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
        if (StringUtils.hasText(token)) {
            return token.trim();
        }
        String header = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
