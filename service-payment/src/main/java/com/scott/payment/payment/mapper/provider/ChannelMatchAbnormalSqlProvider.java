package com.scott.payment.payment.mapper.provider;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelMatchAbnormalSqlProvider
 * @date : 2026-08-06 00:00
 * @description : 勾兑异常管理查询 SQL Provider，只使用逻辑表和绑定参数生成分页与统计 SQL。
 * @status : create
 */
public final class ChannelMatchAbnormalSqlProvider {

    private static final String COLUMNS = """
            abnormal_event_id, transaction_id, operation_id, abnormal_type, abnormal_level,
            event_status, source_record_type, source_record_id, abnormal_description,
            raw_reference_json, first_seen_time, last_seen_time, resolved_time,
            transaction_date_time, source_transaction_date_time, root_transaction_date_time,
            merchant_id, merchant_order_no, source_transaction_id, transaction_type,
            platform_status, channel_code, channel_order_no, channel_transaction_id,
            channel_status, channel_match_result, detect_source, platform_currency,
            platform_amount, channel_currency, channel_amount, amount_difference,
            currency_exponent, occurrence_count, assigned_to_id, assigned_to_name,
            assigned_time, resolution_type, resolution_reference_id, merchant_notify_required,
            version, create_time, update_time
            """;

    private static final String WHERE = """
            FROM transaction_abnormal_event
            WHERE deleted = 0
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              <if test="query.eventId != null and query.eventId != ''">AND abnormal_event_id = #{query.eventId}</if>
              <if test="query.transactionId != null and query.transactionId != ''">AND transaction_id = #{query.transactionId}</if>
              <if test="query.merchantId != null and query.merchantId != ''">AND merchant_id = #{query.merchantId}</if>
              <if test="query.merchantOrderNo != null and query.merchantOrderNo != ''">AND merchant_order_no = #{query.merchantOrderNo}</if>
              <if test="query.abnormalType != null and query.abnormalType != ''">AND abnormal_type = #{query.abnormalType}</if>
              <if test="query.abnormalLevel != null and query.abnormalLevel != ''">AND abnormal_level = #{query.abnormalLevel}</if>
              <if test="query.eventStatus != null and query.eventStatus != ''">AND event_status = #{query.eventStatus}</if>
              <if test="query.transactionType != null and query.transactionType != ''">AND transaction_type = #{query.transactionType}</if>
              <if test="query.platformStatus != null and query.platformStatus != ''">AND platform_status = #{query.platformStatus}</if>
              <if test="query.channelCode != null and query.channelCode != ''">AND channel_code = #{query.channelCode}</if>
              <if test="query.channelOrderNo != null and query.channelOrderNo != ''">AND channel_order_no = #{query.channelOrderNo}</if>
              <if test="query.assignedToId != null and query.assignedToId != ''">AND assigned_to_id = #{query.assignedToId}</if>
              <if test="query.detectSource != null and query.detectSource != ''">AND detect_source = #{query.detectSource}</if>
              <if test="query.minimumOccurrenceCount != null">AND occurrence_count >= #{query.minimumOccurrenceCount}</if>
            """;

    private ChannelMatchAbnormalSqlProvider() {
    }

    /** @return 案件数量 SQL */
    public static String countSql() {
        return script("SELECT COUNT(1) " + WHERE);
    }

    /** @return 案件分页 SQL */
    public static String pageSql() {
        return script("SELECT " + COLUMNS + WHERE + """
                ORDER BY first_seen_time DESC, id DESC
                LIMIT #{offset}, #{limit}
                """);
    }

    /** @return 案件状态统计 SQL */
    public static String summarySql() {
        return script("""
                SELECT COUNT(1) AS total_count,
                       SUM(CASE WHEN event_status = 'OPEN' THEN 1 ELSE 0 END) AS open_count,
                       SUM(CASE WHEN event_status = 'PROCESSING' THEN 1 ELSE 0 END) AS processing_count,
                       SUM(CASE WHEN event_status = 'RESOLVED' THEN 1 ELSE 0 END) AS resolved_count,
                       SUM(CASE WHEN event_status = 'IGNORED' THEN 1 ELSE 0 END) AS ignored_count,
                       SUM(CASE WHEN abnormal_level IN ('HIGH', 'CRITICAL')
                                AND event_status IN ('OPEN', 'PROCESSING') THEN 1 ELSE 0 END) AS high_or_critical_count
                """ + WHERE);
    }

    private static String script(String sql) {
        return "<script>" + sql + "</script>";
    }
}
