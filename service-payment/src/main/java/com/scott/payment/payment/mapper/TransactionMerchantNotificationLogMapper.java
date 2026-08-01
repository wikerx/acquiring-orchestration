package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionMerchantNotificationLogDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
