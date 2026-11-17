package com.marking.aimarkingsystem.paper.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目视图对象 VO。
 *
 * 说明：
 * - 题目与知识点是多对多，VO 用 knowledgeIds + knowledgeNames 两个平铺字段承载，
 *   不引入额外知识点 VO，由 Service 层查关联表后填充；
 * - promptTemplateName 为模板名回显，便于组卷页面展示"本题将使用哪个提示词模板"。
 */
@Data
@Schema(description = "题目信息")
public class QuestionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "题目ID", example = "1")
    private Long id;

    @Schema(description = "所属试卷ID", example = "1")
    private Long paperId;

    @Schema(description = "题号", example = "1")
    private String questionNo;

    @Schema(description = "大题名称", example = "简答题")
    private String sectionName;

    @Schema(description = "题型：1单选 2多选 3判断 4填空 5简答 6论述 7计算 8案例", example = "5")
    private Integer questionType;

    @Schema(description = "题干内容")
    private String questionContent;

    @Schema(description = "标准答案/参考答案")
    private String standardAnswer;

    @Schema(description = "评分细则/得分点")
    private String scoringRule;

    @Schema(description = "本题分值", example = "10.0")
    private BigDecimal score;

    @Schema(description = "难度：1~5", example = "3")
    private Integer difficulty;

    @Schema(description = "评分提示词模板ID", example = "1")
    private Long promptTemplateId;

    @Schema(description = "评分提示词模板名称（Service 填充，可能为 null）", example = "简答题通用评分")
    private String promptTemplateName;

    @Schema(description = "题目排序号", example = "1")
    private Integer sortOrder;

    @Schema(description = "绑定的知识点ID集合（Service 填充）", example = "[1, 3]")
    private List<Long> knowledgeIds;

    @Schema(description = "绑定的知识点名称集合（Service 填充）", example = "[\"线性结构\", \"二叉树遍历\"]")
    private List<String> knowledgeNames;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
