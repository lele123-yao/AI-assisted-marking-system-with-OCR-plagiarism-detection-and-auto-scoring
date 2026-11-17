package com.marking.aimarkingsystem.paper.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 新增题目 DTO（题目编辑可复用本 DTO，id 通过路径参数传递）。
 *
 * 说明：
 * - standardAnswer / scoringRule 对主观题（题型 5~8）评分必需，
 *   "是否必填"属于业务校验，在 Service 层按题型判断，DTO 层不做死；
 * - knowledgeIds 为题目绑定的知识点标签集合，一道题可挂多个知识点，
 *   关联记录（question_knowledge_rel）的写入由 Service 层完成，本 DTO 不直接操作关联表；
 * - 试卷状态为"草稿"时才允许增删题目，属于 Service 层状态机校验。
 */
@Data
@Schema(description = "新增题目请求")
public class QuestionAddDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "所属试卷ID", example = "1")
    @NotNull(message = "所属试卷不能为空")
    @Min(value = 1, message = "试卷ID不合法")
    private Long paperId;

    @Schema(description = "题号，如 1 / 一.1", example = "1")
    @NotBlank(message = "题号不能为空")
    @Size(max = 32, message = "题号最长 32 位")
    private String questionNo;

    @Schema(description = "大题名称，如 简答题", example = "简答题")
    @Size(max = 64, message = "大题名称最长 64 位")
    private String sectionName;

    @Schema(description = "题型：1单选 2多选 3判断 4填空 5简答 6论述 7计算 8案例", example = "5")
    @NotNull(message = "题型不能为空")
    @Min(value = 1, message = "题型取值 1-8")
    @Max(value = 8, message = "题型取值 1-8")
    private Integer questionType;

    @Schema(description = "题干内容", example = "简述栈和队列的主要区别，并各举一个典型应用场景。")
    @NotBlank(message = "题干内容不能为空")
    @Size(max = 10000, message = "题干内容最长 10000 字符")
    private String questionContent;

    @Schema(description = "标准答案/参考答案，AI评分依据；主观题建议必填",
            example = "栈是后进先出（LIFO），队列是先进先出（FIFO）……")
    @Size(max = 10000, message = "标准答案最长 10000 字符")
    private String standardAnswer;

    @Schema(description = "评分细则/得分点", example = "答出 LIFO/FIFO 各2分；各举一个正确应用场景各3分")
    @Size(max = 2000, message = "评分细则最长 2000 字符")
    private String scoringRule;

    @Schema(description = "本题分值", example = "10.0")
    @NotNull(message = "题目分值不能为空")
    @DecimalMin(value = "0.5", message = "分值最小 0.5 分")
    @DecimalMax(value = "100.0", message = "单题分值不能超过 100 分")
    private BigDecimal score;

    @Schema(description = "难度：1~5，3 为中等；不传默认 3", example = "3")
    @Min(value = 1, message = "难度取值 1-5")
    @Max(value = 5, message = "难度取值 1-5")
    private Integer difficulty;

    @Schema(description = "评分提示词模板ID，不传则按题型自动匹配", example = "1")
    @Min(value = 1, message = "模板ID不合法")
    private Long promptTemplateId;

    @Schema(description = "题目排序号，升序展示", example = "1")
    @Min(value = 0, message = "排序号不能小于 0")
    private Integer sortOrder;

    @Schema(description = "绑定的知识点ID集合，可为空表示暂不绑定", example = "[1, 3]")
    @Size(max = 20, message = "一道题最多绑定 20 个知识点")
    private List<Long> knowledgeIds;
}
