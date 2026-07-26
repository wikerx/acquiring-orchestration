package com.scott.payment.channel.payment.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelTradeStatus
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道统一交易状态枚举，位于 payment-channel-library 枚举层，用于将渠道原始状态映射为平台可理解的渠道结果，不直接更新平台 transaction_status。
 * @status : create
 */
@Getter
public enum ChannelTradeStatus {

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
     * NEED REDIRECT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    NEED_REDIRECT("NEED_REDIRECT");

    /**
     * code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String code;

    ChannelTradeStatus(String code) {
        this.code = code;
    }
}
