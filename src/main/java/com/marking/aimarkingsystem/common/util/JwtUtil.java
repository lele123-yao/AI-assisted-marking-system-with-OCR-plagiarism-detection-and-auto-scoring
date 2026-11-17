package com.marking.aimarkingsystem.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类（基于 JJWT 0.12.x）。
 *
 * 说明：
 * - 密钥与过期时间从 application.yml 的 jwt.secret / jwt.expiration 读取，
 *   不写死在代码里，不同环境（dev/prod）可用不同配置；
 * - 本类只负责"生成 / 解析 / 校验"，token 的存取（黑名单、续期）由 RedisUtil 配合完成；
 * - 算法使用 HS256（HMAC-SHA256），要求密钥不少于 32 字节。
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** 签名密钥，至少 32 字节 */
    @Value("${jwt.secret}")
    private String secret;

    /** 有效期，单位毫秒 */
    @Value("${jwt.expiration}")
    private Long expiration;

    /** 由配置密钥生成签名 Key（HS256 自动匹配） */
    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token，载荷只放用户名。
     * 注意：不要把密码、手机号等敏感信息放进 JWT，Payload 只是 Base64 编码，任何人可解码。
     */
    public String generateToken(String username) {
        return generateToken(username, new HashMap<>());
    }

    /**
     * 生成 token，可附带自定义声明（如 userId、角色编码），供后续业务模块扩展。
     */
    public String generateToken(String username, Map<String, Object> claims) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expiration);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(getSignKey())
                .compact();
    }

    /**
     * 解析 token，返回全部载荷。
     * 签名错误或格式非法会抛出 JwtException，过期会抛出 ExpiredJwtException。
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从 token 中取出用户名（subject） */
    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 校验 token 是否有效（签名正确且未过期）。
     * 只返回布尔值，不抛出异常，适合在过滤器中直接做判断。
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT 已过期: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 校验失败: {}", e.getMessage());
        }
        return false;
    }

    /** 获取配置的过期毫秒数，用于让 Redis 中 token 的 TTL 与 JWT 有效期保持一致 */
    public long getExpiration() {
        return expiration;
    }

    /** 获取 token 的过期时间点 */
    public Date getExpirationDate(String token) {
        return parseToken(token).getExpiration();
    }
}
