package com.marking.aimarkingsystem.agent.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 历史阅卷案例查询（只读）。
 *
 * 隐私约束：
 * - 只查分数和评语，绝不 select 学生作答、学生姓名、学号；
 * - 排除当前答卷，避免把"自己"当历史参考；
 * - 只取已有评语的记录，空评语没有参考价值。
 *
 * 这里用 @Select 而不是新增 XML，是因为只读一条 SQL，
 * 放在 Agent 包内可以做到"不改业务代码"。
 */
@Mapper
public interface HistoryCaseMapper {

    @Select("""
            SELECT i.ai_score      AS aiScore,
                   i.ai_comment    AS aiComment,
                   i.final_score   AS finalScore,
                   i.final_comment AS finalComment
            FROM exam_answer_item i
            JOIN exam_question q ON q.id = i.question_id
            WHERE q.question_type = #{questionType}
              AND i.answer_id <> #{excludeAnswerId}
              AND ((i.final_comment IS NOT NULL AND i.final_comment <> '')
                   OR (i.ai_comment IS NOT NULL AND i.ai_comment <> ''))
            ORDER BY i.update_time DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectCasesByQuestionType(@Param("questionType") Integer questionType,
                                                        @Param("excludeAnswerId") Long excludeAnswerId,
                                                        @Param("limit") int limit);
}
