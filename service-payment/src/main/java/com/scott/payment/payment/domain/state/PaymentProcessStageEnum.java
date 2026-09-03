package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentProcessStageEnum
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 支付processstage枚举，位于 支付核心服务，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
 * @status : create
 */
@Getter
public enum PaymentProcessStageEnum {

    /**
     * 交易已被 payment 服务受理。
     */
    ACCEPTED("ACCEPTED"),

    /**
     * 正在执行路由前风控。
     */
    RISK_CHECKING("RISK_CHECKING"),

    /**
     * 正在选择渠道和 MID。
     */
    ROUTING("ROUTING"),

    /**
     * 正在调用渠道。
     */
    CHANNEL_REQUESTING("CHANNEL_REQUESTING"),

    /**
     * 渠道已受理，等待同步或异步结果。
     */
    CHANNEL_PROCESSING("CHANNEL_PROCESSING"),

    /**
     * 等待付款人完成 3DS 或渠道跳转动作。
     */
    WAITING_3DS("WAITING_3DS"),

    /**
     * 等待风控人工复核。
     */
    WAITING_RISK_REVIEW("WAITING_RISK_REVIEW"),

    /**
     * 退款申请等待运营审批，尚未向渠道发起请求。
     */
    WAITING_APPROVAL("WAITING_APPROVAL"),

    /**
     * 退款审批已通过，等待 MQ 消费者抢占并执行固定渠道请求。
     */
    WAITING_EXECUTION("WAITING_EXECUTION"),

    /**
     * 等待渠道异步回调。
     */
    WAITING_CALLBACK("WAITING_CALLBACK"),

    /**
     * 当前交易动作处理完成。
     */
    FINISHED("FINISHED");

    /**
     * 编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final String code;

    /**
     * 创建内部处理阶段。
     *
     * @param code 内部处理阶段编码
     */
    PaymentProcessStageEnum(String code) {
        this.code = code;
    }
}
