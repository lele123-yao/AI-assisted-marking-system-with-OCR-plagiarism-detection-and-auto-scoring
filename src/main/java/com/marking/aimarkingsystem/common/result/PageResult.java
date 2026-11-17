package com.marking.aimarkingsystem.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 通用分页结果。
 *
 * 不直接返回 MyBatis-Plus 的 Page 对象，是为了避免把分页插件的内部字段
 * （orders、optimizeCountSql 等）暴露给前端；后续课程、试卷、答卷列表都可复用本类。
 */
@Data
@Schema(description = "分页结果")
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "总记录数", example = "26")
    private Long total;

    @Schema(description = "当前页码", example = "1")
    private Long current;

    @Schema(description = "每页条数", example = "10")
    private Long size;

    @Schema(description = "总页数", example = "3")
    private Long pages;

    @Schema(description = "当前页数据")
    private List<T> records;

    /** 分页信息取自分页对象，数据使用转换后的 VO 列表 */
    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setPages(page.getPages());
        result.setRecords(records);
        return result;
    }
}
