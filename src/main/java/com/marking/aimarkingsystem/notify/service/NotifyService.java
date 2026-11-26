package com.marking.aimarkingsystem.notify.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marking.aimarkingsystem.clazz.support.AccountSupport;
import com.marking.aimarkingsystem.common.result.PageResult;
import com.marking.aimarkingsystem.course.entity.Course;
import com.marking.aimarkingsystem.course.mapper.CourseMapper;
import com.marking.aimarkingsystem.notify.entity.NotifyMessage;
import com.marking.aimarkingsystem.notify.mapper.NotifyMessageMapper;
import com.marking.aimarkingsystem.notify.mq.MarkNotifyEvent;
import com.marking.aimarkingsystem.notify.mq.MarkNotifyPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 站内通知服务。
 *
 * 两条职责：
 * 1. 写入侧：阅卷完成后解析接收人、落库站内信，再广播事件（先持久化后推送，
 *    保证用户离线/刷新后仍能查到）；
 * 2. 读取侧：当前登录用户的分页、未读数、标记已读，所有查询都强制带 user_id，
 *    避免通过 id 越权读取或修改他人消息。
 */
@Service
public class NotifyService {

    /** 消息类型：阅卷完成 */
    private static final String TYPE_MARK_COMPLETED = "MARK_COMPLETED";
    /** 业务类型：答卷 */
    private static final String BIZ_TYPE_EXAM_ANSWER = "EXAM_ANSWER";

    private final NotifyMessageMapper notifyMessageMapper;
    private final CourseMapper courseMapper;
    private final AccountSupport accountSupport;
    private final MarkNotifyPublisher notifyPublisher;

    public NotifyService(NotifyMessageMapper notifyMessageMapper,
                         CourseMapper courseMapper,
                         AccountSupport accountSupport,
                         MarkNotifyPublisher notifyPublisher) {
        this.notifyMessageMapper = notifyMessageMapper;
        this.courseMapper = courseMapper;
        this.accountSupport = accountSupport;
        this.notifyPublisher = notifyPublisher;
    }

    // ==================== 写入侧 ====================

    /**
     * 阅卷完成后通知学生与任课教师。
     *
     * 接收人：
     * - 答卷关联的学生账号（纸质扫描未匹配到账号时为空，只通知教师）；
     * - 试卷所属课程的任课教师（course.teacherId）。
     * 用 LinkedHashSet 去重，防止教师自己也是答卷学生时收到两条。
     *
     * 本方法不抛异常给调用方做业务决策：调用方（MQ 消费者）会用 try/catch 包住，
     * 通知失败不能影响阅卷结果与消息 ack。
     */
    public void notifyMarkCompleted(Long answerId, Long paperId, String paperTitle,
                                    Long studentId, Long courseId,
                                    BigDecimal score, boolean needsReview) {
        Set<Long> receiverIds = new LinkedHashSet<>();
        if (studentId != null) {
            receiverIds.add(studentId);
        }
        if (courseId != null) {
            Course course = courseMapper.selectById(courseId);
            if (course != null && course.getTeacherId() != null) {
                receiverIds.add(course.getTeacherId());
            }
        }
        if (receiverIds.isEmpty()) {
            return;
        }

        String title = "阅卷完成";
        String content = buildContent(paperTitle, score, needsReview);
        LocalDateTime now = LocalDateTime.now();

        // 每个接收人一行：已读状态互相独立，且能直接走 (user_id, is_read, create_time) 索引
        for (Long userId : receiverIds) {
            NotifyMessage message = new NotifyMessage();
            message.setUserId(userId);
            message.setType(TYPE_MARK_COMPLETED);
            message.setTitle(title);
            message.setContent(content);
            message.setBizType(BIZ_TYPE_EXAM_ANSWER);
            message.setBizId(answerId);
            message.setIsRead(0);
            notifyMessageMapper.insert(message);
        }

        // 落库成功后再广播：即使 MQ 不可用，站内信也已可查
        MarkNotifyEvent event = new MarkNotifyEvent();
        event.setEventId(IdUtil.fastSimpleUUID());
        event.setType(TYPE_MARK_COMPLETED);
        event.setTitle(title);
        event.setContent(content);
        event.setBizType(BIZ_TYPE_EXAM_ANSWER);
        event.setBizId(answerId);
        event.setPaperId(paperId);
        event.setScore(score);
        event.setNeedsReview(needsReview);
        event.setReceiverIds(new ArrayList<>(receiverIds));
        event.setOccurredAt(now.toString());
        notifyPublisher.publish(event);
    }

    /** 组装正文，控制长度在 512 字符以内（表字段上限） */
    private String buildContent(String paperTitle, BigDecimal score, boolean needsReview) {
        String titleText = StringUtils.hasText(paperTitle) ? paperTitle : "试卷";
        String scoreText = score == null
                ? "-"
                : score.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String content = "试卷《" + titleText + "》的答卷已完成 AI 阅卷，参考分 " + scoreText + " 分";
        if (needsReview) {
            content = content + "（存在疑点，请教师复核）";
        }
        return content.length() > 512 ? content.substring(0, 512) : content;
    }

    // ==================== 读取侧（全部限定当前登录用户） ====================

    /** 当前用户的消息分页，按时间倒序 */
    public PageResult<NotifyMessage> page(Long current, Long size) {
        Long userId = accountSupport.requireCurrentUserId();
        Page<NotifyMessage> page = new Page<>(current, size);
        IPage<NotifyMessage> result = notifyMessageMapper.selectPage(page,
                Wrappers.<NotifyMessage>lambdaQuery()
                        .eq(NotifyMessage::getUserId, userId)
                        .orderByDesc(NotifyMessage::getCreateTime)
                        .orderByDesc(NotifyMessage::getId));
        return PageResult.of(result, result.getRecords());
    }

    /** 当前用户未读数 */
    public long unreadCount() {
        Long userId = accountSupport.requireCurrentUserId();
        return notifyMessageMapper.selectCount(Wrappers.<NotifyMessage>lambdaQuery()
                .eq(NotifyMessage::getUserId, userId)
                .eq(NotifyMessage::getIsRead, 0));
    }

    /**
     * 标记单条已读。
     * 条件里带 user_id：即使传入别人的消息 id 也不会被修改，天然防越权。
     */
    public void markRead(Long id) {
        if (id == null) {
            return;
        }
        Long userId = accountSupport.requireCurrentUserId();
        notifyMessageMapper.update(null, Wrappers.<NotifyMessage>lambdaUpdate()
                .eq(NotifyMessage::getId, id)
                .eq(NotifyMessage::getUserId, userId)
                .eq(NotifyMessage::getIsRead, 0)
                .set(NotifyMessage::getIsRead, 1)
                .set(NotifyMessage::getReadTime, LocalDateTime.now()));
    }

    /** 当前用户全部未读置为已读 */
    public void markAllRead() {
        Long userId = accountSupport.requireCurrentUserId();
        notifyMessageMapper.update(null, Wrappers.<NotifyMessage>lambdaUpdate()
                .eq(NotifyMessage::getUserId, userId)
                .eq(NotifyMessage::getIsRead, 0)
                .set(NotifyMessage::getIsRead, 1)
                .set(NotifyMessage::getReadTime, LocalDateTime.now()));
    }
}
