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
     * code 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final String code;

    PaymentChannelCode(String code) {
        this.code = code;
    }
}
