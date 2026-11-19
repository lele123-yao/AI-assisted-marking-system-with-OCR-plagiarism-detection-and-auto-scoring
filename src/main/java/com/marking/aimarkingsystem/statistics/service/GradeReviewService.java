package com.marking.aimarkingsystem.statistics.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.answer.entity.ExamAnswer;
import com.marking.aimarkingsystem.answer.enums.AnswerSourceEnum;
import com.marking.aimarkingsystem.answer.enums.AnswerStatusEnum;
import com.marking.aimarkingsystem.answer.mapper.ExamAnswerMapper;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.paper.mapper.ExamQuestionMapper;
import com.marking.aimarkingsystem.statistics.dto.GradeReviewDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 成绩复核服务。
 *
 * 业务规则：
 * 1. 教师可以覆盖修改答卷最终分数（复核改分），也可直接确认 AI 结果；
 * 2. 纸质答卷强制人工确认——AI 只写参考分，必须由教师录入最终分后才能复核通过，
 *    不允许"一键通过"把参考分当最终成绩；
 * 3. 复核改分不允许直接照抄 AI 参考分（与纸质录分阶段保持同一条底线规则）；
 * 4. 处理中（OCR/AI 进行中）的答卷不能复核，避免与异步任务互相覆盖；
 * 5. 驳回重批会清空最终分并把状态退回"待教师确认"。
 *
 * 复核结果落库只使用现有字段：
 * - 最终分 → teacher_final_score；
 * - 复核时间 → mark_time；
 * - 复核状态由 marking_status + 分数差共同推导（5 已归档即复核完成）。
 * 复核人、复核意见在当前表结构中无对应列，VO 中为预留字段，后续如需持久化再扩展。
 *
 * 数据权限（占位）：教师只能复核自己所授课程下试卷的答卷。
 */
@Service
public class GradeReviewService {

    private static final Logger log = LoggerFactory.getLogger(GradeReviewService.class);

    private static final int ACTION_CONFIRM = 1;
    private static final int ACTION_ADJUST = 2;
    private static final int ACTION_REJECT = 3;

    private final ExamAnswerMapper examAnswerMapper;
    private final ExamPaperMapper examPaperMapper;
    private final ExamQuestionMapper examQuestionMapper;

    public GradeReviewService(ExamAnswerMapper examAnswerMapper,
                              ExamPaperMapper examPaperMapper,
                              ExamQuestionMapper examQuestionMapper) {
        this.examAnswerMapper = examAnswerMapper;
        this.examPaperMapper = examPaperMapper;
        this.examQuestionMapper = examQuestionMapper;
    }

    /**
     * 提交成绩复核。
     *
     * @param dto 复核参数，reviewAction：1 通过 / 2 改分 / 3 驳回重批
     */
    @Transactional(rollbackFor = Exception.class)
    public void review(GradeReviewDTO dto) {
        ExamAnswer answer = examAnswerMapper.selectById(dto.getAnswerId());
        if (answer == null) {
            throw new BusinessException("答卷不存在: " + dto.getAnswerId());
        }
        ExamPaper paper = examPaperMapper.selectById(answer.getPaperId());
        if (paper == null) {
            throw new BusinessException("答卷所属试卷不存在");
        }
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户

        Integer status = answer.getMarkingStatus();
        if (AnswerStatusEnum.PROCESSING.getCode().equals(status)
                || AnswerStatusEnum.AI_MARKING.getCode().equals(status)) {
            throw new BusinessException("答卷正在OCR或AI批改中，暂不能复核");
        }
        if (AnswerStatusEnum.FAILED.getCode().equals(status)) {
            throw new BusinessException("答卷处理失败，请先重新触发识别或人工录分");
        }

        int action = dto.getReviewAction();
        if (action == ACTION_CONFIRM) {
            confirm(answer);
        } else if (action == ACTION_ADJUST) {
            adjust(answer, paper, dto);
        } else if (action == ACTION_REJECT) {
            reject(answer, dto);
        } else {
            throw new BusinessException("不支持的复核处理方式: " + action);
        }
    }

    /** 复核通过：维持现有最终分并归档 */
    private void confirm(ExamAnswer answer) {
        if (answer.getTeacherFinalScore() == null) {
            if (AnswerSourceEnum.isPaperScan(answer.getAnswerSource())) {
                throw new BusinessException("纸质答卷必须由教师人工录入最终分数后才能复核通过");
            }
            throw new BusinessException("该答卷尚未产生最终分数，无法复核通过");
        }
        updateArchived(answer.getId());
        log.info("成绩复核通过: answerId={}, finalScore={}", answer.getId(), answer.getTeacherFinalScore());
    }

    /** 复核改分：教师覆盖最终分数，可附逐题明细 */
    private void adjust(ExamAnswer answer, ExamPaper paper, GradeReviewDTO dto) {
        BigDecimal finalScore = dto.getFinalScore();
        if (finalScore == null) {
            throw new BusinessException("改分时必须填写最终得分");
        }
        finalScore = scale(finalScore);
        if (paper.getTotalScore() != null && finalScore.compareTo(scale(paper.getTotalScore())) > 0) {
            throw new BusinessException("最终得分不能超过试卷总分 " + scale(paper.getTotalScore()));
        }

        // 底线规则：不能把 AI 参考分直接当教师最终成绩
        if (answer.getAiReferenceScore() != null
                && finalScore.compareTo(scale(answer.getAiReferenceScore())) == 0) {
            throw new BusinessException("不能直接将AI参考分作为教师最终成绩，请根据实际作答调整分数");
        }

        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            BigDecimal sum = validateAndSumItems(answer.getPaperId(), dto.getItems());
            if (sum.compareTo(finalScore) != 0) {
                throw new BusinessException("各题得分合计 " + sum + " 与最终得分 " + finalScore + " 不一致");
            }
        }

        ExamAnswer update = new ExamAnswer();
        update.setId(answer.getId());
        update.setTeacherFinalScore(finalScore);
        update.setMarkingStatus(AnswerStatusEnum.ARCHIVED.getCode());
        update.setMarkTime(LocalDateTime.now());
        examAnswerMapper.updateById(update);
        log.info("成绩复核改分: answerId={}, aiScore={}, finalScore={}",
                answer.getId(), answer.getAiReferenceScore(), finalScore);
    }

    /** 驳回重批：清空最终分，退回待教师确认 */
    private void reject(ExamAnswer answer, GradeReviewDTO dto) {
        ExamAnswer update = new ExamAnswer();
        update.setId(answer.getId());
        update.setTeacherFinalScore(null);
        update.setMarkingStatus(AnswerStatusEnum.PENDING_TEACHER_CONFIRM.getCode());
        update.setMarkTime(null);
        examAnswerMapper.updateById(update);
        // 当前表结构无复核意见列，先落日志保留痕迹，后续扩展字段后再入库
        log.info("成绩复核驳回: answerId={}, remark={}", answer.getId(), dto.getReviewRemark());
    }

    private void updateArchived(Long answerId) {
        ExamAnswer update = new ExamAnswer();
        update.setId(answerId);
        update.setMarkingStatus(AnswerStatusEnum.ARCHIVED.getCode());
        update.setMarkTime(LocalDateTime.now());
        examAnswerMapper.updateById(update);
    }

    /** 校验逐题得分：题目归属、不重复、不超满分，返回合计 */
    private BigDecimal validateAndSumItems(Long paperId, List<GradeReviewDTO.ItemScore> items) {
        Map<Long, ExamQuestion> questionMap = examQuestionMapper.selectList(
                        Wrappers.<ExamQuestion>lambdaQuery().eq(ExamQuestion::getPaperId, paperId))
                .stream()
                .collect(Collectors.toMap(ExamQuestion::getId, question -> question));

        Set<Long> seen = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (GradeReviewDTO.ItemScore item : items) {
            if (!seen.add(item.getQuestionId())) {
                throw new BusinessException("题目重复录入：" + item.getQuestionId());
            }
            ExamQuestion question = questionMap.get(item.getQuestionId());
            if (question == null) {
                throw new BusinessException("题目不属于该试卷：" + item.getQuestionId());
            }
            BigDecimal score = scale(item.getScore());
            if (question.getScore() != null && score.compareTo(scale(question.getScore())) > 0) {
                throw new BusinessException("题目 " + question.getQuestionNo()
                        + " 得分 " + score + " 超过满分 " + scale(question.getScore()));
            }
            sum = sum.add(score);
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
