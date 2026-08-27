package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingFeeTierAccumulatorDO;
import com.scott.payment.clearing.dto.ClearingFeeTierAccumulatorDelta;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingFeeTierAccumulatorMapper
 * @date : 2026-08-26 10:45
 * @email : scott_x@163.com
 * @description : 月累计阶梯事实 Mapper，以批量初始化、有序 FOR UPDATE 和批量版本 CAS 串行化同一动作命中的全部规则。
 * @status : create
 */
public interface ClearingFeeTierAccumulatorMapper {

    /** 幂等创建零值累计行；仅业务唯一键竞争由数据库吸收，其它数据异常直接失败。 */
    @Insert("""
            <script>
            INSERT INTO fee_tier_accumulator
            (merchant_id, fee_plan_version_id, fee_rule_id, period_key,
             accumulated_count, accumulated_amount_usd, version, create_time, update_time)
            VALUES
            <foreach collection="feeRuleIds" item="feeRuleId" separator=",">
                (#{merchantId}, #{feePlanVersionId}, #{feeRuleId}, #{periodKey},
                 0, 0, 0, #{now}, #{now})
            </foreach>
            ON DUPLICATE KEY UPDATE id = id
            </script>
            """)
    int insertIfAbsentBatch(@Param("merchantId") String merchantId,
                            @Param("feePlanVersionId") Long feePlanVersionId,
                            @Param("feeRuleIds") List<Long> feeRuleIds,
                            @Param("periodKey") String periodKey,
                            @Param("now") LocalDateTime now);

    /** 按规则 ID 稳定排序，一次锁定当前动作适用的全部月累计事实。 */
    @Select("""
            <script>
            SELECT *
            FROM fee_tier_accumulator
            WHERE merchant_id = #{merchantId}
              AND fee_plan_version_id = #{feePlanVersionId}
              AND fee_rule_id IN
              <foreach collection="feeRuleIds" item="feeRuleId" open="(" separator="," close=")">
                  #{feeRuleId}
              </foreach>
              AND period_key = #{periodKey}
            ORDER BY fee_rule_id ASC
            FOR UPDATE
            </script>
            """)
    List<ClearingFeeTierAccumulatorDO> selectForUpdateBatch(
            @Param("merchantId") String merchantId,
            @Param("feePlanVersionId") Long feePlanVersionId,
            @Param("feeRuleIds") List<Long> feeRuleIds,
            @Param("periodKey") String periodKey);

    /** 在全部累计行已按规则 ID 锁定后清零期间权威累计，供稳定顺序重放从零重建。 */
    @Update("""
            <script>
            UPDATE fee_tier_accumulator
            SET accumulated_count = 0,
                accumulated_amount_usd = 0,
                last_transaction_id = NULL,
                last_clearing_revision = NULL,
                last_transaction_date_time = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE merchant_id = #{merchantId}
              AND fee_plan_version_id = #{feePlanVersionId}
              AND fee_rule_id IN
              <foreach collection="feeRuleIds" item="feeRuleId" open="(" separator="," close=")">
                  #{feeRuleId}
              </foreach>
              AND period_key = #{periodKey}
            </script>
            """)
    int resetPeriod(@Param("merchantId") String merchantId,
                    @Param("feePlanVersionId") Long feePlanVersionId,
                    @Param("feeRuleIds") List<Long> feeRuleIds,
                    @Param("periodKey") String periodKey,
                    @Param("now") LocalDateTime now);

    /** 在一次 SQL 中按各自行版本 CAS 提交当前动作命中的全部累计增量。 */
    @Update("""
            <script>
            UPDATE fee_tier_accumulator
            SET accumulated_count = accumulated_count + 1,
                accumulated_amount_usd = accumulated_amount_usd
                    + CASE fee_rule_id
                      <foreach collection="deltas" item="delta">
                          WHEN #{delta.feeRuleId} THEN #{delta.amountDelta}
                      </foreach>
                      ELSE 0
                      END,
                last_transaction_id = #{transactionId},
                last_clearing_revision = #{clearingRevision},
                last_transaction_date_time = #{transactionDateTime},
                version = version + 1,
                update_time = #{now}
            WHERE merchant_id = #{merchantId}
              AND fee_plan_version_id = #{feePlanVersionId}
              AND period_key = #{periodKey}
              AND (
                  <foreach collection="deltas" item="delta" separator=" OR ">
                      (fee_rule_id = #{delta.feeRuleId}
                       AND version = #{delta.expectedVersion}
                       AND #{delta.amountDelta} &gt;= 0)
                  </foreach>
              )
            </script>
            """)
    int applyDeltas(@Param("merchantId") String merchantId,
                    @Param("feePlanVersionId") Long feePlanVersionId,
                    @Param("periodKey") String periodKey,
                    @Param("deltas") List<ClearingFeeTierAccumulatorDelta> deltas,
                    @Param("transactionId") String transactionId,
                    @Param("clearingRevision") int clearingRevision,
                    @Param("transactionDateTime") LocalDateTime transactionDateTime,
                    @Param("now") LocalDateTime now);
}
