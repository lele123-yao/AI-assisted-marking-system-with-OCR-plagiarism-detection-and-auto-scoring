package com.marking.aimarkingsystem.plagiarism.controller;

import com.marking.aimarkingsystem.common.annotation.OperationLog;
import com.marking.aimarkingsystem.common.annotation.RequiresPermission;
import com.marking.aimarkingsystem.common.result.Result;
import com.marking.aimarkingsystem.plagiarism.service.PlagiarismService;
import com.marking.aimarkingsystem.plagiarism.vo.FingerprintGenerateVO;
import com.marking.aimarkingsystem.plagiarism.vo.PlagiarismAnswerReportVO;
import com.marking.aimarkingsystem.plagiarism.vo.PlagiarismReportVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 抄袭检测（SimHash）接口。
 *
 * 接口范围：
 * 1. 为试卷下答卷批量生成 SimHash 指纹（服务启动/答卷新增后可手动补算）；
 * 2. 生成或刷新试卷级抄袭比对报告；
 * 3. 查询试卷级抄袭比对报告（优先读缓存）；
 * 4. 查询单份答卷的查重报告（指纹 + 相似答卷列表）。
 *
 * 权限：教师与管理员可访问；学生无权查看他人查重结果。
 *
 * 数据权限（占位，暂不实现）：
 * 任课教师只能查看自己所授课程下试卷的查重报告。
 * 实现时应由 paper.courseId 找到 course.teacherId 与当前登录用户比对，
 * 建议统一放在 PlagiarismService 的校验方法中，避免每个入口重复实现。
 */
@Tag(name = "抄袭检测", description = "SimHash指纹生成、试卷相似度比对、查重报告查询")
@RestController
@RequestMapping("/plagiarism")
@Validated
public class PlagiarismController {

    private final PlagiarismService plagiarismService;

    public PlagiarismController(PlagiarismService plagiarismService) {
        this.plagiarismService = plagiarismService;
    }

    @Operation(summary = "批量生成答卷指纹",
            description = "为试卷下所有缺失指纹的答卷生成 SimHash 指纹；已有指纹的不重复计算。"
                    + "文本为空或过短（默认少于30个有效字符）的答卷会被跳过并在结果中说明原因")
    @OperationLog(module = "抄袭检测", action = "批量生成答卷指纹")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @PostMapping("/papers/{paperId}/fingerprints")
    public Result<FingerprintGenerateVO> generateFingerprints(
            @Parameter(description = "试卷ID", example = "3")
            @PathVariable @Min(value = 1, message = "试卷ID不合法") Long paperId) {
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户
        return Result.success("指纹生成完成", plagiarismService.generateFingerprintsByPaper(paperId));
    }

    @Operation(summary = "生成或刷新试卷抄袭报告",
            description = "对试卷内全部已生成指纹的答卷两两比对，按相似度标记疑似抄袭并生成报告；"
                    + "结果会写入缓存，重复查询可直接读取")
    @OperationLog(module = "抄袭检测", action = "生成试卷抄袭报告")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @PostMapping("/papers/{paperId}/report")
    public Result<PlagiarismReportVO> generateReport(
            @Parameter(description = "试卷ID", example = "3")
            @PathVariable @Min(value = 1, message = "试卷ID不合法") Long paperId) {
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户
        return Result.success("报告生成完成", plagiarismService.generateReport(paperId));
    }

    @Operation(summary = "查询试卷抄袭报告",
            description = "优先返回缓存中的报告；缓存不存在时实时计算一份（约等于生成接口）")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @GetMapping("/papers/{paperId}/report")
    public Result<PlagiarismReportVO> getReport(
            @Parameter(description = "试卷ID", example = "3")
            @PathVariable @Min(value = 1, message = "试卷ID不合法") Long paperId) {
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户
        return Result.success(plagiarismService.getReport(paperId));
    }

    @Operation(summary = "查询单份答卷查重报告",
            description = "返回该答卷的 SimHash 指纹、最高相似度、风险等级，以及与它相似的答卷列表；"
                    + "若指纹尚未生成会先尝试补算，无有效文本时返回业务提示")
    @RequiresPermission({"ROLE_TEACHER", "ROLE_ADMIN"})
    @GetMapping("/answers/{answerId}")
    public Result<PlagiarismAnswerReportVO> getAnswerReport(
            @Parameter(description = "答卷ID", example = "101")
            @PathVariable @Min(value = 1, message = "答卷ID不合法") Long answerId) {
        // 数据权限（占位）：校验答卷所属试卷的课程归属当前教师
        return Result.success(plagiarismService.getAnswerReport(answerId));
    }
}
