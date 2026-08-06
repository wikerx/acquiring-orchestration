package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalTypeEnum
 * @date : 2026-08-06 00:00
 * @description : 渠道勾兑异常类型枚举，用稳定编码区分查询身份缺失、长期未知和平台渠道结果差异。
 * @status : create
 */
@Getter
public enum ChannelMatchAbnormalTypeEnum {

    QUERY_IDENTITY_MISSING("QUERY_IDENTITY_MISSING"),
    QUERY_RESULT_UNKNOWN("QUERY_RESULT_UNKNOWN"),
    STATUS_MISMATCH("STATUS_MISMATCH"),
    CURRENCY_MISMATCH("CURRENCY_MISMATCH"),
    AMOUNT_MISMATCH("AMOUNT_MISMATCH");

    private final String code;

    ChannelMatchAbnormalTypeEnum(String code) {
        this.code = code;
    }
}
