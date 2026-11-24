package com.scott.payment.component.db.settlement;

/**
 * 管理端和商户端共用的结算汇总只读投影。投影以已冻结的结果为准，
 * 按关联交易类型拆分保证金释放和调整结果，并为缺少来源的金额保留可核对的汇总行。
 */
public final class SettlementSummaryProjection {

    private SettlementSummaryProjection() {
    }

    /**
     * 在调用方已限定批次及商户范围的汇总上计算分页视图。
     *
     * @param scopePredicate 调用方定义的固定 SQL 条件，业务输入始终通过绑定参数传入
     * @return 可用于统计总行数和读取分页记录的公共表表达式
     */
    public static String scopedRows(String scopePredicate) {
        return """
                WITH frozen_summary AS (
                    SELECT summary.*, batch.batch_type, batch.target_currency_exponent,
                           CASE
                               WHEN batch.batch_type = 'RESERVE_RELEASE' AND summary.result_item_type = 'RESERVE_RELEASE'
                                   THEN 'RELEASE'
                               WHEN batch.batch_type = 'ADJUSTMENT' AND summary.result_item_type = 'ADJUSTMENT'
                                   THEN 'ADJUSTMENT'
                           END AS reserve_action_type
                    FROM settlement_result_summary summary
                    JOIN settlement_batch batch
                      ON batch.settlement_batch_no = summary.settlement_batch_no
                     AND batch.merchant_id = summary.merchant_id
                """ + scopePredicate + """

                ), reserve_parts AS (
                    SELECT frozen.id AS summary_id, NULLIF(TRIM(locator.transaction_type), '') AS transaction_type,
                           COUNT(DISTINCT item.candidate_id) AS transaction_count,
                           SUM(item.source_amount) AS source_amount, SUM(item.target_amount) AS target_amount
                    FROM frozen_summary frozen
                    JOIN settlement_result_item item
                      ON item.settlement_batch_no = frozen.settlement_batch_no
                     AND item.merchant_id = frozen.merchant_id
                     AND COALESCE(item.payment_type, 'NONE') = frozen.payment_type
                     AND COALESCE(item.payment_method, 'NONE') = frozen.payment_method
                     AND COALESCE(item.transaction_type, 'NONE') = frozen.transaction_type
                     AND item.result_item_type = frozen.result_item_type
                     AND COALESCE(item.fee_category, 'NONE') = frozen.fee_category
                     AND item.direction = frozen.direction
                     AND item.source_currency = frozen.source_currency
                     AND item.target_currency = frozen.target_currency
                    LEFT JOIN merchant_reserve_action action
                      ON action.settlement_batch_no = item.settlement_batch_no
                     AND action.candidate_id = item.candidate_id
                     AND action.source_reserve_detail_no = item.source_detail_no
                     AND action.action_type = frozen.reserve_action_type
                    LEFT JOIN merchant_reserve_item reserve
                      ON reserve.id = action.reserve_item_id
                     AND reserve.reserve_no = action.reserve_no
                     AND reserve.merchant_id = item.merchant_id
                    LEFT JOIN transaction_locator locator
                      ON locator.transaction_id = reserve.source_transaction_id
                     AND locator.merchant_id = reserve.merchant_id
                    WHERE frozen.reserve_action_type IS NOT NULL
                      AND item.source_detail_type = 'RESERVE_CLEARING'
                      AND item.result_role = 'FINANCIAL_COMPONENT'
                    GROUP BY frozen.id, NULLIF(TRIM(locator.transaction_type), '')
                ), reserve_totals AS (
                    SELECT summary_id, SUM(transaction_count) AS transaction_count,
                           SUM(source_amount) AS source_amount, SUM(target_amount) AS target_amount
                    FROM reserve_parts GROUP BY summary_id
                ), projected_summary AS (
                    SELECT frozen.id, frozen.merchant_id, frozen.batch_type, frozen.payment_type, frozen.payment_method,
                           CASE WHEN frozen.reserve_action_type IS NOT NULL
                                THEN part.transaction_type ELSE frozen.transaction_type END AS transaction_type,
                           frozen.result_item_type, frozen.fee_category, frozen.direction, frozen.source_currency,
                           frozen.target_currency, frozen.target_currency_exponent,
                           COALESCE(part.transaction_count, frozen.transaction_count) AS transaction_count,
                           COALESCE(part.source_amount, frozen.source_amount) AS source_amount,
                           COALESCE(part.target_amount, frozen.target_amount) AS target_amount
                    FROM frozen_summary frozen
                    LEFT JOIN reserve_totals totals
                      ON totals.summary_id = frozen.id
                     AND totals.transaction_count = frozen.transaction_count
                     AND totals.source_amount = frozen.source_amount
                     AND totals.target_amount = frozen.target_amount
                    LEFT JOIN reserve_parts part ON part.summary_id = totals.summary_id
                )
                """;
    }

    /**
     * 读取投影汇总并沿用原维度顺序。拆分行在金额和笔数确定后参与分页。
     */
    public static final String SELECT_ROWS = """
            SELECT summary.payment_type, summary.payment_method, summary.transaction_type,
                   summary.result_item_type, summary.fee_category, summary.direction,
                   summary.source_currency,
                   COALESCE(NULLIF(source_currency.fraction_digits, -1), 2) AS source_currency_exponent,
                   summary.target_currency, summary.target_currency_exponent,
                   summary.transaction_count, summary.source_amount, summary.target_amount, summary.batch_type
            FROM projected_summary summary
            LEFT JOIN base_iso_currency source_currency
              ON source_currency.alpha3_code = summary.source_currency
             AND source_currency.deleted = 0
            ORDER BY summary.merchant_id, summary.payment_type, summary.payment_method,
                     summary.transaction_type, summary.result_item_type, summary.fee_category,
                     summary.direction, summary.source_currency, summary.target_currency, summary.id
            """;

    /**
     * 仅使用服务端规范化后的数值拼接 LIMIT，规避 ShardingSphere 对带绑定参数 CTE 的分页索引错位。
     *
     * @param offset 规范化页码计算出的偏移量
     * @param limit 查询预算内的页大小
     * @return 固定格式的 MySQL 分页子句
     */
    public static String pagination(long offset, int limit) {
        return "LIMIT %d, %d".formatted(offset, limit);
    }
}
