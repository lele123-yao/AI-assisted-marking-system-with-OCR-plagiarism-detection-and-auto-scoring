package com.marking.aimarkingsystem.common.result;

import java.io.Serializable;

/**
 * 统一返回结果封装。
 *
 * 全项目所有 Controller 的返回值都用它包装，前端只需要判断 code 就能知道成败，
 * 不需要每个接口约定一套不同的 JSON 结构。
 *
 * 约定：code = 200 表示成功，其余均表示失败；msg 用于给前端展示或调试。
 * 注意这里只封装"业务结果"，HTTP 状态码统一为 200（由全局异常处理器返回），
 * 前端依据 body 中的 code 判断成功与否。
 *
 * @param <T> 业务数据类型，无数据时可为 Void
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，取值见 {@link ResultCode} */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 业务数据，成功时可能有值，失败时一般为 null */
    private T data;

    public Result() {
    }

    public Result(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // ==================== 成功 ====================

    /** 成功但无返回数据，例如"删除成功" */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), null);
    }

    /** 成功并返回数据 */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
    }

    /** 成功，自定义提示语并返回数据 */
    public static <T> Result<T> success(String msg, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), msg, data);
    }

    // ==================== 失败 ====================

    /** 失败，使用默认业务失败码 */
    public static <T> Result<T> fail(String msg) {
        return new Result<>(ResultCode.BUSINESS_ERROR.getCode(), msg, null);
    }

    /** 失败，直接使用预定义响应码 */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMsg(), null);
    }

    /** 失败，使用预定义响应码但自定义提示语 */
    public static <T> Result<T> fail(ResultCode resultCode, String msg) {
        return new Result<>(resultCode.getCode(), msg, null);
    }

    /** 失败，完全自定义 code 和 msg（供业务异常携带的 code 使用） */
    public static <T> Result<T> fail(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }

    // ==================== 访问 ====================

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
