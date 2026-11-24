package com.marking.aimarkingsystem.common.config;

import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.common.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * Spring Security 安全配置。
 *
 * 只做安全框架骨架，不包含任何登录 / 用户管理业务接口。
 *
 * 核心配置：
 * 1. 关闭默认登录页、HTTP Basic、表单登录、默认登出，改为纯 JWT 的无状态认证；
 * 2. 白名单接口放行（登录接口路径预留，阶段6实现后再真正生效）；
 * 3. 注册 JWT 过滤器，位置在 UsernamePasswordAuthenticationFilter 之前；
 * 4. 未登录 / 无权限时返回项目统一的 Result JSON，而不是默认的 HTML 登录页；
 * 5. 提供 BCryptPasswordEncoder，登录与新增用户时使用同一套加密规则。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 无需认证即可访问的路径（登录相关接口在阶段6实现后自动生效） */
    private static final String[] WHITE_LIST = {
            "/auth/login",
            "/auth/register",
            "/auth/captcha",
            // Knife4j / OpenAPI 文档
            "/doc.html",
            "/webjars/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/favicon.ico",
            // 错误转发
            "/error",
            // 本地文件存储回退（无 MinIO 时由后端直出文件，图片/PPT 下载需要免登录）
            "/files/**"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    /**
     * 密码编码器：BCrypt。
     * 特点：同一明文每次加密结果不同（内置随机盐），只能单向校验，
     * 因此数据库里存的密文不能被"解密"，登录时用 matches() 比对。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 阻止 JWT 过滤器被 Servlet 容器重复注册。
     * JwtAuthenticationFilter 是 @Component，Spring Boot 默认会把所有 Filter Bean
     * 注册成普通 Servlet Filter，导致它既在 Security 链里执行、又在 Servlet 链里再执行一次。
     * 这里显式禁用自动注册，只保留 Security 链中的那一份。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 前后端分离 + JWT，不需要 CSRF（CSRF 针对的是基于 Cookie 的会话）
                .csrf(AbstractHttpConfigurer::disable)
                // 启用 CORS，复用阶段2 CorsConfig 中的跨域规则
                .cors(Customizer.withDefaults())
                // 关闭默认登录页 / Basic 认证 / 默认登出，避免返回 HTML 登录页面
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // 无状态：不创建 HttpSession，身份完全由每次请求的 JWT 决定
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 浏览器跨域预检请求直接放行
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(WHITE_LIST).permitAll()
                        // 其余接口全部要求已认证；更细的权限码由 @RequiresPermission 控制
                        .anyRequest().authenticated())
                // 认证 / 授权失败时输出统一 JSON（HTTP 状态码 401 / 403）
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                // JWT 过滤器放在用户名密码过滤器之前，先建立身份再做授权
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 未携带有效 token 访问受保护接口时返回 401 */
    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) ->
                writeJson(response, HttpStatus.UNAUTHORIZED, Result.fail(ResultCode.UNAUTHORIZED));
    }

    /** 已认证但权限不足时返回 403（@RequiresPermission 抛出的子异常不会走这里，由全局异常处理器处理） */
    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) ->
                writeJson(response, HttpStatus.FORBIDDEN, Result.fail(ResultCode.FORBIDDEN));
    }

    /** 以 UTF-8 JSON 输出统一结果 */
    private void writeJson(HttpServletResponse response, HttpStatus status, Result<?> body) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
