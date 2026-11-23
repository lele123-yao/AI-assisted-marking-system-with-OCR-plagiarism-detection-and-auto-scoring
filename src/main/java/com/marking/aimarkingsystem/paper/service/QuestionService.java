package com.marking.aimarkingsystem.paper.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.knowledge.entity.QuestionKnowledge;
import com.marking.aimarkingsystem.knowledge.entity.QuestionKnowledgeRel;
import com.marking.aimarkingsystem.knowledge.mapper.QuestionKnowledgeMapper;
import com.marking.aimarkingsystem.knowledge.mapper.QuestionKnowledgeRelMapper;
import com.marking.aimarkingsystem.paper.dto.QuestionAddDTO;
import com.marking.aimarkingsystem.paper.dto.QuestionBatchDTO;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.paper.mapper.ExamQuestionMapper;
import com.marking.aimarkingsystem.paper.vo.QuestionVO;
import com.marking.aimarkingsystem.prompt.entity.PromptTemplate;
import com.marking.aimarkingsystem.prompt.mapper.PromptTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 题目服务：新增/编辑主观题，并维护"题目-知识点"多对多关联。
 *
 * 与 MyBatis-Plus 的分工：
 * - 单表 CRUD 走 BaseMapper；
 * - 多对多关联不写 JOIN 复杂 SQL，改为"查关联表 -> 批量查知识点 -> 内存组装"，
 *   与模块1 UserService 填充角色的方式一致，避免 N+1 与复杂 SQL 维护成本。
 *
 * 数据权限说明（占位，暂不实现）：
 * 教师只能维护自己课程下试卷的题目。完整实现时应由 paperId 反查 exam_paper.courseId，
 * 再校验该课程 teacherId 是否为当前登录用户，不通过抛 403。本阶段只保留注释。
 */
@Service
public class QuestionService {

    /** 知识点标签通用状态：1 启用 */
    private static final int STATUS_ENABLED = 1;

    /** 提示词模板 questionType = 0 表示通用模板 */
    private static final int TEMPLATE_GENERAL_TYPE = 0;

    /** 主观题起始题型：5 简答、6 论述、7 计算、8 案例 */
    private static final int SUBJECTIVE_TYPE_START = 5;

    /** 默认难度 3（中等） */
    private static final int DEFAULT_DIFFICULTY = 3;

    private final ExamQuestionMapper questionMapper;

    private final ExamPaperMapper paperMapper;

    private final QuestionKnowledgeMapper knowledgeMapper;

    private final QuestionKnowledgeRelMapper knowledgeRelMapper;

    private final PromptTemplateMapper promptTemplateMapper;

    private final PaperService paperService;

    public QuestionService(ExamQuestionMapper questionMapper,
                           ExamPaperMapper paperMapper,
                           QuestionKnowledgeMapper knowledgeMapper,
                           QuestionKnowledgeRelMapper knowledgeRelMapper,
                           PromptTemplateMapper promptTemplateMapper,
                           PaperService paperService) {
        this.questionMapper = questionMapper;
        this.paperMapper = paperMapper;
        this.knowledgeMapper = knowledgeMapper;
        this.knowledgeRelMapper = knowledgeRelMapper;
        this.promptTemplateMapper = promptTemplateMapper;
        this.paperService = paperService;
    }

    /**
     * 批量保存整份试卷的题目（整体替换）。
     *
     * 适用场景：
     * - 上传解析后，教师确认了整份试卷的题目与评分标准，一次性入库；
     * - 编辑草稿时整卷替换。
     *
     * 校验策略：先整体校验（题号唯一、主观题必须有参考答案与评分细则、分值 > 0），
     * 全部通过后再在同一事务内删除旧题、插入新题并回写试卷汇总，
     * 避免出现"删了旧题、新题又因校验失败没插进去"的半成品数据。
     *
     * 数据权限占位：同新增题目，需校验试卷归属当前教师。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchSaveDraft(QuestionBatchDTO dto) {
        ExamPaper paper = requirePaper(dto.getPaperId());
        assertPaperEditable(paper);

        List<QuestionBatchDTO.Item> items = dto.getQuestions() == null
                ? Collections.emptyList() : dto.getQuestions();

        validateBatchItems(items);

        // 清空旧题目与知识点关联（逻辑删除题目）
        List<ExamQuestion> existing = questionMapper.selectList(
                Wrappers.<ExamQuestion>lambdaQuery().eq(ExamQuestion::getPaperId, paper.getId()));
        if (!existing.isEmpty()) {
            List<Long> oldIds = existing.stream().map(ExamQuestion::getId).toList();
            knowledgeRelMapper.delete(Wrappers.<QuestionKnowledgeRel>lambdaQuery()
                    .in(QuestionKnowledgeRel::getQuestionId, oldIds));
            questionMapper.delete(Wrappers.<ExamQuestion>lambdaQuery()
                    .eq(ExamQuestion::getPaperId, paper.getId()));
        }

        // 按前端顺序插入，sortOrder 从 1 开始
        BigDecimal totalScore = BigDecimal.ZERO;
        int sortOrder = 1;
        for (QuestionBatchDTO.Item item : items) {
            ExamQuestion question = new ExamQuestion();
            question.setPaperId(paper.getId());
            question.setQuestionNo(item.getQuestionNo().trim());
            question.setSectionName(item.getSectionName());
            question.setQuestionType(item.getQuestionType());
            question.setQuestionContent(item.getQuestionContent());
            question.setStandardAnswer(item.getStandardAnswer());
            question.setScoringRule(item.getScoringRule());
            question.setScore(item.getScore());
            question.setDifficulty(DEFAULT_DIFFICULTY);
            question.setSortOrder(sortOrder++);
            questionMapper.insert(question);
            totalScore = totalScore.add(item.getScore());
        }

        paperService.refreshSummary(paper.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionCount", items.size());
        result.put("totalScore", totalScore);
        return result;
    }

    /** 批量题目的整体校验：题号唯一、主观题必需字段、分值合法 */
    private void validateBatchItems(List<QuestionBatchDTO.Item> items) {
        Set<String> seenQuestionNos = new HashSet<>();
        int row = 0;
        for (QuestionBatchDTO.Item item : items) {
            row++;
            String questionNo = item.getQuestionNo() == null ? "" : item.getQuestionNo().trim();
            if (questionNo.isEmpty()) {
                throw new BusinessException("第 " + row + " 道题的题号不能为空");
            }
            if (!seenQuestionNos.add(questionNo)) {
                throw new BusinessException("题号重复：" + questionNo);
            }
            if (item.getScore() == null || item.getScore().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("第 " + questionNo + " 题的分值必须大于 0");
            }
            if (item.getQuestionType() != null && item.getQuestionType() >= SUBJECTIVE_TYPE_START) {
                if (!StringUtils.hasText(item.getStandardAnswer())) {
                    throw new BusinessException("第 " + questionNo + " 题为主观题，必须填写参考答案");
                }
                if (!StringUtils.hasText(item.getScoringRule())) {
                    throw new BusinessException("第 " + questionNo + " 题为主观题，必须填写评分细则");
                }
            }
        }
    }

    /**
     * 新增题目，并绑定知识点标签。
     *
     * 业务校验：
     * - 只有草稿状态的试卷才能加题（避免改已发布试卷）；
     * - 同一试卷内题号不能重复；
     * - 若指定了评分提示词模板，模板必须存在、启用，且题型匹配（通用模板 0 除外）；
     * - 知识点必须存在，且属于该试卷所在课程。
     *
     * 数据权限占位：后续需校验试卷所属课程是否归当前登录教师所有。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long addQuestion(QuestionAddDTO dto) {
        ExamPaper paper = requirePaper(dto.getPaperId());
        assertPaperEditable(paper);
        assertQuestionNoUnique(paper.getId(), dto.getQuestionNo(), null);
        validateSubjective(dto);
        validateTemplate(dto);

        List<Long> knowledgeIds = normalizeKnowledgeIds(dto.getKnowledgeIds());
        validateKnowledge(knowledgeIds, paper.getCourseId());

        ExamQuestion question = new ExamQuestion();
        applyDto(question, dto);
        question.setDifficulty(dto.getDifficulty() == null ? DEFAULT_DIFFICULTY : dto.getDifficulty());
        question.setSortOrder(dto.getSortOrder() == null
                ? nextSortOrder(paper.getId()) : dto.getSortOrder());
        questionMapper.insert(question);

        bindKnowledge(question.getId(), knowledgeIds);
        // 回写试卷的总分与题目数量，供列表页与状态校验直接读取
        paperService.refreshSummary(paper.getId());
        return question.getId();
    }

    /**
     * 编辑题目。
     *
     * 说明：
     * - 不允许把题目移动到其他试卷（paperId 必须与原值一致），否则试卷汇总会错乱；
     * - 知识点采用"整体覆盖"：先删除旧关联，再按新集合插入，
     *   与 UserService.assignRoles 的语义保持一致。
     *
     * 数据权限占位：同新增，需校验试卷归属当前教师。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateQuestion(Long id, QuestionAddDTO dto) {
        ExamQuestion exists = requireQuestion(id);

        if (!Objects.equals(dto.getPaperId(), exists.getPaperId())) {
            throw new BusinessException("不允许把题目移动到其他试卷");
        }

        ExamPaper paper = requirePaper(exists.getPaperId());
        assertPaperEditable(paper);
        assertQuestionNoUnique(paper.getId(), dto.getQuestionNo(), id);
        validateSubjective(dto);
        validateTemplate(dto);

        List<Long> knowledgeIds = normalizeKnowledgeIds(dto.getKnowledgeIds());
        validateKnowledge(knowledgeIds, paper.getCourseId());

        ExamQuestion update = new ExamQuestion();
        update.setId(id);
        applyDto(update, dto);
        if (update.getDifficulty() == null) {
            update.setDifficulty(exists.getDifficulty());
        }
        if (update.getSortOrder() == null) {
            update.setSortOrder(exists.getSortOrder());
        }
        questionMapper.updateById(update);

        // 整体覆盖关联：关联表使用逻辑删除，无唯一索引，因此不会出现唯一键冲突；
        // 若后续为该表补充 (question_id, knowledge_id) 唯一索引，需参考 UserRoleMapper 改物理删除
        knowledgeRelMapper.delete(Wrappers.<QuestionKnowledgeRel>lambdaQuery()
                .eq(QuestionKnowledgeRel::getQuestionId, id));
        bindKnowledge(id, knowledgeIds);

        paperService.refreshSummary(paper.getId());
    }

    /**
     * 查询某份试卷下的题目列表（按排序号升序）。
     *
     * 批量填充模板名称与知识点名称，避免逐条查询：
     * 一页 20 道题固定只产生 4 条 SQL（题目、关联、知识点、模板）。
     *
     * 数据权限占位：后续需校验试卷归属当前教师。
     */
    public List<QuestionVO> listByPaper(Long paperId) {
        requirePaper(paperId);

        List<ExamQuestion> questions = questionMapper.selectList(
                Wrappers.<ExamQuestion>lambdaQuery()
                        .eq(ExamQuestion::getPaperId, paperId)
                        .orderByAsc(ExamQuestion::getSortOrder)
                        .orderByAsc(ExamQuestion::getId));
        if (questions.isEmpty()) {
            return Collections.emptyList();
        }

        List<QuestionVO> records = questions.stream()
                .map(this::toQuestionVO)
                .toList();
        fillTemplateNames(records);
        fillKnowledge(records);
        return records;
    }

    /**
     * 删除题目。
     *
     * 逻辑删除题目本身，同时清理其知识点关联，最后回写试卷汇总，
     * 三者必须在同一事务内，避免出现"题目没了但总分没减"的脏数据。
     *
     * 数据权限占位：后续需校验试卷归属当前教师。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestion(Long id) {
        ExamQuestion question = requireQuestion(id);
        ExamPaper paper = requirePaper(question.getPaperId());
        assertPaperEditable(paper);

        questionMapper.deleteById(id);
        knowledgeRelMapper.delete(Wrappers.<QuestionKnowledgeRel>lambdaQuery()
                .eq(QuestionKnowledgeRel::getQuestionId, id));
        paperService.refreshSummary(paper.getId());
    }

    /**
     * 单独维护题目的知识点标签（整体覆盖，传空集合表示清空）。
     *
     * 为什么不复用编辑题目接口：组卷页面常常只调整知识点打标，
     * 不应要求把题干、标准答案等全量字段一起回传，避免误覆盖。
     *
     * 数据权限占位：后续需校验试卷归属当前教师。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignKnowledge(Long questionId, List<Long> knowledgeIds) {
        ExamQuestion question = requireQuestion(questionId);
        ExamPaper paper = requirePaper(question.getPaperId());
        assertPaperEditable(paper);

        List<Long> normalized = normalizeKnowledgeIds(knowledgeIds);
        validateKnowledge(normalized, paper.getCourseId());

        knowledgeRelMapper.delete(Wrappers.<QuestionKnowledgeRel>lambdaQuery()
                .eq(QuestionKnowledgeRel::getQuestionId, questionId));
        bindKnowledge(questionId, normalized);
    }

    // ==================== 内部方法 ====================

    /** 主观题（5~8）必须填写标准答案与评分细则，否则 AI 无法按点给分 */
    private void validateSubjective(QuestionAddDTO dto) {
        if (dto.getQuestionType() == null || dto.getQuestionType() < SUBJECTIVE_TYPE_START) {
            return;
        }
        if (!StringUtils.hasText(dto.getStandardAnswer())) {
            throw new BusinessException("主观题必须填写标准答案");
        }
        if (!StringUtils.hasText(dto.getScoringRule())) {
            throw new BusinessException("主观题必须填写评分细则");
        }
    }

    /** 校验提示词模板存在、启用且题型匹配 */
    private void validateTemplate(QuestionAddDTO dto) {
        if (dto.getPromptTemplateId() == null) {
            return;
        }

        PromptTemplate template = promptTemplateMapper.selectById(dto.getPromptTemplateId());
        if (template == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "评分提示词模板不存在");
        }
        if (!Objects.equals(template.getStatus(), STATUS_ENABLED)) {
            throw new BusinessException("评分提示词模板已停用");
        }
        boolean typeMatched = Objects.equals(template.getQuestionType(), TEMPLATE_GENERAL_TYPE)
                || Objects.equals(template.getQuestionType(), dto.getQuestionType());
        if (!typeMatched) {
            throw new BusinessException("评分提示词模板与题目题型不匹配");
        }
    }

    private List<Long> normalizeKnowledgeIds(List<Long> knowledgeIds) {
        if (knowledgeIds == null || knowledgeIds.isEmpty()) {
            return Collections.emptyList();
        }
        return knowledgeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /** 知识点必须存在，且归属于试卷所在课程（跨课程关联会让学情分析口径混乱） */
    private void validateKnowledge(List<Long> knowledgeIds, Long courseId) {
        if (knowledgeIds.isEmpty()) {
            return;
        }

        List<QuestionKnowledge> knowledges = knowledgeMapper.selectBatchIds(knowledgeIds);
        if (knowledges.size() != knowledgeIds.size()) {
            throw new BusinessException("存在无效的知识点ID");
        }

        boolean crossCourse = knowledges.stream()
                .anyMatch(knowledge -> !Objects.equals(knowledge.getCourseId(), courseId));
        if (crossCourse) {
            throw new BusinessException("存在不属于该试卷所属课程的知识点");
        }
    }

    private void bindKnowledge(Long questionId, List<Long> knowledgeIds) {
        for (Long knowledgeId : knowledgeIds) {
            QuestionKnowledgeRel relation = new QuestionKnowledgeRel();
            relation.setQuestionId(questionId);
            relation.setKnowledgeId(knowledgeId);
            knowledgeRelMapper.insert(relation);
        }
    }

    private void assertPaperEditable(ExamPaper paper) {
        if (!Objects.equals(paper.getStatus(), PaperService.STATUS_DRAFT)) {
            throw new BusinessException("只有草稿状态的试卷才能维护题目");
        }
    }

    private void assertQuestionNoUnique(Long paperId, String questionNo, Long excludeId) {
        Long count = questionMapper.selectCount(
                Wrappers.<ExamQuestion>lambdaQuery()
                        .eq(ExamQuestion::getPaperId, paperId)
                        .eq(ExamQuestion::getQuestionNo, questionNo)
                        .ne(excludeId != null, ExamQuestion::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("该试卷下题号已存在");
        }
    }

    /** 未指定排序号时追加到末尾 */
    private Integer nextSortOrder(Long paperId) {
        Long count = questionMapper.selectCount(
                Wrappers.<ExamQuestion>lambdaQuery().eq(ExamQuestion::getPaperId, paperId));
        return count == null ? 1 : count.intValue() + 1;
    }

    private ExamPaper requirePaper(Long paperId) {
        ExamPaper paper = paperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        return paper;
    }

    private ExamQuestion requireQuestion(Long questionId) {
        ExamQuestion question = questionMapper.selectById(questionId);
        if (question == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "题目不存在");
        }
        return question;
    }

    private void applyDto(ExamQuestion question, QuestionAddDTO dto) {
        question.setPaperId(dto.getPaperId());
        question.setQuestionNo(dto.getQuestionNo());
        question.setSectionName(dto.getSectionName());
        question.setQuestionType(dto.getQuestionType());
        question.setQuestionContent(dto.getQuestionContent());
        question.setStandardAnswer(dto.getStandardAnswer());
        question.setScoringRule(dto.getScoringRule());
        question.setScore(dto.getScore());
        question.setDifficulty(dto.getDifficulty());
        question.setPromptTemplateId(dto.getPromptTemplateId());
        question.setSortOrder(dto.getSortOrder());
    }

    /** 批量填充模板名称 */
    private void fillTemplateNames(List<QuestionVO> questions) {
        List<Long> templateIds = questions.stream()
                .map(QuestionVO::getPromptTemplateId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (templateIds.isEmpty()) {
            return;
        }

        Map<Long, String> nameMap = promptTemplateMapper.selectBatchIds(templateIds).stream()
                .collect(Collectors.toMap(PromptTemplate::getId, PromptTemplate::getTemplateName));
        questions.forEach(vo -> {
            if (vo.getPromptTemplateId() != null) {
                vo.setPromptTemplateName(nameMap.get(vo.getPromptTemplateId()));
            }
        });
    }

    /** 批量填充知识点ID与名称 */
    private void fillKnowledge(List<QuestionVO> questions) {
        List<Long> questionIds = questions.stream().map(QuestionVO::getId).toList();
        List<QuestionKnowledgeRel> relations = knowledgeRelMapper.selectList(
                Wrappers.<QuestionKnowledgeRel>lambdaQuery()
                        .in(QuestionKnowledgeRel::getQuestionId, questionIds));
        if (relations.isEmpty()) {
            questions.forEach(vo -> vo.setKnowledgeIds(Collections.emptyList()));
            questions.forEach(vo -> vo.setKnowledgeNames(Collections.emptyList()));
            return;
        }

        List<Long> knowledgeIds = relations.stream()
                .map(QuestionKnowledgeRel::getKnowledgeId)
                .distinct()
                .toList();
        Map<Long, String> nameMap = knowledgeMapper.selectBatchIds(knowledgeIds).stream()
                .collect(Collectors.toMap(QuestionKnowledge::getId, QuestionKnowledge::getKnowledgeName));

        Map<Long, List<Long>> questionKnowledgeMap = relations.stream()
                .collect(Collectors.groupingBy(QuestionKnowledgeRel::getQuestionId,
                        Collectors.mapping(QuestionKnowledgeRel::getKnowledgeId, Collectors.toList())));

        questions.forEach(vo -> {
            List<Long> ids = questionKnowledgeMap.getOrDefault(vo.getId(), Collections.emptyList());
            vo.setKnowledgeIds(ids);
            vo.setKnowledgeNames(ids.stream()
                    .map(nameMap::get)
                    .filter(Objects::nonNull)
                    .toList());
        });
    }

    private QuestionVO toQuestionVO(ExamQuestion question) {
        QuestionVO vo = new QuestionVO();
        vo.setId(question.getId());
        vo.setPaperId(question.getPaperId());
        vo.setQuestionNo(question.getQuestionNo());
        vo.setSectionName(question.getSectionName());
        vo.setQuestionType(question.getQuestionType());
        vo.setQuestionContent(question.getQuestionContent());
        vo.setStandardAnswer(question.getStandardAnswer());
        vo.setScoringRule(question.getScoringRule());
        vo.setScore(question.getScore());
        vo.setDifficulty(question.getDifficulty());
        vo.setPromptTemplateId(question.getPromptTemplateId());
        vo.setSortOrder(question.getSortOrder());
        vo.setCreateTime(question.getCreateTime());
        return vo;
    }
}
