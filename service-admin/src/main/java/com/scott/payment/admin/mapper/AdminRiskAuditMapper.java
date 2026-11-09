package com.scott.payment.admin.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理端风控审计数据访问入口，统一通过 riskAudit 逻辑数据源查询季度分片和非分片定位表。
 *
 * <p>Mapper 只接收服务端解析出的评估时间，不允许客户端指定物理表或季度后缀。</p>
 */
public interface AdminRiskAuditMapper {

    /**
     * 根据风控记录号查询稳定的评估时间，用于精确路由主记录和执行明细所在季度。
     *
     * @param riskRecordNo 风控记录号
     * @return 原始评估时间；定位记录不存在时返回 {@code null}
     */
    @Select("""
            SELECT evaluation_time
            FROM risk_evaluation_locator
            WHERE risk_record_no = #{riskRecordNo}
            LIMIT 1
            """)
    LocalDateTime selectEvaluationTime(@Param("riskRecordNo") String riskRecordNo);

    /**
     * 查询风控评估主记录总数。
     *
     * @param riskRecordNo 风控记录号，允许为空
     * @param merchantId 商户号，允许为空
     * @param merchantOrderNo 商户订单号，允许为空
     * @param paymentOrderNo 系统订单号，允许为空
     * @param decisionResult 决策结果，允许为空
     * @param riskLevel 风险等级，允许为空
     * @param evaluationTime 精确定位时间，精确查询时必填
     * @param evaluationStartTime 评估时间起点（包含）
     * @param evaluationEndTimeExclusive 评估时间终点（不包含）
     * @return 满足条件的记录数量
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM risk_evaluation_record
            WHERE 1 = 1
            <if test="riskRecordNo != null and riskRecordNo != ''">AND risk_record_no = #{riskRecordNo}</if>
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="merchantOrderNo != null and merchantOrderNo != ''">AND merchant_order_no = #{merchantOrderNo}</if>
            <if test="paymentOrderNo != null and paymentOrderNo != ''">AND payment_order_no = #{paymentOrderNo}</if>
            <if test="decisionResult != null and decisionResult != ''">AND decision_result = #{decisionResult}</if>
            <if test="riskLevel != null and riskLevel != ''">AND risk_level = #{riskLevel}</if>
            <if test="evaluationTime != null">AND evaluation_time = #{evaluationTime}</if>
            <if test="evaluationTime == null and evaluationStartTime != null">AND evaluation_time &gt;= #{evaluationStartTime}</if>
            <if test="evaluationTime == null and evaluationEndTimeExclusive != null">AND evaluation_time &lt; #{evaluationEndTimeExclusive}</if>
            </script>
            """)
    long countEvaluations(@Param("riskRecordNo") String riskRecordNo,
                          @Param("merchantId") String merchantId,
                          @Param("merchantOrderNo") String merchantOrderNo,
                          @Param("paymentOrderNo") String paymentOrderNo,
                          @Param("decisionResult") String decisionResult,
                          @Param("riskLevel") String riskLevel,
                          @Param("evaluationTime") LocalDateTime evaluationTime,
                          @Param("evaluationStartTime") LocalDateTime evaluationStartTime,
                          @Param("evaluationEndTimeExclusive") LocalDateTime evaluationEndTimeExclusive);

    /**
     * 分页查询风控评估主记录。
     *
     * @param riskRecordNo 风控记录号，允许为空
     * @param merchantId 商户号，允许为空
     * @param merchantOrderNo 商户订单号，允许为空
     * @param paymentOrderNo 系统订单号，允许为空
     * @param decisionResult 决策结果，允许为空
     * @param riskLevel 风险等级，允许为空
     * @param evaluationTime 精确定位时间，精确查询时必填
     * @param evaluationStartTime 评估时间起点（包含）
     * @param evaluationEndTimeExclusive 评估时间终点（不包含）
     * @param offset 分页偏移量
     * @param pageSize 每页记录数
     * @return 当前页风控评估记录
     */
    @Select("""
            <script>
            SELECT id,
                   risk_record_no,
                   merchant_id,
                   merchant_name,
                   merchant_order_no,
                   payment_order_no,
                   transaction_amount,
                   transaction_currency,
                   risk_level,
                   decision_result,
                   decision_reason,
                   hit_count,
                   evaluation_time
            FROM risk_evaluation_record
            WHERE 1 = 1
            <if test="riskRecordNo != null and riskRecordNo != ''">AND risk_record_no = #{riskRecordNo}</if>
            <if test="merchantId != null and merchantId != ''">AND merchant_id = #{merchantId}</if>
            <if test="merchantOrderNo != null and merchantOrderNo != ''">AND merchant_order_no = #{merchantOrderNo}</if>
            <if test="paymentOrderNo != null and paymentOrderNo != ''">AND payment_order_no = #{paymentOrderNo}</if>
            <if test="decisionResult != null and decisionResult != ''">AND decision_result = #{decisionResult}</if>
            <if test="riskLevel != null and riskLevel != ''">AND risk_level = #{riskLevel}</if>
            <if test="evaluationTime != null">AND evaluation_time = #{evaluationTime}</if>
            <if test="evaluationTime == null and evaluationStartTime != null">AND evaluation_time &gt;= #{evaluationStartTime}</if>
            <if test="evaluationTime == null and evaluationEndTimeExclusive != null">AND evaluation_time &lt; #{evaluationEndTimeExclusive}</if>
            ORDER BY evaluation_time DESC, id DESC
            LIMIT #{offset}, #{pageSize}
            </script>
            """)
    List<Map<String, Object>> selectEvaluations(@Param("riskRecordNo") String riskRecordNo,
                                                @Param("merchantId") String merchantId,
                                                @Param("merchantOrderNo") String merchantOrderNo,
                                                @Param("paymentOrderNo") String paymentOrderNo,
                                                @Param("decisionResult") String decisionResult,
                                                @Param("riskLevel") String riskLevel,
                                                @Param("evaluationTime") LocalDateTime evaluationTime,
                                                @Param("evaluationStartTime") LocalDateTime evaluationStartTime,
                                                @Param("evaluationEndTimeExclusive") LocalDateTime evaluationEndTimeExclusive,
                                                @Param("offset") long offset,
                                                @Param("pageSize") long pageSize);

    /**
     * 查询单笔风控评估的全部执行明细。
     *
     * @param riskRecordNo 风控记录号
     * @param evaluationTime 定位表解析出的评估时间
     * @return 按执行阶段和决策时间排序的明细
     */
    @Select("""
            SELECT id,
                   risk_record_no,
                   module_type,
                   function_code,
                   function_name,
                   rule_id,
                   hit_element,
                   hit_value_masked,
                   risk_level,
                   decision_result,
                   reason_code,
                   decision_reason,
                   decision_time,
                   time_window_seconds,
                   threshold_count,
                   elements_json,
                   current_count,
                   stage_code,
                   stage_name,
                   stage_order,
                   match_result,
                   decision_effect
            FROM risk_evaluation_hit_detail
            WHERE risk_record_no = #{riskRecordNo}
              AND evaluation_time = #{evaluationTime}
            ORDER BY COALESCE(stage_order, 9999) ASC, decision_time ASC, id ASC
            """)
    List<Map<String, Object>> selectEvaluationDetails(@Param("riskRecordNo") String riskRecordNo,
                                                      @Param("evaluationTime") LocalDateTime evaluationTime);

    /**
     * 查询单笔风控评估的执行明细总数。
     *
     * @param riskRecordNo 风控记录号
     * @param evaluationTime 定位表解析出的评估时间
     * @return 执行明细数量
     */
    @Select("""
            SELECT COUNT(1)
            FROM risk_evaluation_hit_detail
            WHERE risk_record_no = #{riskRecordNo}
              AND evaluation_time = #{evaluationTime}
            """)
    long countEvaluationDetails(@Param("riskRecordNo") String riskRecordNo,
                                @Param("evaluationTime") LocalDateTime evaluationTime);

    /**
     * 分页查询单笔风控评估的执行明细。
     *
     * @param riskRecordNo 风控记录号
     * @param evaluationTime 定位表解析出的评估时间
     * @param offset 分页偏移量
     * @param pageSize 每页记录数
     * @return 当前页执行明细
     */
    @Select("""
            SELECT id,
                   risk_record_no,
                   module_type,
                   function_code,
                   function_name,
                   rule_id,
                   hit_element,
                   hit_value_masked,
                   risk_level,
                   decision_result,
                   reason_code,
                   decision_reason,
                   decision_time,
                   time_window_seconds,
                   threshold_count,
                   elements_json,
                   current_count,
                   stage_code,
                   stage_name,
                   stage_order,
                   match_result,
                   decision_effect
            FROM risk_evaluation_hit_detail
            WHERE risk_record_no = #{riskRecordNo}
              AND evaluation_time = #{evaluationTime}
            ORDER BY COALESCE(stage_order, 9999) ASC, decision_time ASC, id ASC
            LIMIT #{offset}, #{pageSize}
            """)
    List<Map<String, Object>> selectEvaluationDetailsPage(@Param("riskRecordNo") String riskRecordNo,
                                                          @Param("evaluationTime") LocalDateTime evaluationTime,
                                                          @Param("offset") long offset,
                                                          @Param("pageSize") long pageSize);

    /**
     * 查询指定时间范围内的高风险商户排行。
     *
     * @param evaluationStartTime 统计起点（包含）
     * @param evaluationEndTimeExclusive 统计终点（不包含）
     * @param limit 返回数量上限
     * @return 商户风险统计
     */
    @Select("""
            SELECT COALESCE(NULLIF(merchant_id, ''), '-') AS merchant_id,
                   COALESCE(NULLIF(merchant_name, ''), '-') AS merchant_name,
                   COUNT(1) AS risk_count,
                   SUM(CASE WHEN risk_level IN ('HIGH', 'CRITICAL') THEN 1 ELSE 0 END) AS high_risk_count,
                   SUM(CASE WHEN decision_result = 'REJECT' THEN 1 ELSE 0 END) AS reject_count,
                   MAX(evaluation_time) AS latest_evaluation_time
            FROM risk_evaluation_record
            WHERE evaluation_time &gt;= #{evaluationStartTime}
              AND evaluation_time &lt; #{evaluationEndTimeExclusive}
            GROUP BY COALESCE(NULLIF(merchant_id, ''), '-'), COALESCE(NULLIF(merchant_name, ''), '-')
            ORDER BY high_risk_count DESC, risk_count DESC, latest_evaluation_time DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> selectMerchantRiskRanking(
            @Param("evaluationStartTime") LocalDateTime evaluationStartTime,
            @Param("evaluationEndTimeExclusive") LocalDateTime evaluationEndTimeExclusive,
            @Param("limit") int limit);
}
