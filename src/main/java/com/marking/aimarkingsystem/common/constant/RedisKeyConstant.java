package com.marking.aimarkingsystem.common.constant;

/**
 * Redis Key 常量。
 *
 * 统一收口 Key 前缀，避免 Service 和过滤器里各写一遍字符串导致不一致。
 * 后续业务模块（AI 阅卷任务、验证码等）继续在此追加即可。
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    /** 登录会话：login:token:{username} → 当前有效的 JWT */
    public static final String LOGIN_TOKEN = "login:token:";

    /**
     * 答卷提交幂等锁：answer:submit:{paperId}:{studentId} → 提交请求标识
     * 用 SET NX EX 抢占，同一学生对同一试卷在有效期内只允许一次提交请求进入业务逻辑。
     */
    public static final String ANSWER_SUBMIT_IDEMPOTENT = "answer:submit:";

    /**
     * OCR 任务记录：ocr:task:{taskId} → 任务 JSON（答卷ID、图片对象键、状态、创建时间）
     * 模块3-2 阶段3 只把任务登记在 Redis（24 小时过期），
     * 模块4 接入 MQ 与持久化任务表后，这里可替换为数据库任务表。
     */
    public static final String OCR_TASK = "ocr:task:";

    /**
     * OCR 触发锁：ocr:trigger:{answerId}
     * 防止同一份答卷被并发重复触发 OCR（生成多个任务、调用多次 OCR 服务）。
     */
    public static final String OCR_TRIGGER = "ocr:trigger:";
}
