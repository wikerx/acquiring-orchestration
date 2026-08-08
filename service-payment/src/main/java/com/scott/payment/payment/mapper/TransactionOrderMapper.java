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
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionOrderMapper
 * @date : 2026-07-14 17:40
 * @email : scott_x@163.com
 * @description : 交易生命周期主单 Mapper，位于 service-payment 数据访问层，仅访问 transaction_order 逻辑表并显式携带交易分片时间。
 * @status : create
 */
public interface TransactionOrderMapper extends BaseMapper<TransactionOrderDO> {

    /**
     * 通过交易分片时间在逻辑表中查询生命周期主单。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param transactionDateTime 交易分片时间
     * @return 交易生命周期主单，不存在时返回 null
     */
    @Select("""
            SELECT *
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
            SELECT *
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
                captured_amount = captured_amount + #{amount},
                available_capture_amount = available_capture_amount - #{amount},
                available_refund_amount = available_refund_amount + #{amount},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND transaction_status = 'SUCCESS'
              AND available_capture_amount >= #{amount}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int increaseCapturedAmount(@Param("operationId") String operationId,
                               @Param("transactionDateTime") LocalDateTime transactionDateTime,
                               @Param("latestTransactionId") String latestTransactionId,
                               @Param("amount") BigDecimal amount,
                               @Param("expectedVersion") Integer expectedVersion);

    /**
     * 使用分片时间和版本 CAS 汇总退款成功金额。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                refunded_amount = refunded_amount + #{amount},
                available_refund_amount = available_refund_amount - #{amount},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND transaction_status = 'SUCCESS'
              AND available_refund_amount >= #{amount}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int increaseRefundedAmount(@Param("operationId") String operationId,
                               @Param("transactionDateTime") LocalDateTime transactionDateTime,
                               @Param("latestTransactionId") String latestTransactionId,
                               @Param("amount") BigDecimal amount,
                               @Param("expectedVersion") Integer expectedVersion);

    /**
     * 使用分片时间和版本 CAS 汇总增量授权成功金额。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                authorized_amount = authorized_amount + #{amount},
                transaction_amount = transaction_amount + #{amount},
                available_capture_amount = available_capture_amount + #{amount},
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
                                 @Param("amount") BigDecimal amount,
                                 @Param("expectedVersion") Integer expectedVersion);

    /**
     * 使用分片时间和版本 CAS 汇总撤销成功金额。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                authorized_cancel_amount = authorized_cancel_amount + #{amount},
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
              AND available_capture_amount >= #{amount}
              AND authorized_amount - authorized_cancel_amount = #{amount}
              AND #{amount} > 0
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int markVoidSuccess(@Param("operationId") String operationId,
                        @Param("transactionDateTime") LocalDateTime transactionDateTime,
                        @Param("latestTransactionId") String latestTransactionId,
                        @Param("amount") BigDecimal amount,
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
     * 使用分片时间和版本 CAS 推进首次交易成功并初始化金额汇总。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                channel_match_status = #{channelMatchStatus},
                channel_match_result = 'SUCCESS',
                next_channel_match_time = NULL,
                channel_match_fail_reason = NULL,
                authorized_amount = CASE
                    WHEN transaction_type IN ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT') THEN #{amount}
                    ELSE authorized_amount
                END,
                captured_amount = CASE
                    WHEN transaction_type = 'PAYMENT' THEN #{amount}
                    ELSE captured_amount
                END,
                available_capture_amount = CASE
                    WHEN transaction_type IN ('AUTHORIZATION', 'PRE_AUTHORIZATION') THEN #{amount}
                    ELSE available_capture_amount
                END,
                available_refund_amount = CASE
                    WHEN transaction_type = 'PAYMENT' THEN #{amount}
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
                           @Param("amount") BigDecimal amount,
                           @Param("expectedVersion") Integer expectedVersion,
                           @Param("channelMatchStatus") String channelMatchStatus);
}
