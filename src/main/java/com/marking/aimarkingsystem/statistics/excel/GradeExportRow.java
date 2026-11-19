package com.marking.aimarkingsystem.statistics.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 成绩导出 Excel 行模型。
 *
 * 线上答卷与纸质答卷统一使用同一套列：来源列区分"线上作答/纸质扫描"，
 * 分数列统一为"AI参考分 + 教师最终分"，未出分的单元格留空，由教师线下核对。
 */
@Data
@ColumnWidth(18)
public class GradeExportRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "序号", index = 0)
    @ColumnWidth(8)
    private Integer index;

    @ExcelProperty(value = "学号", index = 1)
    private String studentNo;

    @ExcelProperty(value = "姓名", index = 2)
    @ColumnWidth(12)
    private String studentName;

    @ExcelProperty(value = "班级", index = 3)
    @ColumnWidth(22)
    private String studentClass;

    @ExcelProperty(value = "答卷来源", index = 4)
    @ColumnWidth(12)
    private String answerSourceDesc;

    @ExcelProperty(value = "AI参考分", index = 5)
    private String aiReferenceScore;

    @ExcelProperty(value = "教师最终分", index = 6)
    private String teacherFinalScore;

    @ExcelProperty(value = "是否及格", index = 7)
    @ColumnWidth(10)
    private String passFlag;

    @ExcelProperty(value = "复核状态", index = 8)
    @ColumnWidth(14)
    private String reviewStatusDesc;

    @ExcelProperty(value = "复核时间", index = 9)
    @ColumnWidth(20)
    private String markTime;
}
