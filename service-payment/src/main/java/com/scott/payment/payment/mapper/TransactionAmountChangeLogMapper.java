package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionAmountChangeLogDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionAmountChangeLogMapper
 * @date : 2026-07-14 19:46
 * @email : scott_x@163.com
 * @description : 交易金额变动日志 Mapper，位于 service-payment 数据访问层，仅访问 transaction_amount_change_log 逻辑表。
 * @status : create
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
              amount_currency, change_amount, authorized_before, authorized_after, captured_before,
              captured_after, refunded_before, refunded_after, available_capture_before,
              available_capture_after, available_refund_before, available_refund_after, change_reason,
              change_time, transaction_date_time, transaction_utc_time, transaction_time_zone, create_time
            )
            VALUES
            (
              #{logDO.amountChangeId}, #{logDO.transactionId}, #{logDO.operationId},
              #{logDO.sourceTransactionId}, #{logDO.changeType}, #{logDO.amountCurrency},
              #{logDO.changeAmount}, #{logDO.authorizedBefore}, #{logDO.authorizedAfter},
              #{logDO.capturedBefore}, #{logDO.capturedAfter}, #{logDO.refundedBefore},
              #{logDO.refundedAfter}, #{logDO.availableCaptureBefore}, #{logDO.availableCaptureAfter},
              #{logDO.availableRefundBefore}, #{logDO.availableRefundAfter}, #{logDO.changeReason},
              #{logDO.changeTime}, #{logDO.transactionDateTime}, #{logDO.transactionUtcTime},
              #{logDO.transactionTimeZone}, #{logDO.createTime}
            )
            """)
    int insertLogical(@Param("logDO") TransactionAmountChangeLogDO logDO);

    /**
     * 按生命周期和半开交易时间范围查询金额变动日志逻辑表。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 金额变动日志列表
     */
    @Select("""
            SELECT *
            FROM transaction_amount_change_log
            WHERE operation_id = #{operationId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
            ORDER BY change_time ASC, id ASC
            """)
    List<TransactionAmountChangeLogDO> selectByOperationId(@Param("operationId") String operationId,
                                                           @Param("beginTime") LocalDateTime beginTime,
                                                           @Param("endTimeExclusive") LocalDateTime endTimeExclusive);
}
