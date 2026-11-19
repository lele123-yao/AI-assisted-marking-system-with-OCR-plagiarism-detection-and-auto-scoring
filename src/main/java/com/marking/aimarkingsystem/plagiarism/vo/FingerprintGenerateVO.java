package com.marking.aimarkingsystem.plagiarism.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 试卷维度的指纹生成结果。
 *
 * 批量生成时不是所有答卷都能算出指纹：
 * 缺考、未 OCR、作答文本过短都会被跳过，因此返回 skipped 明细而不是只给一个数字，
 * 方便教师判断是"确实没作答"还是"OCR 没成功"。
 */
@Data
@Schema(description = "SimHash指纹生成结果")
public class FingerprintGenerateVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "试卷ID", example = "3")
    private Long paperId;

    @Schema(description = "试卷下答卷总数", example = "60")
    private Integer totalCount;

    @Schema(description = "本次新生成指纹的答卷数", example = "40")
    private Integer generatedCount;

    @Schema(description = "已有指纹、本次跳过的答卷数", example = "18")
    private Integer existingCount;

    @Schema(description = "无法生成指纹（文本为空或过短）的答卷数", example = "2")
    private Integer skippedCount;

    @Schema(description = "跳过明细")
    private List<SkippedItem> skippedItems;

    /** 跳过明细：哪份答卷、什么原因 */
    @Data
    @Schema(description = "跳过明细")
    public static class SkippedItem implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "答卷ID", example = "120")
        private Long answerId;

        @Schema(description = "学号", example = "20260020")
        private String studentNo;

        @Schema(description = "跳过原因", example = "作答文本为空或过短（当前 8 字，最少 30 字）")
        private String reason;

        public SkippedItem() {
        }

        public SkippedItem(Long answerId, String studentNo, String reason) {
            this.answerId = answerId;
            this.studentNo = studentNo;
            this.reason = reason;
        }
    }
}
