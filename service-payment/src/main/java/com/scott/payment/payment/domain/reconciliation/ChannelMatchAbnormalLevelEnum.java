package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalLevelEnum
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 渠道勾兑异常级别枚举，用于管理端分流案件，不参与交易状态推导。
 * @status : create
 */
@Getter
public enum ChannelMatchAbnormalLevelEnum {

    /**
     * WARNING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    WARNING("WARNING"),
    /**
     * HIGH 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    HIGH("HIGH"),
    /**
     * CRITICAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CRITICAL("CRITICAL");

    private final String code;

    ChannelMatchAbnormalLevelEnum(String code) {
        this.code = code;
    }
}
