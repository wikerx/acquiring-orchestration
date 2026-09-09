package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchDetectSourceEnum
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 勾兑异常发现来源枚举，区分自动查询、回调、状态流转和人工复查。
 * @status : create
 */
@Getter
public enum ChannelMatchDetectSourceEnum {

    /**
     * AUTO QUERY 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AUTO_QUERY("AUTO_QUERY"),
    /**
     * CALLBACK 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CALLBACK("CALLBACK"),
    /**
     * STATUS TRANSITION 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    STATUS_TRANSITION("STATUS_TRANSITION"),
    /**
     * MANUAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    MANUAL("MANUAL");

    private final String code;

    ChannelMatchDetectSourceEnum(String code) {
        this.code = code;
    }
}
