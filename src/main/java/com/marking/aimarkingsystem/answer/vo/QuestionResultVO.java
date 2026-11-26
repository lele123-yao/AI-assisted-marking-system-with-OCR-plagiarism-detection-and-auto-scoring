package com.marking.aimarkingsystem.answer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 答卷逐题结果 VO：把题目信息、AI 结果、教师结果组装在一起返回给前端。
 *
 * 合并来源：
 * - questionNo / questionContent / standardAnswer / scoringRule / fullScore 来自 exam_question；
 * - aiScore / aiComment / finalScore / finalComment 来自 exam_answer_item。
 */
@Data
@Schema(description = "答卷逐题结果")
public class QuestionResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "题目ID", example = "11")
    private Long questionId;

    @Schema(description = "题号", example = "1")
    private String questionNo;

    @Schema(description = "大题名称", example = "简答题")
    private String sectionName;

    @Schema(description = "题型：1单选 2多选 3判断 4填空 5简答 6论述 7计算 8案例", example = "5")
    private Integer questionType;

    @Schema(description = "题干")
    private String questionContent;

    @Schema(description = "参考答案")
    private String standardAnswer;

    @Schema(description = "评分细则")
    private String scoringRule;

    @Schema(description = "本题满分", example = "10.00")
    private BigDecimal fullScore;

    @Schema(description = "考生作答原文（线上作答时可用）")
    private String studentAnswer;

    @Schema(description = "AI 参考分，未阅卷时为空", example = "8.50")
    private BigDecimal aiScore;

    @Schema(description = "AI 评语")
    private String aiComment;

    @Schema(description = "教师最终分，未复核时为空", example = "9.00")
    private BigDecimal finalScore;

    @Schema(description = "教师最终评语")
    private String finalComment;

    @Schema(description = "AI 逐得分点明细（细则条目、是否命中、该点得分）；规则判分时为空")
    private List<Map<String, Object>> points;

    @Schema(description = "判分来源：RULE 规则判分，LLM 大模型判分")
    private String gradedBy;
}
