package com.scott.payment.payment.domain.reconciliation;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalStatusEnum
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 渠道勾兑异常案件状态枚举，约束案件领取、复查和关闭的可审计流转。
 * @status : create
 */
@Getter
public enum ChannelMatchAbnormalStatusEnum {

    /**
     * OPEN 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    OPEN("OPEN", false),
    /**
     * PROCESSING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    PROCESSING("PROCESSING", false),
    /**
     * RESOLVED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    RESOLVED("RESOLVED", true),
    /**
     * IGNORED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    IGNORED("IGNORED", true);

    private final String code;
    private final boolean terminal;

    ChannelMatchAbnormalStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
