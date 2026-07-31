package com.scott.payment.risk.mq.message;

import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.risk.domain.RiskListMatch;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 风控评估审计 MQ 消息。
 */
@Data
public class RiskEvaluationAuditMessage extends BaseMqMessage {

    private static final long serialVersionUID = 1L;

    /**
     * 风控评估流水号，也是审计消息的稳定幂等键。
     */
    private String riskRecordNo;

    /**
     * 商户号，用于审计记录租户归属。
     */
    private String merchantId;

    /**
     * 商户订单号，用于商户侧查询关联。
     */
    private String merchantOrderNo;

    /**
     * 平台支付订单号，用于支付链路审计关联。
     */
    private String paymentOrderNo;

    /**
     * 被评估交易金额，单位为交易币种主单位，保持 {@link BigDecimal} 精度。
     */
    private BigDecimal transactionAmount;

    /**
     * ISO 4217 交易币种代码。
     */
    private String transactionCurrency;

    /**
     * 聚合后的风险等级编码。
     */
    private String riskLevel;

    /**
     * 最终风控决策：ALLOW、BLOCK、REVIEW 或 CHALLENGE。
     */
    private String decisionResult;

    /**
     * 决策原因摘要，不包含原始卡号、IP 或其他敏感匹配值。
     */
    private String decisionReason;

    /**
     * 本次评估命中规则数量。
     */
    private Integer hitCount;

    /**
     * 风控评估完成时间，精度为毫秒。
     */
    private LocalDateTime evaluationTime;

    /**
     * 已脱敏的规则命中明细。
     */
    private List<RiskListMatch> hits = new ArrayList<>();

    /**
     * 返回审计消费的数据库幂等键。
     *
     * @return 风控评估流水号
     */
    public String idempotentKey() {
        return riskRecordNo;
    }
}
