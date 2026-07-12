package com.scott.payment.payment.service.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRiskDecisionDTO
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单交易风控决策 DTO，位于 service-payment 服务 DTO 层，用于承载实时风控返回的通过、拒绝、复核或跳过结果。
 * @status : create
 */
@Data
public class PaymentRiskDecisionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 风控评估流水号，用于审计和人工复核关联。
     */
    private String riskRecordNo;

    /**
     * 是否允许交易继续进入路由和渠道调用。
     */
    private boolean passed;

    /**
     * 风控决策编码，例如 PASS、REJECT、REVIEW、SKIP。
     */
    private String decision;

    /**
     * 风控原因码。
     */
    private String riskCode;

    /**
     * 风控原因描述，返回商户前需按产品规则转换。
     */
    private String riskMessage;

    /**
     * 默认通过决策。
     *
     * @return 风控通过结果
     */
    public static PaymentRiskDecisionDTO pass() {
        PaymentRiskDecisionDTO dto = new PaymentRiskDecisionDTO();
        dto.setPassed(true);
        dto.setDecision("PASS");
        return dto;
    }

    /**
     * 默认跳过决策，用于本地骨架或远程风控未开启场景。
     *
     * @return 风控跳过结果
     */
    public static PaymentRiskDecisionDTO skip() {
        PaymentRiskDecisionDTO dto = new PaymentRiskDecisionDTO();
        dto.setPassed(true);
        dto.setDecision("SKIP");
        dto.setRiskCode("RISK_SERVICE_BYPASSED");
        dto.setRiskMessage("risk service is bypassed by local skeleton configuration");
        return dto;
    }
}
