package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingReserveDetailDO;
import com.scott.payment.clearing.entity.ClearingReserveStateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingReserveMapper
 * @date : 2026-08-26 10:45
 * @email : scott_x@163.com
 * @description : 独立保证金明细与状态 Mapper；原支付状态读取必须携带原支付分片时间并在退款阶段加行锁。
 * @status : create
 */
public interface ClearingReserveMapper {

    /** 按当前动作真实分片时间读取当前有效修订保证金明细。 */
    @Select("""
            SELECT *
            FROM transaction_reserve_clearing_detail
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND clearing_revision = #{clearingRevision}
              AND record_status = 'ACTIVE'
            ORDER BY line_no ASC, id ASC
            """)
    List<ClearingReserveDetailDO> selectActiveRevision(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime,
            @Param("clearingRevision") int clearingRevision);

    /** 写入当前动作的一条 HOLD、RETURN、RELEASE 或 ADJUSTMENT 保证金事实。 */
    @Insert("""
            INSERT INTO transaction_reserve_clearing_detail
            (reserve_clearing_detail_no, finance_state_id, transaction_id, operation_id,
             original_transaction_id, original_transaction_date_time, source_reserve_detail_no,
             merchant_id, payment_type, payment_method, transaction_type, clearing_revision,
             line_no, reserve_action_type,
             item_code, item_name, direction, reserve_currency, reserve_currency_exponent,
             basis_amount, reserve_rate, retained_amount, returned_amount, released_amount,
             adjustment_amount, remaining_amount, fee_plan_id, fee_plan_version_id,
             fee_plan_version_no, reserve_snapshot_hash, reserve_basis, reserve_delay_unit,
             reserve_delay_days, rounding_mode, formula_snapshot, expected_reserve_release_date,
             record_status, transaction_date_time, transaction_utc_time, transaction_time_zone,
             create_time, update_time)
            VALUES
            (#{row.reserveClearingDetailNo}, #{row.financeStateId}, #{row.transactionId}, #{row.operationId},
             #{row.originalTransactionId}, #{row.originalTransactionDateTime}, #{row.sourceReserveDetailNo},
             #{row.merchantId}, #{row.paymentType}, #{row.paymentMethod}, #{row.transactionType},
             #{row.clearingRevision}, #{row.lineNo},
             #{row.reserveActionType}, #{row.itemCode}, #{row.itemName}, #{row.direction},
             #{row.reserveCurrency}, #{row.reserveCurrencyExponent}, #{row.basisAmount}, #{row.reserveRate},
             #{row.retainedAmount}, #{row.returnedAmount}, #{row.releasedAmount}, #{row.adjustmentAmount},
             #{row.remainingAmount}, #{row.feePlanId}, #{row.feePlanVersionId}, #{row.feePlanVersionNo},
             #{row.reserveSnapshotHash}, #{row.reserveBasis}, #{row.reserveDelayUnit},
             #{row.reserveDelayDays}, #{row.roundingMode}, #{row.formulaSnapshot},
             #{row.expectedReserveReleaseDate}, #{row.recordStatus}, #{row.transactionDateTime},
             #{row.transactionUtcTime}, #{row.transactionTimeZone}, #{row.createTime}, #{row.updateTime})
            """)
    int insertDetail(@Param("row") ClearingReserveDetailDO row);

    /** 新支付计提保证金时创建原支付保证金状态。 */
    @Insert("""
            INSERT INTO transaction_reserve_clearing_state
            (reserve_state_id, original_transaction_id, operation_id, original_finance_state_id,
             original_hold_detail_no, original_fee_plan_version_id, original_reserve_snapshot_hash,
             merchant_id, reserve_currency, reserve_currency_exponent, original_basis_amount,
             original_reserve_rate, original_rounding_mode, retained_amount, returned_amount,
             released_amount, debit_adjustment_amount, credit_adjustment_amount,
             remaining_amount, expected_reserve_release_date, reserve_status,
             transaction_date_time, original_transaction_utc_time, transaction_time_zone,
             version, create_time, update_time)
            VALUES
            (#{row.reserveStateId}, #{row.originalTransactionId}, #{row.operationId},
             #{row.originalFinanceStateId}, #{row.originalHoldDetailNo}, #{row.originalFeePlanVersionId},
             #{row.originalReserveSnapshotHash}, #{row.merchantId}, #{row.reserveCurrency},
             #{row.reserveCurrencyExponent}, #{row.originalBasisAmount}, #{row.originalReserveRate},
             #{row.originalRoundingMode}, #{row.retainedAmount}, #{row.returnedAmount},
             #{row.releasedAmount}, #{row.debitAdjustmentAmount}, #{row.creditAdjustmentAmount},
             #{row.remainingAmount}, #{row.expectedReserveReleaseDate},
             #{row.reserveStatus}, #{row.transactionDateTime}, #{row.originalTransactionUtcTime},
             #{row.transactionTimeZone}, #{row.version}, #{row.createTime}, #{row.updateTime})
            """)
    int insertState(@Param("row") ClearingReserveStateDO row);

    /** 按原支付号和原支付真实分片时间锁定保证金状态。 */
    @Select("""
            SELECT *
            FROM transaction_reserve_clearing_state
            WHERE original_transaction_id = #{originalTransactionId}
              AND transaction_date_time = #{originalTransactionDateTime}
            LIMIT 1
            FOR UPDATE
            """)
    ClearingReserveStateDO selectStateForUpdate(
            @Param("originalTransactionId") String originalTransactionId,
            @Param("originalTransactionDateTime") LocalDateTime originalTransactionDateTime);

    /** 按原HOLD业务号和原支付分片时间读取不可变保证金配置与支付维度快照。 */
    @Select("""
            SELECT *
            FROM transaction_reserve_clearing_detail
            WHERE reserve_clearing_detail_no = #{holdDetailNo}
              AND transaction_date_time = #{originalTransactionDateTime}
              AND reserve_action_type = 'HOLD'
              AND record_status = 'ACTIVE'
            LIMIT 1
            """)
    ClearingReserveDetailDO selectHoldDetail(
            @Param("holdDetailNo") String holdDetailNo,
            @Param("originalTransactionDateTime") LocalDateTime originalTransactionDateTime);

    /** 查询单季度内已到期且仍有余额的保证金状态，扫描结果不替代释放事务内行锁。 */
    @Select("""
            SELECT reserve_state_id, original_transaction_id, transaction_date_time
            FROM transaction_reserve_clearing_state
            WHERE transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTime}
              AND reserve_status = 'OPEN'
              AND remaining_amount > 0
              AND expected_reserve_release_date IS NOT NULL
              AND expected_reserve_release_date <= #{businessDate}
            ORDER BY expected_reserve_release_date ASC, transaction_date_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<ClearingReserveStateDO> selectDueReleaseCandidates(
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("businessDate") LocalDate businessDate,
            @Param("limit") int limit);

    /** 使用原支付分片键和版本 CAS 累加本次退款返还金额。 */
    @Update("""
            UPDATE transaction_reserve_clearing_state
            SET returned_amount = returned_amount + #{returnAmount},
                remaining_amount = #{remainingAmount},
                reserve_status = #{reserveStatus},
                last_return_transaction_id = #{returnTransactionId},
                last_return_transaction_date_time = #{returnTransactionDateTime},
                version = version + 1,
                update_time = #{now}
            WHERE original_transaction_id = #{originalTransactionId}
              AND transaction_date_time = #{originalTransactionDateTime}
              AND version = #{expectedVersion}
              AND reserve_status = 'OPEN'
              AND #{returnAmount} > 0
              AND retained_amount + debit_adjustment_amount
                  = returned_amount + released_amount + credit_adjustment_amount + remaining_amount
              AND remaining_amount >= #{returnAmount}
              AND remaining_amount = #{remainingAmount} + #{returnAmount}
              AND ((#{reserveStatus} = 'OPEN' AND #{remainingAmount} > 0)
                   OR (#{reserveStatus} = 'FULLY_RETURNED' AND #{remainingAmount} = 0))
            """)
    int applyReturn(@Param("originalTransactionId") String originalTransactionId,
                    @Param("originalTransactionDateTime") LocalDateTime originalTransactionDateTime,
                    @Param("expectedVersion") long expectedVersion,
                    @Param("returnAmount") BigDecimal returnAmount,
                    @Param("remainingAmount") BigDecimal remainingAmount,
                    @Param("reserveStatus") String reserveStatus,
                    @Param("returnTransactionId") String returnTransactionId,
                    @Param("returnTransactionDateTime") LocalDateTime returnTransactionDateTime,
                    @Param("now") LocalDateTime now);

    /** 使用原支付分片键、OPEN状态和版本CAS一次性释放当前全部剩余保证金。 */
    @Update("""
            UPDATE transaction_reserve_clearing_state
            SET released_amount = released_amount + #{releaseAmount},
                remaining_amount = 0,
                reserve_status = 'FULLY_RELEASED',
                version = version + 1,
                update_time = #{now}
            WHERE original_transaction_id = #{originalTransactionId}
              AND transaction_date_time = #{originalTransactionDateTime}
              AND version = #{expectedVersion}
              AND reserve_status = 'OPEN'
              AND expected_reserve_release_date IS NOT NULL
              AND expected_reserve_release_date <= #{releaseDate}
              AND #{releaseAmount} > 0
              AND retained_amount + debit_adjustment_amount
                  = returned_amount + released_amount + credit_adjustment_amount + remaining_amount
              AND remaining_amount = #{releaseAmount}
            """)
    int applyRelease(@Param("originalTransactionId") String originalTransactionId,
                     @Param("originalTransactionDateTime") LocalDateTime originalTransactionDateTime,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("releaseAmount") BigDecimal releaseAmount,
                     @Param("now") LocalDateTime now,
                     @Param("releaseDate") LocalDate releaseDate);

    /** 经复核后按标签币种方向 CAS 调整剩余负债和累计调整金额。 */
    @Update("""
            UPDATE transaction_reserve_clearing_state
            SET debit_adjustment_amount = debit_adjustment_amount
                    + CASE WHEN #{direction} = 'DEBIT' THEN #{adjustmentAmount} ELSE 0 END,
                credit_adjustment_amount = credit_adjustment_amount
                    + CASE WHEN #{direction} = 'CREDIT' THEN #{adjustmentAmount} ELSE 0 END,
                remaining_amount = #{remainingAmount},
                expected_reserve_release_date = CASE
                    WHEN #{direction} = 'DEBIT' THEN #{releaseDate}
                    ELSE expected_reserve_release_date END,
                reserve_status = CASE WHEN #{remainingAmount} > 0 THEN 'OPEN' ELSE 'ADJUSTED' END,
                version = version + 1,
                update_time = #{now}
            WHERE original_transaction_id = #{originalTransactionId}
              AND transaction_date_time = #{originalTransactionDateTime}
              AND version = #{expectedVersion}
              AND #{direction} IN ('DEBIT', 'CREDIT')
              AND #{adjustmentAmount} > 0
              AND retained_amount + debit_adjustment_amount
                  = returned_amount + released_amount + credit_adjustment_amount + remaining_amount
              AND ((#{direction} = 'DEBIT'
                    AND #{releaseDate} IS NOT NULL
                    AND #{remainingAmount} = remaining_amount + #{adjustmentAmount})
                   OR (#{direction} = 'CREDIT'
                       AND reserve_status = 'OPEN'
                       AND remaining_amount >= #{adjustmentAmount}
                       AND remaining_amount = #{remainingAmount} + #{adjustmentAmount}))
            """)
    int applyAdjustment(@Param("originalTransactionId") String originalTransactionId,
                        @Param("originalTransactionDateTime") LocalDateTime originalTransactionDateTime,
                        @Param("expectedVersion") long expectedVersion,
                        @Param("direction") String direction,
                        @Param("adjustmentAmount") BigDecimal adjustmentAmount,
                        @Param("remainingAmount") BigDecimal remainingAmount,
                        @Param("releaseDate") LocalDate releaseDate,
                        @Param("now") LocalDateTime now);
}
