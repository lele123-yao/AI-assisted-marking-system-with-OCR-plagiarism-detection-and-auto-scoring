package com.marking.aimarkingsystem.answer.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 考生名单导入结果 VO。
 *
 * 返回四类信息，便于前端展示"导入成功多少、跳过多少、哪几行失败"：
 * - batchNo：本次导入批次号，可用它按批次撤销（DELETE ... WHERE import_batch_no = ?）；
 * - totalRows：Excel 数据行总数（不含表头）；
 * - successCount / skippedCount / failedCount：成功、已存在跳过、校验失败的数量；
 * - failures：失败明细，含 Excel 行号（表头为第 1 行，数据从第 2 行开始）。
 */
@Data
@Schema(description = "考生名单导入结果")
public class RosterImportResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "导入批次号", example = "20261112-143000-317")
    private String batchNo;

    @Schema(description = "Excel 数据行总数", example = "45")
    private Integer totalRows;

    @Schema(description = "成功导入数量", example = "42")
    private Integer successCount;

    @Schema(description = "已存在而跳过的数量", example = "2")
    private Integer skippedCount;

    @Schema(description = "校验失败数量", example = "1")
    private Integer failedCount;

    @Schema(description = "失败明细")
    private List<FailedRow> failures = new ArrayList<>();

    /** 单行失败明细 */
    @Data
    @Schema(description = "导入失败行")
    public static class FailedRow implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "Excel 行号（表头为第 1 行）", example = "7")
        private Integer rowNum;

        @Schema(description = "学号，可能为空", example = "20260007")
        private String studentNo;

        @Schema(description = "失败原因", example = "学号为空")
        private String reason;

        public FailedRow() {
        }

        public FailedRow(Integer rowNum, String studentNo, String reason) {
            this.rowNum = rowNum;
            this.studentNo = studentNo;
            this.reason = reason;
        }
    }
}
