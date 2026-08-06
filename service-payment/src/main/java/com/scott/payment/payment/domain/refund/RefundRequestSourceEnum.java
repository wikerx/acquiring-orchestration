package com.scott.payment.payment.domain.refund;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundRequestSourceEnum
 * @date : 2026-08-06 00:00
 * @description : 退款请求来源枚举，位于支付退款领域，用于稳定区分 OpenAPI、管理后台、商户后台和系统任务入口。
 * @status : create
 */
@Getter
public enum RefundRequestSourceEnum {

    OPENAPI("OPENAPI", "API_CLIENT"),
    ADMIN_PORTAL("ADMIN_PORTAL", "ADMIN"),
    MERCHANT_PORTAL("MERCHANT_PORTAL", "MERCHANT"),
    SYSTEM("SYSTEM", "SYSTEM"),
    LEGACY_UNKNOWN("LEGACY_UNKNOWN", null);

    private final String code;
    private final String applicantType;

    RefundRequestSourceEnum(String code, String applicantType) {
        this.code = code;
        this.applicantType = applicantType;
    }

    /**
     * 将不受信任的入口字符串归一为受控来源，未知值按历史来源处理。
     *
     * @param value 入口传入的来源编码
     * @return 受控来源枚举
     */
    public static RefundRequestSourceEnum from(String value) {
        if (value != null) {
            for (RefundRequestSourceEnum source : values()) {
                if (source.code.equalsIgnoreCase(value.trim())) {
                    return source;
                }
            }
        }
        return LEGACY_UNKNOWN;
    }
}
