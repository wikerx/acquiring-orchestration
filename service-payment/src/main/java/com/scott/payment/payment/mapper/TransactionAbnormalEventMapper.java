package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionAbnormalEventDO;
import com.scott.payment.payment.service.dto.reconciliation.ChannelMatchAbnormalDTOs.AbnormalRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionAbnormalEventMapper
 * @date : 2026-08-06 00:00
 * @description : 勾兑异常案件 Mapper，仅访问 transaction_abnormal_event 逻辑表并要求所有更新携带分片时间和版本。
 * @status : create
 */
public interface TransactionAbnormalEventMapper extends BaseMapper<TransactionAbnormalEventDO> {

    /**
     * 写入异常案件逻辑表。
     *
     * @param row 异常案件
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_abnormal_event
            (abnormal_event_id, transaction_id, operation_id, abnormal_type, abnormal_level,
             event_status, source_record_type, source_record_id, abnormal_description,
             raw_reference_json, first_seen_time, transaction_date_time, transaction_utc_time,
             transaction_time_zone, deduplication_key, merchant_id, merchant_order_no,
             source_transaction_id, source_transaction_date_time, root_transaction_date_time,
             transaction_type, platform_status, channel_code, channel_order_no,
             channel_transaction_id, channel_status, channel_match_result, detect_source,
             platform_currency, platform_amount, channel_currency, channel_amount,
             amount_difference, currency_exponent, last_seen_time, occurrence_count,
             merchant_notify_required, version, deleted, create_time, update_time)
            VALUES
            (#{row.abnormalEventId}, #{row.transactionId}, #{row.operationId}, #{row.abnormalType},
             #{row.abnormalLevel}, #{row.eventStatus}, #{row.sourceRecordType}, #{row.sourceRecordId},
             #{row.abnormalDescription}, #{row.rawReferenceJson}, #{row.firstSeenTime},
             #{row.transactionDateTime}, #{row.transactionUtcTime}, #{row.transactionTimeZone},
             #{row.deduplicationKey}, #{row.merchantId}, #{row.merchantOrderNo},
             #{row.sourceTransactionId}, #{row.sourceTransactionDateTime}, #{row.rootTransactionDateTime},
             #{row.transactionType}, #{row.platformStatus}, #{row.channelCode}, #{row.channelOrderNo},
             #{row.channelTransactionId}, #{row.channelStatus}, #{row.channelMatchResult},
             #{row.detectSource}, #{row.platformCurrency}, #{row.platformAmount},
             #{row.channelCurrency}, #{row.channelAmount}, #{row.amountDifference},
             #{row.currencyExponent}, #{row.lastSeenTime}, #{row.occurrenceCount},
             #{row.merchantNotifyRequired}, #{row.version}, #{row.deleted},
             #{row.createTime}, #{row.updateTime})
            """)
    int insertLogical(@Param("row") TransactionAbnormalEventDO row);

    /**
     * 原子新增或更新同一交易、同一异常类型的案件；重复发现只增加次数，已关闭案件重新打开。
     *
     * @param row 最新脱敏异常快照
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_abnormal_event
            (abnormal_event_id, transaction_id, operation_id, abnormal_type, abnormal_level,
             event_status, source_record_type, source_record_id, abnormal_description,
             raw_reference_json, first_seen_time, transaction_date_time, transaction_utc_time,
             transaction_time_zone, deduplication_key, merchant_id, merchant_order_no,
             source_transaction_id, source_transaction_date_time, root_transaction_date_time,
             transaction_type, platform_status, channel_code, channel_order_no,
             channel_transaction_id, channel_status, channel_match_result, detect_source,
             platform_currency, platform_amount, channel_currency, channel_amount,
             amount_difference, currency_exponent, last_seen_time, occurrence_count,
             merchant_notify_required, version, deleted, create_time, update_time)
            VALUES
            (#{row.abnormalEventId}, #{row.transactionId}, #{row.operationId}, #{row.abnormalType},
             #{row.abnormalLevel}, #{row.eventStatus}, #{row.sourceRecordType}, #{row.sourceRecordId},
             #{row.abnormalDescription}, #{row.rawReferenceJson}, #{row.firstSeenTime},
             #{row.transactionDateTime}, #{row.transactionUtcTime}, #{row.transactionTimeZone},
             #{row.deduplicationKey}, #{row.merchantId}, #{row.merchantOrderNo},
             #{row.sourceTransactionId}, #{row.sourceTransactionDateTime}, #{row.rootTransactionDateTime},
             #{row.transactionType}, #{row.platformStatus}, #{row.channelCode}, #{row.channelOrderNo},
             #{row.channelTransactionId}, #{row.channelStatus}, #{row.channelMatchResult},
             #{row.detectSource}, #{row.platformCurrency}, #{row.platformAmount},
             #{row.channelCurrency}, #{row.channelAmount}, #{row.amountDifference},
             #{row.currencyExponent}, #{row.lastSeenTime}, 1, 0, 0, 0,
             #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE
              abnormal_level = VALUES(abnormal_level),
              abnormal_description = VALUES(abnormal_description),
              raw_reference_json = VALUES(raw_reference_json),
              platform_status = VALUES(platform_status),
              channel_status = VALUES(channel_status),
              channel_match_result = VALUES(channel_match_result),
              detect_source = VALUES(detect_source),
              platform_currency = VALUES(platform_currency),
              platform_amount = VALUES(platform_amount),
              channel_currency = VALUES(channel_currency),
              channel_amount = VALUES(channel_amount),
              amount_difference = VALUES(amount_difference),
              currency_exponent = VALUES(currency_exponent),
              last_seen_time = VALUES(last_seen_time),
              occurrence_count = occurrence_count + 1,
              resolved_time = CASE WHEN event_status IN ('RESOLVED', 'IGNORED') THEN NULL ELSE resolved_time END,
              resolution_type = CASE WHEN event_status IN ('RESOLVED', 'IGNORED') THEN NULL ELSE resolution_type END,
              resolution_reference_id = CASE WHEN event_status IN ('RESOLVED', 'IGNORED') THEN NULL ELSE resolution_reference_id END,
              event_status = CASE WHEN event_status IN ('RESOLVED', 'IGNORED') THEN 'OPEN' ELSE event_status END,
              version = version + 1,
              update_time = VALUES(update_time)
            """)
    int upsertOccurrence(@Param("row") TransactionAbnormalEventDO row);

    /**
     * 使用去重键和精确分片时间锁定案件。
     *
     * @return 已存在案件
     */
    @Select("""
            SELECT *
            FROM transaction_abnormal_event
            WHERE deduplication_key = #{deduplicationKey}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    TransactionAbnormalEventDO selectByDeduplicationKeyForUpdate(
            @Param("deduplicationKey") String deduplicationKey,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 记录相同异常再次出现，终态案件会重新打开。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_abnormal_event
            SET last_seen_time = #{seenTime},
                occurrence_count = occurrence_count + 1,
                resolved_time = CASE WHEN event_status IN ('RESOLVED', 'IGNORED') THEN NULL ELSE resolved_time END,
                event_status = CASE WHEN event_status IN ('RESOLVED', 'IGNORED') THEN 'OPEN' ELSE event_status END,
                version = version + 1,
                update_time = #{seenTime}
            WHERE id = #{id}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int touchOccurrence(@Param("id") Long id,
                        @Param("transactionDateTime") LocalDateTime transactionDateTime,
                        @Param("expectedVersion") Integer expectedVersion,
                        @Param("seenTime") LocalDateTime seenTime);

    /**
     * 使用案件号和分片时间查询案件。
     *
     * @return 异常案件
     */
    @Select("""
            SELECT *
            FROM transaction_abnormal_event
            WHERE abnormal_event_id = #{eventId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            """)
    TransactionAbnormalEventDO selectByEventId(@Param("eventId") String eventId,
                                               @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /** @return 精确案件详情投影 */
    @Select("""
            SELECT abnormal_event_id, transaction_id, operation_id, abnormal_type, abnormal_level,
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
            FROM transaction_abnormal_event
            WHERE abnormal_event_id = #{eventId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            """)
    AbnormalRecord selectRecord(@Param("eventId") String eventId,
                                @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 领取或转派未关闭案件。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_abnormal_event
            SET event_status = 'PROCESSING',
                assigned_to_id = #{operatorId},
                assigned_to_name = #{operatorName},
                assigned_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE abnormal_event_id = #{eventId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND event_status IN ('OPEN', 'PROCESSING')
              AND deleted = 0
            """)
    int assign(@Param("eventId") String eventId,
               @Param("transactionDateTime") LocalDateTime transactionDateTime,
               @Param("expectedVersion") Integer expectedVersion,
               @Param("operatorId") String operatorId,
               @Param("operatorName") String operatorName,
               @Param("now") LocalDateTime now);

    /**
     * 关闭或忽略案件，不修改交易终态。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_abnormal_event
            SET event_status = #{targetStatus},
                resolution_type = #{resolutionType},
                resolution_reference_id = #{referenceId},
                resolved_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE abnormal_event_id = #{eventId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND event_status IN ('OPEN', 'PROCESSING')
              AND deleted = 0
            """)
    int resolve(@Param("eventId") String eventId,
                @Param("transactionDateTime") LocalDateTime transactionDateTime,
                @Param("expectedVersion") Integer expectedVersion,
                @Param("targetStatus") String targetStatus,
                @Param("resolutionType") String resolutionType,
                @Param("referenceId") String referenceId,
                @Param("now") LocalDateTime now);

    /**
     * 渠道结果已经由正常状态机确认时幂等关闭活动案件，不修改交易状态。
     *
     * @return 关闭案件数
     */
    @Update("""
            UPDATE transaction_abnormal_event
            SET event_status = 'RESOLVED',
                resolution_type = 'AUTO_RECOVERED',
                resolution_reference_id = #{referenceId},
                resolved_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND event_status IN ('OPEN', 'PROCESSING')
              AND deleted = 0
            """)
    int resolveActiveByTransaction(@Param("transactionId") String transactionId,
                                   @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                   @Param("referenceId") String referenceId,
                                   @Param("now") LocalDateTime now);
}
