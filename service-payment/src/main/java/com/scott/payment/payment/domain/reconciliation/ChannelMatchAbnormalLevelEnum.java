package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * 渠道勾兑异常级别枚举，用于管理端分流案件，不参与交易状态推导。
 */
@Getter
public enum ChannelMatchAbnormalLevelEnum {

    /**
     * 可由后续查询继续确认的普通异常，不代表资金事实已经错误。
     */
    WARNING("WARNING"),
    /**
     * 渠道与平台状态存在明显差异，需要优先人工复查。
     */
    HIGH("HIGH"),
    /**
     * 币种或金额等资金事实不一致，需要立即阻断自动处置并人工核对。
     */
    CRITICAL("CRITICAL");

    private final String code;

    /**
     * 创建勾兑异常级别。
     *
     * @param code 持久化和接口使用的稳定级别编码
     */
    ChannelMatchAbnormalLevelEnum(String code) {
        this.code = code;
    }
}
