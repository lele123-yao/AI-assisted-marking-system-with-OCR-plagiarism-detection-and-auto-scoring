package com.marking.aimarkingsystem.prompt.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.ResultCode;
import com.marking.aimarkingsystem.course.entity.Course;
import com.marking.aimarkingsystem.course.mapper.CourseMapper;
import com.marking.aimarkingsystem.paper.entity.ExamQuestion;
import com.marking.aimarkingsystem.paper.mapper.ExamQuestionMapper;
import com.marking.aimarkingsystem.prompt.dto.PromptTemplateSaveDTO;
import com.marking.aimarkingsystem.prompt.entity.PromptTemplate;
import com.marking.aimarkingsystem.prompt.mapper.PromptTemplateMapper;
import com.marking.aimarkingsystem.prompt.vo.PromptTemplateVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评分提示词模板服务：按题型维护 AI 评分 Prompt。
 *
 * 模板适用范围：
 * - courseId 为空表示全校通用；
 * - questionType = 0 表示通用题型，否则对应具体题型。
 * 匹配优先级（由后续 AI 阅卷模块使用）：
 * 题目指定 promptTemplateId > 课程+题型 > 通用+题型 > 代码内置默认。
 *
 * 数据权限说明（占位，暂不实现）：
 * 教师只能维护自己课程的专属模板（courseId 属于自己），全校通用模板（courseId 为空）
 * 应仅管理员可维护。本阶段只保留注释，由 Controller 层的 @RequiresPermission 先做角色拦截。
 */
@Service
public class PromptTemplateService {

    /** 启用状态 */
    private static final int STATUS_ENABLED = 1;

    /** 模板初始版本号 */
    private static final int INIT_VERSION = 1;

    /** 默认采样温度：评分场景取低温度，保证多次调用结果稳定 */
    private static final BigDecimal DEFAULT_TEMPERATURE = new BigDecimal("0.20");

    private final PromptTemplateMapper templateMapper;

    private final ExamQuestionMapper examQuestionMapper;

    private final CourseMapper courseMapper;

    public PromptTemplateService(PromptTemplateMapper templateMapper,
                                 ExamQuestionMapper examQuestionMapper,
                                 CourseMapper courseMapper) {
        this.templateMapper = templateMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.courseMapper = courseMapper;
    }

    /**
     * 新增模板。
     * 版本号固定从 1 开始，由服务端维护，DTO 不接收。
     *
     * 数据权限占位：courseId 不为空时，后续需校验该课程归属当前教师。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(PromptTemplateSaveDTO dto) {
        assertTemplateCodeUnique(dto.getTemplateCode(), null);
        requireCourseIfPresent(dto.getCourseId());

        PromptTemplate template = new PromptTemplate();
        applyDto(template, dto);
        template.setTemperature(dto.getTemperature() == null
                ? DEFAULT_TEMPERATURE : dto.getTemperature());
        template.setVersion(INIT_VERSION);
        if (template.getStatus() == null) {
            template.setStatus(STATUS_ENABLED);
        }

        templateMapper.insert(template);
        return template.getId();
    }

    /**
     * 编辑模板。
     *
     * 版本号递增：提示词是 AI 评分口径的核心，任何修改都应留下版本痕迹，
     * 便于回溯"某次阅卷当时用的是哪一版模板"。若需要保留旧版本记录，
     * 后续可改为"复制新记录 + 旧记录停用"，本阶段采用同记录递增的轻量方案。
     *
     * 数据权限占位：同新增，需校验模板课程归属当前教师。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PromptTemplateSaveDTO dto) {
        PromptTemplate exists = requireTemplate(id);
        assertTemplateCodeUnique(dto.getTemplateCode(), id);
        requireCourseIfPresent(dto.getCourseId());

        PromptTemplate update = new PromptTemplate();
        update.setId(id);
        applyDto(update, dto);
        if (update.getTemperature() == null) {
            update.setTemperature(exists.getTemperature());
        }
        if (update.getStatus() == null) {
            update.setStatus(exists.getStatus());
        }
        update.setVersion(exists.getVersion() == null ? INIT_VERSION : exists.getVersion() + 1);
        templateMapper.updateById(update);
    }

    /**
     * 删除模板。
     *
     * 已被题目引用的模板不允许直接删除，否则题目会指向不存在的模板，
     * AI 阅卷时只能退回默认提示词，评分口径不可控。应先改用其他模板或停用本模板。
     *
     * 数据权限占位：需校验模板归属当前教师。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireTemplate(id);

        Long referenced = examQuestionMapper.selectCount(
                Wrappers.<ExamQuestion>lambdaQuery()
                        .eq(ExamQuestion::getPromptTemplateId, id));
        if (referenced != null && referenced > 0) {
            throw new BusinessException("该模板已被题目引用，请先解除引用或改为停用");
        }

        templateMapper.deleteById(id);
    }

    /** 模板详情 */
    public PromptTemplateVO get(Long id) {
        PromptTemplateVO vo = toVO(requireTemplate(id));
        fillCourseNames(List.of(vo));
        return vo;
    }

    /**
     * 模板列表。
     *
     * @param questionType 题型过滤，可为空；传 0 表示只查通用模板
     * @param courseId     课程过滤，可为空；为空时不限制课程范围
     * @param status       状态过滤，可为空
     *
     * 数据权限占位：教师查询时应额外拼接 "course_id is null or course_id in (自己课程)"。
     */
    public List<PromptTemplateVO> list(Integer questionType, Long courseId, Integer status) {
        List<PromptTemplateVO> records = templateMapper.selectList(
                        Wrappers.<PromptTemplate>lambdaQuery()
                                .eq(questionType != null, PromptTemplate::getQuestionType, questionType)
                                .eq(courseId != null, PromptTemplate::getCourseId, courseId)
                                .eq(status != null, PromptTemplate::getStatus, status)
                                .orderByAsc(PromptTemplate::getQuestionType)
                                .orderByAsc(PromptTemplate::getId))
                .stream()
                .map(this::toVO)
                .toList();

        fillCourseNames(records);
        return records;
    }

    /**
     * 分页查询模板。
     *
     * 数据权限占位：教师查询时应额外拼接 "course_id is null or course_id in (自己课程)"，
     * 保证能看到全校通用模板但不能看到其他教师的专属模板。
     */
    public PageResult<PromptTemplateVO> pageTemplates(Integer pageNum, Integer pageSize,
                                                      Integer questionType, Long courseId, Integer status) {
        Page<PromptTemplate> page = new Page<>(pageNum, pageSize);
        Page<PromptTemplate> templatePage = templateMapper.selectPage(page,
                Wrappers.<PromptTemplate>lambdaQuery()
                        .eq(questionType != null, PromptTemplate::getQuestionType, questionType)
                        .eq(courseId != null, PromptTemplate::getCourseId, courseId)
                        .eq(status != null, PromptTemplate::getStatus, status)
                        .orderByAsc(PromptTemplate::getQuestionType)
                        .orderByDesc(PromptTemplate::getId));

        List<PromptTemplateVO> records = templatePage.getRecords().stream()
                .map(this::toVO)
                .toList();
        fillCourseNames(records);
        return PageResult.of(templatePage, records);
    }

    // ==================== 内部方法 ====================

    private void assertTemplateCodeUnique(String templateCode, Long excludeId) {
        Long count = templateMapper.selectCount(
                Wrappers.<PromptTemplate>lambdaQuery()
                        .eq(PromptTemplate::getTemplateCode, templateCode)
                        .ne(excludeId != null, PromptTemplate::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("模板编码已存在");
        }
    }

    private void requireCourseIfPresent(Long courseId) {
        if (courseId == null) {
            return;
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "限定课程不存在");
        }
    }

    private PromptTemplate requireTemplate(Long id) {
        PromptTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "提示词模板不存在");
        }
        return template;
    }

    /** 批量填充课程名称，null 表示全校通用 */
    private void fillCourseNames(List<PromptTemplateVO> templates) {
        List<Long> courseIds = templates.stream()
                .map(PromptTemplateVO::getCourseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return;
        }

        Map<Long, String> nameMap = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getCourseName));
        templates.forEach(vo -> {
            if (vo.getCourseId() != null) {
                vo.setCourseName(nameMap.get(vo.getCourseId()));
            }
        });
    }

    private void applyDto(PromptTemplate template, PromptTemplateSaveDTO dto) {
        template.setTemplateCode(dto.getTemplateCode());
        template.setTemplateName(dto.getTemplateName());
        template.setQuestionType(dto.getQuestionType());
        template.setCourseId(dto.getCourseId());
        template.setSystemPrompt(dto.getSystemPrompt());
        template.setUserPrompt(dto.getUserPrompt());
        template.setModelName(dto.getModelName());
        template.setTemperature(dto.getTemperature());
        template.setStatus(dto.getStatus());
        template.setRemark(dto.getRemark());
    }

    private PromptTemplateVO toVO(PromptTemplate template) {
        PromptTemplateVO vo = new PromptTemplateVO();
        vo.setId(template.getId());
        vo.setTemplateCode(template.getTemplateCode());
        vo.setTemplateName(template.getTemplateName());
        vo.setQuestionType(template.getQuestionType());
        vo.setCourseId(template.getCourseId());
        vo.setSystemPrompt(template.getSystemPrompt());
        vo.setUserPrompt(template.getUserPrompt());
        vo.setModelName(template.getModelName());
        vo.setTemperature(template.getTemperature());
        vo.setVersion(template.getVersion());
        vo.setStatus(template.getStatus());
        vo.setRemark(template.getRemark());
        vo.setCreateTime(template.getCreateTime());
        vo.setUpdateTime(template.getUpdateTime());
        return vo;
    }
}
