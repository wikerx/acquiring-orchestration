package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionStatusEnum
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 支付交易状态枚举，位于 支付核心服务，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
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
     * 编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final String code;

    /**
     * {@code terminal}，用于明确 支付交易状态枚举 当前业务分支是否成立。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
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
