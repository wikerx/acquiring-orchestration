package com.scott.payment.openapi.client.payout.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * OpenAPI 发送给代付核心的创建命令，承载商户订单号、金额、币种和收款方信息。
 */
@Data
public class PayoutCreateClientRequestDTO implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 商户代付单号。
     */
    private String merchantOrderNo;

    /**
     * 代付金额。
     */
    private BigDecimal amount;

    /**
     * 币种。
     */
    private String currency;

    /**
     * 业务时间。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime transactionDateTime;

    /**
     * OpenAPI 收到的密文请求体指纹。
     */
    private String requestFingerprint;
}
