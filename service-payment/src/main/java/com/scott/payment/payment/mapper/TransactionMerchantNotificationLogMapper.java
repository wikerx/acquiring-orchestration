package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionMerchantNotificationLogDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantNotificationLogMapper
 * @date : 2026-07-14 22:28
 * @email : scott_x@163.com
 * @description : 商户通知日志 Mapper，位于 service-payment 数据访问层，仅访问 transaction_merchant_notification_log 逻辑表及其物理分表。
 * @status : create
 */
public interface TransactionMerchantNotificationLogMapper extends BaseMapper<TransactionMerchantNotificationLogDO> {

    /**
     * 按交易 ID 和精确分片时间查询商户通知日志。
     *
     * @param transactionId 平台当前交易 ID
     * @param transactionDateTime 交易分片时间
     * @return 商户通知日志列表
     */
    @Select("""
            SELECT *
            FROM transaction_merchant_notification_log
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
            ORDER BY notify_time DESC
            LIMIT 100
            """)
    List<TransactionMerchantNotificationLogDO> selectByTransactionId(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 按生命周期和半开交易时间范围查询商户通知日志。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 商户通知日志列表
     */
    @Select("""
            SELECT *
            FROM transaction_merchant_notification_log
            WHERE operation_id = #{operationId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
            ORDER BY notify_time DESC
            LIMIT 200
            """)
    List<TransactionMerchantNotificationLogDO> selectByOperationId(@Param("operationId") String operationId,
                                                                   @Param("beginTime") LocalDateTime beginTime,
                                                                   @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 按平台交易 ID 查询商户通知日志。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param transactionId 平台当前交易 ID
     * @return 商户通知日志列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE transaction_id = #{transactionId}
            ORDER BY notify_time DESC
            LIMIT 100
            """)
    List<TransactionMerchantNotificationLogDO> selectByTransactionIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                                             @Param("transactionId") String transactionId);

    /**
     * 按 operation_id 查询同一生命周期的商户通知日志。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @return 商户通知日志列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
            ORDER BY notify_time DESC
            LIMIT 200
            """)
    List<TransactionMerchantNotificationLogDO> selectByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                                           @Param("operationId") String operationId);
}
