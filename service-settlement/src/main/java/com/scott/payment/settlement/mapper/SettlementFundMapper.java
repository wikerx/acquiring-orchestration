package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.MerchantFundAccountDO;
import com.scott.payment.settlement.entity.MerchantFundLedgerDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 资金入账 Mapper；固定先锁账户，再分配账户序号、追加流水并以余额和版本 CAS 更新账户。 */
public interface SettlementFundMapper {

    /** 锁定结算账户，建立同账户所有资金动作的统一串行顺序。 */
    @Select("""
            SELECT id, account_no, merchant_id, settlement_currency, available_balance,
                   account_status, account_version
            FROM merchant_fund_account
            WHERE id = #{accountId}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    MerchantFundAccountDO selectAccountForUpdate(@Param("accountId") Long accountId);

    /** 账户已锁定后读取最大流水序号；唯一键继续兜底数据库并发。 */
    @Select("""
            SELECT COALESCE(MAX(account_sequence), 0)
            FROM merchant_fund_ledger
            WHERE account_id = #{accountId}
            """)
    Long selectMaxAccountSequence(@Param("accountId") Long accountId);

    /** 按唯一幂等键读取既有资金流水，用于命令重放校验。 */
    @Select("""
            SELECT *
            FROM merchant_fund_ledger
            WHERE idempotency_key = #{idempotencyKey}
            LIMIT 1
            FOR UPDATE
            """)
    MerchantFundLedgerDO selectLedgerByIdempotencyForUpdate(
            @Param("idempotencyKey") String idempotencyKey);

    /** 追加不可变资金流水；禁止使用覆盖式幂等写。 */
    @Insert("""
            INSERT INTO merchant_fund_ledger
            (ledger_no, ledger_group_no, account_id, merchant_id, business_type, summary,
             business_no, settlement_batch_no, currency, direction, amount, balance_before,
             balance_after, account_sequence, operation_mode, operator_name, business_time,
             posted_time, idempotency_key, reversal_of_ledger_id, create_time)
            VALUES
            (#{row.ledgerNo}, #{row.ledgerGroupNo}, #{row.accountId}, #{row.merchantId},
             #{row.businessType}, #{row.summary}, #{row.businessNo}, #{row.settlementBatchNo},
             #{row.currency}, #{row.direction}, #{row.amount}, #{row.balanceBefore},
             #{row.balanceAfter}, #{row.accountSequence}, #{row.operationMode}, #{row.operatorName},
             #{row.businessTime}, #{row.postedTime}, #{row.idempotencyKey},
             #{row.reversalOfLedgerId}, #{row.createTime})
            """)
    int insertLedger(@Param("row") MerchantFundLedgerDO row);

    /** 使用锁读到的余额和版本双重 CAS 更新账户，禁止只按主键覆盖。 */
    @Update("""
            UPDATE merchant_fund_account
            SET available_balance = #{balanceAfter},
                account_version = account_version + 1,
                update_by = 'service-settlement',
                update_time = #{now}
            WHERE id = #{accountId}
              AND available_balance = #{balanceBefore}
              AND account_version = #{expectedVersion}
              AND deleted = 0
            """)
    int updateAccountBalance(@Param("accountId") Long accountId,
                             @Param("balanceAfter") BigDecimal balanceAfter,
                             @Param("balanceBefore") BigDecimal balanceBefore,
                             @Param("expectedVersion") long expectedVersion,
                             @Param("now") LocalDateTime now);
}
