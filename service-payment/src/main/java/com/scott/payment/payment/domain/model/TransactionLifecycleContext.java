package com.scott.payment.payment.domain.model;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionLifecycleContext
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单交易生命周期上下文，位于 service-payment 领域模型层，用于关联同一原始交易下授权、请款、退款、拒付等不同交易动作。
 * @status : create
 */
@Data
public class TransactionLifecycleContext implements Serializable {

    /**
     * 序列化版本号，用于服务内对象复制或测试场景兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 同一原始交易生命周期的主标识，后续正式表建议使用 transaction_order_no 字段承载。
     */
    private String transactionOrderNo;

    /**
     * 当前交易动作单号，后续正式表建议使用 transaction_no 字段承载。
     */
    private String transactionNo;

    /**
     * 原交易动作单号，例如请款关联授权、退款关联请款或支付。
     */
    private String originalTransactionNo;

    /**
     * 父交易动作单号，用于表达更细粒度的链式关系。
     */
    private String parentTransactionNo;

    /**
     * 商户侧订单号，用于商户维度查询和创建类幂等。
     */
    private String merchantOrderNo;

    /**
     * 商户侧交易唯一号，用于同一商户请求维度幂等和链路追踪。
     */
    private String merchantTransactionId;
}
