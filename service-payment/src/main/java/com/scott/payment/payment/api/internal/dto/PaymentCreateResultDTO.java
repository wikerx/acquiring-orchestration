package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateResultDTO
 * @date : 2026-05-31 21:01
 * @email : scott_x@163.com
 * @description : service-payment 创建收单交易的内部响应参数，返回交易生命周期标识、当前交易动作单号和字典交易状态。
 * @status : create
 */
@Data
public class PaymentCreateResultDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 平台支付订单号，兼容现有 OpenAPI 响应；后续可逐步迁移为 transactionOrderNo。
     */
    private String paymentOrderNo;

    /**
     * 同一原始交易生命周期的主标识，后续正式表建议使用 transaction_order_no 字段承载。
     */
    private String transactionOrderNo;

    /**
     * 当前交易动作单号，后续正式表建议使用 transaction_no 字段承载。
     */
    private String transactionNo;

    /**
     * 商户订单号，原样返回给 OpenAPI 和商户。
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
     * 内部处理阶段，用于说明当前交易处于风控、路由、渠道请求或等待回调等节点。
     */
    private String processStage;

    /**
     * 失败原因码，仅当 status=FAILED 时返回。
     */
    private String failReasonCode;

    /**
     * 挂起原因码，仅当 status=PENDING 时返回。
     */
    private String pendingReasonCode;

    /**
     * 交易金额，单位为最小币种单位，例如 USD 分。
     */
    private Long amount;

    /**
     * 交易币种，使用 ISO 4217 三位大写币种代码。
     */
    private String currency;
}
