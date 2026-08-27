package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingTransactionAbnormalEventDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 清分异常案件 Mapper；所有写入和关闭操作都携带交易分片时间。 */
public interface ClearingTransactionAbnormalEventMapper {

    /** 相同清分异常重复出现时增加次数，已关闭案件重新打开。 */
    @Insert("""
            INSERT INTO transaction_abnormal_event
            (abnormal_event_id, transaction_id, operation_id, abnormal_type, abnormal_level,
             event_status, source_record_type, source_record_id, abnormal_description,
             raw_reference_json, first_seen_time, transaction_date_time, transaction_utc_time,
             transaction_time_zone, deduplication_key, merchant_id, merchant_order_no,
             source_transaction_id, transaction_type, platform_status, channel_match_result,
             detect_source, last_seen_time, occurrence_count, merchant_notify_required,
             version, deleted, create_time, update_time)
            VALUES
            (#{row.abnormalEventId}, #{row.transactionId}, #{row.operationId}, #{row.abnormalType},
             #{row.abnormalLevel}, #{row.eventStatus}, #{row.sourceRecordType}, #{row.sourceRecordId},
             #{row.abnormalDescription}, #{row.rawReferenceJson}, #{row.firstSeenTime},
             #{row.transactionDateTime}, #{row.transactionUtcTime}, #{row.transactionTimeZone},
             #{row.deduplicationKey}, #{row.merchantId}, #{row.merchantOrderNo},
             #{row.sourceTransactionId}, #{row.transactionType}, #{row.platformStatus},
             #{row.channelMatchResult}, #{row.detectSource}, #{row.lastSeenTime},
             #{row.occurrenceCount}, #{row.merchantNotifyRequired}, #{row.version}, #{row.deleted},
             #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE
              abnormal_level = VALUES(abnormal_level),
              abnormal_description = VALUES(abnormal_description),
              raw_reference_json = VALUES(raw_reference_json),
              platform_status = VALUES(platform_status),
              last_seen_time = VALUES(last_seen_time),
              occurrence_count = occurrence_count + 1,
              resolved_time = CASE WHEN event_status IN ('RESOLVED', 'IGNORED') THEN NULL ELSE resolved_time END,
              resolution_type = CASE WHEN event_status IN ('RESOLVED', 'IGNORED') THEN NULL ELSE resolution_type END,
              resolution_reference_id = CASE WHEN event_status IN ('RESOLVED', 'IGNORED') THEN NULL ELSE resolution_reference_id END,
              event_status = CASE WHEN event_status IN ('RESOLVED', 'IGNORED') THEN 'OPEN' ELSE event_status END,
              version = version + 1,
              update_time = VALUES(update_time)
            """)
    int upsertOccurrence(@Param("row") ClearingTransactionAbnormalEventDO row);

    /** 完成清分后只关闭同一交易分片上的清分异常，不影响渠道勾兑案件。 */
    @Update("""
            UPDATE transaction_abnormal_event
            SET event_status = 'RESOLVED',
                resolution_type = 'CLEARING_AUTO_RECOVERED',
                resolution_reference_id = #{referenceId},
                resolved_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND abnormal_type IN (
                  'CLEARING_CONTROLLED_FAILURE', 'CLEARING_FINANCIAL_MISMATCH',
                  'CLEARING_PROJECTION_MISMATCH', 'CLEARING_MANUAL_REVIEW')
              AND event_status IN ('OPEN', 'PROCESSING')
              AND deleted = 0
            """)
    int resolveActiveClearingCases(@Param("transactionId") String transactionId,
                                   @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                   @Param("referenceId") String referenceId,
                                   @Param("now") LocalDateTime now);
}
