package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskEvaluationAuditMessage
 * @date : 2026-08-01 14:50
 * @email : scott_x@163.com
 * @description : 风控评估审计公共 MQ 消息，供 Risk 生产并由 service-data 幂等持久化
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RiskEvaluationAuditMessage extends BaseMqMessage {

    private static final long serialVersionUID = 1L;

    /** 风控评估流水号，也是数据库最终幂等键。 */
    private String riskRecordNo;

    /** 商户号，用于审计记录租户归属。 */
    private String merchantId;

    /** 商户订单号，用于商户侧关联查询。 */
    private String merchantOrderNo;

    /** 平台支付订单号，用于支付链路关联。 */
    private String paymentOrderNo;

    /** 被评估交易金额，单位为交易币种主单位。 */
    private BigDecimal transactionAmount;

    /** ISO 4217 交易币种代码。 */
    private String transactionCurrency;

    /** 聚合后的风险等级编码。 */
    private String riskLevel;

    /** 最终风控决策：ALLOW、BLOCK、REVIEW 或 CHALLENGE。 */
    private String decisionResult;

    /** 决策原因摘要，不包含原始敏感匹配值。 */
    private String decisionReason;

    /** 本次评估命中规则数量。 */
    private Integer hitCount;

    /** 风控评估完成时间，精度为毫秒。 */
    private LocalDateTime evaluationTime;

    /** 已脱敏的规则执行与命中明细。 */
    private List<RiskAuditHitMessage> hits = new ArrayList<>();

    /**
     * 获取风控审计数据库最终幂等键。
     *
     * @return 风控评估流水号
     */
    public String idempotentKey() {
        return riskRecordNo;
    }
}
