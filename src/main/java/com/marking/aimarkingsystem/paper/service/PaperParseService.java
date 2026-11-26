package com.marking.aimarkingsystem.paper.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.util.DeepSeekLlmUtil;
import com.marking.aimarkingsystem.common.util.MinioUtil;
import com.marking.aimarkingsystem.paper.dto.PageParseStatus;
import com.marking.aimarkingsystem.paper.dto.PaperParseResult;
import com.marking.aimarkingsystem.paper.dto.ParsedQuestionDTO;
import com.marking.aimarkingsystem.paper.util.OcrClient;
import com.marking.aimarkingsystem.paper.util.PdfTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 试卷上传解析服务。
 *
 * 流程（拆成两段，避免长耗时 OCR 占着 HTTP 连接）：
 * 1. prepare：校验文件（扩展名 + 文件头）、上传对象存储、提取 PDF 文本层；
 * 2. complete：后台线程从对象存储取回图片逐张 OCR，去重后合并文本，再规则/大模型拆题；
 * 3. 返回题目草稿，教师在前端增删改后，以人工确认结果为准入库。
 *
 * 不做持久化：本接口只"识别 + 上传素材"，建卷仍走 POST /papers + POST /questions/batch。
 */
@Slf4j
@Service
public class PaperParseService {

    /** 大模型请求的最大文本长度；本地规则解析始终使用完整文本 */
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

    /** 题号后的行内题型，兼容 [单选题]、【单选题】、（单选题）及 OCR 缺失/错误括号。 */
    private static final Pattern INLINE_TYPE_PREFIX = Pattern.compile(
            "^[\\s\\p{Zs}]*([\\[【(（「『［]?)[\\s\\p{Zs}]*"
                    + "(单选题|多选题|双选题|选择题|填空题|判断题|简答题|论述题|计算题|案例分析题|解答题)"
                    + "[\\s\\p{Zs}]*([\\]】)）」』］]?)[\\s\\p{Zs}]*(?:[：:、]\\s*)?");

    /** 至少两个独立选项行，是没有题型文字时判断单选题的兜底证据。 */
    private static final Pattern OPTION_LINE = Pattern.compile(
            "(?m)^[\\s\\p{Zs}]*[A-HＡ-Ｈ][\\s\\p{Zs}]*[.、．:：)）][\\s\\p{Zs}]*\\S+");

    private static final Pattern MULTIPLE_CHOICE_HINT = Pattern.compile(
            "多选|至少(?:有)?(?:两项|两个|2项|2个)|多个正确|选出所有");

    private static final String UNKNOWN_SECTION = "题型待确认";

    /** 截图账号水印：与正文分栏的长数字后缀，或“汉字+长数字”的独立碎片。 */
    private static final Pattern WATERMARK_SUFFIX = Pattern.compile(
            "^(.*?\\S)[\\s\\p{Zs}]{2,}\\S*\\p{IsHan}\\S*\\d{5,}\\S*$");
    private static final Pattern TEXT_NUMBER_WATERMARK = Pattern.compile("^[\\p{IsHan}]{1,6}\\d{7,20}$");
    private static final Pattern LONG_NUMBER_LINE = Pattern.compile("^\\d{7,20}$");
    private static final Pattern SHORT_NUMBER_FRAGMENT = Pattern.compile("^\\d{3,4}$");

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
            "^[\\s\\p{Zs}]*[（(]?[\\s\\p{Zs}]*\\d+(?:\\.\\d+)?[\\s\\p{Zs}]*分[\\s\\p{Zs}]*[)）]?[\\s\\p{Zs}]*$");

    /** 手机状态栏、答题 App 控件、页码和长数字水印；均限定为独立短行，避免误删公式。 */
    private static final List<Pattern> OCR_NOISE_LINES = List.of(
            Pattern.compile("^[\\s\\p{Zs}]*(?:[1-5]G|HD|VOLTE|Wi-?Fi)(?:[\\s\\p{Zs}]+\\d{1,3}%?)?[\\s\\p{Zs}]*$",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("^[\\s\\p{Zs}]*\\d{1,2}:\\d{2}(?::\\d{2})?[\\s\\p{Zs}]*$"),
            Pattern.compile("^[\\s\\p{Zs}]*[（(]?\\d+\\s*/\\s*\\d+[)）]?[\\s\\p{Zs}]*$"),
            Pattern.compile("^[\\s\\p{Zs}]*(?:答题卡|答题计时|考试倒计时|剩余时间|答题进度|题号导航|"
                    + "上一题|下一题|返回|暂停|继续答题|交卷|收藏|分享|更多)(?:[：:].{0,20})?[\\s\\p{Zs}]*$"),
            Pattern.compile("^[\\s\\p{Zs}]*\\d{9,20}[\\s\\p{Zs}]*$")
    );

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
            Pattern.compile("^\\s*题号\\s*$"),
            // OCR 截图/拍照常见噪声：卷头信息栏、得分栏、答题 App 的界面文字与倒计时。
            // 全部限定"段落首行 + 短行"，避免误伤真实题干。
            Pattern.compile("^\\s*(姓名|学号|准考证号|班级|学院|专业|学校|座位号|课程名称|考试科目|考试名称|授课教师)"
                    + "\\s*[：:].{0,40}$"),
            Pattern.compile("^\\s*(得分|总分|分数|评卷人|复核人)\\s*[：:]?\\s*\\d{0,6}\\s*$"),
            Pattern.compile("^\\s*(密封线|装订线)"),
            Pattern.compile("^\\s*(随堂练习|在线考试|考试说明|答题说明|返回|暂停|继续答题|交卷|题号导航)\\s*$"),
            Pattern.compile("^\\s*\\d{1,2}:\\d{2}(:\\d{2})?\\s*$")
    );

    /** 单页解析状态值 */
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED_DUPLICATE";

    /** 是否允许上传解析调用大模型；默认关闭，保证试卷/题目上传零成本 */
    @Value("${paper-parse.llm-enabled:false}")
    private boolean llmEnabled;

    /** 近重复图片判定阈值（dHash 汉明距离），0 表示关闭近重复检测 */
    @Value("${paper-parse.duplicate-hamming-distance:6}")
    private int duplicateHammingDistance = 6;

    /** 单次解析任务的总时长预算（秒），超出后剩余图片直接标记失败 */
    @Value("${paper-parse.task-timeout-seconds:120}")
    private int taskTimeoutSeconds = 120;

    /** 多页文本去重：题号相同且题干相似度达到该值视为同一题 */
    @Value("${paper-parse.merge-similarity:0.58}")
    private double mergeSimilarity = 0.58;

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

    /** 已上传到对象存储的一张图片：后台线程凭 objectName 再取回字节，避免在内存里长期压着全部原图 */
    public record UploadedImage(String objectName, String fileName) implements Serializable {
    }

    /** 第一阶段产物：只包含已经落盘的素材与 PDF 文本，交给后台线程继续识别 */
    public record PreparedParse(String pdfUrl,
                                String pdfText,
                                List<UploadedImage> images,
                                List<String> warnings,
                                List<PageParseStatus> pages) implements Serializable {
    }

    /** 逐张图片处理进度回调 */
    @FunctionalInterface
    public interface ProgressListener {
        void onImageDone(int done, int total);
    }

    /**
     * 解析第一阶段（请求线程内，只做秒级操作）：校验文件、上传对象存储、提取 PDF 文本层。
     *
     * 为什么要拆两段：逐张 OCR 最坏可达数分钟，同步返回会把网关和手机浏览器拖到超时；
     * 这里只保留"存文件 + PDF 取文本"这类快操作，图片识别交给后台任务。
     */
    public PreparedParse prepare(List<MultipartFile> files) {
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
            // 扩展名可以随便改，这里再校验文件头，避免把其它文件改名成 .jpg 传进来
            assertSize(file);
            assertFileSignature(file, extension);
        }
        if (pdfFiles.size() > 1) {
            throw new BusinessException("一次最多上传 1 个 PDF");
        }
        if (imageFiles.size() > MAX_IMAGES) {
            throw new BusinessException("一次最多上传 " + MAX_IMAGES + " 张图片");
        }

        List<String> warnings = new ArrayList<>();
        List<PageParseStatus> pages = new ArrayList<>();
        String pdfUrl = null;
        String pdfText = "";
        if (!pdfFiles.isEmpty()) {
            MultipartFile pdf = pdfFiles.get(0);
            pdfUrl = minioUtil.upload(pdf, PDF_DIRECTORY);
            pdfText = pdfTextExtractor.extract(pdf);
            if (StringUtils.hasText(pdfText)) {
                pages.add(new PageParseStatus(pdf.getOriginalFilename(), STATUS_SUCCESS,
                        pdfText.length(), "已提取 PDF 文本层"));
            } else {
                pages.add(new PageParseStatus(pdf.getOriginalFilename(), STATUS_FAILED,
                        0, "未提取到文本层，可能是扫描件"));
                warnings.add("PDF 未提取到文本层（可能是扫描件），请改用拍照图片 OCR 或手动录入题目");
            }
        }

        List<UploadedImage> images = new ArrayList<>();
        for (MultipartFile image : imageFiles) {
            images.add(new UploadedImage(minioUtil.upload(image, IMAGE_DIRECTORY), image.getOriginalFilename()));
        }
        return new PreparedParse(pdfUrl, pdfText, images, warnings, pages);
    }

    /**
     * 解析第二阶段（后台线程）：从对象存储取回图片逐张 OCR，去重后合并文本并拆题。
     *
     * @param listener 每处理完一张图片回调一次，用于推进任务进度
     */
    public PaperParseResult complete(PreparedParse prepared, ProgressListener listener) {
        PaperParseResult result = new PaperParseResult();
        result.setPdfUrl(prepared.pdfUrl());
        result.setImageUrls(prepared.images().stream().map(UploadedImage::objectName).toList());
        List<String> warnings = new ArrayList<>(prepared.warnings());
        List<PageParseStatus> pages = new ArrayList<>(prepared.pages());
        List<String> recognizedPages = new ArrayList<>();
        if (StringUtils.hasText(prepared.pdfText())) {
            recognizedPages.add(prepared.pdfText());
        }

        List<UploadedImage> images = prepared.images();
        if (!images.isEmpty() && !ocrClient.isConfigured()) {
            warnings.add("未配置 OCR 接口，图片已上传但未识别文字，请手动录入题目");
            for (UploadedImage image : images) {
                pages.add(new PageParseStatus(image.fileName(), STATUS_FAILED, 0, "未配置 OCR 接口"));
            }
        } else if (!images.isEmpty()) {
            Set<String> byteDigests = new HashSet<>();
            Map<Long, String> perceptualHashes = new HashMap<>();
            // 单次任务的总时长预算：超过后剩余图片直接标记失败，避免一张卡死拖到无限久
            long deadline = System.currentTimeMillis() + taskTimeoutSeconds * 1000L;
            int done = 0;
            for (UploadedImage image : images) {
                done++;
                try {
                    if (System.currentTimeMillis() > deadline) {
                        pages.add(new PageParseStatus(image.fileName(), STATUS_FAILED, 0,
                                "超出单次解析总时长 " + taskTimeoutSeconds + " 秒，请减少图片后重试"));
                        warnings.add("图片 " + image.fileName() + " 超出解析总时长，未识别");
                        continue;
                    }

                    byte[] imageBytes = readObject(image.objectName());
                    // 第一层：字节级完全一致（同一张图被重复选择）
                    if (!byteDigests.add(imageDigest(imageBytes))) {
                        pages.add(new PageParseStatus(image.fileName(), STATUS_SKIPPED,
                                0, "与本次上传的另一张图片完全相同"));
                        warnings.add("已跳过重复图片：" + image.fileName());
                        continue;
                    }
                    // 第二层：感知哈希近似（同一页重拍、轻微旋转或重新压缩）
                    long hash = perceptualHash(imageBytes);
                    if (hash != 0L) {
                        String similarTo = findNearDuplicate(perceptualHashes, hash, image.fileName());
                        if (similarTo != null) {
                            pages.add(new PageParseStatus(image.fileName(), STATUS_SKIPPED,
                                    0, "与 " + similarTo + " 高度相似"));
                            warnings.add("图片 " + image.fileName() + " 与 " + similarTo + " 高度相似，已跳过");
                            continue;
                        }
                    }

                    String ocrText = ocrClient.recognize(imageBytes, image.fileName());
                    if (StringUtils.hasText(ocrText)) {
                        recognizedPages.add(ocrText);
                        pages.add(new PageParseStatus(image.fileName(), STATUS_SUCCESS,
                                ocrText.length(), "已识别"));
                    } else {
                        pages.add(new PageParseStatus(image.fileName(), STATUS_FAILED, 0, "未识别到文字"));
                        warnings.add("图片 " + image.fileName() + " 未识别到文字");
                    }
                } catch (Exception e) {
                    pages.add(new PageParseStatus(image.fileName(), STATUS_FAILED, 0, e.getMessage()));
                    warnings.add("图片 " + image.fileName() + " 识别失败：" + e.getMessage());
                } finally {
                    if (listener != null) {
                        listener.onImageDone(done, images.size());
                    }
                }
            }
        }

        // 规则路径使用完整文本；20,000 字上限只约束耗时较长的大模型请求。
        String extracted = cleanText(String.join("\n", recognizedPages));
        result.setExtractedText(extracted);
        result.setWarnings(warnings);
        result.setPages(pages);

        if (!StringUtils.hasText(extracted)) {
            result.setSource("MANUAL");
            result.setQuestions(Collections.emptyList());
            if (warnings.isEmpty()) {
                warnings.add("未识别到可用文本，请手动录入题目");
            }
            return result;
        }

        List<ParsedQuestionDTO> questions;
        if (!llmEnabled) {
            // 默认路径：只用本地 OCR + 规则拆分，不发起任何大模型请求，上传解析不产生费用
            questions = ruleSplit(extracted);
            result.setSource("RULE");
        } else {
            try {
                questions = askLlm(truncate(extracted, MAX_TEXT_LENGTH));
                result.setSource("LLM");
            } catch (Exception e) {
                log.warn("大模型结构化题目失败，改用规则拆分: {}", e.getMessage());
                warnings.add("大模型未返回可用结果，已使用本地规则免费拆分，请人工核对题目");
                questions = ruleSplit(extracted);
                result.setSource("RULE");
            }
        }

        if (questions.stream().anyMatch(question -> UNKNOWN_SECTION.equals(question.getSectionName()))) {
            warnings.add("部分题目缺少可靠题型依据，已标记为“题型待确认”，请人工选择题型");
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
     * 本地规则拆分：优先保留数字原题号，并按行内题型、大题标题、选项证据依次判断题型。
     * 多页文本直接连续解析，因此没有新题号的下一页开头会自然并入上一题。
     */
    List<ParsedQuestionDTO> ruleSplit(String text) {
        String[] blocks = QUESTION_START.split(text);
        List<ParsedQuestionDTO> questions = new ArrayList<>();
        String currentSection = null;
        BigDecimal sectionScore = null;

        for (String block : blocks) {
            String content = block == null ? "" : block.trim();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            Matcher matcher = QUESTION_PREFIX.matcher(content);
            if (!matcher.find()) {
                // 首个题号前是卷头；无编号的大题标题只更新上下文，不生成题目。
                if (isSectionHeading(content)) {
                    currentSection = sectionNameOf(content);
                    sectionScore = sectionScoreOf(content);
                }
                continue;
            }

            String questionNo = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            content = content.substring(matcher.end()).trim();
            if (isSectionHeading(content)) {
                currentSection = sectionNameOf(content);
                sectionScore = sectionScoreOf(content);
                continue;
            }

            String inlineSection = null;
            Matcher inlineType = INLINE_TYPE_PREFIX.matcher(content);
            if (inlineType.find()) {
                inlineSection = inlineType.group(2);
                boolean brokenBracket = !inlineType.group(1).isEmpty() && inlineType.group(3).isEmpty();
                content = content.substring(inlineType.end()).trim();
                if (brokenBracket) {
                    content = removeRepeatedQuestionNo(content, questionNo);
                }
            }
            if (content.length() < MIN_QUESTION_LENGTH || isBoilerplate(content)) {
                continue;
            }

            BigDecimal score = extractScore(content);
            if (score == null) {
                score = sectionScore == null ? DEFAULT_SCORE : sectionScore;
            }
            content = removeScoreOnlyLines(content);
            if (content.length() < MIN_QUESTION_LENGTH || isBoilerplate(content)) {
                continue;
            }

            String sectionName = resolveSectionName(inlineSection, currentSection, content);
            ParsedQuestionDTO question = new ParsedQuestionDTO();
            question.setQuestionNo(StringUtils.hasText(questionNo)
                    ? questionNo : String.valueOf(questions.size() + 1));
            question.setSectionName(sectionName);
            question.setQuestionType(typeOf(sectionName));
            question.setQuestionContent(truncate(content, 10_000));
            question.setScore(score);
            questions.add(question);
        }

        // 单道无题号题目仍可使用；没有问号、选项或作答动词时，不把整页说明文字伪造成题目。
        if (questions.isEmpty() && text.length() >= MIN_QUESTION_LENGTH
                && !isBoilerplate(text) && !isSectionHeading(text) && hasQuestionEvidence(text)) {
            String content = removeScoreOnlyLines(text.trim());
            String sectionName = resolveSectionName(null, currentSection, content);
            BigDecimal score = extractScore(content);
            ParsedQuestionDTO question = new ParsedQuestionDTO();
            question.setQuestionNo("1");
            question.setSectionName(sectionName);
            question.setQuestionType(typeOf(sectionName));
            question.setQuestionContent(truncate(content, 10_000));
            question.setScore(score == null ? DEFAULT_SCORE : score);
            questions.add(question);
        }
        return mergeDuplicateQuestions(questions);
    }

    /** 大题中声明的每小题分值，只作为没有单题分值时的后备。 */
    private BigDecimal sectionScoreOf(String content) {
        BigDecimal score = lastScore(SECTION_SCORE_PATTERN, firstLine(content));
        return score != null && score.compareTo(new BigDecimal("0.5")) >= 0
                && score.compareTo(new BigDecimal("100")) <= 0 ? score : null;
    }

    /** 行内题型 > 大题标题 > 选项模式；没有证据时不猜成简答题。 */
    private String resolveSectionName(String inlineSection, String currentSection, String content) {
        if (StringUtils.hasText(inlineSection)) {
            return inlineSection;
        }
        if (StringUtils.hasText(currentSection)) {
            if ("选择题".equals(currentSection) && MULTIPLE_CHOICE_HINT.matcher(content).find()) {
                return "多选题";
            }
            return currentSection;
        }
        if (countOptions(content) >= 2) {
            return MULTIPLE_CHOICE_HINT.matcher(content).find() ? "多选题" : "单选题";
        }
        return UNKNOWN_SECTION;
    }

    private int countOptions(String content) {
        int count = 0;
        Matcher matcher = OPTION_LINE.matcher(content);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private boolean hasQuestionEvidence(String content) {
        return countOptions(content) >= 2
                || Pattern.compile("[?？]|____|(?:^|[\\s，。；：:])(?:简述|论述|计算|证明|求解|求|下列|以下|什么是|为什么)")
                        .matcher(content).find();
    }

    /** OCR 把“1.「单选题」1.题干”识别成缺括号文本时，去掉紧挨题型的重复题号。 */
    private String removeRepeatedQuestionNo(String content, String questionNo) {
        if (!StringUtils.hasText(questionNo) || !content.startsWith(questionNo)
                || content.length() == questionNo.length()) {
            return content;
        }
        char next = content.charAt(questionNo.length());
        if (Character.isDigit(next) || ".、．)）".indexOf(next) >= 0) {
            return content;
        }
        return content.substring(questionNo.length()).trim();
    }

    /** 同页/多页重复题合并；相同题号但正文不同的题目仍然保留，绝不仅按题号去重。 */
    private List<ParsedQuestionDTO> mergeDuplicateQuestions(List<ParsedQuestionDTO> source) {
        List<ParsedQuestionDTO> merged = new ArrayList<>();
        for (ParsedQuestionDTO candidate : source) {
            int duplicateIndex = -1;
            for (int i = 0; i < merged.size(); i++) {
                if (isDuplicateQuestion(merged.get(i), candidate)) {
                    duplicateIndex = i;
                    break;
                }
            }
            if (duplicateIndex < 0) {
                merged.add(candidate);
                continue;
            }

            ParsedQuestionDTO existing = merged.get(duplicateIndex);
            String existingContent = normalizeForComparison(existing.getQuestionContent());
            String candidateContent = normalizeForComparison(candidate.getQuestionContent());
            if (candidateContent.length() > existingContent.length()) {
                existing.setQuestionContent(candidate.getQuestionContent());
                existing.setScore(candidate.getScore());
            }
            if (UNKNOWN_SECTION.equals(existing.getSectionName())
                    && !UNKNOWN_SECTION.equals(candidate.getSectionName())) {
                existing.setSectionName(candidate.getSectionName());
                existing.setQuestionType(candidate.getQuestionType());
            }
        }
        return merged;
    }

    private boolean isDuplicateQuestion(ParsedQuestionDTO left, ParsedQuestionDTO right) {
        String leftContent = normalizeForComparison(left.getQuestionContent());
        String rightContent = normalizeForComparison(right.getQuestionContent());
        if (leftContent.length() < MIN_QUESTION_LENGTH || rightContent.length() < MIN_QUESTION_LENGTH) {
            return false;
        }
        if (leftContent.equals(rightContent)) {
            return true;
        }
        if (!StringUtils.hasText(left.getQuestionNo()) || !StringUtils.hasText(right.getQuestionNo())
                || !left.getQuestionNo().equals(right.getQuestionNo())) {
            return false;
        }
        return leftContent.contains(rightContent) || rightContent.contains(leftContent)
                || contentSimilarity(leftContent, rightContent) >= mergeSimilarity;
    }

    private String normalizeForComparison(String content) {
        return content == null ? "" : content.replaceAll("[\\s\\p{P}\\p{Zs}]", "").toLowerCase();
    }

    /** 字符二元组 Dice 相似度，对 OCR 个别错字稳健，且不会把长文本两两做高耗时编辑距离。 */
    private double contentSimilarity(String left, String right) {
        if (left.length() < 2 || right.length() < 2) {
            return 0;
        }
        Map<String, Integer> leftGrams = bigrams(left);
        Map<String, Integer> rightGrams = bigrams(right);
        int intersection = 0;
        for (Map.Entry<String, Integer> entry : leftGrams.entrySet()) {
            intersection += Math.min(entry.getValue(), rightGrams.getOrDefault(entry.getKey(), 0));
        }
        int leftCount = leftGrams.values().stream().mapToInt(Integer::intValue).sum();
        int rightCount = rightGrams.values().stream().mapToInt(Integer::intValue).sum();
        return leftCount + rightCount == 0 ? 0 : 2.0 * intersection / (leftCount + rightCount);
    }

    private Map<String, Integer> bigrams(String content) {
        Map<String, Integer> grams = new HashMap<>();
        for (int i = 0; i + 1 < content.length(); i++) {
            grams.merge(content.substring(i, i + 2), 1, Integer::sum);
        }
        return grams;
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

    /**
     * 是否为大题标题。首行匹配还不够：后面若存在真实题干，则这是带行内题型的题目。
     */
    private boolean isSectionHeading(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String[] lines = text.trim().split("\\R");
        if (lines.length == 0 || !SECTION_HEADING.matcher(lines[0].trim()).matches()) {
            return false;
        }
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || SCORE_ONLY_LINE.matcher(line).matches() || isBoilerplate(line)) {
                continue;
            }
            return false;
        }
        return true;
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
                    || PAGE_FOOTER_LINE.matcher(line).matches()
                    || isOcrNoiseLine(line)) {
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

    private boolean isOcrNoiseLine(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        for (Pattern pattern : OCR_NOISE_LINES) {
            if (pattern.matcher(line).matches()) {
                return true;
            }
        }
        return false;
    }

    /** 去掉题干中单独成行的"（15分）"，分值已单独存入 score 字段 */
    private String removeScoreOnlyLines(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            if (SCORE_ONLY_LINE.matcher(line).matches() || isOcrNoiseLine(line)) {
                continue;
            }
            builder.append(line).append('\n');
        }
        return removeWatermarkFragments(builder.toString().trim());
    }

    /** 清理能明确判定的账号水印，不改写含等号、运算符或上下标的数学公式。 */
    private String removeWatermarkFragments(String text) {
        List<String> lines = new ArrayList<>();
        boolean watermarkFound = false;
        for (String rawLine : text.split("\\R", -1)) {
            String line = rawLine.trim();
            Matcher suffix = WATERMARK_SUFFIX.matcher(line);
            if (suffix.matches()) {
                line = suffix.group(1).trim();
                watermarkFound = true;
            }
            if ((LONG_NUMBER_LINE.matcher(line).matches() || TEXT_NUMBER_WATERMARK.matcher(line).matches())
                    && !line.matches(".*[=+*/^_{}\\\\].*")) {
                watermarkFound = true;
                continue;
            }
            lines.add(line);
        }

        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            // 已确认有账号水印时，其断裂出的 3~4 位纯数字行也忽略；普通数值题不受影响。
            if (watermarkFound && SHORT_NUMBER_FRAGMENT.matcher(line).matches()) {
                continue;
            }
            cleaned.append(line).append('\n');
        }
        return cleaned.toString().trim();
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

    private byte[] readObject(String objectName) throws IOException {
        try (InputStream in = minioUtil.download(objectName)) {
            return in.readAllBytes();
        }
    }

    /** 与已有图片的 dHash 距离不超过阈值即视为同一页；否则把当前哈希登记下来。 */
    private String findNearDuplicate(Map<Long, String> perceptualHashes, long hash, String fileName) {
        for (Map.Entry<Long, String> entry : perceptualHashes.entrySet()) {
            if (hammingDistance(entry.getKey(), hash) <= duplicateHammingDistance) {
                return entry.getValue();
            }
        }
        perceptualHashes.putIfAbsent(hash, fileName);
        return null;
    }

    /**
     * 64 位差值哈希（dHash）：缩放到 9x8 灰度图，比较相邻像素亮度。
     * 纯 JDK 实现，不引入 OpenCV；对重拍、重新压缩、轻微旋转有容忍度。
     * 返回 0 表示图片无法解码或整张图亮度完全均匀，此时不参与近重复判定。
     */
    long perceptualHash(byte[] imageBytes) {
        try (InputStream in = new ByteArrayInputStream(imageBytes)) {
            BufferedImage source = ImageIO.read(in);
            if (source == null) {
                return 0L;
            }
            BufferedImage scaled = new BufferedImage(9, 8, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = scaled.createGraphics();
            try {
                graphics.drawImage(source, 0, 0, 9, 8, null);
            } finally {
                graphics.dispose();
            }
            long hash = 0L;
            int bit = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    if (brightness(scaled.getRGB(x, y)) > brightness(scaled.getRGB(x + 1, y))) {
                        hash |= 1L << bit;
                    }
                    bit++;
                }
            }
            return hash;
        } catch (Exception e) {
            log.debug("图片无法计算感知哈希，跳过近重复判定: {}", e.getMessage());
            return 0L;
        }
    }

    private int brightness(int rgb) {
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }

    static int hammingDistance(long left, long right) {
        return Long.bitCount(left ^ right);
    }

    private String imageDigest(byte[] imageBytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(imageBytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", e);
        }
    }

    private void assertSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException("文件不能超过 20MB：" + file.getOriginalFilename());
        }
    }

    /** 只读前 8 字节校验文件头，不改动原文件流 */
    private void assertFileSignature(MultipartFile file, String extension) {
        byte[] head = new byte[8];
        int length;
        try (InputStream in = file.getInputStream()) {
            length = in.read(head);
        } catch (IOException e) {
            throw new BusinessException("读取文件失败：" + file.getOriginalFilename());
        }
        if (!matchesSignature(head, length, extension)) {
            throw new BusinessException("文件内容与扩展名不符（可能是改过后缀的其它文件）："
                    + file.getOriginalFilename());
        }
    }

    /** 文件头白名单：PDF(%PDF-)、JPEG(FF D8 FF)、PNG(89 50 4E 47 0D 0A 1A 0A) */
    static boolean matchesSignature(byte[] head, int length, String extension) {
        if (head == null || length <= 0) {
            return false;
        }
        return switch (extension) {
            case "pdf" -> length >= 5 && head[0] == '%' && head[1] == 'P' && head[2] == 'D'
                    && head[3] == 'F' && head[4] == '-';
            case "jpg", "jpeg" -> length >= 3 && (head[0] & 0xFF) == 0xFF
                    && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            case "png" -> length >= 8 && (head[0] & 0xFF) == 0x89 && head[1] == 'P'
                    && head[2] == 'N' && head[3] == 'G' && (head[4] & 0xFF) == 0x0D
                    && (head[5] & 0xFF) == 0x0A && (head[6] & 0xFF) == 0x1A && (head[7] & 0xFF) == 0x0A;
            default -> false;
        };
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
