package com.marking.aimarkingsystem.learning.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学情分析报告实体，对应表 learning_analysis_report（模块8）。
 *
 * 一条记录是一次"AI 学情讲评"的完整产物：
 * - reportType = PAPER：针对单份试卷的班级学情讲评（当前阶段只支持这一种）；
 * - reportType = COURSE：课程阶段性分析，paperId 为空，预留；
 * - analysisText 存大模型生成的 Markdown 讲评正文；
 * - pptFileUrl 存 PPT 在 MinIO 中的 objectKey（不是可访问 URL），
 *   下载/预览时由 Service 调 MinioUtil.getPresignedUrl 换短时效签名地址。
 *
 * 状态说明：
 * - 0 生成中：先落库再调用大模型，前端可以轮询到"生成中"；
 * - 1 成功：讲评文本与 PPT 都已产出；
 * - 2 失败：errorMsg 记录原因，analysisText 可能已生成（仅 PPT 失败时保留正文，便于重试）。
 *
 * 同一试卷允许多份报告（不做 paper_id 唯一索引），查询时按 create_time 倒序取最新。
 */
@Data
@TableName("learning_analysis_report")
public class LearningAnalysisReport implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 试卷ID，关联 exam_paper.id；课程级报告时为空 */
    private Long paperId;

    /** 课程ID，关联 course.id；冗余存储，便于按课程查询与数据权限过滤 */
    private Long courseId;

    /** 报告类型：PAPER 单试卷讲评，COURSE 课程阶段分析 */
    private String reportType;

    /** 报告标题 */
    private String reportTitle;

    /** 大模型生成的讲评正文（Markdown） */
    private String analysisText;

    /** PPT 在 MinIO 中的对象键（objectKey），不存公网 URL */
    private String pptFileUrl;

    /** 生成状态：0 生成中，1 成功，2 失败 */
    private Integer status;

    /** 失败原因摘要 */
    private String errorMsg;

    /** 创建教师ID，关联 sys_user.id */
    private Long creatorId;

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
