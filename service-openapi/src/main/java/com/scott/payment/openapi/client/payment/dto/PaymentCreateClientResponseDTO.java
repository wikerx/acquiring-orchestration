package com.scott.payment.openapi.client.payment.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateClientResponseDTO
 * @date : 2026-05-31 21:11
 * @email : scott_x@163.com
 * @description : service-payment 创建收单交易的内部响应参数
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateClientResponseDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIPayment Create Client Response 数据传输对象，位于 service-openapi 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class PaymentCreateClientResponseDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 平台支付订单号。
     */
    private String paymentOrderNo;

    /**
     * 商户订单号。
     */
    private String merchantOrderNo;

    /**
     * 交易状态。
     */
    private String status;

    /**
     * 交易金额，最小币种单位。
     */
    private Long amount;

    /**
     * 交易币种。
     */
    private String currency;
}
