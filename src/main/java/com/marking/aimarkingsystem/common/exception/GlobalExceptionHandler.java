package com.marking.aimarkingsystem.common.exception;

import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * @RestControllerAdvice 会拦截所有 Controller 抛出的异常，
 * 在这里统一转换成 Result 结构返回给前端，
 * 这样 Controller / Service 层就不需要写 try-catch，代码更干净。
 *
 * 处理顺序由异常类型最具体的优先匹配：
 * BusinessException → 参数校验异常 → Exception 兜底。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常：可预期错误，只记录 warn 日志，不打印堆栈。
     * 例如 Service 中 throw new BusinessException("用户名已存在")。
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 请求体参数校验失败：@RequestBody + @Valid 触发。
     * 例如 DTO 上标注 @NotBlank、@Size 等注解后，参数不合法会抛出该异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + " " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (msg.isEmpty()) {
            msg = ResultCode.PARAM_INVALID.getMsg();
        }
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCode.PARAM_INVALID, msg);
    }

    /**
     * 方法参数校验失败：Controller 类上标注 @Validated 后，
     * @RequestParam / @PathVariable 上的校验注解不通过时触发。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.fail(ResultCode.PARAM_INVALID, msg);
    }

    /**
     * 请求的接口 / 静态资源不存在。
     * 若不单独处理，会被 Exception 兜底当作系统异常（code=500），还会打印无意义的 error 堆栈。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("请求资源不存在: {}", e.getResourcePath());
        return Result.fail(ResultCode.NOT_FOUND);
    }

    /**
     * 兜底异常：所有未被上面处理器匹配的异常都会走到这里。
     * 这类错误通常意味着代码 bug 或环境问题，必须打印完整堆栈便于排查。
     * 注意：对外只返回模糊提示，不要把数据库/堆栈信息暴露给前端。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }
}
