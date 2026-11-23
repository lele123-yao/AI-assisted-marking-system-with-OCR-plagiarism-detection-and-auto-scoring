package com.marking.aimarkingsystem.paper.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试卷实体，对应表 exam_paper。
 *
 * 约定：
 * - answerMode 支持两种作答模式：1 线上作答（用 startTime/endTime/duration 控制作答窗口），
 *   2 纸质线下阅卷（时间字段可为空，答题卡扫描件由答卷模块管理）；
 * - status 是状态机：0 草稿 -> 1 待考试 -> 2 阅卷中 -> 3 已归档，
 *   状态流转校验在 Service 层实现，本实体只承载数据；
 * - totalScore / questionCount 是汇总冗余字段，由 Service 在保存题目后回写，
 *   避免列表页每次聚合查询题目表。
 */
@Data
@TableName("exam_paper")
public class ExamPaper implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 试卷编号，业务唯一，如 DS-2026-MID */
    private String paperNo;

    /** 试卷标题 */
    private String paperTitle;

    /** 所属课程ID，关联 course.id */
    private Long courseId;

    /** 作答模式：1 线上作答，2 纸质线下阅卷 */
    private Integer answerMode;

    /** 试卷状态：0 草稿，1 待考试，2 阅卷中，3 已归档 */
    private Integer status;

    /** 试卷总分（题目分值汇总，由 Service 回写） */
    private BigDecimal totalScore;

    /** 及格分 */
    private BigDecimal passScore;

    /** 题目数量（由 Service 回写） */
    private Integer questionCount;

    /** 考试时长（分钟），纸质模式可为空 */
    private Integer duration;

    /** 开考时间，线上模式用于控制作答入口 */
    private LocalDateTime startTime;

    /** 收卷时间，线上模式截止提交 */
    private LocalDateTime endTime;

    /** 是否启用AI阅卷：1 启用，0 仅人工批改 */
    private Integer aiMarking;

    /** 创建人ID，关联 sys_user.id */
    private Long creatorId;

    /** 试卷说明 */
    private String description;

    /** 试卷 PDF 源文件在 MinIO 中的对象名，可为空 */
    private String pdfUrl;

    /** 拍照图片对象名 JSON 数组，可为空 */
    private String imageUrl;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入与更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 未删除，1 已删除 */
    @TableLogic
    private Integer delFlag;
}
