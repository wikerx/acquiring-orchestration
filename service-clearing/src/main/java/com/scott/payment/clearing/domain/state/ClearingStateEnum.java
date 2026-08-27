package com.scott.payment.clearing.domain.state;

import java.util.EnumSet;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingStateEnum
 * @date : 2026-08-26 08:28
 * @email : scott_x@163.com
 * @description : 动作级清分权威状态机；CLEARED和NOT_REQUIRED终态不可逆，人工复核只允许显式人工重试。
 * @status : create
 */
public enum ClearingStateEnum {
    /** 尚未接收可处理的终态事件。 */
    NOT_CLEARED,
    /** 已接收终态事件并等待领取。 */
    PENDING,
    /** 已由一个实例持有有期限的处理租约。 */
    PROCESSING,
    /** 源支付、请款、结算费用结果或保证金事实尚未完成。 */
    WAITING_SOURCE,
    /** 可重试技术或数据可见性失败。 */
    FAILED,
    /** 配置冲突、金额风险或重试耗尽，需人工处理。 */
    MANUAL_REVIEW,
    /** 已形成当前修订全部交易与保证金清分事实。 */
    CLEARED,
    /** 经规则确认本动作不产生任何清分事实。 */
    NOT_REQUIRED;

    /**
     * 判断给定来源是否允许把当前状态推进到目标状态。
     *
     * @param target 目标状态
     * @param origin 自动消费或人工重试来源
     * @return 该状态流转是否符合清分权威状态机
     */
    public boolean canTransitionTo(ClearingStateEnum target, ClearingTransitionOrigin origin) {
        if (target == null || origin == null || this == target) {
            return false;
        }
        if (this == MANUAL_REVIEW) {
            return origin == ClearingTransitionOrigin.MANUAL_RETRY && target == PROCESSING;
        }
        return automaticTargets().contains(target);
    }

    /** @return 是否为不可逆清分完成状态 */
    public boolean isCompletedTerminal() {
        return this == CLEARED || this == NOT_REQUIRED;
    }

    private Set<ClearingStateEnum> automaticTargets() {
        return switch (this) {
            case NOT_CLEARED -> EnumSet.of(PENDING);
            case PENDING, WAITING_SOURCE, FAILED -> EnumSet.of(PROCESSING);
            case PROCESSING -> EnumSet.of(CLEARED, NOT_REQUIRED, WAITING_SOURCE, FAILED, MANUAL_REVIEW);
            case MANUAL_REVIEW, CLEARED, NOT_REQUIRED -> EnumSet.noneOf(ClearingStateEnum.class);
        };
    }
}
