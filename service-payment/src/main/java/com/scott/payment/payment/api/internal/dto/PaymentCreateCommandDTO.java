package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCreateCommandDTO
 * @date : 2026-05-31 21:00
 * @email : scott_x@163.com
 * @description : service-openapi 调用 service-payment 创建收单交易的内部请求参数
 * @status : create
 */
@Data
public class PaymentCreateCommandDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 支付平台颁发的商户号，用于定位商户、通道、风控和费率配置。
     */
    private String merchantId;

    /**
     * 商户订单号，商户侧保持唯一，用于幂等和交易查询。
     */
    private String merchantOrderNo;

    /**
     * 商户请求唯一标识，通常来自 JWT jti 或交易 transactionId，用于链路追踪和幂等。
     */
    private String requestId;

    /**
     * 订单金额，主币种单位，例如 123.45 USD。
     */
    private BigDecimal amount;

    /**
     * 订单币种，使用 ISO 4217 三位大写币种代码。
     */
    private String currency;

    /**
     * 交易请求时间，按 UTC+8 业务时区写入。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 请求体安全摘要，OpenAPI 层传入用于排查，但不保存完整密文和敏感卡信息。
     */
    private String requestFingerprint;
}
