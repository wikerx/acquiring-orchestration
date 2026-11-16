package com.scott.payment.data.mapper;

import com.scott.payment.component.mq.message.RiskAuditHitMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 风控评估主审计与脱敏命中明细数据访问入口，不参与实时风控规则计算。
 */
public interface DataRiskAuditMapper {

    /**
     * 按风控评估流水号和定位表记录的评估时间查询已经提交的主记录数量。
     *
     * @param riskRecordNo 风控评估流水号
     * @param evaluationTime 风控评估时间，也是季度分片键
     * @return 0 或 1
     */
    @Select("""
            SELECT COUNT(1)
            FROM risk_evaluation_record
            WHERE risk_record_no = #{riskRecordNo}
              AND evaluation_time = #{evaluationTime}
            """)
    int countByRiskRecordNo(@Param("riskRecordNo") String riskRecordNo,
                            @Param("evaluationTime") LocalDateTime evaluationTime);

    /**
     * 查询风控记录号对应的稳定季度定位时间。
     *
     * @param riskRecordNo 风控评估流水号
     * @return 已提交定位记录的评估时间，不存在时返回 null
     */
    @Select("""
            SELECT evaluation_time
            FROM risk_evaluation_locator
            WHERE risk_record_no = #{riskRecordNo}
            LIMIT 1
            """)
    LocalDateTime selectEvaluationTime(@Param("riskRecordNo") String riskRecordNo);

    /**
     * 插入非分片风控定位记录，唯一键承担跨季度数据库最终幂等。
     *
     * @param riskRecordNo 风控评估流水号
     * @param evaluationTime 风控评估原始业务时间
     * @return 成功插入的记录数
     */
    @Insert("""
            INSERT INTO risk_evaluation_locator (risk_record_no, evaluation_time)
            VALUES (#{riskRecordNo}, #{evaluationTime})
            """)
    int insertEvaluationLocator(@Param("riskRecordNo") String riskRecordNo,
                                @Param("evaluationTime") LocalDateTime evaluationTime);

    /**
     * 插入风控评估主审计记录。
     *
     * @param riskRecordNo 风控评估流水号
     * @param merchantId 商户号
     * @param merchantOrderNo 商户订单号
     * @param paymentOrderNo 平台支付订单号
     * @param transactionAmount 商户交易金额
     * @param transactionCurrency 商户交易币种
     * @param riskLevel 评估风险等级
     * @param decisionResult 风控决策结果
     * @param decisionReasonCode 决策原因码
     * @param decisionReason 已脱敏决策原因
     * @param hitCount 命中规则数量
     * @param evaluationTime 风控评估时间
     * @return 成功插入的记录数
     */
    @Insert("""
            INSERT INTO risk_evaluation_record (
                risk_record_no, merchant_id, merchant_order_no, payment_order_no,
                transaction_amount, transaction_currency, risk_level, decision_result,
                decision_reason_code, decision_reason, hit_count, evaluation_time
            ) VALUES (
                #{riskRecordNo}, #{merchantId}, #{merchantOrderNo}, #{paymentOrderNo},
                #{transactionAmount}, #{transactionCurrency}, #{riskLevel}, #{decisionResult},
                #{decisionReasonCode}, #{decisionReason}, #{hitCount}, #{evaluationTime}
            )
            """)
    int insertEvaluationRecord(@Param("riskRecordNo") String riskRecordNo,
                               @Param("merchantId") String merchantId,
                               @Param("merchantOrderNo") String merchantOrderNo,
                               @Param("paymentOrderNo") String paymentOrderNo,
                               @Param("transactionAmount") BigDecimal transactionAmount,
                               @Param("transactionCurrency") String transactionCurrency,
                               @Param("riskLevel") String riskLevel,
                               @Param("decisionResult") String decisionResult,
                               @Param("decisionReasonCode") String decisionReasonCode,
                               @Param("decisionReason") String decisionReason,
                               @Param("hitCount") Integer hitCount,
                               @Param("evaluationTime") LocalDateTime evaluationTime);

    /**
     * 插入一条已脱敏的规则执行或命中明细。
     *
     * @param riskRecordNo 风控评估流水号
     * @param hit          已脱敏 MQ 命中明细
     * @param evaluationTime 风控评估时间，也是季度分片键
     * @param decisionTime 规则决策时间
     * @return 成功插入的记录数
     */
    @Insert("""
            INSERT INTO risk_evaluation_hit_detail (
                risk_record_no, evaluation_time, module_type, function_code, function_name, rule_id,
                hit_element, hit_value_masked, risk_level, decision_result,
                reason_code, decision_reason, decision_time, time_window_seconds, threshold_count,
                elements_json, current_count, stage_code, stage_name, stage_order,
                match_result, decision_effect
            ) VALUES (
                #{riskRecordNo}, #{evaluationTime}, #{hit.moduleType}, #{hit.functionCode}, #{hit.functionName}, #{hit.ruleId},
                #{hit.hitElement}, #{hit.hitValueMasked}, #{hit.riskLevel}, #{hit.decisionAction},
                #{hit.reasonCode}, #{hit.decisionReason}, #{decisionTime}, #{hit.timeWindowSeconds}, #{hit.thresholdCount},
                #{hit.elementsJson}, #{hit.currentCount}, #{hit.stageCode}, #{hit.stageName}, #{hit.stageOrder},
                #{hit.matchResult}, #{hit.decisionEffect}
            )
            """)
    int insertEvaluationHit(@Param("riskRecordNo") String riskRecordNo,
                            @Param("hit") RiskAuditHitMessage hit,
                            @Param("evaluationTime") LocalDateTime evaluationTime,
                            @Param("decisionTime") LocalDateTime decisionTime);
}
