package com.marking.aimarkingsystem.paper.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 解析出的题目草稿（OCR/PDF 识别结果）。
 *
 * 只作为"辅助识别结果"返回给前端，教师可以自由增删改；
 * 真正入库仍走题目保存接口，以教师确认后的内容为准。
 */
@Data
@Schema(description = "解析出的题目草稿")
public class ParsedQuestionDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "题号", example = "1")
    private String questionNo;

    @Schema(description = "大题名称", example = "简答题")
    private String sectionName;

    @Schema(description = "题型：1单选 2多选 3判断 4填空 5简答 6论述 7计算 8案例", example = "5")
    private Integer questionType;

    @Schema(description = "题干")
    private String questionContent;

    @Schema(description = "参考答案（识别结果可能为空，需教师补充）")
    private String standardAnswer;

    @Schema(description = "评分细则（识别结果可能为空，需教师补充）")
    private String scoringRule;

    @Schema(description = "本题分值，识别不出时默认 10 分", example = "10.00")
    private BigDecimal score;
}
