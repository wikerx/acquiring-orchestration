package com.scott.payment.openapi.client.payment.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantApiResponseLogUpdateClientRequestDTO
 * @date : 2026-07-16 16:45
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 响应日志回写客户端请求，位于 service-openapi 客户端 DTO 层，只携带密文掩码和摘要，不携带完整密文。
 * @status : create
 */
@Data
public class TransactionMerchantApiResponseLogUpdateClientRequestDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 平台当前交易唯一标识。
     */
    private String transactionId;

    /**
     * 商户请求唯一号，通常等于 orderInfo.orderId。
     */
    private String requestId;

    /**
     * 平台响应商户的脱敏明文 JSON，可为空。
     */
    private String responsePlainJsonMasked;

    /**
     * 平台响应密文 SHA-256 摘要。
     */
    private String responseCipherDigest;

    /**
     * 平台响应密文掩码，禁止保存完整密文。
     */
    private String responseCipherMasked;

    /**
     * 响应加密完成时间。
     */
    private LocalDateTime responseTime;
}
