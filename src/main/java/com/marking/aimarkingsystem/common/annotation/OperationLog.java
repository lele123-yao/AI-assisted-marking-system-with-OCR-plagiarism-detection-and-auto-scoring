package com.marking.aimarkingsystem.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解（模块7）。
 *
 * 标注在 Controller 方法上，由 {@code OperationLogAspect} 统一记录：
 * 操作人、请求地址、接口入参、返回值、耗时、成功/失败。
 *
 * 示例：
 * <pre>
 * &#64;OperationLog(module = "答卷管理", action = "提交答卷")
 * &#64;PostMapping("/answers")
 * public Result&lt;Long&gt; submit(&#64;RequestBody AnswerSubmitDTO dto) { ... }
 * </pre>
 *
 * 设计说明：
 * - 用注解而不是在业务代码里手写 log.info，避免日志逻辑侵入业务；
 * - module/action 由开发者填写，便于按模块检索日志；
 * - recordParams/recordResult 可关闭，用于文件上传、Excel 导出等大对象接口；
 * - 本阶段只输出到日志文件，后续如需入库，只需在切面里增加一个持久化实现，
 *   业务代码无需改动。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 业务模块，如"答卷管理""学情分析" */
    String module() default "";

    /** 操作动作，如"提交答卷""导出成绩" */
    String action() default "";

    /** 是否记录入参；文件上传、批量导入等接口建议关闭 */
    boolean recordParams() default true;

    /** 是否记录返回值；导出文件等接口建议关闭 */
    boolean recordResult() default true;

    /** 单个字段（入参/返回值 JSON）最大记录长度，超出截断，防止日志膨胀 */
    int maxLength() default 1000;
}
