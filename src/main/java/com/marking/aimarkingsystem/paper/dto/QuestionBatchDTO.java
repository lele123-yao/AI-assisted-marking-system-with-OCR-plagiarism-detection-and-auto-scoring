package com.marking.aimarkingsystem.paper.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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
 * 批量保存试卷题目 DTO。
 *
 * 使用场景：上传解析（或手动录入）后，教师一次性确认整份试卷的题目。
 * 语义是"整体替换"：先清空该试卷原有题目，再按传入顺序插入，
 * 因此只允许在草稿状态调用，避免破坏已发布试卷与已有答卷的题目绑定。
 */
@Data
@Schema(description = "批量保存题目请求")
public class QuestionBatchDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "所属试卷ID", example = "1")
    @NotNull(message = "所属试卷不能为空")
    @Min(value = 1, message = "试卷ID不合法")
    private Long paperId;

    @Schema(description = "题目列表，顺序即展示顺序；传空数组表示清空题目")
    @NotNull(message = "题目列表不能为空")
    @Valid
    private List<Item> questions;

    /** 单道题目 */
    @Data
    @Schema(description = "批量题库中的单道题")
    public static class Item implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "题号，试卷内唯一", example = "1")
        @NotBlank(message = "题号不能为空")
        @Size(max = 32, message = "题号最长 32 位")
        private String questionNo;

        @Schema(description = "大题名称", example = "简答题")
        @Size(max = 64, message = "大题名称最长 64 位")
        private String sectionName;

        @Schema(description = "题型：1单选 2多选 3判断 4填空 5简答 6论述 7计算 8案例", example = "5")
        @NotNull(message = "题型不能为空")
        @Min(value = 1, message = "题型取值 1-8")
        @Max(value = 8, message = "题型取值 1-8")
        private Integer questionType;

        @Schema(description = "题干内容")
        @NotBlank(message = "题干内容不能为空")
        @Size(max = 10000, message = "题干内容最长 10000 字符")
        private String questionContent;

        @Schema(description = "参考答案")
        @Size(max = 10000, message = "参考答案最长 10000 字符")
        private String standardAnswer;

        @Schema(description = "评分细则")
        @Size(max = 2000, message = "评分细则最长 2000 字符")
        private String scoringRule;

        @Schema(description = "本题分值", example = "10.0")
        @NotNull(message = "题目分值不能为空")
        @DecimalMin(value = "0.5", message = "分值最小 0.5 分")
        @DecimalMax(value = "100.0", message = "单题分值不能超过 100 分")
        private BigDecimal score;
    }
}
