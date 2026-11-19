package com.marking.aimarkingsystem.learning.util;

import com.marking.aimarkingsystem.common.exception.BusinessException;
import com.marking.aimarkingsystem.common.util.MinioUtil;
import com.marking.aimarkingsystem.learning.vo.LearningStatisticsVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 学情讲评 PPT 生成工具（模块8）。
 *
 * 职责边界：
 * - 只负责"统计数据 + 讲评文本 -> pptx 字节流 -> MinIO objectKey"；
 * - 不查数据库、不调用大模型，输入全部由 LearningAnalysisService 传入；
 * - 放在 learning 包而不是 common 包，避免公共层反向依赖业务 VO。
 *
 * POI 生成 PPT 的关键点：
 * 1. XMLSlideShow 是整份演示文稿，write(OutputStream) 输出为 .pptx（OOXML 是 zip 包）；
 * 2. 版面优先复用母版占位符（TITLE_AND_CONTENT / TITLE_ONLY / BLANK），
 *    不要全部用绝对坐标画文本框，否则换主题或改版式时很难维护；
 * 3. 每个占位符/文本框的坐标由 anchor（Rectangle2D）控制，单位是 point，不是像素；
 * 4. 表格用 slide.createTable() 创建，再 addRow()/addCell()，列宽用 setColumnWidth 设置；
 * 5. 中文字体显式设置 setFontFamily("微软雅黑")，否则部分环境会回退成 Calibri 显示方框；
 * 6. 大模型输出的是 Markdown，这里只做轻量清洗（去掉 #、-、**、`），
 *    不做完整 Markdown 渲染——PPT 是讲评提纲，正文详情仍在 analysisText 中。
 *
 * 部署注意：POI 在 Linux 服务器上生成 PPT 不需要额外字体文件（字体名只是写入 XML），
 * 但若后续要转 PDF 预览，则服务器必须安装中文字体（如 fonts-wqy-zenhei）。
 */
@Slf4j
@Component
public class PptUtil {

    /** 中文字体，避免 PowerPoint 回退字体导致排版错乱 */
    private static final String FONT_FAMILY = "微软雅黑";

    /** 16:9 页面尺寸（point）：960 x 540 */
    private static final Dimension PAGE_SIZE = new Dimension(960, 540);

    private static final String PPT_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    /** MinIO 目录前缀 */
    private static final String PPT_DIRECTORY = "learning-report";

    /** 每页 PPT 最多放多少行讲评文字，超出自动分页 */
    private static final int LINES_PER_SLIDE = 8;

    /** 讲评章节最多生成多少页，防止大模型输出过长把 PPT 撑爆 */
    private static final int MAX_ANALYSIS_SLIDES = 12;

    /** 表格最多展示多少行，避免一页塞 50 行看不清 */
    private static final int MAX_TABLE_ROWS = 8;

    private static final String EMPTY_VALUE = "—";

    private final MinioUtil minioUtil;

    public PptUtil(MinioUtil minioUtil) {
        this.minioUtil = minioUtil;
    }

    /**
     * 生成 PPT 并上传到 MinIO。
     *
     * @param statistics   学情统计快照
     * @param analysisText 大模型生成的 Markdown 讲评正文
     * @param reportTitle  报告标题（PPT 封面）
     * @return MinIO objectKey，写入 learning_analysis_report.ppt_file_url
     */
    public String generateAndUpload(LearningStatisticsVO statistics, String analysisText, String reportTitle) {
        byte[] content = generate(statistics, analysisText, reportTitle);
        String objectKey = minioUtil.uploadBytes(content, PPT_DIRECTORY, "learning-report.pptx", PPT_CONTENT_TYPE);
        log.info("学情讲评PPT生成完成: objectKey={}, size={}B", objectKey, content.length);
        return objectKey;
    }

    /**
     * 生成 pptx 字节流（不落盘、不上传），便于单元测试或在其他场景复用。
     */
    public byte[] generate(LearningStatisticsVO statistics, String analysisText, String reportTitle) {
        try (XMLSlideShow ppt = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.setPageSize(PAGE_SIZE);

            createCoverSlide(ppt, reportTitle, statistics);
            createOverallSlide(ppt, statistics);
            createClassSlide(ppt, statistics);
            createWeakQuestionSlide(ppt, statistics);
            createKnowledgeSlide(ppt, statistics);
            createAnalysisSlides(ppt, analysisText);

            ppt.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("生成学情讲评PPT失败: reportTitle={}", reportTitle, e);
            throw new BusinessException("PPT生成失败：" + e.getMessage());
        }
    }

    // ==================== 各类幻灯片 ====================

    /** 封面：标题 + 副标题 + 日期 */
    private void createCoverSlide(XMLSlideShow ppt, String reportTitle, LearningStatisticsVO statistics) {
        XSLFSlide slide = ppt.createSlide(layout(ppt, SlideLayout.BLANK));

        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(60, 160, 840, 100));
        XSLFTextParagraph titleParagraph = titleBox.addNewTextParagraph();
        titleParagraph.setTextAlign(TextParagraph.TextAlign.CENTER);
        XSLFTextRun titleRun = titleParagraph.addNewTextRun();
        titleRun.setText(nullToEmpty(reportTitle));
        titleRun.setFontFamily(FONT_FAMILY);
        titleRun.setFontSize(34d);
        titleRun.setBold(true);

        XSLFTextBox subtitleBox = slide.createTextBox();
        subtitleBox.setAnchor(new Rectangle(60, 270, 840, 60));
        XSLFTextParagraph subtitleParagraph = subtitleBox.addNewTextParagraph();
        subtitleParagraph.setTextAlign(TextParagraph.TextAlign.CENTER);
        XSLFTextRun subtitleRun = subtitleParagraph.addNewTextRun();
        subtitleRun.setText(nullToEmpty(statistics.getPaperTitle()) + " · 学情分析报告");
        subtitleRun.setFontFamily(FONT_FAMILY);
        subtitleRun.setFontSize(20d);

        XSLFTextBox footerBox = slide.createTextBox();
        footerBox.setAnchor(new Rectangle(60, 440, 840, 40));
        XSLFTextParagraph footerParagraph = footerBox.addNewTextParagraph();
        footerParagraph.setTextAlign(TextParagraph.TextAlign.CENTER);
        XSLFTextRun footerRun = footerParagraph.addNewTextRun();
        footerRun.setText("AI 智能异步阅卷系统 · " + LocalDate.now());
        footerRun.setFontFamily(FONT_FAMILY);
        footerRun.setFontSize(14d);
    }

    /** 整体学情概览（项目符号列表） */
    private void createOverallSlide(XMLSlideShow ppt, LearningStatisticsVO statistics) {
        XSLFSlide slide = createContentSlide(ppt, "一、整体学情概览");

        int total = nullToZero(statistics.getTotalCount());
        int marked = nullToZero(statistics.getMarkedCount());
        List<String> lines = new ArrayList<>();
        lines.add("答卷总数：" + total + " 份（已出分 " + marked + " 份，未出分 " + (total - marked) + " 份）");
        lines.add("平均分：" + score(statistics.getAvgScore())
                + " / " + score(statistics.getTotalScore()));
        lines.add("最高分：" + score(statistics.getMaxScore())
                + "    最低分：" + score(statistics.getMinScore()));
        lines.add("及格人数：" + nullToZero(statistics.getPassCount())
                + "    及格率：" + percent(statistics.getPassRate()));
        lines.add("优秀人数：" + nullToZero(statistics.getExcellentCount())
                + "    优秀率：" + percent(statistics.getExcellentRate()));
        writeBullets(slide, lines);
    }

    /** 班级维度对比表 */
    private void createClassSlide(XMLSlideShow ppt, LearningStatisticsVO statistics) {
        List<LearningStatisticsVO.ClassMetric> metrics = statistics.getClassMetrics();
        if (metrics == null || metrics.isEmpty()) {
            writeSingleBulletSlide(ppt, "二、班级学情对比", "本次考试没有可用的班级信息，无法进行班级对比。");
            return;
        }

        XSLFSlide slide = createTitleOnlySlide(ppt, "二、班级学情对比");
        String[] headers = {"班级", "答卷数", "已出分", "平均分", "及格率", "优秀率"};
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(metrics.size(), MAX_TABLE_ROWS); i++) {
            LearningStatisticsVO.ClassMetric metric = metrics.get(i);
            rows.add(new String[]{
                    nullToEmpty(metric.getClassName()),
                    str(metric.getStudentCount()),
                    str(metric.getMarkedCount()),
                    score(metric.getAvgScore()),
                    percent(metric.getPassRate()),
                    percent(metric.getExcellentRate())
            });
        }
        createTable(slide, headers, rows);
    }

    /** 高频错题表 */
    private void createWeakQuestionSlide(XMLSlideShow ppt, LearningStatisticsVO statistics) {
        List<LearningStatisticsVO.WeakQuestion> weakQuestions = statistics.getWeakQuestions();
        if (weakQuestions == null || weakQuestions.isEmpty()) {
            writeSingleBulletSlide(ppt, "三、高频错题讲评", "本次考试没有得分率低于 60% 的题目。");
            return;
        }

        XSLFSlide slide = createTitleOnlySlide(ppt, "三、高频错题讲评");
        String[] headers = {"题号", "题型", "满分", "平均分", "得分率", "低分人数"};
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(weakQuestions.size(), MAX_TABLE_ROWS); i++) {
            LearningStatisticsVO.WeakQuestion question = weakQuestions.get(i);
            rows.add(new String[]{
                    nullToEmpty(question.getQuestionNo()),
                    nullToEmpty(question.getQuestionType()),
                    score(question.getFullScore()),
                    score(question.getAvgScore()),
                    percent(question.getScoreRate()),
                    str(question.getWrongCount())
            });
        }
        createTable(slide, headers, rows);
    }

    /** 知识点得分率表 */
    private void createKnowledgeSlide(XMLSlideShow ppt, LearningStatisticsVO statistics) {
        List<LearningStatisticsVO.KnowledgeStat> knowledgeStats = statistics.getKnowledgeStats();
        if (knowledgeStats == null || knowledgeStats.isEmpty()) {
            writeSingleBulletSlide(ppt, "四、知识点薄弱分析",
                    "本次考试未绑定知识点，建议先在题目管理中维护知识点标签。");
            return;
        }

        XSLFSlide slide = createTitleOnlySlide(ppt, "四、知识点薄弱分析");
        String[] headers = {"知识点", "关联题数", "作答人次", "平均得分", "得分率"};
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < Math.min(knowledgeStats.size(), MAX_TABLE_ROWS); i++) {
            LearningStatisticsVO.KnowledgeStat knowledge = knowledgeStats.get(i);
            rows.add(new String[]{
                    nullToEmpty(knowledge.getKnowledgeName()),
                    str(knowledge.getQuestionCount()),
                    str(knowledge.getAnswerCount()),
                    score(knowledge.getAvgScore()),
                    percent(knowledge.getScoreRate())
            });
        }
        createTable(slide, headers, rows);
    }

    /** AI 讲评正文分页 */
    private void createAnalysisSlides(XMLSlideShow ppt, String analysisText) {
        List<String> lines = sanitizeAnalysisText(analysisText);
        if (lines.isEmpty()) {
            return;
        }
        int totalSlides = Math.min((lines.size() + LINES_PER_SLIDE - 1) / LINES_PER_SLIDE, MAX_ANALYSIS_SLIDES);
        for (int page = 0; page < totalSlides; page++) {
            int from = page * LINES_PER_SLIDE;
            int to = Math.min(from + LINES_PER_SLIDE, lines.size());
            XSLFSlide slide = createContentSlide(ppt, "五、AI 教学讲评建议（" + (page + 1) + "/" + totalSlides + "）");
            writeBullets(slide, lines.subList(from, to));
        }
    }

    // ==================== 版面与内容工具 ====================

    private XSLFSlideLayout layout(XMLSlideShow ppt, SlideLayout slideLayout) {
        XSLFSlideMaster master = ppt.getSlideMasters().get(0);
        return master.getLayout(slideLayout);
    }

    /** 标题 + 正文占位符版式 */
    private XSLFSlide createContentSlide(XMLSlideShow ppt, String title) {
        XSLFSlide slide = ppt.createSlide(layout(ppt, SlideLayout.TITLE_AND_CONTENT));
        setSlideTitle(slide, title);
        return slide;
    }

    /** 只有标题的版式，正文区域留给表格 */
    private XSLFSlide createTitleOnlySlide(XMLSlideShow ppt, String title) {
        XSLFSlide slide = ppt.createSlide(layout(ppt, SlideLayout.TITLE_ONLY));
        setSlideTitle(slide, title);
        return slide;
    }

    private void setSlideTitle(XSLFSlide slide, String title) {
        XSLFTextShape titleShape = slide.getPlaceholder(0);
        if (titleShape == null) {
            return;
        }
        XSLFTextRun run = titleShape.setText(title);
        run.setFontFamily(FONT_FAMILY);
        run.setFontSize(28d);
        run.setBold(true);
    }

    /** 向正文占位符写项目符号；占位符缺失时退化为文本框 */
    private void writeBullets(XSLFSlide slide, List<String> lines) {
        XSLFTextShape body = slide.getPlaceholder(1);
        if (body == null) {
            XSLFTextBox box = slide.createTextBox();
            box.setAnchor(new Rectangle(40, 100, 880, 380));
            body = box;
        } else {
            body.clearText();
        }

        for (String line : lines) {
            XSLFTextParagraph paragraph = body.addNewTextParagraph();
            paragraph.setBullet(true);
            paragraph.setIndentLevel(0);
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(line);
            run.setFontFamily(FONT_FAMILY);
            run.setFontSize(18d);
        }
    }

    private void writeSingleBulletSlide(XMLSlideShow ppt, String title, String line) {
        XSLFSlide slide = createContentSlide(ppt, title);
        writeBullets(slide, List.of(line));
    }

    /** 创建表格并填充；第一行加粗作为表头 */
    private void createTable(XSLFSlide slide, String[] headers, List<String[]> rows) {
        XSLFTable table = slide.createTable();
        XSLFTableRow headerRow = table.addRow();
        for (String header : headers) {
            fillCell(headerRow.addCell(), header, true);
        }
        for (String[] row : rows) {
            XSLFTableRow tableRow = table.addRow();
            for (int i = 0; i < headers.length; i++) {
                fillCell(tableRow.addCell(), i < row.length ? row[i] : "", false);
            }
        }

        table.setAnchor(new Rectangle(40, 105, 880, 380));
        double columnWidth = 880d / headers.length;
        for (int i = 0; i < headers.length; i++) {
            table.setColumnWidth(i, columnWidth);
        }
    }

    private void fillCell(XSLFTableCell cell, String text, boolean bold) {
        XSLFTextRun run = cell.setText(nullToEmpty(text));
        run.setFontFamily(FONT_FAMILY);
        run.setFontSize(14d);
        run.setBold(bold);
    }

    /**
     * 轻量清洗大模型输出的 Markdown：
     * 去掉标题井号、列表符号、加粗/行内代码标记，保留纯文本行；
     * 分隔线（---）与空行直接丢弃。
     */
    private List<String> sanitizeAnalysisText(String analysisText) {
        List<String> lines = new ArrayList<>();
        if (!StringUtils.hasText(analysisText)) {
            return lines;
        }
        for (String rawLine : analysisText.split("\\r?\\n")) {
            String line = rawLine
                    .replaceAll("^\\s*#{1,6}\\s*", "")
                    .replaceAll("^\\s*[-*+]\\s+", "")
                    .replaceAll("^\\s*\\d+[.、]\\s*", "")
                    .replace("**", "")
                    .replace("__", "")
                    .replace("`", "")
                    .trim();
            if (line.isEmpty() || line.matches("^[-=]{3,}$")) {
                continue;
            }
            // 单行过长时截断，避免 PPT 文本框溢出
            if (line.length() > 110) {
                line = line.substring(0, 110) + "…";
            }
            lines.add(line);
        }
        return lines;
    }

    // ==================== 格式化 ====================

    private String score(java.math.BigDecimal value) {
        if (value == null) {
            return EMPTY_VALUE;
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String percent(Double rate) {
        if (rate == null) {
            return EMPTY_VALUE;
        }
        return String.format(Locale.ROOT, "%.1f%%", rate * 100);
    }

    private String str(Integer value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
