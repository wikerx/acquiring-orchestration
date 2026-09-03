package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentFailureReasonEnum
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 支付失败原因枚举，位于 支付核心服务，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
 * @status : create
 */
@Getter
public enum PaymentFailureReasonEnum {

    /**
     * 风控拒绝交易。
     */
    RISK_REJECTED("RISK_REJECTED"),

    /**
     * 渠道路由失败。
     */
    ROUTE_FAILED("ROUTE_FAILED"),

    /**
     * 渠道不支持当前交易能力。
     */
    CHANNEL_UNSUPPORTED("CHANNEL_UNSUPPORTED"),

    /**
     * 渠道不支持标签币种且系统交易汇率不存在。
     */
    EXCHANGE_RATE_NOT_FOUND("EXCHANGE_RATE_NOT_FOUND"),

    /**
     * 渠道请求失败。
     */
    CHANNEL_REQUEST_FAILED("CHANNEL_REQUEST_FAILED"),

    /**
     * 渠道响应解析失败。
     */
    CHANNEL_RESPONSE_INVALID("CHANNEL_RESPONSE_INVALID"),

    /**
     * 渠道请求超时。
     */
    CHANNEL_TIMEOUT("CHANNEL_TIMEOUT"),

    /**
     * 交易状态流转不允许。
     */
    STATE_TRANSITION_DENIED("STATE_TRANSITION_DENIED");

    /**
     * 编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final String code;

    /**
     * 创建失败原因。
     *
     * @param code 失败原因编码
     */
    PaymentFailureReasonEnum(String code) {
        this.code = code;
    }
}
