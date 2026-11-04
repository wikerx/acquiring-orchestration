package com.scott.payment.data.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户 OpenAPI 响应审计持久化投影。
 *
 * <p>该对象只映射精确定位交易分片和校验响应事实所需字段，不承载支付订单状态，
 * 也不允许保存完整响应密文、PAN、CVV、JWT 或密钥。</p>
 */
@Data
public class DataMerchantApiResponseAuditDO {

    /**
     * 平台当前交易唯一标识。
     */
    private String transactionId;

    /**
     * 当前交易真实分片时间。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 商户请求唯一号，可为空。
     */
    private String requestId;

    /**
     * 返回商户的脱敏明文 JSON。
     */
    private String responsePlainJsonMasked;

    /**
     * 完整响应密文的 SHA-256 摘要。
     */
    private String responseCipherDigest;

    /**
     * 响应密文短掩码。
     */
    private String responseCipherMasked;

    /**
     * OpenAPI 完成响应加密的时间。
     */
    private LocalDateTime responseTime;
}
