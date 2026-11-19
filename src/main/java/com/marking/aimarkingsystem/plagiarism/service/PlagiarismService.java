package com.marking.aimarkingsystem.plagiarism.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.marking.aimarkingsystem.answer.entity.ExamAnswer;
import com.marking.aimarkingsystem.answer.enums.AnswerSourceEnum;
import com.marking.aimarkingsystem.answer.mapper.ExamAnswerMapper;
import com.marking.aimarkingsystem.common.constant.RedisKeyConstant;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.util.RedisUtil;
import com.marking.aimarkingsystem.common.util.SimHashUtil;
import com.marking.aimarkingsystem.paper.entity.ExamPaper;
import com.marking.aimarkingsystem.paper.mapper.ExamPaperMapper;
import com.marking.aimarkingsystem.plagiarism.enums.PlagiarismRiskEnum;
import com.marking.aimarkingsystem.plagiarism.vo.FingerprintGenerateVO;
import com.marking.aimarkingsystem.plagiarism.vo.PlagiarismAnswerReportVO;
import com.marking.aimarkingsystem.plagiarism.vo.PlagiarismPairVO;
import com.marking.aimarkingsystem.plagiarism.vo.PlagiarismReportVO;
import com.marking.aimarkingsystem.plagiarism.vo.SimilarAnswerVO;
import com.marking.aimarkingsystem.plagiarism.vo.SuspectAnswerVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 抄袭检测（SimHash）服务。
 *
 * 职责：
 * 1. 为答卷生成 SimHash 指纹并落库（线上作答取 answerContent，纸质扫描取 ocrText）；
 * 2. 同一试卷内全部已生成指纹的答卷两两比对，按相似度标记疑似抄袭；
 * 3. 生成试卷级比对报告（缓存到 Redis，可强制刷新）与单答卷查重报告。
 *
 * 设计要点：
 * - 只在同一试卷内比对：不同试卷题目不同，字符串相似不代表抄袭；
 * - 比对是 O(n²) 的指纹异或运算，100 人规模仅数千次比较，无需额外索引或分桶；
 * - 指纹为 NULL 的答卷视为"待生成"，批量生成接口只补算这部分，天然支持增量；
 * - 不新建数据表：答卷对结果按需计算，报告整体缓存在 Redis（有 TTL，可刷新）。
 *
 * 数据权限（占位）：教师只能查看/生成自己所授课程下试卷的查重报告。
 * 实现时应由 paper.courseId 找到 course.teacherId，与当前登录用户比对，不通过返回 403。
 */
@Service
public class PlagiarismService {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismService.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ExamAnswerMapper examAnswerMapper;
    private final ExamPaperMapper examPaperMapper;
    private final RedisUtil redisUtil;

    /** 生成指纹所需的最小有效字符数，过短的文本指纹无区分度，直接跳过 */
    @Value("${plagiarism.simhash.min-text-length:30}")
    private int minTextLength;

    /** 高度疑似抄袭阈值 */
    @Value("${plagiarism.simhash.high-threshold:0.90}")
    private double highThreshold;

    /** 可疑阈值，低于该值不进入报告 */
    @Value("${plagiarism.simhash.suspicious-threshold:0.80}")
    private double suspiciousThreshold;

    /** 报告缓存时长（小时） */
    @Value("${plagiarism.simhash.report-cache-hours:2}")
    private long reportCacheHours;

    /** 报告中最多返回的答卷对数量，防止极端情况下响应体过大 */
    @Value("${plagiarism.simhash.max-pairs:200}")
    private int maxPairs;

    public PlagiarismService(ExamAnswerMapper examAnswerMapper,
                             ExamPaperMapper examPaperMapper,
                             RedisUtil redisUtil) {
        this.examAnswerMapper = examAnswerMapper;
        this.examPaperMapper = examPaperMapper;
        this.redisUtil = redisUtil;
    }

    // ==================== 指纹生成 ====================

    /**
     * 为单份答卷生成指纹并落库。
     *
     * @return 生成的指纹；文本为空或过短时返回 null（不写库）
     */
    public String generateFingerprint(Long answerId) {
        ExamAnswer answer = requireAnswer(answerId);
        return doGenerate(answer);
    }

    /**
     * 尽力而为地生成指纹：异常只打日志，不影响调用方主流程。
     *
     * 供答卷提交、OCR 完成等业务链路调用——查重是辅助能力，
     * 失败不应导致提交失败或 OCR 任务重投。
     */
    public void generateFingerprintQuietly(Long answerId) {
        if (answerId == null) {
            return;
        }
        try {
            ExamAnswer answer = examAnswerMapper.selectById(answerId);
            if (answer == null) {
                return;
            }
            doGenerate(answer);
        } catch (Exception e) {
            log.warn("生成SimHash指纹失败（不影响主流程）: answerId={}, error={}", answerId, e.getMessage());
        }
    }

    /**
     * 为试卷下所有缺失指纹的答卷批量生成指纹（已有指纹的不重复计算）。
     */
    public FingerprintGenerateVO generateFingerprintsByPaper(Long paperId) {
        ExamPaper paper = requirePaper(paperId);
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户
        List<ExamAnswer> answers = loadPaperAnswers(paperId);
        return fillFingerprints(paper, answers);
    }

    // ==================== 报告 ====================

    /**
     * 生成（或刷新）试卷级抄袭比对报告，并写入 Redis 缓存。
     */
    public PlagiarismReportVO generateReport(Long paperId) {
        ExamPaper paper = requirePaper(paperId);
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户
        List<ExamAnswer> answers = loadPaperAnswers(paperId);
        FingerprintGenerateVO fingerprintStats = fillFingerprints(paper, answers);
        PlagiarismReportVO report = buildReport(paper, answers, fingerprintStats);
        cacheReport(report);
        return report;
    }

    /**
     * 查询报告：优先读缓存，缓存不存在则实时生成一份。
     */
    public PlagiarismReportVO getReport(Long paperId) {
        requirePaper(paperId);
        // 数据权限（占位）：校验 paper.courseId 所属课程的任课教师是否为当前用户
        String cacheKey = RedisKeyConstant.PLAGIARISM_REPORT + paperId;
        String cached = redisUtil.get(cacheKey);
        if (StringUtils.hasText(cached)) {
            try {
                return JSONUtil.toBean(cached, PlagiarismReportVO.class);
            } catch (Exception e) {
                log.warn("查重报告缓存解析失败，转为实时生成: paperId={}", paperId, e);
            }
        }
        return generateReport(paperId);
    }

    /**
     * 单份答卷的查重报告：给出它的指纹，以及同试卷内与它相似的答卷列表。
     */
    public PlagiarismAnswerReportVO getAnswerReport(Long answerId) {
        ExamAnswer answer = requireAnswer(answerId);
        requirePaper(answer.getPaperId());
        // 数据权限（占位）：校验答卷所属试卷的课程归属当前教师

        if (!StringUtils.hasText(answer.getSimHash())) {
            String hash = doGenerate(answer);
            if (!StringUtils.hasText(hash)) {
                throw new BusinessException("该答卷暂无可比对文本（未作答、未OCR或文本过短），无法生成指纹");
            }
        }

        List<ExamAnswer> samePaperAnswers = loadPaperAnswers(answer.getPaperId());
        List<SimilarAnswerVO> similarAnswers = new ArrayList<>();
        int compared = 0;
        for (ExamAnswer other : samePaperAnswers) {
            if (other.getId().equals(answer.getId()) || !StringUtils.hasText(other.getSimHash())) {
                continue;
            }
            compared++;
            int distance = SimHashUtil.hammingDistance(answer.getSimHash(), other.getSimHash());
            double similarity = round4(1.0d - (double) distance / SimHashUtil.BITS);
            String risk = riskOf(similarity);
            if (risk == null) {
                continue;
            }
            SimilarAnswerVO item = new SimilarAnswerVO();
            item.setAnswerId(other.getId());
            item.setStudentNo(other.getStudentNo());
            item.setStudentName(other.getStudentName());
            item.setHammingDistance(distance);
            item.setSimilarity(similarity);
            item.setRiskLevel(risk);
            item.setRiskLevelDesc(PlagiarismRiskEnum.descOf(risk));
            similarAnswers.add(item);
        }
        similarAnswers.sort(Comparator.comparing(SimilarAnswerVO::getSimilarity).reversed());

        PlagiarismAnswerReportVO report = new PlagiarismAnswerReportVO();
        report.setAnswerId(answer.getId());
        report.setPaperId(answer.getPaperId());
        report.setStudentNo(answer.getStudentNo());
        report.setStudentName(answer.getStudentName());
        report.setSimHash(answer.getSimHash());
        report.setComparedCount(compared);
        report.setSimilarAnswers(similarAnswers);
        if (!similarAnswers.isEmpty()) {
            report.setMaxSimilarity(similarAnswers.get(0).getSimilarity());
            report.setRiskLevel(similarAnswers.get(0).getRiskLevel());
            report.setRiskLevelDesc(similarAnswers.get(0).getRiskLevelDesc());
        }
        return report;
    }

    // ==================== 内部实现 ====================

    /** 计算并写入指纹；文本不足时返回 null */
    private String doGenerate(ExamAnswer answer) {
        String text = extractText(answer);
        String normalized = SimHashUtil.normalize(text);
        if (normalized.length() < minTextLength) {
            log.info("答卷文本过短，跳过指纹生成: answerId={}, length={}", answer.getId(), normalized.length());
            return null;
        }
        String hash = SimHashUtil.simHash(text);
        if (hash == null) {
            return null;
        }
        ExamAnswer update = new ExamAnswer();
        update.setId(answer.getId());
        update.setSimHash(hash);
        examAnswerMapper.updateById(update);
        answer.setSimHash(hash);
        log.info("SimHash指纹已生成: answerId={}, simHash={}", answer.getId(), hash);
        return hash;
    }

    /** 批量补算指纹，返回统计结果 */
    private FingerprintGenerateVO fillFingerprints(ExamPaper paper, List<ExamAnswer> answers) {
        List<FingerprintGenerateVO.SkippedItem> skippedItems = new ArrayList<>();
        int generated = 0;
        int existing = 0;
        for (ExamAnswer answer : answers) {
            if (StringUtils.hasText(answer.getSimHash())) {
                existing++;
                continue;
            }
            String text = extractText(answer);
            int length = SimHashUtil.normalize(text).length();
            if (length < minTextLength) {
                skippedItems.add(new FingerprintGenerateVO.SkippedItem(
                        answer.getId(), answer.getStudentNo(),
                        "作答文本为空或过短（当前 " + length + " 字，最少 " + minTextLength + " 字）"));
                continue;
            }
            if (doGenerate(answer) != null) {
                generated++;
            } else {
                skippedItems.add(new FingerprintGenerateVO.SkippedItem(
                        answer.getId(), answer.getStudentNo(), "指纹计算失败"));
            }
        }

        FingerprintGenerateVO vo = new FingerprintGenerateVO();
        vo.setPaperId(paper.getId());
        vo.setTotalCount(answers.size());
        vo.setGeneratedCount(generated);
        vo.setExistingCount(existing);
        vo.setSkippedCount(skippedItems.size());
        vo.setSkippedItems(skippedItems);
        return vo;
    }

    /** 同一试卷内两两比对，生成报告 */
    private PlagiarismReportVO buildReport(ExamPaper paper,
                                           List<ExamAnswer> answers,
                                           FingerprintGenerateVO fingerprintStats) {
        List<ExamAnswer> valid = answers.stream()
                .filter(answer -> StringUtils.hasText(answer.getSimHash()))
                .toList();

        List<PlagiarismPairVO> pairs = new ArrayList<>();
        Map<Long, SuspectAnswerVO> suspects = new HashMap<>();
        int comparedPairs = 0;

        for (int i = 0; i < valid.size(); i++) {
            for (int j = i + 1; j < valid.size(); j++) {
                ExamAnswer a = valid.get(i);
                ExamAnswer b = valid.get(j);
                comparedPairs++;

                int distance = SimHashUtil.hammingDistance(a.getSimHash(), b.getSimHash());
                double similarity = round4(1.0d - (double) distance / SimHashUtil.BITS);
                String risk = riskOf(similarity);
                if (risk == null) {
                    continue;
                }
                pairs.add(toPair(a, b, distance, similarity, risk));
                markSuspect(suspects, a, similarity, risk);
                markSuspect(suspects, b, similarity, risk);
            }
        }

        pairs.sort(Comparator.comparing(PlagiarismPairVO::getSimilarity).reversed());
        long highRiskPairs = pairs.stream()
                .filter(pair -> PlagiarismRiskEnum.HIGH.name().equals(pair.getRiskLevel()))
                .count();

        List<PlagiarismPairVO> limitedPairs = pairs.size() > maxPairs
                ? new ArrayList<>(pairs.subList(0, maxPairs))
                : pairs;

        List<SuspectAnswerVO> suspectList = new ArrayList<>(suspects.values());
        suspectList.sort(Comparator.comparing(SuspectAnswerVO::getMaxSimilarity).reversed());

        PlagiarismReportVO report = new PlagiarismReportVO();
        report.setPaperId(paper.getId());
        report.setPaperTitle(paper.getPaperTitle());
        report.setTotalAnswerCount(answers.size());
        report.setFingerprintedCount(valid.size());
        report.setSkippedCount(fingerprintStats.getSkippedCount());
        report.setComparedPairCount(comparedPairs);
        report.setHighRiskPairCount((int) highRiskPairs);
        report.setSuspiciousPairCount(pairs.size() - (int) highRiskPairs);
        report.setGeneratedTime(LocalDateTime.now().format(TIME_FORMATTER));
        report.setPairs(limitedPairs);
        report.setSuspects(suspectList);
        return report;
    }

    private PlagiarismPairVO toPair(ExamAnswer a, ExamAnswer b, int distance, double similarity, String risk) {
        PlagiarismPairVO pair = new PlagiarismPairVO();
        pair.setAnswerIdA(a.getId());
        pair.setStudentNoA(a.getStudentNo());
        pair.setStudentNameA(a.getStudentName());
        pair.setAnswerIdB(b.getId());
        pair.setStudentNoB(b.getStudentNo());
        pair.setStudentNameB(b.getStudentName());
        pair.setHammingDistance(distance);
        pair.setSimilarity(similarity);
        pair.setRiskLevel(risk);
        pair.setRiskLevelDesc(PlagiarismRiskEnum.descOf(risk));
        return pair;
    }

    /** 合并同一答卷的多个相似对象，保留最高相似度与相似次数 */
    private void markSuspect(Map<Long, SuspectAnswerVO> suspects, ExamAnswer answer, double similarity, String risk) {
        SuspectAnswerVO item = suspects.get(answer.getId());
        if (item == null) {
            item = new SuspectAnswerVO();
            item.setAnswerId(answer.getId());
            item.setStudentNo(answer.getStudentNo());
            item.setStudentName(answer.getStudentName());
            item.setSimHash(answer.getSimHash());
            item.setMaxSimilarity(similarity);
            item.setSimilarCount(1);
            item.setRiskLevel(risk);
            item.setRiskLevelDesc(PlagiarismRiskEnum.descOf(risk));
            suspects.put(answer.getId(), item);
            return;
        }
        item.setSimilarCount(item.getSimilarCount() + 1);
        if (similarity > item.getMaxSimilarity()) {
            item.setMaxSimilarity(similarity);
        }
        if (PlagiarismRiskEnum.HIGH.name().equals(risk)) {
            item.setRiskLevel(risk);
            item.setRiskLevelDesc(PlagiarismRiskEnum.descOf(risk));
        }
    }

    private String riskOf(double similarity) {
        if (similarity >= highThreshold) {
            return PlagiarismRiskEnum.HIGH.name();
        }
        if (similarity >= suspiciousThreshold) {
            return PlagiarismRiskEnum.SUSPICIOUS.name();
        }
        return null;
    }

    private double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    private void cacheReport(PlagiarismReportVO report) {
        try {
            redisUtil.set(RedisKeyConstant.PLAGIARISM_REPORT + report.getPaperId(),
                    JSONUtil.toJsonStr(report), reportCacheHours, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("查重报告缓存失败（不影响本次返回）: paperId={}", report.getPaperId(), e);
        }
    }

    private List<ExamAnswer> loadPaperAnswers(Long paperId) {
        return examAnswerMapper.selectList(Wrappers.<ExamAnswer>lambdaQuery()
                .eq(ExamAnswer::getPaperId, paperId)
                .orderByAsc(ExamAnswer::getId));
    }

    private ExamPaper requirePaper(Long paperId) {
        if (paperId == null) {
            throw new BusinessException("试卷ID不能为空");
        }
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException("试卷不存在: " + paperId);
        }
        return paper;
    }

    private ExamAnswer requireAnswer(Long answerId) {
        ExamAnswer answer = examAnswerMapper.selectById(answerId);
        if (answer == null) {
            throw new BusinessException("答卷不存在: " + answerId);
        }
        return answer;
    }

    /**
     * 提取用于生成指纹的纯文本。
     *
     * 线上答卷取 answerContent（学生作答），纸质扫描取 ocrText（OCR 结果）。
     * 注意：AI 阅卷完成后 answerContent 会被逐题得分明细覆盖，
     * 因此线上答卷的指纹应在提交时立即生成（AnswerService 已接入）。
     */
    private String extractText(ExamAnswer answer) {
        if (AnswerSourceEnum.isPaperScan(answer.getAnswerSource())) {
            return answer.getOcrText();
        }
        return extractOnlineText(answer.getAnswerContent());
    }

    /**
     * 从线上作答 JSON 中提取文本。
     *
     * 兼容两种形态：
     * - 对象：{"1":"第一题答案","2":"第二题答案"}
     * - 数组：[{"questionId":1,"answer":"..."}]
     * 若内容不是 JSON，则原样作为文本参与计算。
     */
    private String extractOnlineText(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        try {
            if (JSONUtil.isTypeJSONObject(content)) {
                JSONObject object = JSONUtil.parseObj(content);
                StringBuilder sb = new StringBuilder();
                for (String key : object.keySet()) {
                    Object value = object.get(key);
                    if (value != null) {
                        sb.append(value).append('\n');
                    }
                }
                return sb.toString();
            }
            if (JSONUtil.isTypeJSONArray(content)) {
                JSONArray array = JSONUtil.parseArray(content);
                StringBuilder sb = new StringBuilder();
                for (Object element : array) {
                    if (element instanceof JSONObject item) {
                        Object value = item.get("answer");
                        if (value == null) {
                            value = item.get("content");
                        }
                        if (value != null) {
                            sb.append(value).append('\n');
                        }
                    } else if (element != null) {
                        sb.append(element).append('\n');
                    }
                }
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("线上作答内容解析失败，按原始文本处理: error={}", e.getMessage());
        }
        return content;
    }
}
