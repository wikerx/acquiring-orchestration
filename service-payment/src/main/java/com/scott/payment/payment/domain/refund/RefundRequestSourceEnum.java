package com.scott.payment.payment.domain.refund;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundRequestSourceEnum
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 退款来源枚举，位于 支付核心服务，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
 * @status : create
 */
@Getter
public enum RefundRequestSourceEnum {

    /**
     * OPENAPI 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    OPENAPI("OPENAPI", "API_CLIENT"),
    /**
     * ADMIN PORTAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    ADMIN_PORTAL("ADMIN_PORTAL", "ADMIN"),
    /**
     * MERCHANT PORTAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    MERCHANT_PORTAL("MERCHANT_PORTAL", "MERCHANT"),
    /**
     * SYSTEM 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    SYSTEM("SYSTEM", "SYSTEM"),
    /**
     * LEGACY UNKNOWN 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
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
