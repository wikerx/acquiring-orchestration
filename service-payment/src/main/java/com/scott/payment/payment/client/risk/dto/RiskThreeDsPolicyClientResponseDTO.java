package com.scott.payment.payment.client.risk.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskThreeDsPolicyClientResponseDTO
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : service-risk 路由后 3DS 策略响应的支付侧客户端模型，不直接改变交易状态。
 * @status : create
 */
@Data
public class RiskThreeDsPolicyClientResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否必须完成渠道 3DS 认证。 */
    private boolean required;
    /** FORCE_3DS、SKIP_3DS 或 NONE。 */
    private String action;
    /** 命中规则主键；未命中时为空。 */
    private Long ruleId;
    /** 内部规则原因；不得直接展示给付款人。 */
    private String reason;
}
