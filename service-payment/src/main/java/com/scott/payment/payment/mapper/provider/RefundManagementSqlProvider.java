package com.scott.payment.payment.mapper.provider;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundManagementSqlProvider
 * @date : 2026-08-06 15:45
 * @email : scott_x@163.com
 * @description : 退款管理查询 SQL Provider，只拼接受控表名和固定条件，所有业务输入继续通过 MyBatis 参数绑定。
 * @status : create
 */
public final class RefundManagementSqlProvider {

    private static final String FROM_SQL = """
            FROM transaction_operation o
            LEFT JOIN transaction_refund_approval a
              ON a.refund_transaction_id = o.transaction_id
             AND a.merchant_id = o.merchant_id
            LEFT JOIN transaction_payment_method_info p
              ON p.transaction_id = o.transaction_id
             AND p.transaction_date_time = o.transaction_date_time
            """;

    private static final String WHERE_SQL = """
            WHERE o.deleted = 0
              AND o.transaction_type IN ('REFUND', 'VOID')
              AND o.transaction_date_time >= #{beginTime}
              AND o.transaction_date_time < #{endTimeExclusive}
              <if test="query.merchantId != null and query.merchantId != ''">AND o.merchant_id = #{query.merchantId}</if>
              <if test="query.refundTransactionId != null and query.refundTransactionId != ''">AND o.transaction_id = #{query.refundTransactionId}</if>
              <if test="query.sourceTransactionId != null and query.sourceTransactionId != ''">AND o.source_transaction_id = #{query.sourceTransactionId}</if>
              <if test="query.merchantOrderNo != null and query.merchantOrderNo != ''">AND o.merchant_order_no = #{query.merchantOrderNo}</if>
              <if test="query.merchantOperationNo != null and query.merchantOperationNo != ''">AND o.merchant_operation_no = #{query.merchantOperationNo}</if>
              <if test="query.transactionType != null and query.transactionType != ''">AND o.transaction_type = #{query.transactionType}</if>
              <if test="query.refundScope != null and query.refundScope != ''">AND o.refund_scope = #{query.refundScope}</if>
              <if test="query.transactionStatus != null and query.transactionStatus != ''">AND o.transaction_status = #{query.transactionStatus}</if>
              <if test="query.requestSource != null and query.requestSource != ''">AND o.request_source = #{query.requestSource}</if>
              <if test="query.channelCode != null and query.channelCode != ''">AND o.channel_code = #{query.channelCode}</if>
              <if test="query.channelOrderNo != null and query.channelOrderNo != ''">AND o.channel_order_no = #{query.channelOrderNo}</if>
              <if test="query.acquirerReferenceNo != null and query.acquirerReferenceNo != ''">AND o.acquirer_reference_no = #{query.acquirerReferenceNo}</if>
              <if test="query.paymentMethod != null and query.paymentMethod != ''">AND p.payment_method = #{query.paymentMethod}</if>
              <if test="query.paymentBrand != null and query.paymentBrand != ''">AND p.payment_brand = #{query.paymentBrand}</if>
              <if test="query.labelCurrency != null and query.labelCurrency != ''">AND o.label_currency = #{query.labelCurrency}</if>
              <if test="query.transactionCurrency != null and query.transactionCurrency != ''">AND o.transaction_currency = #{query.transactionCurrency}</if>
              <if test="query.minimumTransactionAmount != null">AND o.transaction_amount >= #{query.minimumTransactionAmount}</if>
              <if test="query.maximumTransactionAmount != null">AND o.transaction_amount <= #{query.maximumTransactionAmount}</if>
              <if test="query.applicantId != null and query.applicantId != ''">AND o.applicant_id = #{query.applicantId}</if>
              <if test="query.completeBeginTime != null">AND o.complete_time >= #{query.completeBeginTime}</if>
              <if test="query.completeEndTime != null">AND o.complete_time < #{query.completeEndTime}</if>
              <if test="query.approvalStatus != null and query.approvalStatus != ''">
                AND (CASE
                    WHEN o.transaction_type = 'VOID' THEN 'NOT_APPLICABLE'
                    WHEN a.id IS NULL THEN 'NOT_REQUIRED'
                    ELSE a.approval_status
                END) = #{query.approvalStatus}
              </if>
            """;

    private static final String RECORD_COLUMNS = """
            o.transaction_id AS refund_transaction_id,
            o.operation_id, o.source_transaction_id, o.merchant_id, o.merchant_order_no,
            o.merchant_operation_no, o.transaction_type, o.refund_scope, o.request_source,
            o.request_reason, o.applicant_type, o.applicant_id, o.applicant_name,
            o.execution_mode, o.transaction_status, o.process_stage,
            o.fail_reason_code, o.fail_reason_message, o.label_currency, o.label_amount,
            o.transaction_currency, o.transaction_amount, o.currency_exponent,
            p.payment_method, p.payment_brand, o.channel_code, o.channel_order_no,
            o.channel_transaction_id, o.channel_response_code, o.acquirer_reference_no,
            o.channel_match_status, NULL AS merchant_notification_status,
            o.transaction_date_time, o.complete_time,
            a.approval_id,
            CASE WHEN o.transaction_type = 'VOID' THEN 'NOT_APPLICABLE'
                 WHEN a.id IS NULL THEN 'NOT_REQUIRED'
                 ELSE a.approval_status END AS approval_status,
            a.approval_policy_code, a.approval_operator_id, a.approval_operator_name,
            a.approval_time, a.approval_reason, a.expire_time AS approval_expire_time,
            a.execution_event_id, a.version AS approval_version
            """;

    private RefundManagementSqlProvider() {
    }

    /** @return 退款总数 SQL */
    public static String countSql() {
        return script("SELECT COUNT(DISTINCT o.id) " + FROM_SQL + WHERE_SQL);
    }

    /** @return 退款分页 SQL */
    public static String pageSql() {
        return script("SELECT " + RECORD_COLUMNS + FROM_SQL + WHERE_SQL + """
                ORDER BY o.transaction_date_time DESC, o.id DESC
                LIMIT #{offset}, #{limit}
                """);
    }

    /** @return 退款状态统计 SQL */
    public static String statusSummarySql() {
        return script("""
                SELECT COUNT(DISTINCT o.id) AS total_count,
                       COUNT(DISTINCT CASE WHEN a.approval_status = 'PENDING' THEN o.id END) AS pending_approval_count,
                       COUNT(DISTINCT CASE WHEN o.transaction_status IN ('PENDING', 'PROCESSING') THEN o.id END) AS processing_count,
                       COUNT(DISTINCT CASE WHEN o.transaction_status = 'SUCCESS' THEN o.id END) AS success_count,
                       COUNT(DISTINCT CASE WHEN o.transaction_status = 'FAILED'
                             OR a.approval_status IN ('REJECTED', 'EXPIRED') THEN o.id END) AS failed_or_rejected_count
                """ + FROM_SQL + WHERE_SQL);
    }

    /** @return 退款分币种金额统计 SQL */
    public static String currencySummarySql() {
        return script("""
                SELECT o.transaction_currency AS currency,
                       COALESCE(SUM(CASE WHEN o.transaction_status = 'SUCCESS' THEN o.transaction_amount ELSE 0 END), 0) AS successful_amount,
                       COALESCE(SUM(CASE WHEN o.transaction_status IN ('PENDING', 'PROCESSING') THEN o.transaction_amount ELSE 0 END), 0) AS pending_amount
                """ + FROM_SQL + WHERE_SQL + """
                GROUP BY o.transaction_currency
                ORDER BY o.transaction_currency
                """);
    }

    /** @return 单笔退款详情 SQL */
    public static String detailSql() {
        return script("SELECT " + RECORD_COLUMNS + FROM_SQL + """
                WHERE o.transaction_id = #{transactionId}
                  AND o.transaction_date_time = #{transactionDateTime}
                  AND o.transaction_type IN ('REFUND', 'VOID')
                  AND o.deleted = 0
                  <if test="merchantId != null and merchantId != ''">AND o.merchant_id = #{merchantId}</if>
                LIMIT 1
                """);
    }

    private static String script(String sql) {
        return "<script>" + sql + "</script>";
    }
}
