package com.scott.payment.channel.payout.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutChannelStatus
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : Provider 原始状态映射后的统一代付状态，不等同于 service-payout 平台状态机状态。
 * @status : create
 */
@Getter
public enum PayoutChannelStatus {

    /**
     * SUCCESS 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    SUCCESS("SUCCESS"),
    /**
     * FAILED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    FAILED("FAILED"),
    /**
     * PENDING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    PENDING("PENDING"),
    /**
     * PROCESSING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    PROCESSING("PROCESSING"),
    /**
     * RETURNED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    RETURNED("RETURNED");

    private final String code;

    PayoutChannelStatus(String code) {
        this.code = code;
    }
}
