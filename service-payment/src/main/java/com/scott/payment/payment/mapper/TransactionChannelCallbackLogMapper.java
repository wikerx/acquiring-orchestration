package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionChannelCallbackLogDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelCallbackLogMapper
 * @date : 2026-07-14 22:24
 * @email : scott_x@163.com
 * @description : 渠道回调原始日志 Mapper，位于 service-payment 数据访问层，仅访问 transaction_channel_callback_log 逻辑表及其物理分表。
 * @status : create
 */
public interface TransactionChannelCallbackLogMapper extends BaseMapper<TransactionChannelCallbackLogDO> {

    /**
     * 写入渠道回调原始日志逻辑表。
     *
     * @param logDO 渠道回调原始日志
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_channel_callback_log
            (
              callback_log_id, transaction_id, operation_id, channel_code, callback_type,
              channel_order_no, channel_transaction_id, request_uri, http_method, source_ip,
              request_header_json_masked, request_body_json_masked, signature_valid, ip_allowed,
              platform_response_code, platform_response_body, callback_received_time,
              transaction_date_time, transaction_utc_time, transaction_time_zone, create_time
            )
            VALUES
            (
              #{logDO.callbackLogId}, #{logDO.transactionId}, #{logDO.operationId},
              #{logDO.channelCode}, #{logDO.callbackType}, #{logDO.channelOrderNo},
              #{logDO.channelTransactionId}, #{logDO.requestUri}, #{logDO.httpMethod},
              #{logDO.sourceIp}, #{logDO.requestHeaderJsonMasked}, #{logDO.requestBodyJsonMasked},
              #{logDO.signatureValid}, #{logDO.ipAllowed}, #{logDO.platformResponseCode},
              #{logDO.platformResponseBody}, #{logDO.callbackReceivedTime},
              #{logDO.transactionDateTime}, #{logDO.transactionUtcTime}, #{logDO.transactionTimeZone},
              #{logDO.createTime}
            )
            """)
    int insertLogical(@Param("logDO") TransactionChannelCallbackLogDO logDO);

    /**
     * 按交易 ID 和精确分片时间查询回调原始日志。
     *
     * @param transactionId 平台当前交易 ID
     * @param transactionDateTime 交易分片时间
     * @return 回调原始日志列表
     */
    @Select("""
            SELECT *
            FROM transaction_channel_callback_log
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
            ORDER BY callback_received_time DESC
            LIMIT 100
            """)
    List<TransactionChannelCallbackLogDO> selectByTransactionId(@Param("transactionId") String transactionId,
                                                                @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 按生命周期和半开交易时间范围查询回调原始日志。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 回调原始日志列表
     */
    @Select("""
            SELECT *
            FROM transaction_channel_callback_log
            WHERE operation_id = #{operationId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
            ORDER BY callback_received_time DESC
            LIMIT 200
            """)
    List<TransactionChannelCallbackLogDO> selectByOperationId(@Param("operationId") String operationId,
                                                              @Param("beginTime") LocalDateTime beginTime,
                                                              @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 写入渠道回调原始日志物理分表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param logDO 渠道回调原始日志
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO ${physicalTableName}
            (
              callback_log_id, transaction_id, operation_id, channel_code, callback_type,
              channel_order_no, channel_transaction_id, request_uri, http_method, source_ip,
              request_header_json_masked, request_body_json_masked, signature_valid, ip_allowed,
              platform_response_code, platform_response_body, callback_received_time,
              transaction_date_time, transaction_utc_time, transaction_time_zone, create_time
            )
            VALUES
            (
              #{logDO.callbackLogId}, #{logDO.transactionId}, #{logDO.operationId},
              #{logDO.channelCode}, #{logDO.callbackType}, #{logDO.channelOrderNo},
              #{logDO.channelTransactionId}, #{logDO.requestUri}, #{logDO.httpMethod},
              #{logDO.sourceIp}, #{logDO.requestHeaderJsonMasked}, #{logDO.requestBodyJsonMasked},
              #{logDO.signatureValid}, #{logDO.ipAllowed}, #{logDO.platformResponseCode},
              #{logDO.platformResponseBody}, #{logDO.callbackReceivedTime},
              #{logDO.transactionDateTime}, #{logDO.transactionUtcTime}, #{logDO.transactionTimeZone},
              #{logDO.createTime}
            )
            """)
    int insertPhysical(@Param("physicalTableName") String physicalTableName,
                       @Param("logDO") TransactionChannelCallbackLogDO logDO);

    /**
     * 按平台交易 ID 查询渠道回调原始日志。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param transactionId 平台当前交易 ID
     * @return 回调原始日志列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE transaction_id = #{transactionId}
            ORDER BY callback_received_time DESC
            LIMIT 100
            """)
    List<TransactionChannelCallbackLogDO> selectByTransactionIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                                        @Param("transactionId") String transactionId);

    /**
     * 按 operation_id 查询同一生命周期的渠道回调原始日志。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @return 回调原始日志列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
            ORDER BY callback_received_time DESC
            LIMIT 200
            """)
    List<TransactionChannelCallbackLogDO> selectByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                                      @Param("operationId") String operationId);
}
