package com.scott.payment.channel.payment.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelCode
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道编码枚举，位于 payment-channel-library 枚举层，用于收敛平台内置渠道编码，后台渠道配置仍以数据库 channel_code 为准。
 * @status : create
 */
@Getter
public enum PaymentChannelCode {

    /**
     * Mastercard Payment Gateway Services。
     */
    MPGS("MPGS"),

    /**
     * WorldPay Gateway XML 独立渠道编码；与 WPGJSON 分别配置渠道、MID 和协议实现。
     */
    WPGXML("WPGXML"),

    /**
     * WorldPay Gateway JSON 独立渠道编码；与 WPGXML 分别配置渠道、MID 和协议实现。
     */
    WPGJSON("WPGJSON");

    /**
     * code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String code;

    PaymentChannelCode(String code) {
        this.code = code;
    }
}
