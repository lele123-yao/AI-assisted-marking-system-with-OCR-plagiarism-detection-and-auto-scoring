package com.marking.aimarkingsystem.clazz.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.marking.aimarkingsystem.clazz.dto.ClassRosterRowDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * 班级名单 Excel 读取监听器。
 *
 * 与考生名单导入一致：EasyExcel 逐行解析，监听器只负责收集数据，
 * 批量查库、角色校验、建号、入班等业务逻辑统一放在 Service 中处理，
 * 这样单行失败不会中断整批导入，也便于一次性给出完整失败清单。
 */
public class ClassRosterImportListener implements ReadListener<ClassRosterRowDTO> {

    private final List<ClassRosterRowDTO> rows = new ArrayList<>();

    @Override
    public void invoke(ClassRosterRowDTO data, AnalysisContext context) {
        rows.add(data);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 全部读取完成后由 Service 统一消费
    }

    public List<ClassRosterRowDTO> getRows() {
        return rows;
    }
}
