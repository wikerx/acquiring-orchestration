package com.scott.payment.component.db.settlement;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

/** 非写入查询，按授权商户、交易精确身份和清分修订读取已保存的费用事实。 */
public final class TransactionFeeFactQuery {
    private TransactionFeeFactQuery() { }

    /** 清分组件的原币金额和相应冻结的收费条件。十进制值按文本传递。 */
    public record ClearingLine(String groupNo, String detailNo, String itemType, String feeCategory,
                               String riskServiceType, String componentType, String direction,
                               String currency, Integer currencyExponent, String amount,
                               String basisCurrency, String basisAmount, String percentageRate,
                               String minimumAmountUsd, String maximumAmountUsd,
                               String limitEvaluationStatus, String appliedLimit) { }

    /** 结算跟踪行及最终入账费用行，原值、舍入前值、最终值分别保留。 */
    public record SettlementLine(String groupNo, String detailNo, String batchNo, String batchStatus,
                                 String resultRole, String feeCategory, String direction,
                                 String sourceCurrency, String sourceAmount, String rate,
                                 String currency, Integer currencyExponent, String unroundedAmount,
                                 String amount, String appliedLimit) { }

    /** 关联动作的复合查询身份由商户范围内的定位记录给出。 */
    public record SourceAction(String transactionId, LocalDateTime transactionDateTime, String transactionType) { }

    /** 一次详情读取所得的费用事实。清分及结算读模型各有独立版本边界。 */
    public record Facts(List<ClearingLine> clearing, List<SettlementLine> settlement,
                        SourceAction sourceAction, boolean complete) { }

    /** 查询已有费用明细，保留跟踪角色及金额的小数位，超出结果预算时明确标记。 */
    public static Facts read(NamedParameterJdbcTemplate jdbc, String merchantId, String transactionId,
                             LocalDateTime transactionDateTime, int revision, String sourceTransactionId,
                             int maxResultRows) {
        int limit = Math.min(maxResultRows, 501);
        MapSqlParameterSource identity = new MapSqlParameterSource()
                .addValue("merchantId", merchantId).addValue("transactionId", transactionId)
                .addValue("transactionDateTime", transactionDateTime).addValue("revision", revision)
                .addValue("rowLimit", limit);
        List<ClearingLine> clearing = jdbc.query("""
                SELECT fee_group_no, clearing_detail_no, item_type, fee_category, risk_service_type,
                       component_type, direction, currency, currency_exponent, amount, basis_currency,
                       basis_amount, percentage_rate, minimum_amount_usd, maximum_amount_usd,
                       limit_evaluation_status, applied_limit
                FROM transaction_clearing_detail
                WHERE transaction_id = :transactionId AND transaction_date_time = :transactionDateTime
                  AND merchant_id = :merchantId AND clearing_revision = :revision AND record_status = 'ACTIVE'
                  AND item_type IN ('PLATFORM_FEE', 'FEE_REVERSAL')
                ORDER BY line_no, id LIMIT :rowLimit
                """, identity, (row, index) -> new ClearingLine(row.getString("fee_group_no"),
                row.getString("clearing_detail_no"), row.getString("item_type"), row.getString("fee_category"),
                row.getString("risk_service_type"), row.getString("component_type"), row.getString("direction"),
                row.getString("currency"), row.getInt("currency_exponent"), row.getString("amount"),
                row.getString("basis_currency"), row.getString("basis_amount"), row.getString("percentage_rate"),
                row.getString("minimum_amount_usd"), row.getString("maximum_amount_usd"),
                row.getString("limit_evaluation_status"), row.getString("applied_limit")));
        List<SettlementLine> settlement = jdbc.query("""
                SELECT item.fee_group_no, item.source_detail_no, item.settlement_batch_no, batch.batch_status,
                       item.result_role, item.fee_category, item.direction, item.source_currency,
                       item.source_amount, rate.direct_rate, item.target_currency,
                       item.target_currency_exponent, item.unrounded_target_amount, item.target_amount,
                       item.applied_limit
                FROM settlement_candidate candidate
                JOIN settlement_batch batch ON batch.settlement_batch_no = candidate.settlement_batch_no
                  AND batch.merchant_id = candidate.merchant_id
                JOIN settlement_result_item item ON item.candidate_id = candidate.id
                  AND item.settlement_batch_no = batch.settlement_batch_no AND item.merchant_id = :merchantId
                JOIN settlement_batch_rate rate ON rate.id = item.settlement_batch_rate_id
                WHERE candidate.source_type = 'CLEARING_REVISION' AND candidate.source_revision = :revision
                  AND candidate.source_transaction_id = :transactionId
                  AND candidate.source_transaction_date_time = :transactionDateTime AND candidate.merchant_id = :merchantId
                  AND item.source_transaction_id = :transactionId AND item.source_transaction_date_time = :transactionDateTime
                  AND item.source_detail_type = 'TRANSACTION_CLEARING'
                  AND batch.batch_type = 'REGULAR' AND batch.batch_status IN ('POSTED', 'REVERSED')
                  AND item.result_item_type IN ('FEE_COMPONENT', 'FEE_GROUP_FINAL')
                ORDER BY item.result_line_no, item.id LIMIT :rowLimit
                """, identity, (row, index) -> new SettlementLine(row.getString("fee_group_no"),
                row.getString("source_detail_no"), row.getString("settlement_batch_no"), row.getString("batch_status"),
                row.getString("result_role"), row.getString("fee_category"), row.getString("direction"),
                row.getString("source_currency"), row.getString("source_amount"), row.getString("direct_rate"),
                row.getString("target_currency"), row.getInt("target_currency_exponent"),
                row.getString("unrounded_target_amount"), row.getString("target_amount"), row.getString("applied_limit")));
        SourceAction source = sourceTransactionId == null || sourceTransactionId.isBlank() ? null
                : jdbc.query("""
                    SELECT transaction_id, transaction_date_time, transaction_type FROM transaction_locator
                    WHERE transaction_id = :sourceTransactionId AND merchant_id = :merchantId LIMIT 1
                    """, identity.addValue("sourceTransactionId", sourceTransactionId),
                    (row, index) -> new SourceAction(row.getString("transaction_id"),
                            row.getObject("transaction_date_time", LocalDateTime.class),
                            row.getString("transaction_type"))).stream().findFirst().orElse(null);
        return new Facts(clearing, settlement, source, clearing.size() < limit && settlement.size() < limit);
    }
}
