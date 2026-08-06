package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentProcessStageEnum
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单交易处理阶段枚举，位于 service-payment 领域状态层，用于记录风控、路由、渠道请求等内部过程节点，不写入 transaction_status 字典状态。
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
     * code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
