package com.scott.payment.risk.api.internal.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskThreeDsPolicyResultDTO
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 路由后 3DS 策略只读评估结果，只表达认证策略，不改变支付交易状态。
 * @status : create
 */
@Data
public class RiskThreeDsPolicyResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否必须完成渠道 3DS 认证后才允许提交支付或授权。 */
    private boolean required;

    /** 命中的策略动作：FORCE_3DS、SKIP_3DS 或 NONE。 */
    private String action;

    /** 命中的风控规则主键；未命中时为空。 */
    private Long ruleId;

    /** 内部可读的规则原因；不得直接作为付款人提示。 */
    private String reason;
}
