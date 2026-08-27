package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.LocatorFacts;
import com.scott.payment.clearing.entity.ClearingTransactionDetailDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionDetailMapper
 * @date : 2026-08-26 10:40
 * @email : scott_x@163.com
 * @description : 不可变交易清分明细 Mapper，负责精确分片读取和批量写入，不进行费用或返费计算。
 * @status : create
 */
public interface ClearingTransactionDetailMapper {

    /** 批量写入当前动作同一修订的本金、费用和返费原子事实。 */
    @Insert("""
            <script>
            INSERT INTO transaction_clearing_detail
            (clearing_detail_no, finance_state_id, transaction_id, operation_id,
             source_transaction_id, source_clearing_detail_no, source_settlement_result_item_no,
             merchant_id, payment_type, payment_method, transaction_type, clearing_revision,
             line_no, item_type, fee_category,
             risk_service_type, item_code, item_name, direction, label_currency, label_amount,
             label_currency_exponent, fee_group_no, component_no, component_type, basis_currency,
             basis_amount, basis_currency_exponent, amount, currency, currency_exponent,
             fee_plan_id, fee_plan_version_id, fee_plan_version_no, fee_rule_id, fee_rule_tier_id,
             charge_trigger, fee_mode, tier_period_key, tier_metric, tier_count_before,
             tier_count_delta, tier_count_after, tier_amount_usd_before, tier_amount_usd_delta,
             tier_amount_usd_after, percentage_rate, fixed_amount_usd, minimum_amount_usd,
             maximum_amount_usd, limit_evaluation_status, applied_limit, rounding_mode,
             formula_snapshot, rule_snapshot_json, fee_snapshot_hash, settlement_eligible_date,
             record_status, transaction_date_time, transaction_utc_time, transaction_time_zone,
             create_time, update_time)
            VALUES
            <foreach collection='rows' item='row' separator=','>
            (#{row.clearingDetailNo}, #{row.financeStateId}, #{row.transactionId}, #{row.operationId},
             #{row.sourceTransactionId}, #{row.sourceClearingDetailNo}, #{row.sourceSettlementResultItemNo},
             #{row.merchantId}, #{row.paymentType}, #{row.paymentMethod}, #{row.transactionType},
             #{row.clearingRevision}, #{row.lineNo},
             #{row.itemType}, #{row.feeCategory}, #{row.riskServiceType}, #{row.itemCode},
             #{row.itemName}, #{row.direction}, #{row.labelCurrency}, #{row.labelAmount},
             #{row.labelCurrencyExponent}, #{row.feeGroupNo}, #{row.componentNo}, #{row.componentType},
             #{row.basisCurrency}, #{row.basisAmount}, #{row.basisCurrencyExponent}, #{row.amount},
             #{row.currency}, #{row.currencyExponent}, #{row.feePlanId}, #{row.feePlanVersionId},
             #{row.feePlanVersionNo}, #{row.feeRuleId}, #{row.feeRuleTierId}, #{row.chargeTrigger},
             #{row.feeMode}, #{row.tierPeriodKey}, #{row.tierMetric}, #{row.tierCountBefore},
             #{row.tierCountDelta}, #{row.tierCountAfter}, #{row.tierAmountUsdBefore},
             #{row.tierAmountUsdDelta}, #{row.tierAmountUsdAfter}, #{row.percentageRate},
             #{row.fixedAmountUsd}, #{row.minimumAmountUsd}, #{row.maximumAmountUsd},
             #{row.limitEvaluationStatus}, #{row.appliedLimit}, #{row.roundingMode},
             #{row.formulaSnapshot}, #{row.ruleSnapshotJson}, #{row.feeSnapshotHash},
             #{row.settlementEligibleDate}, #{row.recordStatus}, #{row.transactionDateTime},
             #{row.transactionUtcTime}, #{row.transactionTimeZone}, #{row.createTime}, #{row.updateTime})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("rows") List<ClearingTransactionDetailDO> rows);

    /** 按源动作真实分片时间读取当前有效修订明细。 */
    @Select("""
            SELECT *
            FROM transaction_clearing_detail
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_revision = #{clearingRevision}
              AND record_status = 'ACTIVE'
            ORDER BY line_no ASC, id ASC
            """)
    List<ClearingTransactionDetailDO> selectActiveRevision(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime,
            @Param("clearingRevision") int clearingRevision);

    /**
     * 按退款 locator 精确读取已清分退款本金或返费；查询条件不使用跨季度时间范围广播。
     */
    @Select("""
            <script>
            SELECT *
            FROM transaction_clearing_detail
            WHERE record_status = 'ACTIVE'
              AND item_type IN ('PRINCIPAL', 'FEE_REVERSAL')
              AND source_transaction_id = #{sourceTransactionId}
              AND (
                <foreach collection='locators' item='locator' separator=' OR '>
                  (transaction_id = #{locator.transactionId}
                   AND transaction_date_time = #{locator.transactionDateTime})
                </foreach>
              )
            ORDER BY transaction_date_time ASC, transaction_id ASC, line_no ASC
            </script>
            """)
    List<ClearingTransactionDetailDO> selectRefundFacts(
            @Param("sourceTransactionId") String sourceTransactionId,
            @Param("locators") List<LocatorFacts> locators);

    /** 人工重算保留旧修订，仅将精确旧修订的 ACTIVE 明细改为 SUPERSEDED。 */
    @Update("""
            UPDATE transaction_clearing_detail
            SET record_status = 'SUPERSEDED',
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_revision = #{revision}
              AND record_status = 'ACTIVE'
            """)
    int supersedeActiveRevision(@Param("transactionId") String transactionId,
                                @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                @Param("revision") int revision,
                                @Param("now") LocalDateTime now);
}
