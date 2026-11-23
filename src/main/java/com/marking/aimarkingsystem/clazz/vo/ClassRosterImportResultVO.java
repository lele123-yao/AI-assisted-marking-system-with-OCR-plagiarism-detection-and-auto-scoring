package com.marking.aimarkingsystem.clazz.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 班级名单导入结果。
 *
 * 成功 = 新绑定进班级（含自动新建账号）；
 * 跳过 = 该学生本来就在本班；
 * 失败 = 单行校验不通过，明细可在前端下载成 CSV。
 */
@Data
@Schema(description = "班级名单导入结果")
public class ClassRosterImportResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Excel 数据行总数（不含表头）", example = "45")
    private Integer totalRows = 0;

    @Schema(description = "成功加入班级数量", example = "40")
    private Integer successCount = 0;

    @Schema(description = "因已在本班而跳过的数量", example = "3")
    private Integer skippedCount = 0;

    @Schema(description = "失败数量", example = "2")
    private Integer failedCount = 0;

    @Schema(description = "本次自动创建的学生账号数量", example = "10")
    private Integer createdCount = 0;

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

        @Schema(description = "姓名，可能为空", example = "张三")
        private String studentName;

        @Schema(description = "失败原因", example = "账号不存在且未开启自动创建")
        private String reason;

        public FailedRow() {
        }

        public FailedRow(Integer rowNum, String studentNo, String studentName, String reason) {
            this.rowNum = rowNum;
            this.studentNo = studentNo;
            this.studentName = studentName;
            this.reason = reason;
        }
    }
}
