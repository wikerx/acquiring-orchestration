package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.MerchantReserveActionDO;
import com.scott.payment.settlement.entity.MerchantReserveItemDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReserveMapper
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 保证金资金化数据访问接口；聚合行使用锁和 version CAS，动作表以来源唯一键提供 HOLD、RETURN、RELEASE、ADJUSTMENT 与 REVERSAL 的最终幂等。
 * @status : create
 */
public interface SettlementReserveMapper {

    /** HOLD 首次创建保证金聚合；唯一来源冲突后由服务层回读校验。 */
    @Insert("""
            INSERT INTO merchant_reserve_item
            (reserve_no, account_id, merchant_id, source_transaction_id, source_business_no,
             currency, retained_amount, returned_amount, released_amount,
             debit_adjustment_amount, credit_adjustment_amount, reversed_amount,
             reserve_status, expected_release_date, release_batch_no, version, create_time, update_time)
            VALUES
            (#{row.reserveNo}, #{row.accountId}, #{row.merchantId}, #{row.sourceTransactionId},
             #{row.sourceBusinessNo}, #{row.currency}, #{row.retainedAmount}, #{row.returnedAmount},
             #{row.releasedAmount}, #{row.debitAdjustmentAmount}, #{row.creditAdjustmentAmount},
             #{row.reversedAmount}, #{row.reserveStatus},
             #{row.expectedReleaseDate}, #{row.releaseBatchNo}, #{row.version},
             #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertItemIdempotent(@Param("row") MerchantReserveItemDO row);

    /** 按原 HOLD 清分明细号锁定保证金聚合。 */
    @Select("""
            SELECT *
            FROM merchant_reserve_item
            WHERE merchant_id = #{merchantId}
              AND source_business_no = #{sourceReserveDetailNo}
            LIMIT 1
            FOR UPDATE
            """)
    MerchantReserveItemDO selectBySourceForUpdate(
            @Param("merchantId") String merchantId,
            @Param("sourceReserveDetailNo") String sourceReserveDetailNo);

    /** 冲正按原批动作逆序锁读，保证先撤销后发生的 RETURN/RELEASE。 */
    @Select("""
            SELECT *
            FROM merchant_reserve_action
            WHERE settlement_batch_no = #{settlementBatchNo}
            ORDER BY id DESC
            FOR UPDATE
            """)
    List<MerchantReserveActionDO> selectActionsByBatchForUpdate(
            @Param("settlementBatchNo") String settlementBatchNo);

    /** 按主键锁定保证金聚合供冲正动作更新。 */
    @Select("""
            SELECT *
            FROM merchant_reserve_item
            WHERE id = #{reserveItemId}
            LIMIT 1
            FOR UPDATE
            """)
    MerchantReserveItemDO selectItemByIdForUpdate(@Param("reserveItemId") Long reserveItemId);

    /** 追加不可变保证金动作；重复结算消息不得二次改变聚合。 */
    @Insert("""
            INSERT INTO merchant_reserve_action
            (reserve_action_no, reserve_item_id, reserve_no, settlement_batch_no, candidate_id,
             source_reserve_detail_no, action_type, direction, currency, amount,
             reversal_of_action_id, action_time, create_time)
            VALUES
            (#{row.reserveActionNo}, #{row.reserveItemId}, #{row.reserveNo},
             #{row.settlementBatchNo}, #{row.candidateId}, #{row.sourceReserveDetailNo},
             #{row.actionType}, #{row.direction}, #{row.currency}, #{row.amount},
             #{row.reversalOfActionId}, #{row.actionTime}, #{row.createTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertActionIdempotent(@Param("row") MerchantReserveActionDO row);

    /** 回读动作身份，区分合法重放与幂等键碰撞。 */
    @Select("""
            SELECT *
            FROM merchant_reserve_action
            WHERE reserve_action_no = #{reserveActionNo}
            LIMIT 1
            FOR UPDATE
            """)
    MerchantReserveActionDO selectActionForUpdate(@Param("reserveActionNo") String reserveActionNo);

    /** RETURN 减少仍扣留责任，金额和版本条件防止并发超额返还。 */
    @Update("""
            UPDATE merchant_reserve_item
            SET returned_amount = returned_amount + #{amount},
                reserve_status = CASE
                    WHEN retained_amount + debit_adjustment_amount
                        = returned_amount + released_amount + credit_adjustment_amount
                          + reversed_amount + #{amount}
                    THEN 'RETURNED' ELSE 'PARTIALLY_RETURNED' END,
                version = version + 1,
                update_time = #{now}
            WHERE id = #{reserveItemId}
              AND currency = #{currency}
              AND retained_amount + debit_adjustment_amount
                  - returned_amount - released_amount - credit_adjustment_amount - reversed_amount
                  >= #{amount}
              AND version = #{expectedVersion}
            """)
    int applyReturn(@Param("reserveItemId") Long reserveItemId,
                    @Param("currency") String currency,
                    @Param("amount") BigDecimal amount,
                    @Param("expectedVersion") long expectedVersion,
                    @Param("now") LocalDateTime now);

    /** 到期 RELEASE 减少仍扣留责任并记录释放批次。 */
    @Update("""
            UPDATE merchant_reserve_item
            SET released_amount = released_amount + #{amount},
                reserve_status = CASE
                    WHEN retained_amount + debit_adjustment_amount
                        = returned_amount + released_amount + credit_adjustment_amount
                          + reversed_amount + #{amount}
                    THEN 'RELEASED' ELSE 'HELD' END,
                release_batch_no = #{settlementBatchNo},
                version = version + 1,
                update_time = #{now}
            WHERE id = #{reserveItemId}
              AND currency = #{currency}
              AND retained_amount + debit_adjustment_amount
                  - returned_amount - released_amount - credit_adjustment_amount - reversed_amount
                  >= #{amount}
              AND version = #{expectedVersion}
            """)
    int applyRelease(@Param("reserveItemId") Long reserveItemId,
                     @Param("currency") String currency,
                     @Param("amount") BigDecimal amount,
                     @Param("settlementBatchNo") String settlementBatchNo,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("now") LocalDateTime now);

    /** 借方调整增加原标签币种保证金责任。 */
    @Update("""
            UPDATE merchant_reserve_item
            SET debit_adjustment_amount = debit_adjustment_amount + #{amount},
                reserve_status = 'ADJUSTED',
                version = version + 1,
                update_time = #{now}
            WHERE id = #{reserveItemId}
              AND currency = #{currency}
              AND version = #{expectedVersion}
            """)
    int applyDebitAdjustment(@Param("reserveItemId") Long reserveItemId,
                             @Param("currency") String currency,
                             @Param("amount") BigDecimal amount,
                             @Param("expectedVersion") long expectedVersion,
                             @Param("now") LocalDateTime now);

    /** 贷方调整减少责任，剩余责任不足时必须拒绝。 */
    @Update("""
            UPDATE merchant_reserve_item
            SET credit_adjustment_amount = credit_adjustment_amount + #{amount},
                reserve_status = 'ADJUSTED',
                version = version + 1,
                update_time = #{now}
            WHERE id = #{reserveItemId}
              AND currency = #{currency}
              AND retained_amount + debit_adjustment_amount
                  - returned_amount - released_amount - credit_adjustment_amount - reversed_amount >= #{amount}
              AND version = #{expectedVersion}
            """)
    int applyCreditAdjustment(@Param("reserveItemId") Long reserveItemId,
                              @Param("currency") String currency,
                              @Param("amount") BigDecimal amount,
                              @Param("expectedVersion") long expectedVersion,
                              @Param("now") LocalDateTime now);

    /** 撤销 HOLD，只允许在该留存仍有足额未返还、未释放责任时执行。 */
    @Update("""
            UPDATE merchant_reserve_item
            SET reversed_amount = reversed_amount + #{amount},
                reserve_status = CASE
                    WHEN retained_amount + debit_adjustment_amount
                        = returned_amount + released_amount + credit_adjustment_amount
                          + reversed_amount + #{amount}
                    THEN 'REVERSED' ELSE reserve_status END,
                version = version + 1,
                update_time = #{now}
            WHERE id = #{reserveItemId}
              AND retained_amount + debit_adjustment_amount
                  - returned_amount - released_amount - credit_adjustment_amount - reversed_amount
                  >= #{amount}
              AND version = #{expectedVersion}
            """)
    int reverseHold(@Param("reserveItemId") Long reserveItemId,
                    @Param("amount") BigDecimal amount,
                    @Param("expectedVersion") long expectedVersion,
                    @Param("now") LocalDateTime now);

    /** 撤销 RETURN，恢复相同原币种保证金责任。 */
    @Update("""
            UPDATE merchant_reserve_item
            SET returned_amount = returned_amount - #{amount},
                reserve_status = 'HELD',
                version = version + 1,
                update_time = #{now}
            WHERE id = #{reserveItemId}
              AND returned_amount >= #{amount}
              AND version = #{expectedVersion}
            """)
    int reverseReturn(@Param("reserveItemId") Long reserveItemId,
                      @Param("amount") BigDecimal amount,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("now") LocalDateTime now);

    /** 撤销 RELEASE，恢复相同原币种保证金责任。 */
    @Update("""
            UPDATE merchant_reserve_item
            SET released_amount = released_amount - #{amount},
                reserve_status = 'HELD',
                release_batch_no = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE id = #{reserveItemId}
              AND released_amount >= #{amount}
              AND version = #{expectedVersion}
            """)
    int reverseRelease(@Param("reserveItemId") Long reserveItemId,
                       @Param("amount") BigDecimal amount,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("now") LocalDateTime now);

    /** 撤销借方调整；后续已消耗责任时不得回减到负责任。 */
    @Update("""
            UPDATE merchant_reserve_item
            SET debit_adjustment_amount = debit_adjustment_amount - #{amount},
                reserve_status = 'ADJUSTED',
                version = version + 1,
                update_time = #{now}
            WHERE id = #{reserveItemId}
              AND debit_adjustment_amount >= #{amount}
              AND retained_amount + debit_adjustment_amount
                  - returned_amount - released_amount - credit_adjustment_amount - reversed_amount >= #{amount}
              AND version = #{expectedVersion}
            """)
    int reverseDebitAdjustment(@Param("reserveItemId") Long reserveItemId,
                               @Param("amount") BigDecimal amount,
                               @Param("expectedVersion") long expectedVersion,
                               @Param("now") LocalDateTime now);

    /** 撤销贷方调整，恢复相同原标签币种责任。 */
    @Update("""
            UPDATE merchant_reserve_item
            SET credit_adjustment_amount = credit_adjustment_amount - #{amount},
                reserve_status = 'ADJUSTED',
                version = version + 1,
                update_time = #{now}
            WHERE id = #{reserveItemId}
              AND credit_adjustment_amount >= #{amount}
              AND version = #{expectedVersion}
            """)
    int reverseCreditAdjustment(@Param("reserveItemId") Long reserveItemId,
                                @Param("amount") BigDecimal amount,
                                @Param("expectedVersion") long expectedVersion,
                                @Param("now") LocalDateTime now);
}
