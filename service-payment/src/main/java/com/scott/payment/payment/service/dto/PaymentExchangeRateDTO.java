package com.scott.payment.payment.service.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentExchangeRateDTO
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : 支付核心汇率快照 DTO，位于 service-payment 服务 DTO 层，用于把业务汇率表中的最终汇率带入交易落库。
 * @status : create
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
     * 汇率生效时间。
     */
    private LocalDateTime effectiveTime;
}
