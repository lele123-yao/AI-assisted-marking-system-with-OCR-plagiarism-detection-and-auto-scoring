package com.marking.aimarkingsystem.notify.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 站内通知消息，对应表 notify_message。
 *
 * 设计要点：
 * - 一条业务事件对每个接收人各存一行（而不是一行多接收人），
 *   这样"已读/未读"是每个人独立状态，分页与未读数查询都能直接走
 *   (user_id, is_read, create_time) 索引；
 * - bizType + bizId 用于前端点击跳转（首期为 EXAM_ANSWER + answerId）；
 * - 本表是实时推送的"兜底副本"：WebSocket 不在线也能通过 HTTP 查到。
 */
@Data
@TableName("notify_message")
public class NotifyMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人ID，关联 sys_user.id */
    private Long userId;

    /** 消息类型：MARK_COMPLETED 阅卷完成 */
    private String type;

    /** 消息标题 */
    private String title;

    /** 消息正文 */
    private String content;

    /** 业务类型：EXAM_ANSWER 答卷 */
    private String bizType;

    /** 业务主键（答卷ID），用于前端跳转 */
    private Long bizId;

    /** 是否已读：0 未读，1 已读 */
    private Integer isRead;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 读取时间，标记已读时写入 */
    private LocalDateTime readTime;
}
