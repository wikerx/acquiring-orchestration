package com.scott.payment.risk.api.internal.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskThreeDsPolicyRequestDTO
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 路由后 3DS 策略只读评估请求，必须携带已选渠道，不执行累计限额或频控预占。
 * @status : create
 */
@Data
public class RiskThreeDsPolicyRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前支付平台商户号。 */
    @NotBlank(message = "merchantId is required")
    private String merchantId;

    /** 已完成路由的渠道编码，例如 MPGS。 */
    @NotBlank(message = "channelCode is required")
    private String channelCode;

    /** 平台统一支付方式，例如 BANK_CARD。 */
    private String paymentMethod;

    /** 卡品牌，例如 VISA；未知时允许为空并按 ALL 维度匹配。 */
    private String cardBrand;

    /** 交易金额，单位为 {@link #currency} 的主币种单位。 */
    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than 0")
    private BigDecimal amount;

    /** ISO 4217 三位币种代码。 */
    @NotBlank(message = "currency is required")
    private String currency;

    /** 路由前评估得到的最高风险等级；为空时按最低风险等级匹配。 */
    private String currentRiskLevel;
}
