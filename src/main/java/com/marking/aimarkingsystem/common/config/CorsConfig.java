package com.marking.aimarkingsystem.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域（CORS）配置。
 *
 * 前后端分离开发时，前端通常跑在 5173 / 3000 等端口，与后端 8080 不同源，
 * 浏览器会拦截跨域请求。这里通过 Spring MVC 的全局 CORS 配置放行。
 *
 * 说明：
 * 1. 使用 allowedOriginPatterns 而不是 allowedOrigins("*")：
 *    当 allowCredentials = true（允许携带 Cookie / Authorization）时，
 *    规范不允许使用 "*"，必须用 originPatterns。
 * 2. 上线时应把 "*" 收敛为具体前端域名，避免任意站点调用接口。
 * 3. 阶段3引入 Spring Security 后，还需要在 SecurityFilterChain 中开启
 *    http.cors(...)，否则请求会先被 Security 过滤器链拦下。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 允许的前端来源，开发环境放开，生产环境请改为具体域名
                .allowedOriginPatterns("*")
                // 允许的请求方法
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                // 允许的请求头（* 表示不限制，包含 Authorization）
                .allowedHeaders("*")
                // 允许前端读取的响应头，例如后续 JWT 需要读取 Authorization
                .exposedHeaders("Authorization")
                // 允许携带 Cookie 等凭证
                .allowCredentials(true)
                // 预检请求（OPTIONS）的缓存时间，单位秒，减少重复预检
                .maxAge(3600);
    }
}
