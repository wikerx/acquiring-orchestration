package com.scott.payment.payment.client.risk.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskThreeDsPolicyClientRequestDTO
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : service-payment 调用 service-risk 的路由后 3DS 策略只读请求，不携带 PAN、CVV 或渠道凭据。
 * @status : create
 */
@Data
public class RiskThreeDsPolicyClientRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 支付平台商户号。 */
    private String merchantId;
    /** 已完成路由的渠道编码。 */
    private String channelCode;
    /** 平台统一支付方式。 */
    private String paymentMethod;
    /** 卡品牌；未知时允许为空。 */
    private String cardBrand;
    /** 交易金额，单位为 {@link #currency} 的主币种单位。 */
    private BigDecimal amount;
    /** ISO 4217 三位币种代码。 */
    private String currency;
    /** 路由前评估得到的最高风险等级；当前无值时按 LOW 匹配。 */
    private String currentRiskLevel;
}
