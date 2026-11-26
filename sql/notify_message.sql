-- ==================== 阅卷完成站内通知 ====================
-- 说明：项目没有数据库迁移工具（Flyway/Liquibase），本脚本需手动执行一次。
-- 作用：持久化"阅卷完成"等站内信，保证 WebSocket 离线、刷新页面后仍可查询。
-- 与实时推送的关系：先写本表，再通过 RabbitMQ Fanout 广播到各实例做实时推送。

CREATE TABLE IF NOT EXISTS `notify_message` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       NOT NULL COMMENT '接收人ID，关联 sys_user.id',
    `type`        VARCHAR(32)  NOT NULL COMMENT '消息类型：MARK_COMPLETED 阅卷完成',
    `title`       VARCHAR(128) NOT NULL COMMENT '消息标题',
    `content`     VARCHAR(512)          DEFAULT NULL COMMENT '消息正文',
    `biz_type`    VARCHAR(32)           DEFAULT NULL COMMENT '业务类型：EXAM_ANSWER 答卷',
    `biz_id`      BIGINT                DEFAULT NULL COMMENT '业务主键（答卷ID），用于前端跳转',
    `is_read`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0 未读，1 已读',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `read_time`   DATETIME              DEFAULT NULL COMMENT '读取时间',
    PRIMARY KEY (`id`),
    -- 未读数与分页列表都按 user_id 过滤，配合 is_read / create_time 走索引
    KEY `idx_user_read_time` (`user_id`, `is_read`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT = '站内通知消息';
