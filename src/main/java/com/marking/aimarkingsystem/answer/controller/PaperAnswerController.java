package com.marking.aimarkingsystem.answer.controller;

import com.marking.aimarkingsystem.answer.dto.PaperAnswerQueryDTO;
import com.marking.aimarkingsystem.answer.dto.PaperImageUploadDTO;
import com.marking.aimarkingsystem.answer.dto.RosterImportDTO;
import com.marking.aimarkingsystem.answer.dto.TeacherScoreDTO;
import com.marking.aimarkingsystem.answer.service.PaperAnswerService;
import com.marking.aimarkingsystem.answer.vo.PaperAnswerDetailVO;
import com.marking.aimarkingsystem.answer.vo.PaperAnswerListVO;
import com.marking.aimarkingsystem.answer.vo.RosterImportResultVO;
import com.marking.aimarkingsystem.common.annotation.OperationLog;
import com.marking.aimarkingsystem.common.annotation.RateLimit;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 纸质试卷线下批改辅助接口（教师端）。
 *
 * 接口范围：
 * 1. Excel 导入考生名单，批量生成空白纸质答卷；
 * 2. 纸质答卷分页查询；
 * 3. 教师手动录入逐题得分并确认最终成绩（可直接采纳 AI 参考分）；
 * 4. 上传答卷扫描图片到 MinIO；
 * 5. 触发 OCR 识别任务（本阶段只登记任务与状态，MQ 投递由模块4实现）；
 * 6. 查看纸质答卷详情（OCR 文本、AI 参考分、教师最终分、原图地址）。
 *
 * 权限：教师与管理员可访问；学生查询自己的答卷走学生端接口（后续模块）。
 *
 * 数据权限（占位，暂不实现）：
 * 任课教师只能操作自己课程下的纸质答卷。实现时应由 paper.courseId 找到 course.teacherId，
 * 与当前登录用户比对，不通过返回 403；建议统一放在 Service 的 requireTeacherOwnsPaper 中，
 * 避免每个 Controller 方法重复判断，也避免遗漏某个入口造成越权。
 */
@Tag(name = "纸质答卷批改", description = "名单导入、纸质答卷查询、录分、图片上传、OCR触发、详情")
@RestController
@RequestMapping("/paper-answers")
@Validated
public class PaperAnswerController {

    private final PaperAnswerService paperAnswerService;

    public PaperAnswerController(PaperAnswerService paperAnswerService) {
        this.paperAnswerService = paperAnswerService;
    }

    @Operation(summary = "导入考生名单",
            description = "上传 xlsx/xls 名单（列：学号、姓名、班级），为每名考生生成一条 PAPER_SCAN 空白答卷；"
                    + "单行错误只记录失败明细，同一试卷下已存在的学号自动跳过")
    @OperationLog(module = "纸质答卷", action = "导入考生名单", recordParams = false, maxLength = 2000)
    @RateLimit(key = "paper:roster:import", limit = 5, windowSeconds = 300,
            message = "名单导入过于频繁，请稍后再试")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @PostMapping(value = "/roster/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<RosterImportResultVO> importRoster(
            @Valid @ModelAttribute RosterImportDTO dto,
            @Parameter(description = "考生名单 Excel") @RequestPart("file") MultipartFile file) {
        // 数据权限（占位）：需校验 dto.paperId 所属试卷的课程归属当前教师
        return Result.success("导入完成", paperAnswerService.importRoster(dto, file));
    }

    @Operation(summary = "分页查询纸质答卷",
            description = "只查询 PAPER_SCAN 来源的答卷，支持按试卷、状态、学号/姓名关键字筛选")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @GetMapping("/page")
    public Result<PageResult<PaperAnswerListVO>> pageAnswers(
            @Valid @ModelAttribute PaperAnswerQueryDTO query) {
        // 数据权限（占位）：查询条件需限定在当前教师所授课程范围内
        return Result.success(paperAnswerService.pageAnswers(query));
    }

    @Operation(summary = "教师手动录入分数",
            description = "逐题录入得分并提交最终分：每题不得超过该题满分，各题之和必须等于最终分；"
                    + "教师可以直接采纳 AI 参考分，最终成绩以教师提交为准")
    @OperationLog(module = "纸质答卷", action = "教师录入分数")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @PostMapping("/{answerId}/score")
    public Result<Void> saveTeacherScore(
            @Parameter(description = "答卷ID", example = "101")
            @PathVariable @Min(value = 1, message = "答卷ID不合法") Long answerId,
            @Valid @RequestBody TeacherScoreDTO dto) {
        // 数据权限（占位）：需校验答卷所属试卷的课程归属当前教师
        paperAnswerService.saveTeacherScore(answerId, dto);
        return Result.success("成绩录入成功", null);
    }

    @Operation(summary = "上传答卷扫描图片",
            description = "支持 jpg/jpeg/png/webp/pdf，单文件不超过 20MB；图片存入 MinIO 并绑定到答卷记录；"
                    + "替换图片会清空旧的 OCR 文本与 AI 参考分，需要重新触发识别")
    @OperationLog(module = "纸质答卷", action = "上传答卷扫描图片", recordParams = false)
    @RateLimit(key = "paper:image:upload", limit = 10, windowSeconds = 60,
            message = "图片上传过于频繁，请稍后再试")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<String> uploadPaperImage(
            @Valid @ModelAttribute PaperImageUploadDTO dto,
            @Parameter(description = "答卷扫描图片") @RequestPart("file") MultipartFile file) {
        // 数据权限（占位）：需校验 dto.paperId 所属试卷的课程归属当前教师
        return Result.success("上传成功", paperAnswerService.uploadPaperImage(dto, file));
    }

    @Operation(summary = "触发OCR识别任务",
            description = "校验答卷已有扫描图片后登记 OCR 任务并把状态推进到 1（OCR识别中）；"
                    + "重复触发会被状态与 Redis 锁拦截。MQ 投递由模块4实现")
    @OperationLog(module = "纸质答卷", action = "触发OCR识别")
    @RateLimit(key = "paper:ocr:trigger", limit = 5, windowSeconds = 60,
            message = "OCR触发过于频繁，请稍后再试")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @PostMapping("/{answerId}/ocr")
    public Result<String> triggerOcr(
            @Parameter(description = "答卷ID", example = "101")
            @PathVariable @Min(value = 1, message = "答卷ID不合法") Long answerId) {
        // 数据权限（占位）：需校验答卷所属试卷的课程归属当前教师
        return Result.success("OCR任务已创建", paperAnswerService.triggerOcr(answerId));
    }

    @Operation(summary = "查看纸质答卷详情",
            description = "返回 OCR 文本、AI 参考分、教师最终分、原图对象键与临时签名访问地址")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @GetMapping("/{answerId}")
    public Result<PaperAnswerDetailVO> getDetail(
            @Parameter(description = "答卷ID", example = "101")
            @PathVariable @Min(value = 1, message = "答卷ID不合法") Long answerId) {
        // 数据权限（占位）：需校验答卷所属试卷的课程归属当前教师
        return Result.success(paperAnswerService.getDetail(answerId));
    }
}
