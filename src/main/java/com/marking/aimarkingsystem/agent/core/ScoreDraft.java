package com.marking.aimarkingsystem.agent.core;

import java.math.BigDecimal;

/**
 * 评分草稿：主 Agent 的初评结果，也是 Checker 的校验对象。
 * 单独抽成公共 record，避免 Service 内部类无法被 Checker 复用。
 */
public record ScoreDraft(BigDecimal score, String comment) {
}
