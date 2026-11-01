package com.scott.payment.payment.service.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付核心汇率快照 DTO，位于 service-payment 服务 DTO 层，用于把业务汇率表中的最终汇率带入交易落库。
 */
@Data
public class PaymentExchangeRateDTO implements Serializable {

    /**
     * 序列化版本号，用于服务内对象兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 汇率记录主键。
     */
    private Long rateId;

    /**
     * 原始汇率记录主键，人工业务汇率允许为空。
     */
    private Long rawRateId;

    /**
     * 汇率规则主键，人工业务汇率允许为空。
     */
    private Long ruleId;

    /**
     * 汇率源编码。
     */
    private String sourceCode;

    /**
     * 源币种。
     */
    private String baseCurrency;

    /**
     * 目标币种。
     */
    private String quoteCurrency;

    /**
     * 最终交易汇率。
     */
    private BigDecimal finalRate;

    /**
     * 汇率调整说明，用于运营审计还原最终汇率来源。
     */
    private String adjustDescription;

    /**
     * 汇率生效时间。
     */
    private LocalDateTime effectiveTime;
}
