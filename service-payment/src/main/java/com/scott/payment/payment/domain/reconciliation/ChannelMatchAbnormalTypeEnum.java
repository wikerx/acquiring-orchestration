package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * 渠道勾兑异常类型枚举，用稳定编码区分查询身份缺失、长期未知和平台渠道结果差异。
 */
@Getter
public enum ChannelMatchAbnormalTypeEnum {

    /**
     * 原渠道、原 MID 或渠道订单号缺失，无法安全发起主动查询。
     */
    QUERY_IDENTITY_MISSING("QUERY_IDENTITY_MISSING"),
    /**
     * 渠道查询超时、不可用或返回无法识别的状态，平台继续保留非终态。
     */
    QUERY_RESULT_UNKNOWN("QUERY_RESULT_UNKNOWN"),
    /**
     * 渠道权威状态与平台已记录状态不一致。
     */
    STATUS_MISMATCH("STATUS_MISMATCH"),
    /**
     * 渠道返回币种与原动作上送渠道的交易币种不一致。
     */
    CURRENCY_MISMATCH("CURRENCY_MISMATCH"),
    /**
     * 渠道返回金额与原动作上送渠道的交易金额不一致。
     */
    AMOUNT_MISMATCH("AMOUNT_MISMATCH");

    private final String code;

    /**
     * 创建勾兑异常类型。
     *
     * @param code 持久化和接口使用的稳定异常编码
     */
    ChannelMatchAbnormalTypeEnum(String code) {
        this.code = code;
    }
}
