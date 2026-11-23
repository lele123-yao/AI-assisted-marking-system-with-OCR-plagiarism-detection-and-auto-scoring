package com.marking.aimarkingsystem.paper.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.util.DeepSeekLlmUtil;
import com.marking.aimarkingsystem.common.util.MinioUtil;
import com.marking.aimarkingsystem.paper.dto.PaperParseResult;
import com.marking.aimarkingsystem.paper.dto.ParsedQuestionDTO;
import com.marking.aimarkingsystem.paper.util.OcrClient;
import com.marking.aimarkingsystem.paper.util.PdfTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 试卷上传解析服务。
 *
 * 流程：
 * 1. 校验文件（PDF ≤ 20MB、图片 jpg/jpeg/png、最多 9 张，均为选填）；
 * 2. 文件上传 MinIO，返回对象名（数据库只存对象名）；
 * 3. PDF 用 PDFBox 提取文本层；图片调用可配置 OCR 接口；
 * 4. 文本优先交给大模型结构化，失败/无 Key 时用规则拆分兜底；
 * 5. 返回题目草稿，教师在前端增删改后，以人工确认结果为准入库。
 *
 * 不做持久化：本接口只"识别 + 上传素材"，建卷仍走 POST /papers + POST /questions/batch。
 */
@Slf4j
@Service
public class PaperParseService {

    /** PDF 文本/图片 OCR 文本最大参与解析长度，防止同步请求超时 */
    private static final int MAX_TEXT_LENGTH = 20_000;

    /** 最多返回的题目数量 */
    private static final int MAX_QUESTIONS = 100;

    /** 最多图片数量 */
    private static final int MAX_IMAGES = 9;

    /** 单文件大小上限 20MB */
    private static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private static final String PDF_DIRECTORY = "paper/pdf";
    private static final String IMAGE_DIRECTORY = "paper/images";

    /** 识别不出分值时默认 10 分 */
    private static final BigDecimal DEFAULT_SCORE = new BigDecimal("10.00");

    /** 题号起始标记：第1题 / 1. / 1、 / 一、 */
    private static final Pattern QUESTION_START = Pattern.compile(
            "(?m)(?=^\\s*(?:第\\s*\\d+\\s*题|\\d+\\s*[.、．)）]|[一二三四五六七八九十]+\\s*[、.．]))");

    /** 去掉题号前缀，并捕获其中的数字 */
    private static final Pattern QUESTION_PREFIX = Pattern.compile(
            "^\\s*(?:第\\s*(\\d+)\\s*题|[一二三四五六七八九十]+\\s*[、.．]|(\\d+)\\s*[.、．)）])\\s*");

    /** 题目正文最短长度：过短的通常是页码、题号、残留符号 */
    private static final int MIN_QUESTION_LENGTH = 5;

    /** 单题分值标记：只认括号内的"（10分）"，避免把"满分150分"误当成单题分值 */
    private static final Pattern SCORE_PATTERN = Pattern.compile(
            "[（(][\\s\\p{Zs}]*(\\d+(?:\\.\\d+)?)[\\s\\p{Zs}]*分[\\s\\p{Zs}]*[)）]");

    /** "本题15分/本小题满分15分"这类文字标注 */
    private static final Pattern SCORE_KEYWORD_PATTERN = Pattern.compile(
            "(?:本题|本小题|满分)[\\s\\p{Zs}]*(\\d+(?:\\.\\d+)?)[\\s\\p{Zs}]*分");

    /** 大题标题里的"每小题5分"，作为该大题下未标注分值题目的默认分 */
    private static final Pattern SECTION_SCORE_PATTERN = Pattern.compile(
            "每小题[\\s\\p{Zs}]*(\\d+(?:\\.\\d+)?)[\\s\\p{Zs}]*分");

    /** 单独成行的分值标注："15分" / "（15分）" */
    private static final Pattern SCORE_LINE_PATTERN = Pattern.compile(
            "(?m)^[\\s\\p{Zs}]*[（(]?[\\s\\p{Zs}]*(\\d+(?:\\.\\d+)?)[\\s\\p{Zs}]*分[\\s\\p{Zs}]*[)）]?[\\s\\p{Zs}]*$");

    /** 整行只有分值标注：保存题干时去掉，分值单独存到 score 字段 */
    private static final Pattern SCORE_ONLY_LINE = Pattern.compile(
            "^[\\s\\p{Zs}]*[（(][\\s\\p{Zs}]*\\d+(?:\\.\\d+)?[\\s\\p{Zs}]*分[\\s\\p{Zs}]*[)）][\\s\\p{Zs}]*$");

    /** 整行都是横线/下划线等分隔符，常见于 PDF 排版分页与答题区域 */
    private static final Pattern SEPARATOR_LINE = Pattern.compile(
            "^[\\s\\p{Zs}]*[-－—_=＝*＊·•.．。…]{4,}[\\s\\p{Zs}]*$");

    /** 页码行："第 1 页 共 4 页" / "第1页（共4页）" */
    private static final Pattern PAGE_NUMBER_LINE = Pattern.compile(
            "^[\\s\\p{Zs}]*第[\\s\\p{Zs}]*\\d+[\\s\\p{Zs}]*页"
                    + "(?:[\\s\\p{Zs}]*[（(]?[\\s\\p{Zs}]*共[\\s\\p{Zs}]*\\d+[\\s\\p{Zs}]*页[)）]?)?"
                    + "[\\s\\p{Zs}]*$");

    /** 页脚：前缀带试卷名称的"数学试卷 第 2 页（共 4 页）" */
    private static final Pattern PAGE_FOOTER_LINE = Pattern.compile(
            "^.*第[\\s\\p{Zs}]*\\d+[\\s\\p{Zs}]*页[（(]?[\\s\\p{Zs}]*共[\\s\\p{Zs}]*\\d+[\\s\\p{Zs}]*页[)）]?[\\s\\p{Zs}]*$");

    /** 连续 4 个以上的横线/下划线：答题区域排版线，题干中直接去掉 */
    private static final Pattern LONG_DASH_RUN = Pattern.compile("[-－—_―]{4,}");

    /** 大题标题关键词 */
    private static final Pattern SECTION_KEYWORD = Pattern.compile(
            "^(单选题|多选题|双选题|选择题|填空题|判断题|简答题|论述题|计算题|案例分析题|解答题|阅读题|作文题|非选择题)");

    /** 大题标题前的"一、/二、" */
    private static final Pattern SECTION_PREFIX = Pattern.compile("^[一二三四五六七八九十]+\\s*[、.．]\\s*");

    /**
     * 大题标题：只匹配"一、简答题（本题共2小题，共25分）"、
     * "选择题：本题共8小题，每小题5分"这类纯标题，
     * 不会误伤"简答题：简述牛顿第一定律"这种真实题干。
     */
    private static final Pattern SECTION_HEADING = Pattern.compile(
            "^(?:[一二三四五六七八九十]+\\s*[、.．]\\s*)?"
                    + "(?:单选题|多选题|双选题|选择题|填空题|判断题|简答题|论述题|计算题|案例分析题|解答题|阅读题|作文题|非选择题)"
                    + "\\s*(?:[（(][^）)]*[)）])?"
                    + "\\s*(?:[：:]\\s*(?:本题共|每小题|共\\s*\\d+(?:\\.\\d+)?\\s*分|满分)[\\s\\S]*)?$");

    /**
     * 卷头、考试说明、答题卡说明等非题目内容。
     * 只匹配段落首行；"答题卡"这类可能出现在题干里的词不单独列入，
     * 优先保证不误删真实题目。
     */
    private static final List<Pattern> BOILERPLATE_PATTERNS = List.of(
            Pattern.compile("全卷共\\s*\\d+\\s*页"),
            Pattern.compile("满分\\s*\\d+(?:\\.\\d+)?\\s*分"),
            Pattern.compile("考试时间"),
            Pattern.compile("考生注意"),
            Pattern.compile("注意事项"),
            Pattern.compile("答题前"),
            Pattern.compile("答题时"),
            Pattern.compile("准考证"),
            Pattern.compile("条形码"),
            Pattern.compile("一律无效"),
            Pattern.compile("本试题卷"),
            Pattern.compile("本试卷"),
            Pattern.compile("考试结束后"),
            Pattern.compile("监考"),
            Pattern.compile("填涂"),
            Pattern.compile("评卷人"),
            Pattern.compile("第[ⅠⅡI]{1,3}卷"),
            Pattern.compile("本大题共\\s*\\d+\\s*小题"),
            Pattern.compile("每小题\\s*\\d+(?:\\.\\d+)?\\s*分"),
            Pattern.compile("^\\s*题号\\s*$")
    );

    private final MinioUtil minioUtil;
    private final PdfTextExtractor pdfTextExtractor;
    private final OcrClient ocrClient;
    private final DeepSeekLlmUtil llmUtil;

    public PaperParseService(MinioUtil minioUtil,
                             PdfTextExtractor pdfTextExtractor,
                             OcrClient ocrClient,
                             DeepSeekLlmUtil llmUtil) {
        this.minioUtil = minioUtil;
        this.pdfTextExtractor = pdfTextExtractor;
        this.ocrClient = ocrClient;
        this.llmUtil = llmUtil;
    }

    /**
     * 解析上传的 PDF / 图片。
     *
     * @param files 上传文件（PDF 与图片可混合，也可都为空）
     * @return 解析结果（素材对象名 + 提取文本 + 题目草稿 + 提示）
     */
    public PaperParseResult parse(List<MultipartFile> files) {
        List<MultipartFile> validFiles = files == null ? Collections.emptyList()
                : files.stream().filter(file -> file != null && !file.isEmpty()).toList();
        if (validFiles.isEmpty()) {
            throw new BusinessException("请至少选择一个 PDF 或图片文件");
        }

        List<MultipartFile> pdfFiles = new ArrayList<>();
        List<MultipartFile> imageFiles = new ArrayList<>();
        for (MultipartFile file : validFiles) {
            String extension = extensionOf(file.getOriginalFilename());
            if ("pdf".equals(extension)) {
                pdfFiles.add(file);
            } else if ("jpg".equals(extension) || "jpeg".equals(extension) || "png".equals(extension)) {
                imageFiles.add(file);
            } else {
                throw new BusinessException("仅支持 PDF / JPG / JPEG / PNG 文件：" + file.getOriginalFilename());
            }
        }
        if (pdfFiles.size() > 1) {
            throw new BusinessException("一次最多上传 1 个 PDF");
        }
        if (imageFiles.size() > MAX_IMAGES) {
            throw new BusinessException("一次最多上传 " + MAX_IMAGES + " 张图片");
        }

        PaperParseResult result = new PaperParseResult();
        List<String> warnings = new ArrayList<>();
        StringBuilder text = new StringBuilder();

        if (!pdfFiles.isEmpty()) {
            MultipartFile pdf = pdfFiles.get(0);
            assertSize(pdf);
            String pdfUrl = minioUtil.upload(pdf, PDF_DIRECTORY);
            result.setPdfUrl(pdfUrl);

            String pdfText = pdfTextExtractor.extract(pdf);
            if (StringUtils.hasText(pdfText)) {
                text.append(pdfText).append('\n');
            } else {
                warnings.add("PDF 未提取到文本层（可能是扫描件），请改用拍照图片 OCR 或手动录入题目");
            }
        }

        if (!imageFiles.isEmpty()) {
            List<String> imageUrls = new ArrayList<>();
            for (MultipartFile image : imageFiles) {
                assertSize(image);
                imageUrls.add(minioUtil.upload(image, IMAGE_DIRECTORY));
            }
            result.setImageUrls(imageUrls);

            if (ocrClient.isConfigured()) {
                for (MultipartFile image : imageFiles) {
                    try {
                        String ocrText = ocrClient.recognize(image);
                        if (StringUtils.hasText(ocrText)) {
                            text.append(ocrText).append('\n');
                        }
                    } catch (Exception e) {
                        warnings.add("图片 " + image.getOriginalFilename() + " 识别失败：" + e.getMessage());
                    }
                }
            } else {
                warnings.add("未配置 OCR 接口，图片已上传但未识别文字，请手动录入题目");
            }
        }

        String extracted = truncate(cleanText(text.toString()), MAX_TEXT_LENGTH);
        result.setExtractedText(extracted);
        result.setWarnings(warnings);

        if (!StringUtils.hasText(extracted)) {
            result.setSource("MANUAL");
            result.setQuestions(Collections.emptyList());
            if (warnings.isEmpty()) {
                warnings.add("未识别到可用文本，请手动录入题目");
            }
            return result;
        }

        List<ParsedQuestionDTO> questions;
        try {
            questions = askLlm(extracted);
            result.setSource("LLM");
        } catch (Exception e) {
            log.warn("大模型结构化题目失败，改用规则拆分: {}", e.getMessage());
            warnings.add("大模型解析未成功（" + e.getMessage() + "），已改用规则自动拆分，请人工核对题目");
            questions = ruleSplit(extracted);
            result.setSource("RULE");
        }

        if (questions.size() > MAX_QUESTIONS) {
            warnings.add("识别到超过 " + MAX_QUESTIONS + " 道题，已截断，请人工补充");
            questions = questions.subList(0, MAX_QUESTIONS);
        }
        if (questions.isEmpty()) {
            warnings.add("未识别到真正的题目（卷头、考生注意事项、大题标题已自动忽略），请手动录入");
        }
        result.setQuestions(questions);
        return result;
    }

    // ==================== 大模型结构化 ====================

    private List<ParsedQuestionDTO> askLlm(String text) {
        String systemPrompt = "你是一名试卷结构化助手。请从用户提供的试卷文本中提取真正需要学生作答的题目，"
                + "必须且只能返回一个 JSON 数组，每个元素格式为："
                + "{\"questionNo\":\"题号\",\"sectionName\":\"大题名称\","
                + "\"questionType\":5,\"questionContent\":\"题干\","
                + "\"standardAnswer\":\"参考答案\",\"scoringRule\":\"评分细则\",\"score\":10}。"
                + "questionType 取值 1-8（1 单选、2 多选、3 判断、4 填空、5 简答、6 论述、7 计算、8 案例）；"
                + "无法确定的字段留空字符串；score 为数字。"
                + "只提取题目本身：忽略卷头（考试名称、页码、总分、考试时间等）、"
                + "考生注意事项（如“答题前请填写姓名/准考证号”“请按答题卡要求作答”）、"
                + "答题卡说明、分值说明和题型说明；"
                + "“一、选择题”“二、简答题（本题共2小题，共25分）”这类大题标题不要单独作为题目返回，"
                + "只把大题名称填入该大题下各题的 sectionName；"
                + "题号按原文顺序填写，没有题号时留空；文本中没有可识别的题目时返回 []。"
                + "PDF 提取可能导致公式错位或数字粘连（如把 x²/a² + y²/b² = 1 提成“x2 y2”），"
                + "请结合上下文还原成可读的数学表达式，不要编造或改变题目原意；"
                + "题干中单独出现的“（15分）”这类分值不要写进题干，填入 score 字段。"
                + "不要返回 JSON 以外的任何文字。";

        String raw = llmUtil.chatAs(currentUsername(), systemPrompt, "试卷文本：\n" + text);
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("大模型未返回题目 JSON 数组");
        }

        JSONArray array = JSONUtil.parseArray(raw.substring(start, end + 1));
        List<ParsedQuestionDTO> questions = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject object = array.getJSONObject(i);
            String content = trimToNull(object.getStr("questionContent"));
            if (content == null) {
                continue;
            }
            ParsedQuestionDTO question = new ParsedQuestionDTO();
            question.setQuestionNo(defaultIfBlank(object.getStr("questionNo"), String.valueOf(i + 1)));
            question.setSectionName(defaultIfBlank(object.getStr("sectionName"), "简答题"));
            question.setQuestionType(normalizeType(object.getInt("questionType", 5)));
            question.setQuestionContent(truncate(removeScoreOnlyLines(content), 10_000));
            question.setStandardAnswer(truncate(object.getStr("standardAnswer"), 10_000));
            question.setScoringRule(truncate(object.getStr("scoringRule"), 2_000));
            question.setScore(normalizeScore(object.get("score")));
            questions.add(question);
        }

        // 大模型偶发把卷头、注意事项或大题标题当成题目，这里再做一次兜底过滤
        questions.removeIf(question -> {
            String content = question.getQuestionContent();
            return content == null || content.trim().length() < MIN_QUESTION_LENGTH
                    || isBoilerplate(content) || isSectionHeading(content);
        });
        normalizeQuestionNos(questions);
        if (questions.isEmpty()) {
            throw new IllegalStateException("大模型未识别出题目");
        }
        return questions;
    }

    // ==================== 规则兜底 ====================

    /**
     * 规则拆分：按"第1题 / 1. / 1、 / 一、"等题号标记切分文本。
     *
     * 只保留真正需要作答的题目：
     * - 第一个编号出现之前的卷头整段丢弃（仅尝试提取大题名称）；
     * - 考生注意、答题卡说明、分值说明等样板文字按首行识别后跳过；
     * - 大题标题只用于更新后续题目的 sectionName，不单独成题；
     * - 题号按解析顺序统一重排，避免卷头编号造成的重复；
     * - 分值优先取括号内的"（10分）"，取不到时默认 10 分。
     */
    private List<ParsedQuestionDTO> ruleSplit(String text) {
        String[] blocks = QUESTION_START.split(text);
        List<ParsedQuestionDTO> questions = new ArrayList<>();
        String currentSection = "简答题";
        BigDecimal sectionScore = null;
        boolean headerSkipped = false;

        for (String block : blocks) {
            String content = block == null ? "" : block.trim();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            Matcher matcher = QUESTION_PREFIX.matcher(content);
            if (!matcher.find()) {
                // 首个编号之前的是卷头，只尝试提取大题名
                if (!headerSkipped) {
                    headerSkipped = true;
                    if (isSectionHeading(content)) {
                        currentSection = sectionNameOf(content);
                    }
                }
                continue;
            }
            content = content.substring(matcher.end()).trim();
            if (isSectionHeading(content)) {
                currentSection = sectionNameOf(content);
                // "每小题5分"作为该大题下未标注分值题目的默认分
                BigDecimal headingScore = lastScore(SECTION_SCORE_PATTERN, firstLine(content));
                sectionScore = headingScore != null && headingScore.compareTo(new BigDecimal("0.5")) >= 0
                        && headingScore.compareTo(new BigDecimal("100")) <= 0 ? headingScore : null;
                continue;
            }
            if (content.length() < MIN_QUESTION_LENGTH || isBoilerplate(content)) {
                continue;
            }
            BigDecimal score = extractScore(content);
            if (score == null) {
                score = sectionScore == null ? DEFAULT_SCORE : sectionScore;
            }
            content = removeScoreOnlyLines(content);
            if (content.length() < MIN_QUESTION_LENGTH) {
                continue;
            }

            ParsedQuestionDTO question = new ParsedQuestionDTO();
            question.setQuestionNo(String.valueOf(questions.size() + 1));
            question.setSectionName(currentSection);
            question.setQuestionType(typeOf(currentSection));
            question.setQuestionContent(truncate(content, 10_000));
            question.setScore(score);
            questions.add(question);
        }

        // 完全找不到题目时，保留原文交给教师手动修改，避免返回空列表
        if (questions.isEmpty() && text.length() >= MIN_QUESTION_LENGTH
                && !isBoilerplate(text) && !isSectionHeading(text)) {
            ParsedQuestionDTO question = new ParsedQuestionDTO();
            question.setQuestionNo("1");
            question.setSectionName("简答题");
            question.setQuestionType(5);
            question.setQuestionContent(truncate(text.trim(), 10_000));
            question.setScore(DEFAULT_SCORE);
            questions.add(question);
        }
        return questions;
    }

    // ==================== 内部工具 ====================

    /** 取段落首行，样板文字与大题标题都只看首行，避免误伤题干内容 */
    private String firstLine(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String[] lines = text.trim().split("\\R", 2);
        return lines[0].replace("\r", "").trim();
    }

    /** 卷头、考生注意事项、答题卡说明等非题目内容 */
    private boolean isBoilerplate(String text) {
        String line = firstLine(text);
        if (!StringUtils.hasText(line)) {
            return true;
        }
        for (Pattern pattern : BOILERPLATE_PATTERNS) {
            if (pattern.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    /** 是否为大题标题（如"一、简答题（本题共2小题，共25分）"），标题不单独成题 */
    private boolean isSectionHeading(String text) {
        String line = firstLine(text);
        return StringUtils.hasText(line) && SECTION_HEADING.matcher(line).matches();
    }

    /** 从大题标题中提取大题名称，取不到时按简答题处理 */
    private String sectionNameOf(String text) {
        String line = SECTION_PREFIX.matcher(firstLine(text)).replaceFirst("").trim();
        Matcher matcher = SECTION_KEYWORD.matcher(line);
        return matcher.find() ? matcher.group(1) : "简答题";
    }

    /** 大题名称映射到题型：与前端 QUESTION_TYPE 字典保持一致 */
    private int typeOf(String sectionName) {
        if (!StringUtils.hasText(sectionName)) {
            return 5;
        }
        String name = sectionName.trim();
        if (name.startsWith("多选") || name.startsWith("双选")) {
            return 2;
        }
        if (name.startsWith("选择") || name.startsWith("单选")) {
            return 1;
        }
        if (name.startsWith("判断")) {
            return 3;
        }
        if (name.startsWith("填空")) {
            return 4;
        }
        if (name.startsWith("论述")) {
            return 6;
        }
        if (name.startsWith("计算")) {
            return 7;
        }
        if (name.startsWith("案例")) {
            return 8;
        }
        return 5;
    }

    /**
     * 从题干中提取单题分值。
     * 依次尝试"（15分）"、"本题15分/满分15分"、单独一行的"15分"；
     * 都取不到时返回 null，由调用方决定用大题默认分还是系统默认分。
     */
    private BigDecimal extractScore(String text) {
        BigDecimal score = lastScore(SCORE_PATTERN, text);
        if (score == null) {
            score = lastScore(SCORE_KEYWORD_PATTERN, text);
        }
        if (score == null) {
            score = lastScore(SCORE_LINE_PATTERN, text);
        }
        if (score == null || score.compareTo(new BigDecimal("0.5")) < 0
                || score.compareTo(new BigDecimal("100")) > 0) {
            return null;
        }
        return score.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** 取所有匹配中的最后一个分值（题干末尾的分值通常就是本题分值） */
    private BigDecimal lastScore(Pattern pattern, String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        BigDecimal score = null;
        while (matcher.find()) {
            try {
                score = new BigDecimal(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // 理论上不会发生：正则只捕获数字
            }
        }
        return score;
    }

    /**
     * 清洗 PDF 提取文本：去掉分隔线、页码页脚和多余空行。
     * 公式错位是 PDF 文本层固有问题，这里不做猜测性改写。
     */
    private String cleanText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder builder = new StringBuilder();
        boolean lastBlank = false;
        for (String rawLine : normalized.split("\n", -1)) {
            String line = rawLine.trim();
            if (SEPARATOR_LINE.matcher(line).matches()
                    || PAGE_NUMBER_LINE.matcher(line).matches()
                    || PAGE_FOOTER_LINE.matcher(line).matches()) {
                continue;
            }
            // 去掉题干中间的答题横线（如"-------- 求|AB|"），保留正文
            line = LONG_DASH_RUN.matcher(line).replaceAll(" ").trim();
            if (line.isEmpty()) {
                if (lastBlank) {
                    continue;
                }
                lastBlank = true;
                builder.append('\n');
                continue;
            }
            lastBlank = false;
            builder.append(line).append('\n');
        }
        return builder.toString().trim();
    }

    /** 去掉题干中单独成行的"（15分）"，分值已单独存入 score 字段 */
    private String removeScoreOnlyLines(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            if (SCORE_ONLY_LINE.matcher(line).matches()) {
                continue;
            }
            builder.append(line).append('\n');
        }
        return builder.toString().trim();
    }

    /** 题号去重：保留首个出现的题号，空号/重复号顺延到未占用的序号 */
    private void normalizeQuestionNos(List<ParsedQuestionDTO> questions) {
        Set<String> used = new HashSet<>();
        int next = 1;
        for (ParsedQuestionDTO question : questions) {
            String questionNo = trimToNull(question.getQuestionNo());
            if (questionNo == null || used.contains(questionNo)) {
                while (used.contains(String.valueOf(next))) {
                    next++;
                }
                questionNo = String.valueOf(next);
            }
            used.add(questionNo);
            question.setQuestionNo(questionNo);
        }
    }

    private void assertSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException("文件不能超过 20MB：" + file.getOriginalFilename());
        }
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
    }

    private int normalizeType(Integer type) {
        return type == null || type < 1 || type > 8 ? 5 : type;
    }

    private BigDecimal normalizeScore(Object value) {
        if (value == null) {
            return DEFAULT_SCORE;
        }
        try {
            BigDecimal score = new BigDecimal(value.toString());
            if (score.compareTo(BigDecimal.ZERO) <= 0) {
                return DEFAULT_SCORE;
            }
            return score.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return DEFAULT_SCORE;
        }
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated()
                ? null : authentication.getName();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
