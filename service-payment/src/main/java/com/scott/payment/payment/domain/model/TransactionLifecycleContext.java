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
     * 平台内部生命周期关联标识，落库字段为 operation_id，不返回商户。
     */
    private String operationId;

    /**
     * 平台当前交易唯一标识，落库字段为 transaction_id，每个交易动作不同。
     */
    private String transactionId;

    /**
     * 原平台交易 ID，例如请款关联授权、退款关联请款或支付。
     */
    private String sourceTransactionId;

    /**
     * 商户侧订单号，用于商户维度查询和创建类幂等。
     */
    private String merchantOrderNo;

    /**
     * 商户本次 API 请求唯一标识，来自 orderInfo.orderId。
     */
    private String merchantOrderId;
}
