package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionAmountChangeLogDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 交易金额变动日志 Mapper，位于 service-payment 数据访问层，仅访问 transaction_amount_change_log 逻辑表。
 */
public interface TransactionAmountChangeLogMapper extends BaseMapper<TransactionAmountChangeLogDO> {

    /**
     * 写入金额变动日志逻辑表，由 ShardingSphere 按交易时间路由。
     *
     * @param logDO 金额变动日志
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_amount_change_log
            (
              amount_change_id, transaction_id, operation_id, source_transaction_id, change_type,
              amount_currency, change_amount, label_amount_currency, label_change_amount,
              label_authorized_before, label_authorized_after, label_captured_before, label_captured_after,
              label_refunded_before, label_refunded_after, label_available_capture_before,
              label_available_capture_after, label_available_refund_before, label_available_refund_after,
              authorized_before, authorized_after, captured_before,
              captured_after, refunded_before, refunded_after, available_capture_before,
              available_capture_after, available_refund_before, available_refund_after, change_reason,
              change_time, transaction_date_time, transaction_utc_time, transaction_time_zone, create_time
            )
            VALUES
            (
              #{logDO.amountChangeId}, #{logDO.transactionId}, #{logDO.operationId},
              #{logDO.sourceTransactionId}, #{logDO.changeType}, #{logDO.amountCurrency},
              #{logDO.changeAmount}, #{logDO.labelAmountCurrency}, #{logDO.labelChangeAmount},
              #{logDO.labelAuthorizedBefore}, #{logDO.labelAuthorizedAfter},
              #{logDO.labelCapturedBefore}, #{logDO.labelCapturedAfter},
              #{logDO.labelRefundedBefore}, #{logDO.labelRefundedAfter},
              #{logDO.labelAvailableCaptureBefore}, #{logDO.labelAvailableCaptureAfter},
              #{logDO.labelAvailableRefundBefore}, #{logDO.labelAvailableRefundAfter},
              #{logDO.authorizedBefore}, #{logDO.authorizedAfter},
              #{logDO.capturedBefore}, #{logDO.capturedAfter}, #{logDO.refundedBefore},
              #{logDO.refundedAfter}, #{logDO.availableCaptureBefore}, #{logDO.availableCaptureAfter},
              #{logDO.availableRefundBefore}, #{logDO.availableRefundAfter}, #{logDO.changeReason},
              #{logDO.changeTime}, #{logDO.transactionDateTime}, #{logDO.transactionUtcTime},
              #{logDO.transactionTimeZone}, #{logDO.createTime}
            )
            """)
    int insertLogical(@Param("logDO") TransactionAmountChangeLogDO logDO);

}
