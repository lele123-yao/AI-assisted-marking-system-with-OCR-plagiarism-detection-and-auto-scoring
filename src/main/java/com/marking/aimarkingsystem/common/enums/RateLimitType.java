package com.marking.aimarkingsystem.common.enums;

/**
 * 接口限流维度（模块7）。
 *
 * 用于 {@code @RateLimit} 声明"按什么维度计数"：
 * - USER：按登录用户名计数，适合答卷提交、OCR 触发这类"每人每 N 秒 M 次"的场景；
 * - IP：按客户端 IP 计数，适合登录、文件上传等未登录或需要防刷的接口；
 * - GLOBAL：全应用共享一个计数器，适合保护下游资源（如大模型、OCR 服务）的总并发。
 */
public enum RateLimitType {

    /** 按登录用户计数；未登录时退化为按 IP 计数 */
    USER,

    /** 按客户端 IP 计数 */
    IP,

    /** 全局计数，所有调用方共享额度 */
    GLOBAL
}
