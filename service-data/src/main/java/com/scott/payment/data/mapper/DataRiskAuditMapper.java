package com.scott.payment.data.mapper;

import com.scott.payment.component.mq.message.RiskAuditHitMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataRiskAuditMapper
 * @date : 2026-08-01 14:50
 * @email : scott_x@163.com
 * @description : 风控评估主审计与脱敏命中明细数据访问入口，不参与实时风控规则计算
 * @status : create
 */
public interface DataRiskAuditMapper {

    /**
     * 按风控评估流水号查询已经提交的主记录数量。
     *
     * @param riskRecordNo 风控评估流水号
     * @return 0 或 1
     */
    @Select("""
            SELECT COUNT(1)
            FROM risk_evaluation_record
            WHERE risk_record_no = #{riskRecordNo}
            """)
    int countByRiskRecordNo(@Param("riskRecordNo") String riskRecordNo);

    /**
     * 插入风控评估主审计记录。
     *
     * @return 成功插入的记录数
     */
    @Insert("""
            INSERT INTO risk_evaluation_record (
                risk_record_no, merchant_id, merchant_order_no, payment_order_no,
                transaction_amount, transaction_currency, risk_level, decision_result,
                decision_reason, hit_count, evaluation_time
            ) VALUES (
                #{riskRecordNo}, #{merchantId}, #{merchantOrderNo}, #{paymentOrderNo},
                #{transactionAmount}, #{transactionCurrency}, #{riskLevel}, #{decisionResult},
                #{decisionReason}, #{hitCount}, #{evaluationTime}
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
                               @Param("decisionReason") String decisionReason,
                               @Param("hitCount") Integer hitCount,
                               @Param("evaluationTime") LocalDateTime evaluationTime);

    /**
     * 插入一条已脱敏的规则执行或命中明细。
     *
     * @param riskRecordNo 风控评估流水号
     * @param hit          已脱敏 MQ 命中明细
     * @param decisionTime 规则决策时间
     * @return 成功插入的记录数
     */
    @Insert("""
            INSERT INTO risk_evaluation_hit_detail (
                risk_record_no, module_type, function_code, function_name, rule_id,
                hit_element, hit_value_masked, risk_level, decision_result,
                decision_reason, decision_time, time_window_seconds, threshold_count,
                elements_json, current_count, stage_code, stage_name, stage_order,
                match_result, decision_effect
            ) VALUES (
                #{riskRecordNo}, #{hit.moduleType}, #{hit.functionCode}, #{hit.functionName}, #{hit.ruleId},
                #{hit.hitElement}, #{hit.hitValueMasked}, #{hit.riskLevel}, #{hit.decisionAction},
                #{hit.decisionReason}, #{decisionTime}, #{hit.timeWindowSeconds}, #{hit.thresholdCount},
                #{hit.elementsJson}, #{hit.currentCount}, #{hit.stageCode}, #{hit.stageName}, #{hit.stageOrder},
                #{hit.matchResult}, #{hit.decisionEffect}
            )
            """)
    int insertEvaluationHit(@Param("riskRecordNo") String riskRecordNo,
                            @Param("hit") RiskAuditHitMessage hit,
                            @Param("decisionTime") LocalDateTime decisionTime);
}
