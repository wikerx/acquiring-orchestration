package com.scott.payment.settlement.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 已入账历史标签本金三层交易投影修正 Mapper；只允许从冻结旧值 CAS 到审批后的新值。
 */
public interface SettlementPrincipalCorrectionProjectionMapper {

    /** 仅当原批次、汇率、日期和金额全部匹配时修正指定清分修订的财务状态。 */
    @Update("""
            UPDATE transaction_finance_state
            SET settlement_amount = #{correctedAmount}, version = version + 1, update_time = #{now}
            WHERE transaction_id = #{transactionId} AND transaction_date_time = #{transactionDateTime}
              AND clearing_revision = #{clearingRevision} AND settlement_status = 'SETTLED'
              AND settlement_currency = #{currency} AND settlement_amount = #{originalAmount}
              AND settlement_rate = #{rate} AND settlement_date = #{settlementDate}
              AND settlement_batch_no = #{batchNo} AND deleted = 0
            """)
    int correctFinanceState(@Param("transactionId") String transactionId,
                            @Param("transactionDateTime") LocalDateTime transactionDateTime,
                            @Param("clearingRevision") int clearingRevision,
                            @Param("currency") String currency,
                            @Param("originalAmount") BigDecimal originalAmount,
                            @Param("correctedAmount") BigDecimal correctedAmount,
                            @Param("rate") BigDecimal rate,
                            @Param("settlementDate") LocalDate settlementDate,
                            @Param("batchNo") String batchNo,
                            @Param("now") LocalDateTime now);

    /** 回读财务状态是否已具有完全一致的修正结果，用于合法重放判断。 */
    @Select("""
            SELECT COUNT(1) FROM transaction_finance_state
            WHERE transaction_id = #{transactionId} AND transaction_date_time = #{transactionDateTime}
              AND clearing_revision = #{clearingRevision} AND settlement_status = 'SETTLED'
              AND settlement_currency = #{currency} AND settlement_amount = #{correctedAmount}
              AND settlement_rate = #{rate} AND settlement_date = #{settlementDate}
              AND settlement_batch_no = #{batchNo} AND deleted = 0
            """)
    int countCorrectedFinanceState(@Param("transactionId") String transactionId,
                                   @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                   @Param("clearingRevision") int clearingRevision,
                                   @Param("currency") String currency,
                                   @Param("correctedAmount") BigDecimal correctedAmount,
                                   @Param("rate") BigDecimal rate,
                                   @Param("settlementDate") LocalDate settlementDate,
                                   @Param("batchNo") String batchNo);

    /** 仅当原结算快照完全匹配时修正动作单金额。 */
    @Update("""
            UPDATE transaction_operation
            SET settlement_amount = #{correctedAmount}, version = version + 1, update_time = #{now}
            WHERE transaction_id = #{transactionId} AND transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'SETTLED' AND settlement_currency = #{currency}
              AND settlement_amount = #{originalAmount} AND settlement_rate = #{rate}
              AND settlement_date = #{settlementDate} AND settlement_batch_no = #{batchNo}
              AND deleted = 0
            """)
    int correctOperation(@Param("transactionId") String transactionId,
                         @Param("transactionDateTime") LocalDateTime transactionDateTime,
                         @Param("currency") String currency,
                         @Param("originalAmount") BigDecimal originalAmount,
                         @Param("correctedAmount") BigDecimal correctedAmount,
                         @Param("rate") BigDecimal rate,
                         @Param("settlementDate") LocalDate settlementDate,
                         @Param("batchNo") String batchNo,
                         @Param("now") LocalDateTime now);

    /** 回读动作单是否已具有完全一致的修正结果。 */
    @Select("""
            SELECT COUNT(1) FROM transaction_operation
            WHERE transaction_id = #{transactionId} AND transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'SETTLED' AND settlement_currency = #{currency}
              AND settlement_amount = #{correctedAmount} AND settlement_rate = #{rate}
              AND settlement_date = #{settlementDate} AND settlement_batch_no = #{batchNo}
              AND deleted = 0
            """)
    int countCorrectedOperation(@Param("transactionId") String transactionId,
                                @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                @Param("currency") String currency,
                                @Param("correctedAmount") BigDecimal correctedAmount,
                                @Param("rate") BigDecimal rate,
                                @Param("settlementDate") LocalDate settlementDate,
                                @Param("batchNo") String batchNo);

    /** 当前生命周期主单仍指向该动作且原快照匹配时修正展示金额。 */
    @Update("""
            UPDATE transaction_order
            SET settlement_amount = #{correctedAmount}, version = version + 1, update_time = #{now}
            WHERE operation_id = #{operationId} AND transaction_date_time = #{rootTransactionDateTime}
              AND settlement_transaction_id = #{transactionId}
              AND settlement_transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'SETTLED' AND settlement_currency = #{currency}
              AND settlement_amount = #{originalAmount} AND settlement_rate = #{rate}
              AND settlement_date = #{settlementDate} AND settlement_batch_no = #{batchNo}
              AND deleted = 0
            """)
    int correctOrder(@Param("operationId") String operationId,
                     @Param("rootTransactionDateTime") LocalDateTime rootTransactionDateTime,
                     @Param("transactionId") String transactionId,
                     @Param("transactionDateTime") LocalDateTime transactionDateTime,
                     @Param("currency") String currency,
                     @Param("originalAmount") BigDecimal originalAmount,
                     @Param("correctedAmount") BigDecimal correctedAmount,
                     @Param("rate") BigDecimal rate,
                     @Param("settlementDate") LocalDate settlementDate,
                     @Param("batchNo") String batchNo,
                     @Param("now") LocalDateTime now);

    /** 回读生命周期主单是否已具有完全一致的修正结果。 */
    @Select("""
            SELECT COUNT(1) FROM transaction_order
            WHERE operation_id = #{operationId} AND transaction_date_time = #{rootTransactionDateTime}
              AND settlement_transaction_id = #{transactionId}
              AND settlement_transaction_date_time = #{transactionDateTime}
              AND settlement_status = 'SETTLED' AND settlement_currency = #{currency}
              AND settlement_amount = #{correctedAmount} AND settlement_rate = #{rate}
              AND settlement_date = #{settlementDate} AND settlement_batch_no = #{batchNo}
              AND deleted = 0
            """)
    int countCorrectedOrder(@Param("operationId") String operationId,
                            @Param("rootTransactionDateTime") LocalDateTime rootTransactionDateTime,
                            @Param("transactionId") String transactionId,
                            @Param("transactionDateTime") LocalDateTime transactionDateTime,
                            @Param("currency") String currency,
                            @Param("correctedAmount") BigDecimal correctedAmount,
                            @Param("rate") BigDecimal rate,
                            @Param("settlementDate") LocalDate settlementDate,
                            @Param("batchNo") String batchNo);
}
