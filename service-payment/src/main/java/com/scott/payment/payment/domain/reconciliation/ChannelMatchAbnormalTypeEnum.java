package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalTypeEnum
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 渠道勾兑异常类型枚举，用稳定编码区分查询身份缺失、长期未知和平台渠道结果差异。
 * @status : create
 */
@Getter
public enum ChannelMatchAbnormalTypeEnum {

    /**
     * QUERY IDENTITY MISSING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    QUERY_IDENTITY_MISSING("QUERY_IDENTITY_MISSING"),
    /**
     * QUERY RESULT UNKNOWN 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    QUERY_RESULT_UNKNOWN("QUERY_RESULT_UNKNOWN"),
    /**
     * STATUS MISMATCH 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    STATUS_MISMATCH("STATUS_MISMATCH"),
    /**
     * CURRENCY MISMATCH 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CURRENCY_MISMATCH("CURRENCY_MISMATCH"),
    /**
     * AMOUNT MISMATCH 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AMOUNT_MISMATCH("AMOUNT_MISMATCH");

    private final String code;

    ChannelMatchAbnormalTypeEnum(String code) {
        this.code = code;
    }
}
