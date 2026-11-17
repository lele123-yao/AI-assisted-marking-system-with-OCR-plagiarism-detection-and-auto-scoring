package com.marking.aimarkingsystem.answer.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 考生名单 Excel 行模型（EasyExcel 读取用）。
 *
 * @ExcelProperty 按表头名称映射，而不是按列顺序：
 * 列顺序被调整、或多插一列备注时，导入依然正确，这是 EasyExcel 相比 POI 下标读取的优势。
 *
 * 模板列（第 1 行为表头）：
 * | 学号 | 姓名 | 班级 |
 * 班级允许为空，导入时可用 RosterImportDTO.defaultClass 统一补齐。
 */
@Data
public class RosterRowDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 学号，必填，用于匹配考生与后续 OCR 结果回填 */
    @ExcelProperty("学号")
    private String studentNo;

    /** 姓名，必填 */
    @ExcelProperty("姓名")
    private String studentName;

    /** 班级，可空 */
    @ExcelProperty("班级")
    private String studentClass;
}
