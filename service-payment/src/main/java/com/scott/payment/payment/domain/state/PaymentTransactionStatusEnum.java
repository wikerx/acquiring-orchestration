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
     * code 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final String code;

    /**
     * terminal 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：布尔值；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
