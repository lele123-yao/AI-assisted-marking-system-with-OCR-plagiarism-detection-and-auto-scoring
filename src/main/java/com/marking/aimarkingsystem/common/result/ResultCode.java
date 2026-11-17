package com.marking.aimarkingsystem.common.result;

/**
 * 全局响应码枚举。
 *
 * 集中管理 code，避免在代码里到处写魔法数字（如 return Result.fail(400, "..."））。
 * 后续新增业务错误码时，直接在此追加即可，不需要改动 Result 类本身。
 */
public enum ResultCode {

    /** 请求成功 */
    SUCCESS(200, "操作成功"),

    /** 参数校验不通过（@Valid / @Validated 触发） */
    PARAM_INVALID(400, "参数校验失败"),

    /** 未登录或 token 失效（阶段3做 JWT 鉴权时使用） */
    UNAUTHORIZED(401, "未登录或登录已过期"),

    /** 已登录但没有该资源的访问权限 */
    FORBIDDEN(403, "没有访问权限"),

    /** 请求的资源不存在 */
    NOT_FOUND(404, "请求资源不存在"),

    /** 业务处理失败，例如“用户名已存在”这类可预期错误 */
    BUSINESS_ERROR(600, "业务处理失败"),

    /** 系统内部异常，例如空指针、数据库连接失败 */
    SYSTEM_ERROR(500, "系统异常，请稍后重试");

    private final Integer code;

    private final String msg;

    ResultCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
