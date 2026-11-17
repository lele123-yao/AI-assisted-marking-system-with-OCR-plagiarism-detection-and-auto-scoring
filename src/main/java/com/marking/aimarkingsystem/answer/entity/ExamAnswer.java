package com.marking.aimarkingsystem.answer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.marking.aimarkingsystem.answer.enums.AnswerSourceEnum;
import com.marking.aimarkingsystem.answer.enums.AnswerStatusEnum;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 答卷实体，对应表 exam_answer（线上作答与纸质扫描共用一张表）。
 *
 * 两种来源靠 answerSource 区分，取值见 {@link AnswerSourceEnum}：
 * - ONLINE：学生在系统内作答，答案存 answerContent；
 * - PAPER_SCAN：线下纸质作答，扫描件存 paperImgUrl（MinIO objectKey），OCR 文本存 ocrText。
 *
 * 字段约定：
 * - 考生身份三元组：studentId（系统账号，纸质扫描匹配不到时为空）、
 *   studentNo（学号，线上取登录账号、纸质取名单/OCR 结果）、studentName/studentClass（名单导入）；
 * - importBatchNo 记录名单导入批次，用于按批次撤销与问题追溯；
 * - aiReferenceScore 为 AI 辅助参考分，teacherFinalScore 为教师确认后的最终分，
 *   成绩相关统计一律以 teacherFinalScore 为准；
 * - markingStatus 为答卷状态，取值见 {@link AnswerStatusEnum}，流转由后续 Service 控制，
 *   本实体只承载数据；
 * - paperImgUrl 存 MinIO objectKey 而非完整 URL，展示时用 MinioUtil 换短时效签名地址。
 *
 * 对应的表结构扩展见模块3-2 阶段1 的 ALTER SQL。
 */
@Data
@TableName("exam_answer")
public class ExamAnswer implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 答卷来源：ONLINE 线上作答，PAPER_SCAN 纸质扫描，见 {@link AnswerSourceEnum} */
    private String answerSource;

    /** 试卷ID，关联 exam_paper.id */
    private Long paperId;

    /** 学生ID，关联 sys_user.id；纸质扫描未匹配到系统账号时为空 */
    private Long studentId;

    /** 学号：线上取登录账号，纸质取名单导入或OCR识别结果 */
    private String studentNo;

    /** 考生姓名：名单导入或OCR识别后回填，线上仅作快照 */
    private String studentName;

    /** 考生班级：名单导入时写入，用于按班统计与学情分析 */
    private String studentClass;

    /** 名单导入批次号：形如 20261112-143000-01，用于溯源与按批次撤销 */
    private String importBatchNo;

    /** 线上作答内容（按题号组织的JSON），纸质扫描为空 */
    private String answerContent;

    /** OCR识别出的答卷全文，仅纸质扫描有值 */
    private String ocrText;

    /** 答卷/试卷扫描图片在MinIO中的对象键（非完整URL），仅纸质扫描有值 */
    private String paperImgUrl;

    /** AI参考分：仅作辅助，不能直接作为最终成绩 */
    private BigDecimal aiReferenceScore;

    /** 教师最终得分：教师确认/修改后写入，成绩以此为准 */
    private BigDecimal teacherFinalScore;

    /** 答卷状态，取值见 {@link AnswerStatusEnum} */
    private Integer markingStatus;

    /** 阅卷失败原因（OCR识别失败 / AI调用失败），供重试与排查 */
    private String markingError;

    /** 提交/收卷时间 */
    private LocalDateTime submitTime;

    /** 阅卷完成时间（AI出分或教师确认的时间点） */
    private LocalDateTime markTime;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入与更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0 未删除，1 已删除 */
    @TableLogic
    private Integer delFlag;
}
