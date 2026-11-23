package com.marking.aimarkingsystem.common.aspect;

import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.ResultCode;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限校验切面。
 *
 * 工作方式：
 * - 只拦截标注了 @RequiresPermission 的方法；
 * - 从 SecurityContextHolder 取当前登录身份（由 JwtAuthenticationFilter 在请求进入时写入）；
 * - 比对当前用户拥有的权限码与注解要求，不满足则抛 BusinessException，
 *   由阶段2的 GlobalExceptionHandler 统一转成 Result 返回（code = 401 / 403）。
 *
 * 为什么不直接用 Spring Security 的 @PreAuthorize：
 * - @PreAuthorize 的 SpEL 写法在复杂场景可读性差；
 * - 自定义注解便于统一扩展（如后续加入数据权限校验）；
 * - 两者可共存，@PreAuthorize 需要额外开启 @EnableMethodSecurity。
 */
@Aspect
@Component
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    // 用全限定名绑定注解，避免依赖编译参数名（-parameters），详见 OperationLogAspect 注释。
    @Before("@annotation(com.marking.aimarkingsystem.common.annotation.RequiresPermission)")
    public void checkPermission(JoinPoint joinPoint) {
        RequiresPermission requiresPermission = ((MethodSignature) joinPoint.getSignature())
                .getMethod().getAnnotation(RequiresPermission.class);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 1. 未登录 / 匿名用户直接拒绝
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 2. 注解未指定权限码，只要求登录
        String[] required = requiresPermission.value();
        if (required.length == 0) {
            return;
        }

        // 3. 比对权限码：拥有任意一个即通过
        Set<String> owned = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        boolean pass = Arrays.stream(required).anyMatch(owned::contains);
        if (!pass) {
            log.warn("权限不足: user={}, required={}, owned={}",
                    authentication.getName(), Arrays.toString(required), owned);
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // ==================== 数据权限校验（占位，暂不实现） ====================
        // 后续【课程试卷】【答卷存储】【学情分析】模块需要"教师只能看自己课程的数据、
        // 学生只能看自己的答卷"这类行级隔离，在此处扩展即可，无需修改业务代码：
        //
        // 实现思路（预留）：
        // 1. 从 authentication 中取出当前用户 id 与角色（如 TEACHER / STUDENT）；
        // 2. 从 joinPoint.getArgs() 中解析目标资源 id（courseId / paperId / answerId），
        //    可在注解上增加 dataScope 属性声明"第几个参数是资源 id"；
        // 3. 调用对应模块的 Service 校验资源归属（例如 paper.courseId 是否属于当前教师），
        //    不通过则抛 new BusinessException(ResultCode.FORBIDDEN)；
        // 4. 若想从 SQL 层统一过滤，可在 MyBatis-Plus 拦截器中按角色拼接
        //    "where teacher_id = #{currentUserId}"，避免每个接口重复判断。
        //
        // 注意：该逻辑依赖课程/试卷表结构，本阶段不写任何具体实现，
        //       以免把不存在的表结构和业务规则写死在公共组件里。
        // ====================================================================
    }
}
