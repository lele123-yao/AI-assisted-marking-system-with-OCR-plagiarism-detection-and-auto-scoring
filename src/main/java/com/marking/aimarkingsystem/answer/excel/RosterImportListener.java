package com.marking.aimarkingsystem.answer.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.marking.aimarkingsystem.answer.dto.RosterRowDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * 考生名单 Excel 读取监听器。
 *
 * EasyExcel 采用 SAX 逐行解析，不会把整份工作簿加载成 POI 对象树，
 * 因此监听器里只做"收集行数据"，真正的业务校验交给 Service（需要批量查库，见 importRoster）。
 *
 * 为什么不在 invoke 里直接写库：
 * 1. 逐行写库会产生 N 次事务/网络往返，且一旦中途失败难以给出完整错误清单；
 * 2. 名单规模有限（一班几十到几百人），先收集再统一校验、统一入库更可控；
 * 3. 若后续名单达到上万行，可改为在 invoke 中按 500 条分批 flush，结构无需大改。
 */
public class RosterImportListener implements ReadListener<RosterRowDTO> {

    private final List<RosterRowDTO> rows = new ArrayList<>();

    @Override
    public void invoke(RosterRowDTO data, AnalysisContext context) {
        rows.add(data);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        // 全部读取完成，无需处理；数据已由 Service 统一消费
    }

    /** 读取到的全部数据行（不含表头），顺序与 Excel 中的行顺序一致 */
    public List<RosterRowDTO> getRows() {
        return rows;
    }
}
