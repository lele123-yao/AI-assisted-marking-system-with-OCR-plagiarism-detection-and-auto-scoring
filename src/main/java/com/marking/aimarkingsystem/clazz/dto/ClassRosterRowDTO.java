package com.marking.aimarkingsystem.clazz.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 班级名单 Excel 行模型（EasyExcel 读取用）。
 *
 * 模板（第 1 行为表头）：
 * | 学号 | 姓名 |
 * 按表头名称映射，列顺序可调整；学号即登录账号，姓名用于新建账号时作为昵称。
 */
@Data
public class ClassRosterRowDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 学号，必填，同时作为学生登录账号 */
    @ExcelProperty("学号")
    private String studentNo;

    /** 姓名，新建账号时必填 */
    @ExcelProperty("姓名")
    private String studentName;
}
