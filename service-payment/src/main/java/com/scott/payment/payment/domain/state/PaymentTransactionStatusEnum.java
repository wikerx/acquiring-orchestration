package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionStatusEnum
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 收单交易状态枚举，位于 service-payment 领域状态层，对齐字典 transaction_status，仅表达交易结果状态；风控、路由、渠道请求等过程节点应使用处理阶段枚举承载。
 * @status : create
 */
@Getter
public enum PaymentTransactionStatusEnum {

    /**
     * 交易已成功。
     */
    SUCCESS("SUCCESS", true),

    /**
     * 交易已失败，失败原因由失败原因码进一步区分。
     */
    FAILED("FAILED", true),

    /**
     * 交易等待外部动作或异步结果，例如 3DS 跳转、渠道异步回调、拒付处理中。
     */
    PENDING("PENDING", false),

    /**
     * 交易处理中，例如风控、路由、渠道请求、回调处理。
     */
    PROCESSING("PROCESSING", false);

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
     * terminal，用于保存 Payment Transaction Status Enum 中与 terminal 相关的业务属性。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final boolean terminal;

    /**
     * 创建收单支付交易状态。
     *
     * @param code 对外和内部接口传递的状态编码
     */
    PaymentTransactionStatusEnum(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }
}
