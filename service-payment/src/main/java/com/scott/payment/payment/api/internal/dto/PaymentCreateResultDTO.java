package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateResultDTO
 * @date : 2026-05-31 21:01
 * @email : scott_x@163.com
 * @description : service-payment 创建收单交易的内部响应参数
 * @status : create
 */
@Data
public class PaymentCreateResultDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 平台支付订单号，service-payment 生成并作为系统内交易主标识。
     */
    private String paymentOrderNo;

    /**
     * 商户订单号，原样返回给 OpenAPI 和商户。
     */
    private String merchantOrderNo;

    /**
     * 交易状态，当前模拟流程默认返回 RECEIVED，表示支付服务已接收。
     */
    private String status;

    /**
     * 交易金额，单位为最小币种单位，例如 USD 分。
     */
    private Long amount;

    /**
     * 交易币种，使用 ISO 4217 三位大写币种代码。
     */
    private String currency;
}
