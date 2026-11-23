package com.marking.aimarkingsystem.paper.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试卷视图对象 VO。
 *
 * 说明：
 * - VO 与实体解耦：courseName 由 Service 关联课程表填充，列表页直接展示课程名；
 * - status / answerMode 的取值含义见对应 Schema 描述，前端据此渲染标签与控制按钮；
 * - totalScore / questionCount 为 Service 回写的汇总值，前端无需再查题目表。
 */
@Data
@Schema(description = "试卷信息")
public class PaperVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "试卷ID", example = "1")
    private Long id;

    @Schema(description = "试卷编号", example = "DS-2026-MID")
    private String paperNo;

    @Schema(description = "试卷标题", example = "数据结构与算法 期中测验")
    private String paperTitle;

    @Schema(description = "所属课程ID", example = "1")
    private Long courseId;

    @Schema(description = "所属课程名称（Service 填充）", example = "数据结构与算法")
    private String courseName;

    @Schema(description = "作答模式：1 线上作答，2 纸质线下阅卷", example = "1")
    private Integer answerMode;

    @Schema(description = "试卷状态：0 草稿，1 待考试，2 阅卷中，3 已归档", example = "0")
    private Integer status;

    @Schema(description = "试卷总分", example = "40.0")
    private BigDecimal totalScore;

    @Schema(description = "及格分", example = "24.0")
    private BigDecimal passScore;

    @Schema(description = "题目数量", example = "3")
    private Integer questionCount;

    @Schema(description = "考试时长（分钟），纸质模式为 null", example = "90")
    private Integer duration;

    @Schema(description = "开考时间")
    private LocalDateTime startTime;

    @Schema(description = "收卷时间")
    private LocalDateTime endTime;

    @Schema(description = "是否启用AI阅卷：1 启用，0 仅人工", example = "1")
    private Integer aiMarking;

    @Schema(description = "创建人ID", example = "1")
    private Long creatorId;

    @Schema(description = "试卷说明")
    private String description;

    @Schema(description = "试卷 PDF 对象名（MinIO）")
    private String pdfUrl;

    @Schema(description = "拍照图片对象名 JSON 数组")
    private String imageUrl;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
