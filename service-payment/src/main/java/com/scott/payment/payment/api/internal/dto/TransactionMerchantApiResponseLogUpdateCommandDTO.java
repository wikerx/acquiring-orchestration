package com.scott.payment.payment.api.internal.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantApiResponseLogUpdateCommandDTO
 * @date : 2026-07-16 16:40
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 响应日志回写命令，位于 service-payment 内部接口 DTO 层，仅保存响应密文掩码和摘要，禁止保存完整响应密文。
 * @status : create
 */
@Data
public class TransactionMerchantApiResponseLogUpdateCommandDTO implements Serializable {

    /**
     * 序列化版本号，用于服务间 JSON 传输兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 平台当前交易唯一标识，用于定位商户 API 交互日志所在分表和记录。
     */
    private String transactionId;

    /**
     * 当前交易真实分片时间，用于精确更新商户 API 交互日志。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime transactionDateTime;

    /**
     * 商户本次 API 请求唯一标识，通常等于 orderInfo.orderId，用于并发重复请求时缩小更新范围。
     */
    private String requestId;

    /**
     * 平台返回给商户的脱敏明文 JSON。通常已由支付核心预写，切面回写时可为空。
     */
    private String responsePlainJsonMasked;

    /**
     * 平台响应密文摘要，使用 SHA-256 指纹，不保存完整密文。
     */
    private String responseCipherDigest;

    /**
     * 平台响应密文掩码，只保留首尾短片段用于人工核验。
     */
    private String responseCipherMasked;

    /**
     * 响应加密完成时间。
     */
    private LocalDateTime responseTime;
}
