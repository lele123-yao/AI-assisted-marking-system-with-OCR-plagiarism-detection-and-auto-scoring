package com.marking.aimarkingsystem.common.exception;

import com.marking.aimarkingsystem.common.result.ResultCode;

/**
 * 业务异常。
 *
 * 用于表达"可以预期、需要提示给用户"的错误，例如：
 * 用户名已存在、试卷已被发布无法修改、AI 阅卷任务不存在等。
 *
 * 与 RuntimeException 的区别：
 * - 业务异常不需要打印完整堆栈，只需要提示 msg（由全局异常处理器统一处理）
 * - 真正意外的错误（NPE、SQL 异常）才走 Exception 兜底，并打印 error 日志
 *
 * 因为是 RuntimeException 的子类，在 Service 中抛出不强制 try-catch，
 * 会由 Controller 上方的 @RestControllerAdvice 统一捕获。
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，默认使用 ResultCode.BUSINESS_ERROR */
    private final Integer code;

    /** 使用默认业务失败码 */
    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.BUSINESS_ERROR.getCode();
    }

    /** 使用预定义响应码 */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMsg());
        this.code = resultCode.getCode();
    }

    /** 使用预定义响应码 + 自定义提示 */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /** 完全自定义 code 和 msg */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    /**
     * 业务异常一般不需要堆栈信息，重写此方法减少异常创建时的性能开销。
     */
    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
