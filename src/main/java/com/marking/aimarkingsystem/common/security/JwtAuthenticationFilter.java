package com.marking.aimarkingsystem.common.security;

import com.marking.aimarkingsystem.common.constant.RedisKeyConstant;
import com.marking.aimarkingsystem.common.util.JwtUtil;
import com.marking.aimarkingsystem.common.util.RedisUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器。
 *
 * 执行时机：位于 Spring Security 过滤器链中、UsernamePasswordAuthenticationFilter 之前，
 * 每个请求只执行一次（OncePerRequestFilter 保证）。
 *
 * 职责：
 * 1. 从请求头 Authorization: Bearer xxx 中取出 token；
 * 2. 校验签名与有效期；
 * 3. 解析用户名与权限声明，构造 Authentication 写入 SecurityContextHolder；
 * 4. 后续的授权判断（如 PermissionAspect、authorizeHttpRequests）从这里取身份。
 *
 * 注意：本过滤器不负责"登录"，它只负责"带着 token 来的请求是谁"。
 *
 * <h3>高可用与降级取舍（生产环境）</h3>
 * 生产环境推荐 Redis 主从 + 哨兵（Sentinel）架构：主节点故障时哨兵自动选举新主，
 * Lettuce 通过哨兵重新发现主节点并重连，避免 Redis 单点宕机导致登录态校验整体不可用。
 * <p>
 * 但哨兵只解决"单点"问题：如果哨兵集群整体不可用（全部宕机或网络隔离），
 * 本过滤器会进入 fail-open 降级——RedisUtil.get 捕获异常后返回 null，
 * 此时只校验 JWT 签名与有效期即放行请求。
 * <p>
 * 取舍说明（可用性优先于强一致性）：
 * 降级期间"登出立即失效 / 后登录挤掉先登录"能力失效，已登出的旧 token 在自身过期前仍可通过鉴权；
 * 因此生产环境不要把 JWT 过期时间设得过长（当前默认 30 分钟），
 * 并对改密码、删除等高风险操作增加二次校验。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTH_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    /** 登录时写入 JWT 的权限声明名，值为权限码/角色集合 */
    private static final String CLAIM_AUTHORITIES = "authorities";

    private final JwtUtil jwtUtil;

    private final RedisUtil redisUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, RedisUtil redisUtil) {
        this.jwtUtil = jwtUtil;
        this.redisUtil = redisUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        // 已有认证信息时不重复解析，避免覆盖
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null
                && jwtUtil.validateToken(token)) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                String username = claims.getSubject();

                // 与 Redis 中该用户当前有效的 token 比对：
                // - Redis 可用时，登录会覆盖写入，旧 token 比对不通过 → 立即失效（后登录挤掉先登录）；
                // - Redis 不可用 / 降级模式时 redisUtil.get 返回 null，此时信任 JWT 本身的签名与有效期
                //   （前面已通过 jwtUtil.validateToken 校验），保证无 Redis 也能正常鉴权。
                //
                // 高可用说明：
                // 1. 生产环境用 Redis 主从 + 哨兵，主节点故障由哨兵自动切换，Lettuce 发现新主后自动重连，
                //    切换窗口内的 get 失败会短暂降级为下面第 2 点的 fail-open，恢复后自动回到严格比对；
                // 2. 哨兵集群整体不可用时同样走 fail-open：仅校验签名与有效期放行，
                //    代价是"踢下线 / 单账号单会话"失效（见类注释中的取舍说明）。
                String currentToken = redisUtil.get(RedisKeyConstant.LOGIN_TOKEN + username);
                if (currentToken != null && !token.equals(currentToken)) {
                    log.info("token 已失效（被新登录挤掉或已退出登录）: username={}", username);
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }

                List<GrantedAuthority> authorities = parseAuthorities(claims.get(CLAIM_AUTHORITIES));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // token 结构异常或 Redis 不可用时一律不放行，也不让异常冒泡成 500
                log.warn("JWT 解析失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /** 从 Authorization 头解析 Bearer token，缺失或格式不对返回 null */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header == null || header.isBlank() || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 解析权限声明。
     * 兼容两种写法：登录时可存字符串 "ROLE_ADMIN,user:list"（逗号分隔），
     * 也可直接存集合 ["ROLE_ADMIN","user:list"]。
     */
    private List<GrantedAuthority> parseAuthorities(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }

        Collection<?> items;
        if (raw instanceof Collection<?> collection) {
            items = collection;
        } else {
            items = Arrays.asList(raw.toString().split(","));
        }

        return items.stream()
                .map(item -> item == null ? "" : item.toString().trim())
                .filter(item -> !item.isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
