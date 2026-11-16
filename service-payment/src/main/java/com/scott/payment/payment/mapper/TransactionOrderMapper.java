package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易生命周期主单 Mapper，位于 service-payment 数据访问层，仅访问 transaction_order 逻辑表并显式携带交易分片时间。
 */
public interface TransactionOrderMapper extends BaseMapper<TransactionOrderDO> {

    /**
     * 主单稳定投影。显式列序保证结算字段与 ShardingSphere 缓存的逻辑表元数据解耦。
     */
    String ORDER_SELECT_COLUMNS = """
            id, operation_id, root_transaction_id, latest_transaction_id,
            merchant_id, merchant_order_no, merchant_order_id, source_transaction_id,
            payment_method, payment_brand, transaction_type, transaction_status, process_stage,
            pending_reason_code, fail_reason_code, fail_reason_message,
            merchant_visible_message, payer_visible_message,
            label_currency, label_amount, label_currency_exponent, transaction_currency, transaction_amount,
            channel_request_currency, channel_request_amount,
            settlement_currency, settlement_amount, settlement_rate, settlement_date,
            settlement_transaction_id, settlement_transaction_date_time,
            currency_exponent, dcc_enabled, edc_enabled, transaction_rate, rate_source, rate_time,
            label_authorized_amount, label_authorized_cancel_amount, label_captured_amount,
            label_refunded_amount, label_chargeback_amount,
            label_available_capture_amount, label_available_refund_amount,
            authorized_amount, authorized_cancel_amount, captured_amount, refunded_amount,
            chargeback_amount, available_capture_amount, available_refund_amount,
            channel_match_status, settlement_status, reconciliation_status, accounting_status,
            channel_match_result, channel_match_count, last_channel_match_request_id,
            last_channel_match_time, next_channel_match_time, channel_match_fail_reason,
            settlement_batch_no, reconciliation_batch_no,
            channel_id, channel_code, channel_mid_config_id, channel_merchant_id, channel_order_no,
            internal_risk_decision, internal_risk_record_no,
            merchant_website, callback_url, redirect_url, language,
            transaction_date_time, transaction_utc_time, transaction_time_zone,
            transaction_timezone_offset, last_status_time, version, deleted, create_time, update_time
            """;

    /**
     * 通过交易分片时间在逻辑表中查询生命周期主单。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param transactionDateTime 交易分片时间
     * @return 交易生命周期主单，不存在时返回 null
     */
    @Select("""
            SELECT
            """ + ORDER_SELECT_COLUMNS + """
            FROM transaction_order
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            """)
    TransactionOrderDO selectByOperationId(@Param("operationId") String operationId,
                                           @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 通过交易分片时间在逻辑表中锁定唯一生命周期主单。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param transactionDateTime 交易分片时间
     * @return 已在当前事务内锁定的生命周期主单，不存在时返回 null
     */
    @Select("""
            SELECT
            """ + ORDER_SELECT_COLUMNS + """
            FROM transaction_order
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    TransactionOrderDO selectByOperationIdForUpdate(@Param("operationId") String operationId,
                                                    @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 使用分片时间和版本 CAS 汇总请款成功金额。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                label_captured_amount = label_captured_amount + #{labelAmount},
                label_available_capture_amount = label_available_capture_amount - #{labelAmount},
                label_available_refund_amount = label_available_refund_amount + #{labelAmount},
                captured_amount = captured_amount + #{transactionAmount},
                available_capture_amount = available_capture_amount - #{transactionAmount},
                available_refund_amount = available_refund_amount + #{transactionAmount},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND transaction_status = 'SUCCESS'
              AND label_available_capture_amount >= #{labelAmount}
              AND available_capture_amount >= #{transactionAmount}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int increaseCapturedAmount(@Param("operationId") String operationId,
                               @Param("transactionDateTime") LocalDateTime transactionDateTime,
                               @Param("latestTransactionId") String latestTransactionId,
                               @Param("labelAmount") BigDecimal labelAmount,
                               @Param("transactionAmount") BigDecimal transactionAmount,
                               @Param("expectedVersion") Integer expectedVersion);

    /**
     * 使用分片时间和版本 CAS 汇总退款成功金额。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                label_refunded_amount = label_refunded_amount + #{labelAmount},
                label_available_refund_amount = label_available_refund_amount - #{labelAmount},
                refunded_amount = refunded_amount + #{transactionAmount},
                available_refund_amount = available_refund_amount - #{transactionAmount},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND transaction_status = 'SUCCESS'
              AND label_available_refund_amount >= #{labelAmount}
              AND available_refund_amount >= #{transactionAmount}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int increaseRefundedAmount(@Param("operationId") String operationId,
                               @Param("transactionDateTime") LocalDateTime transactionDateTime,
                               @Param("latestTransactionId") String latestTransactionId,
                               @Param("labelAmount") BigDecimal labelAmount,
                               @Param("transactionAmount") BigDecimal transactionAmount,
                               @Param("expectedVersion") Integer expectedVersion);

    /**
     * 使用分片时间和版本 CAS 汇总增量授权成功金额。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                label_authorized_amount = label_authorized_amount + #{labelAmount},
                label_amount = label_amount + #{labelAmount},
                label_available_capture_amount = label_available_capture_amount + #{labelAmount},
                authorized_amount = authorized_amount + #{transactionAmount},
                transaction_amount = transaction_amount + #{transactionAmount},
                available_capture_amount = available_capture_amount + #{transactionAmount},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND transaction_status = 'SUCCESS'
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int increaseAuthorizedAmount(@Param("operationId") String operationId,
                                 @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                 @Param("latestTransactionId") String latestTransactionId,
                                 @Param("labelAmount") BigDecimal labelAmount,
                                 @Param("transactionAmount") BigDecimal transactionAmount,
                                 @Param("expectedVersion") Integer expectedVersion);

    /**
     * 使用分片时间和版本 CAS 汇总撤销成功金额。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                label_authorized_cancel_amount = label_authorized_cancel_amount + #{labelAmount},
                label_available_capture_amount = 0,
                authorized_cancel_amount = authorized_cancel_amount + #{transactionAmount},
                available_capture_amount = 0,
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND transaction_status = 'SUCCESS'
              AND captured_amount = 0
              AND refunded_amount = 0
              AND label_captured_amount = 0
              AND label_refunded_amount = 0
              AND label_available_capture_amount >= #{labelAmount}
              AND label_authorized_amount - label_authorized_cancel_amount = #{labelAmount}
              AND available_capture_amount >= #{transactionAmount}
              AND authorized_amount - authorized_cancel_amount = #{transactionAmount}
              AND #{labelAmount} > 0
              AND #{transactionAmount} > 0
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int markVoidSuccess(@Param("operationId") String operationId,
                        @Param("transactionDateTime") LocalDateTime transactionDateTime,
                        @Param("latestTransactionId") String latestTransactionId,
                        @Param("labelAmount") BigDecimal labelAmount,
                        @Param("transactionAmount") BigDecimal transactionAmount,
                        @Param("expectedVersion") Integer expectedVersion);

    /**
     * 使用分片时间和版本 CAS 将非终态主单推进到目标终态。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                transaction_status = #{transactionStatus},
                process_stage = #{processStage},
                pending_reason_code = NULL,
                fail_reason_code = #{failReasonCode},
                fail_reason_message = #{failReasonMessage},
                merchant_visible_message = #{merchantVisibleMessage},
                payer_visible_message = #{payerVisibleMessage},
                channel_match_status = #{channelMatchStatus},
                channel_match_result = #{transactionStatus},
                next_channel_match_time = NULL,
                channel_match_fail_reason = NULL,
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND deleted = 0
            """)
    int completeStatus(@Param("operationId") String operationId,
                       @Param("transactionDateTime") LocalDateTime transactionDateTime,
                       @Param("latestTransactionId") String latestTransactionId,
                       @Param("expectedVersion") Integer expectedVersion,
                       @Param("transactionStatus") String transactionStatus,
                       @Param("processStage") String processStage,
                       @Param("failReasonCode") String failReasonCode,
                       @Param("failReasonMessage") String failReasonMessage,
                       @Param("merchantVisibleMessage") String merchantVisibleMessage,
                       @Param("payerVisibleMessage") String payerVisibleMessage,
                       @Param("channelMatchStatus") String channelMatchStatus);

    /**
     * 同步当前最新动作的渠道勾兑摘要，不修改订单交易状态。
     *
     * @return 影响行数；历史动作或已勾兑订单返回 0
     */
    @Update("""
            UPDATE transaction_order
            SET channel_match_status = #{matchStatus},
                channel_match_result = #{matchResult},
                last_channel_match_time = #{matchTime},
                next_channel_match_time = #{nextMatchTime},
                channel_match_fail_reason = #{failReason},
                version = version + 1,
                update_time = #{matchTime}
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND latest_transaction_id = #{latestTransactionId}
              AND channel_match_status IN ('PENDING', 'REVIEW_REQUIRED', 'MISMATCHED', 'FAILED')
              AND deleted = 0
            """)
    int updateLatestChannelMatch(@Param("operationId") String operationId,
                                 @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                 @Param("latestTransactionId") String latestTransactionId,
                                 @Param("matchStatus") String matchStatus,
                                 @Param("matchResult") String matchResult,
                                 @Param("matchTime") LocalDateTime matchTime,
                                 @Param("nextMatchTime") LocalDateTime nextMatchTime,
                                 @Param("failReason") String failReason);

    /**
     * 使用分片时间和版本 CAS 推进首次交易成功并初始化金额汇总。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                pending_reason_code = NULL,
                fail_reason_code = NULL,
                fail_reason_message = NULL,
                merchant_visible_message = NULL,
                payer_visible_message = NULL,
                channel_match_status = #{channelMatchStatus},
                channel_match_result = 'SUCCESS',
                next_channel_match_time = NULL,
                channel_match_fail_reason = NULL,
                label_authorized_amount = CASE
                    WHEN transaction_type IN ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT') THEN #{labelAmount}
                    ELSE label_authorized_amount
                END,
                label_captured_amount = CASE
                    WHEN transaction_type = 'PAYMENT' THEN #{labelAmount}
                    ELSE label_captured_amount
                END,
                label_available_capture_amount = CASE
                    WHEN transaction_type IN ('AUTHORIZATION', 'PRE_AUTHORIZATION') THEN #{labelAmount}
                    ELSE label_available_capture_amount
                END,
                label_available_refund_amount = CASE
                    WHEN transaction_type = 'PAYMENT' THEN #{labelAmount}
                    ELSE label_available_refund_amount
                END,
                authorized_amount = CASE
                    WHEN transaction_type IN ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT') THEN #{transactionAmount}
                    ELSE authorized_amount
                END,
                captured_amount = CASE
                    WHEN transaction_type = 'PAYMENT' THEN #{transactionAmount}
                    ELSE captured_amount
                END,
                available_capture_amount = CASE
                    WHEN transaction_type IN ('AUTHORIZATION', 'PRE_AUTHORIZATION') THEN #{transactionAmount}
                    ELSE available_capture_amount
                END,
                available_refund_amount = CASE
                    WHEN transaction_type = 'PAYMENT' THEN #{transactionAmount}
                    ELSE available_refund_amount
                END,
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int markInitialSuccess(@Param("operationId") String operationId,
                           @Param("transactionDateTime") LocalDateTime transactionDateTime,
                           @Param("latestTransactionId") String latestTransactionId,
                           @Param("labelAmount") BigDecimal labelAmount,
                           @Param("transactionAmount") BigDecimal transactionAmount,
                           @Param("expectedVersion") Integer expectedVersion,
                           @Param("channelMatchStatus") String channelMatchStatus);

    /**
     * 首次授权在同步响应、回调或查询后补写渠道授权号。
     * 只允许替换空值或平台根交易号占位值，避免晚到事件覆盖已经确认的渠道身份。
     */
    @Update("""
            UPDATE transaction_order
            SET channel_order_no = #{channelOrderNo},
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND (channel_order_no IS NULL OR channel_order_no = root_transaction_id)
              AND deleted = 0
            """)
    int updateInitialChannelOrderNoIfUnresolved(@Param("operationId") String operationId,
                                                @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                                @Param("channelOrderNo") String channelOrderNo);
}
