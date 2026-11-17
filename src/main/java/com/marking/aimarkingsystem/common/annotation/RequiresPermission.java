package com.marking.aimarkingsystem.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义权限校验注解。
 *
 * 使用方法：
 * <pre>
 *     &#64;RequiresPermission("course:paper:add")
 *     public Result&lt;Void&gt; addPaper(...) { ... }
 * </pre>
 *
 * 语义：
 * - 标注在方法上，由 PermissionAspect 切面拦截校验；
 * - value 为空表示"只要求已登录"，不校验具体权限码；
 * - value 有多个权限码时，**拥有任意一个**即通过（OR 语义）；
 * - 权限码与 sys_permission.perm_code 保持同一套命名规范（模块:资源:动作）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    /** 需要的权限码，如 system:user:add；为空表示只校验登录状态 */
    String[] value() default {};
}
