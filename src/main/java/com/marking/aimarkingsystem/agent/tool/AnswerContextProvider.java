package com.marking.aimarkingsystem.agent.tool;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.answer.entity.ExamAnswer;
import com.marking.aimarkingsystem.answer.entity.ExamAnswerItem;
import com.marking.aimarkingsystem.answer.mapper.ExamAnswerMapper;
import com.marking.aimarkingsystem.answer.service.PaperAnswerItemService;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.paper.mapper.ExamQuestionMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent 的"数据访问层"：把答卷、题目、逐题结果组装成模型可读的文本。
 *
 * 为什么单独抽出来：
 * - 阅卷 Agent 和教师助手 Agent 都要查同样的数据，避免两份 SQL/拼接逻辑；
 * - 返回文本而不是实体，防止模型看到一堆字段名却不知道怎么用；
 * - 所有金额、空值、长文本都在这里统一处理，工具层只做参数校验。
 */
@Component
public class AnswerContextProvider {

    /** 整卷 OCR 文本回填上限，避免一份长答卷把上下文撑爆 */
    private static final int MAX_OCR_LENGTH = 6000;

    /** 线上单题答案回填上限 */
    private static final int MAX_ANSWER_LENGTH = 3000;

    private final ExamAnswerMapper answerMapper;
    private final ExamPaperMapper paperMapper;
    private final ExamQuestionMapper questionMapper;
    private final PaperAnswerItemService answerItemService;

    public AnswerContextProvider(ExamAnswerMapper answerMapper,
                                 ExamPaperMapper paperMapper,
                                 ExamQuestionMapper questionMapper,
                                 PaperAnswerItemService answerItemService) {
        this.answerMapper = answerMapper;
        this.paperMapper = paperMapper;
        this.questionMapper = questionMapper;
        this.answerItemService = answerItemService;
    }

    public ExamAnswer requireAnswer(Long answerId) {
        ExamAnswer answer = answerMapper.selectById(answerId);
        if (answer == null) {
            throw new BusinessException("答卷不存在: " + answerId);
        }
        return answer;
    }

    public ExamQuestion questionOf(Long answerId, Long questionId) {
        ExamAnswer answer = requireAnswer(answerId);
        ExamQuestion question = questionMapper.selectById(questionId);
        if (question == null || !Objects.equals(question.getPaperId(), answer.getPaperId())) {
            throw new BusinessException("题目不属于该答卷: questionId=" + questionId);
        }
        return question;
    }

    public Long courseIdOfAnswer(Long answerId) {
        ExamAnswer answer = requireAnswer(answerId);
        ExamPaper paper = paperMapper.selectById(answer.getPaperId());
        return paper == null ? null : paper.getCourseId();
    }

    /** 答卷对应的试卷实体，工具层需要课程、题型等字段时用 */
    public ExamPaper paperOfAnswer(Long answerId) {
        return paperMapper.selectById(requireAnswer(answerId).getPaperId());
    }

    /** 试卷下的全部题目，已按 sortOrder 升序 */
    public List<ExamQuestion> questionsOfPaper(Long paperId) {
        return questionMapper.selectList(Wrappers.<ExamQuestion>lambdaQuery()
                .eq(ExamQuestion::getPaperId, paperId)
                .orderByAsc(ExamQuestion::getSortOrder));
    }

    /** 某题当前的评分记录（AI 分/教师分/评语），没有评分时返回 null */
    public ExamAnswerItem itemOf(Long answerId, Long questionId) {
        return itemsOf(answerId).get(questionId);
    }

    /**
     * 学生原始作答（不截断、不加工）。
     *
     * 单独给"答卷文本提取工具"用：该工具需要先拿到原文再做清洗与注入过滤，
     * 如果这里就截断，后面的过滤逻辑会拿到半截文本，反而更容易误判。
     */
    public String studentAnswer(Long answerId, Long questionId) {
        ExamAnswer answer = requireAnswer(answerId);
        if (isPaperScan(answer)) {
            return answer.getOcrText() == null ? "" : answer.getOcrText();
        }
        return extractOnlineAnswer(answer.getAnswerContent(), questionId);
    }

    /** 答卷总览：基本信息 + 逐题分值与得分，供教师助手快速建立上下文 */
    public String overview(Long answerId) {
        ExamAnswer answer = requireAnswer(answerId);
        ExamPaper paper = paperMapper.selectById(answer.getPaperId());
        List<ExamQuestion> questions = questionsOf(answer.getPaperId());
        Map<Long, ExamAnswerItem> items = itemsOf(answerId);

        StringBuilder sb = new StringBuilder();
        sb.append("【答卷基本信息】\n");
        sb.append("答卷ID：").append(answer.getId()).append("\n");
        sb.append("试卷：").append(paper == null ? "-" : paper.getPaperTitle())
                .append("（试卷ID ").append(answer.getPaperId()).append("）\n");
        if (paper != null) {
            sb.append("课程ID：").append(paper.getCourseId()).append("\n");
        }
        sb.append("学生：").append(value(answer.getStudentNo())).append(" ")
                .append(value(answer.getStudentName())).append(" ")
                .append(value(answer.getStudentClass())).append("\n");
        sb.append("答卷来源：").append(value(answer.getAnswerSource())).append("\n");
        sb.append("AI参考分：").append(value(answer.getAiReferenceScore()))
                .append("；教师最终分：").append(value(answer.getTeacherFinalScore())).append("\n");
        sb.append("\n【逐题概况】\n");
        for (ExamQuestion question : questions) {
            ExamAnswerItem item = items.get(question.getId());
            sb.append("- questionId=").append(question.getId())
                    .append("，第").append(question.getQuestionNo()).append("题")
                    .append("，满分").append(value(question.getScore()))
                    .append("，AI分").append(item == null ? "-" : value(item.getAiScore()))
                    .append("，教师分").append(item == null ? "-" : value(item.getFinalScore()))
                    .append("，题干：").append(shorten(question.getQuestionContent(), 80))
                    .append("\n");
        }
        return sb.toString();
    }

    /** 单题详情：题干、参考答案、评分细则、考生作答、当前评分，供阅卷 Agent 复核 */
    public String questionDetail(Long answerId, Long questionId) {
        ExamAnswer answer = requireAnswer(answerId);
        ExamQuestion question = questionOf(answerId, questionId);
        Map<Long, ExamAnswerItem> items = itemsOf(answerId);
        ExamAnswerItem item = items.get(questionId);

        StringBuilder sb = new StringBuilder();
        sb.append("【题目】\n");
        sb.append("questionId：").append(question.getId()).append("\n");
        sb.append("题号：").append(value(question.getQuestionNo()));
        if (StringUtils.hasText(question.getSectionName())) {
            sb.append("（").append(question.getSectionName()).append("）");
        }
        sb.append("\n满分：").append(value(question.getScore())).append(" 分\n");
        sb.append("题干：").append(value(question.getQuestionContent())).append("\n");

        sb.append("\n【参考答案】\n").append(value(question.getStandardAnswer())).append("\n");
        sb.append("\n【评分细则】\n").append(value(question.getScoringRule())).append("\n");

        sb.append("\n【考生作答】\n");
        if (isPaperScan(answer)) {
            sb.append("（整卷 OCR 文本，请自行定位本题作答；找不到按 0 分并说明）\n")
                    .append(shorten(answer.getOcrText(), MAX_OCR_LENGTH));
        } else {
            sb.append(shorten(extractOnlineAnswer(answer.getAnswerContent(), questionId), MAX_ANSWER_LENGTH));
        }
        sb.append("\n\n【当前评分】\n");
        sb.append("AI：").append(item == null ? "暂无" : value(item.getAiScore()) + " 分，" + value(item.getAiComment()))
                .append("\n");
        sb.append("教师：").append(item == null ? "暂无" : value(item.getFinalScore()) + " 分，" + value(item.getFinalComment()))
                .append("\n");
        return sb.toString();
    }

    // ==================== 内部方法 ====================

    private List<ExamQuestion> questionsOf(Long paperId) {
        return questionsOfPaper(paperId);
    }

    private Map<Long, ExamAnswerItem> itemsOf(Long answerId) {
        Map<Long, ExamAnswerItem> map = new HashMap<>();
        for (ExamAnswerItem item : answerItemService.listByAnswer(answerId)) {
            map.put(item.getQuestionId(), item);
        }
        return map;
    }

    private boolean isPaperScan(ExamAnswer answer) {
        return "PAPER_SCAN".equals(answer.getAnswerSource());
    }

    /** 线上答卷 answerContent 是 [{questionId, answer}...]，按题号取出作答 */
    private String extractOnlineAnswer(String answerContent, Long questionId) {
        if (!StringUtils.hasText(answerContent) || !JSONUtil.isTypeJSON(answerContent)) {
            return value(answerContent);
        }
        try {
            JSONArray array = JSONUtil.parseArray(answerContent);
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getJSONObject(i);
                if (item != null && Objects.equals(item.getLong("questionId"), questionId)) {
                    return item.getStr("answer", "");
                }
            }
        } catch (Exception ignored) {
            // 格式异常时退化为返回原文，交给模型自行判断
        }
        return value(answerContent);
    }

    private String value(Object value) {
        if (value == null) {
            return "-";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : "-";
    }

    private String shorten(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "-";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...(已截断)";
    }
}
