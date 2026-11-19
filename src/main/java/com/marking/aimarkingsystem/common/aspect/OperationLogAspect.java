package com.marking.aimarkingsystem.common.aspect;

import cn.hutool.json.JSONUtil;
import com.marking.aimarkingsystem.common.annotation.OperationLog;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 操作日志切面（模块7）。
 *
 * 记录内容：
 * - 操作人：SecurityContext 中的用户名 + 角色；
 * - 请求信息：HTTP 方法、URI、客户端 IP；
 * - 业务信息：注解上的模块/动作；
 * - 入参/返回值：JSON 序列化后截断，敏感字段脱敏；
 * - 耗时：纳秒计时，输出毫秒；
 * - 结果：成功/失败（失败记录异常信息，异常仍原样抛出）。
 *
 * 为什么用 @Around：
 * - 只有环绕通知才能同时拿到入参、返回值和执行耗时；
 * - 即使业务抛异常，也能在 finally 前记录"失败 + 耗时"，便于排查慢接口和异常接口。
 *
 * 与权限切面的顺序：
 * - 本切面 @Order(HIGHEST_PRECEDENCE)，在 PermissionAspect 外层；
 * - 这样即使权限校验拒绝，也能记录"谁尝试访问了哪个接口"，便于安全审计。
 *
 * 注意：
 * - 本阶段只写日志文件，不落库；后续需要入库时，在 finally 中调用日志 Service 即可；
 * - 不记录文件流、HttpServletRequest/Response、BindingResult，避免序列化异常和日志爆炸；
 * - 日志中的 password/token/secret 等字段统一替换为 ***。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);

    /** 敏感字段：JSON 中的值替换为 ***，避免密码/令牌进入日志 */
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(\"(password|passwd|oldPassword|newPassword|token|authorization|secret|apiKey|api-key|cookie)\"\\s*:\\s*\")[^\"]*(\")");

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startNanos = System.nanoTime();
        String operator = currentOperator();
        String method = joinPoint.getSignature().toShortString();
        HttpServletRequest request = currentRequest();
        String httpMethod = request == null ? "-" : request.getMethod();
        String uri = request == null ? "-" : request.getRequestURI();
        String ip = request == null ? "-" : clientIp(request);

        String params = operationLog.recordParams()
                ? toJson(filterArgs(joinPoint.getArgs()), operationLog.maxLength())
                : "disabled";

        try {
            Object result = joinPoint.proceed();
            long costMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            String resultText = operationLog.recordResult()
                    ? toJson(result, operationLog.maxLength())
                    : "disabled";
            log.info("操作日志 | module={} action={} operator={} http={} {} ip={} method={} cost={}ms success=true params={} result={}",
                    operationLog.module(), operationLog.action(), operator,
                    httpMethod, uri, ip, method, costMillis, params, resultText);
            return result;
        } catch (Throwable throwable) {
            long costMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            log.warn("操作日志 | module={} action={} operator={} http={} {} ip={} method={} cost={}ms success=false params={} error={}",
                    operationLog.module(), operationLog.action(), operator,
                    httpMethod, uri, ip, method, costMillis, params, throwable.getMessage());
            throw throwable;
        }
    }

    // ==================== 请求与操作人 ====================

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /** 优先取代理头中的真实客户端 IP，其次取 remoteAddr */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String currentOperator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "anonymous";
        }
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
        return authentication.getName() + (roles.isEmpty() ? "" : "[" + roles + "]");
    }

    // ==================== 参数处理 ====================

    /**
     * 过滤不可序列化的参数：
     * - ServletRequest/ServletResponse：包含流和容器对象，序列化会失败或输出巨量内容；
     * - MultipartFile：只记录文件名和大小，不记录二进制内容；
     * - BindingResult：校验结果，日志意义不大。
     */
    private Object[] filterArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return new Object[0];
        }
        List<Object> filtered = new ArrayList<>(args.length);
        for (Object arg : args) {
            if (arg == null) {
                filtered.add(null);
                continue;
            }
            if (arg instanceof ServletRequest || arg instanceof ServletResponse || arg instanceof BindingResult) {
                continue;
            }
            if (arg instanceof MultipartFile file) {
                filtered.add(fileInfo(file));
                continue;
            }
            if (arg instanceof MultipartFile[] files) {
                List<Map<String, Object>> fileInfos = new ArrayList<>(files.length);
                for (MultipartFile file : files) {
                    fileInfos.add(fileInfo(file));
                }
                filtered.add(fileInfos);
                continue;
            }
            filtered.add(arg);
        }
        return filtered.toArray();
    }

    private Map<String, Object> fileInfo(MultipartFile file) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", file.getName());
        info.put("originalFilename", file.getOriginalFilename());
        info.put("size", file.getSize());
        info.put("contentType", file.getContentType());
        return info;
    }

    private String toJson(Object value, int maxLength) {
        if (value == null) {
            return "null";
        }
        try {
            return truncate(maskSensitive(JSONUtil.toJsonStr(value)), maxLength);
        } catch (Exception e) {
            // 极端情况下对象无法序列化（如包含循环引用），退化为 toString，保证日志不断链
            return truncate(maskSensitive(String.valueOf(value)), maxLength);
        }
    }

    private String maskSensitive(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return SENSITIVE_PATTERN.matcher(text).replaceAll("$1***$3");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        int limit = Math.max(maxLength, 64);
        return text.length() <= limit ? text : text.substring(0, limit) + "...(truncated)";
    }
}
