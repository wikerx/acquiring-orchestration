package com.scott.payment.payment.domain.refund;

import lombok.Getter;

/**
 * 退款请求来源枚举，用于确定申请人类型并保留可审计的入口信息。
 */
@Getter
public enum RefundRequestSourceEnum {

    /**
     * 商户通过 OpenAPI 发起，申请人类型记录为 API 客户端。
     */
    OPENAPI("OPENAPI", "API_CLIENT"),
    /**
     * 平台管理人员通过管理后台发起。
     */
    ADMIN_PORTAL("ADMIN_PORTAL", "ADMIN"),
    /**
     * 商户操作人通过商户后台发起。
     */
    MERCHANT_PORTAL("MERCHANT_PORTAL", "MERCHANT"),
    /**
     * 支付系统根据受控规则自动发起。
     */
    SYSTEM("SYSTEM", "SYSTEM"),
    /**
     * 历史数据或旧调用方没有可靠来源，保留未知而不伪造申请人类型。
     */
    LEGACY_UNKNOWN("LEGACY_UNKNOWN", null);

    private final String code;
    private final String applicantType;

    /**
     * 创建退款来源与对应申请人类型映射。
     *
     * @param code 持久化和接口使用的稳定来源编码
     * @param applicantType 审批审计使用的申请人类型，历史未知来源允许为空
     */
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
