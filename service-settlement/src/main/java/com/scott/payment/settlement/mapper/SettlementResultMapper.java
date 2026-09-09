package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementResultItemDO;
import com.scott.payment.settlement.entity.SettlementResultSummaryDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementResultMapper
 * @date : 2026-08-26 23:30
 * @email : scott_x@163.com
 * @description : 结算结果只追加和读取 Mapper；幂等冲突保留原事实，调用方必须回读验证完整业务身份。
 * @status : create
 */
public interface SettlementResultMapper {

    /** 批量追加候选级结果行；批次唯一 LEDGER_POSTING 由资金提交服务在独立事务中生成。 */
    @Insert("""
            <script>
            INSERT INTO settlement_result_item
            (settlement_result_item_no, settlement_batch_no, candidate_id, result_line_no,
             merchant_id, settlement_account_id, source_detail_type, source_detail_no,
             reversal_of_result_item_id, source_transaction_id, source_transaction_date_time,
             fee_group_no, result_item_type, result_role, payment_type, payment_method,
             transaction_type, fee_category, direction, source_amount, source_currency,
             source_currency_exponent, settlement_batch_rate_id, unrounded_target_amount,
             target_amount, target_currency, target_currency_exponent, applied_limit,
             minimum_target_amount, maximum_target_amount, rounding_mode, formula_snapshot,
             ledger_idempotency_key, create_time)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.settlementResultItemNo}, #{row.settlementBatchNo}, #{row.candidateId},
                 #{row.resultLineNo}, #{row.merchantId}, #{row.settlementAccountId},
                 #{row.sourceDetailType}, #{row.sourceDetailNo}, #{row.reversalOfResultItemId},
                 #{row.sourceTransactionId}, #{row.sourceTransactionDateTime}, #{row.feeGroupNo},
                 #{row.resultItemType}, #{row.resultRole}, #{row.paymentType}, #{row.paymentMethod},
                 #{row.transactionType}, #{row.feeCategory}, #{row.direction}, #{row.sourceAmount},
                 #{row.sourceCurrency}, #{row.sourceCurrencyExponent}, #{row.settlementBatchRateId},
                 #{row.unroundedTargetAmount}, #{row.targetAmount}, #{row.targetCurrency},
                 #{row.targetCurrencyExponent}, #{row.appliedLimit}, #{row.minimumTargetAmount},
                 #{row.maximumTargetAmount}, #{row.roundingMode}, #{row.formulaSnapshot},
                 #{row.ledgerIdempotencyKey}, #{row.createTime})
            </foreach>
            ON DUPLICATE KEY UPDATE id = id
            </script>
            """)
    int insertItemsIdempotent(@Param("rows") List<SettlementResultItemDO> rows);

    /** 按候选和行号读取完整结果，用于幂等身份验证。 */
    @Select("""
            SELECT *
            FROM settlement_result_item
            WHERE settlement_batch_no = #{settlementBatchNo}
            ORDER BY candidate_id ASC, result_line_no ASC, id ASC
            """)
    List<SettlementResultItemDO> selectItemsByBatch(
            @Param("settlementBatchNo") String settlementBatchNo);

    /** 只读取参与最终净额的候选财务组件，TRACE 和批次级入账行不会重复计入。 */
    @Select("""
            SELECT *
            FROM settlement_result_item
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND result_role = 'FINANCIAL_COMPONENT'
            ORDER BY candidate_id ASC, result_line_no ASC, id ASC
            """)
    List<SettlementResultItemDO> selectFinancialItemsByBatch(
            @Param("settlementBatchNo") String settlementBatchNo);

    /** 获取目标币种恒等汇率行，批次级净额必须显式引用该不可变汇率身份。 */
    @Select("""
            SELECT id
            FROM settlement_batch_rate
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND source_currency = #{targetCurrency}
              AND target_currency = #{targetCurrency}
              AND direct_rate = 1
              AND rate_status = 'LOCKED'
            LIMIT 1
            """)
    Long selectIdentityRateId(@Param("settlementBatchNo") String settlementBatchNo,
                              @Param("targetCurrency") String targetCurrency);

    /** 数据库回读确保一个批次恰好只有一条最终入账结果。 */
    @Select("""
            SELECT COUNT(1)
            FROM settlement_result_item
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND result_role = 'LEDGER_POSTING'
              AND result_item_type = 'NET_SETTLEMENT'
            """)
    int countLedgerPostingByBatch(@Param("settlementBatchNo") String settlementBatchNo);

    /** 锁定原批唯一净入账结果，冲正必须完整引用该不可变结果。 */
    @Select("""
            SELECT *
            FROM settlement_result_item
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND result_role = 'LEDGER_POSTING'
              AND result_item_type = 'NET_SETTLEMENT'
            LIMIT 1
            FOR UPDATE
            """)
    SettlementResultItemDO selectNetPostingForUpdate(
            @Param("settlementBatchNo") String settlementBatchNo);

    /** 管理详情只读查询每批唯一最终净入账结果，不获取资金写锁。 */
    @Select("""
            SELECT *
            FROM settlement_result_item
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND result_role = 'LEDGER_POSTING'
              AND result_item_type = 'NET_SETTLEMENT'
            LIMIT 1
            """)
    SettlementResultItemDO selectNetPosting(@Param("settlementBatchNo") String settlementBatchNo);

    /** 批量追加 FINANCIAL_COMPONENT 汇总，唯一维度冲突后必须回读验证金额和笔数。 */
    @Insert("""
            <script>
            INSERT INTO settlement_result_summary
            (settlement_batch_no, merchant_id, payment_type, payment_method, transaction_type,
             result_item_type, fee_category, direction, source_currency, target_currency,
             transaction_count, source_amount, target_amount, create_time)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.settlementBatchNo}, #{row.merchantId}, #{row.paymentType},
                 #{row.paymentMethod}, #{row.transactionType}, #{row.resultItemType},
                 #{row.feeCategory}, #{row.direction}, #{row.sourceCurrency},
                 #{row.targetCurrency}, #{row.transactionCount}, #{row.sourceAmount},
                 #{row.targetAmount}, #{row.createTime})
            </foreach>
            ON DUPLICATE KEY UPDATE id = id
            </script>
            """)
    int insertSummariesIdempotent(@Param("rows") List<SettlementResultSummaryDO> rows);

    /** 读取批次全部汇总用于幂等身份验证。 */
    @Select("""
            SELECT *
            FROM settlement_result_summary
            WHERE settlement_batch_no = #{settlementBatchNo}
            ORDER BY merchant_id, payment_type, payment_method, transaction_type,
                     result_item_type, fee_category, direction, source_currency, target_currency, id
            """)
    List<SettlementResultSummaryDO> selectSummariesByBatch(
            @Param("settlementBatchNo") String settlementBatchNo);
}
