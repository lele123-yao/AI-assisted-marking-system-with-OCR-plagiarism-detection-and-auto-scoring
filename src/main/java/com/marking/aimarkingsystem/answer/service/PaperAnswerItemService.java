package com.marking.aimarkingsystem.answer.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.answer.dto.TeacherScoreDTO;
import com.marking.aimarkingsystem.answer.entity.ExamAnswerItem;
import com.marking.aimarkingsystem.answer.mapper.ExamAnswerItemMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 答卷逐题结果服务：分别维护 AI 结果与教师结果。
 *
 * 设计要点：
 * - AI 只写 ai_score / ai_comment，教师只写 final_score / final_comment，
 *   两组列互不覆盖，因此 AI 任务重试不会冲掉教师已经确认的成绩；
 * - 使用 (answer_id, question_id) 唯一键做 upsert，消费者重复消费也安全。
 */
@Service
public class PaperAnswerItemService {

    /** 评语列长度上限，与 exam_answer_item 表定义一致 */
    private static final int COMMENT_MAX_LENGTH = 1000;

    private final ExamAnswerItemMapper itemMapper;

    public PaperAnswerItemService(ExamAnswerItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    /**
     * 写入 AI 逐题结果（只更新 AI 两列）。
     *
     * @param answerId 答卷ID
     * @param results  AI 返回的逐题结果，每项至少包含 questionId / score，可选 comment
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveAiItems(Long answerId, List<Map<String, Object>> results) {
        if (answerId == null || results == null || results.isEmpty()) {
            return;
        }
        for (Map<String, Object> result : results) {
            Long questionId = toLong(result.get("questionId"));
            if (questionId == null) {
                continue;
            }
            BigDecimal score = toDecimal(result.get("score"));
            String comment = truncate(Objects.toString(result.get("comment"), null));

            ExamAnswerItem exists = find(answerId, questionId);
            if (exists == null) {
                ExamAnswerItem item = new ExamAnswerItem();
                item.setAnswerId(answerId);
                item.setQuestionId(questionId);
                item.setAiScore(score);
                item.setAiComment(comment);
                itemMapper.insert(item);
            } else {
                itemMapper.update(null, Wrappers.<ExamAnswerItem>lambdaUpdate()
                        .eq(ExamAnswerItem::getId, exists.getId())
                        .set(ExamAnswerItem::getAiScore, score)
                        .set(ExamAnswerItem::getAiComment, comment));
            }
        }
    }

    /**
     * 写入教师逐题结果（只更新 final 两列）。
     *
     * @param answerId 答卷ID
     * @param items    教师提交的逐题得分与评语
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFinalItems(Long answerId, List<TeacherScoreDTO.QuestionScore> items) {
        if (answerId == null || items == null || items.isEmpty()) {
            return;
        }
        for (TeacherScoreDTO.QuestionScore source : items) {
            if (source == null || source.getQuestionId() == null) {
                continue;
            }
            String comment = truncate(source.getComment());

            ExamAnswerItem exists = find(answerId, source.getQuestionId());
            if (exists == null) {
                ExamAnswerItem item = new ExamAnswerItem();
                item.setAnswerId(answerId);
                item.setQuestionId(source.getQuestionId());
                item.setFinalScore(source.getScore());
                item.setFinalComment(comment);
                itemMapper.insert(item);
            } else {
                itemMapper.update(null, Wrappers.<ExamAnswerItem>lambdaUpdate()
                        .eq(ExamAnswerItem::getId, exists.getId())
                        .set(ExamAnswerItem::getFinalScore, source.getScore())
                        .set(ExamAnswerItem::getFinalComment, comment));
            }
        }
    }

    /** 查询某份答卷的全部逐题结果 */
    public List<ExamAnswerItem> listByAnswer(Long answerId) {
        if (answerId == null) {
            return Collections.emptyList();
        }
        return itemMapper.selectList(Wrappers.<ExamAnswerItem>lambdaQuery()
                .eq(ExamAnswerItem::getAnswerId, answerId)
                .orderByAsc(ExamAnswerItem::getQuestionId));
    }

    private ExamAnswerItem find(Long answerId, Long questionId) {
        return itemMapper.selectOne(Wrappers.<ExamAnswerItem>lambdaQuery()
                .eq(ExamAnswerItem::getAnswerId, answerId)
                .eq(ExamAnswerItem::getQuestionId, questionId)
                .last("LIMIT 1"));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 评语超长截断，避免写库失败导致整份答卷阅卷失败 */
    private String truncate(String comment) {
        if (!StringUtils.hasText(comment)) {
            return comment;
        }
        return comment.length() <= COMMENT_MAX_LENGTH
                ? comment : comment.substring(0, COMMENT_MAX_LENGTH);
    }
}
