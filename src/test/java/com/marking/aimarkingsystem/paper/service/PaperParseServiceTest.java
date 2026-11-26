package com.marking.aimarkingsystem.paper.service;

import cn.hutool.json.JSONUtil;
import com.marking.aimarkingsystem.paper.dto.PageParseStatus;
import com.marking.aimarkingsystem.paper.dto.PaperParseResult;
import com.marking.aimarkingsystem.paper.dto.PaperParseTask;
import com.marking.aimarkingsystem.paper.dto.ParsedQuestionDTO;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperParseServiceTest {

    private final PaperParseService service = new PaperParseService(null, null, null, null);

    @Test
    void recognizesBrokenInlineChoiceMarkersWithoutGuessingShortAnswer() {
        String text = """
                19:19
                5G
                随堂练习
                答题卡
                答题计时
                (0/15)
                1.「单选题1支持向量机的基本思想是什么？
                2.[单选题在硬间隔条件下，支持向量机试图最大化什么？
                11621814571
                """;

        List<ParsedQuestionDTO> questions = service.ruleSplit(text);

        assertEquals(2, questions.size());
        assertEquals(List.of("1", "2"), questions.stream().map(ParsedQuestionDTO::getQuestionNo).toList());
        assertTrue(questions.stream().allMatch(question -> question.getQuestionType() == 1));
        assertTrue(questions.get(0).getQuestionContent().startsWith("支持向量机"));
        assertFalse(questions.get(1).getQuestionContent().contains("11621814571"));
    }

    @Test
    void readsChoiceTypeAndSingleQuestionScoreFromInlineOrSeparateLines() {
        String text = """
                1.单选题(1分)
                下列哪一项属于监督学习？
                A. 分类
                B. 聚类
                答题卡
                2.单选题
                1分
                下列哪一项属于分类算法？
                A. 决策树
                B. K均值
                下一题
                """;

        List<ParsedQuestionDTO> questions = service.ruleSplit(text);

        assertEquals(2, questions.size());
        assertTrue(questions.stream().allMatch(question -> question.getQuestionType() == 1));
        assertTrue(questions.stream().allMatch(question -> question.getScore().compareTo(new BigDecimal("1.00")) == 0));
        assertFalse(questions.get(0).getQuestionContent().contains("单选题"));
        assertFalse(questions.get(0).getQuestionContent().contains("(1分)"));
        assertFalse(questions.get(0).getQuestionContent().contains("答题卡"));
        assertFalse(questions.get(1).getQuestionContent().contains("下一题"));
    }

    @Test
    void keepsSectionTypesScoresAndOriginalMathQuestionNumbers() {
        String text = """
                高等数学试卷
                一、单选题（本题共2小题，每小题5分）
                1. 下列函数中连续的是哪一个？
                A. f(x)=x
                B. f(x)=1/x
                2. 下列极限存在的是哪一个？
                二、多选题（每小题6分）
                3. 以下哪些函数是初等函数？
                三、解答题
                15. 已知函数 f(x)=x²，求其导数。
                16. 计算定积分的值。
                17. 求曲线的切线方程。
                18. 讨论函数的单调性。
                19. 证明该数列收敛。
                """;

        List<ParsedQuestionDTO> questions = service.ruleSplit(text);

        assertEquals(8, questions.size());
        assertEquals(List.of("1", "2", "3", "15", "16", "17", "18", "19"),
                questions.stream().map(ParsedQuestionDTO::getQuestionNo).toList());
        assertEquals(1, questions.get(0).getQuestionType());
        assertEquals(2, questions.get(2).getQuestionType());
        assertEquals(5, questions.get(3).getQuestionType());
        assertEquals(0, questions.get(0).getScore().compareTo(new BigDecimal("5.00")));
        assertEquals(0, questions.get(2).getScore().compareTo(new BigDecimal("6.00")));
    }

    @Test
    void infersChoiceFromOptionsAndMarksUnknownTypeForManualReview() {
        String text = """
                1. 下列哪项描述正确？
                A. 第一项
                B. 第二项
                2. 记录某次实验过程及其数据。
                """;

        List<ParsedQuestionDTO> questions = service.ruleSplit(text);

        assertEquals(1, questions.get(0).getQuestionType());
        assertEquals("单选题", questions.get(0).getSectionName());
        assertEquals("题型待确认", questions.get(1).getSectionName());
    }

    @Test
    void mergesRepeatedPagesAndKeepsTheMoreCompleteOverlappingQuestion() {
        String repeatedPage = """
                1.[单选题]下列哪项是线性模型？
                A. 线性回归
                B. 决策树
                2.简答题
                简述模型评估的作用。
                """;
        assertEquals(2, service.ruleSplit(repeatedPage + "\n" + repeatedPage).size());

        String firstPage = """
                15.解答题
                已知函数 f(x)=x²+2x。
                （1）求函数的导数。
                """;
        String overlappingPage = """
                15.解答题
                已知函数 f(x)=x²+2x。
                （1）求函数的导数。
                （2）求函数在 x=1 处的切线。
                16.解答题
                计算给定定积分的值。
                """;
        List<ParsedQuestionDTO> questions = service.ruleSplit(firstPage + "\n" + overlappingPage);

        assertEquals(2, questions.size());
        assertEquals("15", questions.get(0).getQuestionNo());
        assertTrue(questions.get(0).getQuestionContent().contains("（2）求函数"));
    }

    @Test
    void retainsSameNumberedButDifferentQuestionsInDifferentSections() {
        String text = """
                一、单选题
                1. 下列哪项正确？
                A. 甲
                B. 乙
                二、填空题
                1. 水的化学式是____。
                """;

        List<ParsedQuestionDTO> questions = service.ruleSplit(text);

        assertEquals(2, questions.size());
        assertEquals(List.of("1", "1"), questions.stream().map(ParsedQuestionDTO::getQuestionNo).toList());
        assertEquals(1, questions.get(0).getQuestionType());
        assertEquals(4, questions.get(1).getQuestionType());
    }

    @Test
    void removesOnlyIndependentNoiseLinesAndProtectsMathText() {
        String text = """
                1.简答题
                计算并说明 E=mc^2 中各符号的意义。
                E=12345678
                123456
                11621814571
                5G
                """;

        ParsedQuestionDTO question = service.ruleSplit(text).get(0);

        assertTrue(question.getQuestionContent().contains("E=mc^2"));
        assertTrue(question.getQuestionContent().contains("E=12345678"));
        assertTrue(question.getQuestionContent().contains("123456"));
        assertFalse(question.getQuestionContent().contains("11621814571"));
        assertFalse(question.getQuestionContent().contains("5G"));
    }

    @Test
    void removesObviousWatermarkFragmentsWithoutDroppingRealOptions() {
        String text = """
                1.单选题(1分)
                该指令属于下列哪一项？
                预防措施    先乐瑶24251
                纠正措施
                瑶242511162
                1457
                21814571
                资源平衡    瑶24251116218
                410
                """;

        ParsedQuestionDTO question = service.ruleSplit(text).get(0);

        assertTrue(question.getQuestionContent().contains("预防措施"));
        assertTrue(question.getQuestionContent().contains("纠正措施"));
        assertTrue(question.getQuestionContent().contains("资源平衡"));
        assertFalse(question.getQuestionContent().contains("瑶"));
        assertFalse(question.getQuestionContent().contains("24251"));
        assertFalse(question.getQuestionContent().contains("21814571"));
        assertFalse(question.getQuestionContent().contains("1457"));
        assertFalse(question.getQuestionContent().contains("410"));
    }

    @Test
    void ruleParsingIsNotTruncatedAtTwentyThousandCharacters() {
        StringBuilder text = new StringBuilder("一、简答题\n");
        for (int i = 1; i <= 25; i++) {
            text.append(i).append(". 简述第").append(i).append("题的材料：")
                    .append("实验数据".repeat(250)).append('\n');
        }

        List<ParsedQuestionDTO> questions = service.ruleSplit(text.toString());

        assertTrue(text.length() > 20_000);
        assertEquals(25, questions.size());
        assertEquals("25", questions.get(24).getQuestionNo());
        assertTrue(questions.get(24).getQuestionContent().contains("简述第25题"));
    }

    @Test
    void rejectsContentThatDoesNotMatchItsExtension() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        byte[] pdf = "%PDF-1.7".getBytes(StandardCharsets.US_ASCII);

        assertTrue(PaperParseService.matchesSignature(png, png.length, "png"));
        assertTrue(PaperParseService.matchesSignature(jpeg, jpeg.length, "jpg"));
        assertTrue(PaperParseService.matchesSignature(pdf, pdf.length, "pdf"));
        // 把压缩包/可执行文件改名成图片会被拦下
        assertFalse(PaperParseService.matchesSignature(png, png.length, "jpg"));
        assertFalse(PaperParseService.matchesSignature("PK\u0003\u0004".getBytes(StandardCharsets.US_ASCII), 4, "png"));
    }

    @Test
    void perceptualHashSeesRecompressedScreenshotAsTheSamePage() throws Exception {
        BufferedImage image = new BufferedImage(90, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 90, 80);
        graphics.setColor(Color.BLACK);
        graphics.fillRect(10, 10, 35, 30);
        graphics.setColor(new Color(30, 90, 200));
        graphics.fillRect(50, 40, 30, 30);
        graphics.dispose();

        byte[] png = writeImage(image, "png", 1.0f);
        byte[] jpeg = writeImage(image, "jpg", 0.95f);

        long pngHash = service.perceptualHash(png);
        long jpegHash = service.perceptualHash(jpeg);

        assertNotEquals(0L, pngHash);
        assertEquals(0, PaperParseService.hammingDistance(pngHash, service.perceptualHash(png)));
        assertTrue(PaperParseService.hammingDistance(pngHash, jpegHash) <= 6,
                "同一张图重新压缩后应判定为近重复");

        BufferedImage different = new BufferedImage(90, 80, BufferedImage.TYPE_INT_RGB);
        Graphics2D other = different.createGraphics();
        other.setColor(Color.WHITE);
        other.fillRect(0, 0, 90, 80);
        other.setColor(Color.BLACK);
        other.fillRect(45, 5, 35, 30);
        other.dispose();
        assertTrue(PaperParseService.hammingDistance(pngHash,
                service.perceptualHash(writeImage(different, "png", 1.0f))) > 0);
    }

    @Test
    void taskJsonRoundTripKeepsNestedParseResult() {
        PageParseStatus page = new PageParseStatus("page-1.jpg", "SUCCESS", 12, "已识别");
        ParsedQuestionDTO question = new ParsedQuestionDTO();
        question.setQuestionNo("1");
        question.setQuestionType(1);
        question.setScore(new BigDecimal("2.00"));
        PaperParseResult result = new PaperParseResult();
        result.setPages(List.of(page));
        result.setQuestions(List.of(question));
        result.setWarnings(List.of("提示"));

        PaperParseTask task = new PaperParseTask();
        task.setTaskId("task-1");
        task.setStatus("SUCCESS");
        task.setOwner("teacher");
        task.setTotal(1);
        task.setDone(1);
        task.setResult(result);

        PaperParseTask restored = JSONUtil.toBean(JSONUtil.toJsonStr(task), PaperParseTask.class);

        assertEquals("SUCCESS", restored.getStatus());
        assertEquals(1, restored.getDone());
        assertEquals(1, restored.getResult().getQuestions().size());
        assertEquals("SUCCESS", restored.getResult().getPages().get(0).getStatus());
        assertEquals(0, restored.getResult().getQuestions().get(0).getScore().compareTo(new BigDecimal("2.00")));
    }

    private static byte[] writeImage(BufferedImage image, String format, float quality) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        byte[] bytes = out.toByteArray();
        return bytes;
    }
}
