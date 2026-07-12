package com.scott.payment.openapi.client.payment.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateClientResponseDTO
 * @date : 2026-05-31 21:11
 * @email : scott_x@163.com
 * @description : service-payment 创建收单交易的内部响应参数，返回交易生命周期标识、当前交易动作单号和字典交易状态。
 * @status : create
 */
@Data
public class PaymentCreateClientResponseDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 平台支付订单号，兼容现有 OpenAPI 响应；后续可逐步迁移为 transactionOrderNo。
     */
    private String paymentOrderNo;

    /**
     * 同一原始交易生命周期的主标识。
     */
    private String transactionOrderNo;

    /**
     * 当前交易动作单号。
     */
    private String transactionNo;

    /**
     * 商户订单号。
     */
    private String merchantOrderNo;

    /**
     * 交易类型，对齐字典 transaction_type。
     */
    private String transactionType;

    /**
     * 交易状态，对齐字典 transaction_status。
     */
    private String status;

    /**
     * 内部处理阶段。
     */
    private String processStage;

    /**
     * 失败原因码。
     */
    private String failReasonCode;

    /**
     * 挂起原因码。
     */
    private String pendingReasonCode;

    /**
     * 交易金额，最小币种单位。
     */
    private Long amount;

    /**
     * 交易币种。
     */
    private String currency;
}
