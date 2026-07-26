package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionChannelCallbackDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelCallbackMapper
 * @date : 2026-07-14 22:26
 * @email : scott_x@163.com
 * @description : 渠道回调业务 Mapper，位于 service-payment 数据访问层，仅负责回调幂等记录和处理结果的物理分表访问。
 * @status : create
 */
public interface TransactionChannelCallbackMapper extends BaseMapper<TransactionChannelCallbackDO> {

    /**
     * 写入渠道回调业务记录物理分表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param callbackDO 渠道回调业务记录
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO ${physicalTableName}
            (
              callback_id, callback_log_id, transaction_id, operation_id, channel_code,
              channel_order_no, channel_transaction_id, callback_type, channel_event_type,
              callback_status, idempotency_key, signature_valid, ip_allowed,
              parsed_transaction_status, previous_transaction_status, target_transaction_status,
              process_result, fail_reason, callback_received_time, processed_time,
              transaction_date_time, transaction_utc_time, transaction_time_zone,
              version, deleted, create_time, update_time
            )
            VALUES
            (
              #{callbackDO.callbackId}, #{callbackDO.callbackLogId}, #{callbackDO.transactionId},
              #{callbackDO.operationId}, #{callbackDO.channelCode}, #{callbackDO.channelOrderNo},
              #{callbackDO.channelTransactionId}, #{callbackDO.callbackType}, #{callbackDO.channelEventType},
              #{callbackDO.callbackStatus}, #{callbackDO.idempotencyKey}, #{callbackDO.signatureValid},
              #{callbackDO.ipAllowed}, #{callbackDO.parsedTransactionStatus},
              #{callbackDO.previousTransactionStatus}, #{callbackDO.targetTransactionStatus},
              #{callbackDO.processResult}, #{callbackDO.failReason}, #{callbackDO.callbackReceivedTime},
              #{callbackDO.processedTime}, #{callbackDO.transactionDateTime}, #{callbackDO.transactionUtcTime},
              #{callbackDO.transactionTimeZone}, #{callbackDO.version}, #{callbackDO.deleted},
              #{callbackDO.createTime}, #{callbackDO.updateTime}
            )
            """)
    int insertPhysical(@Param("physicalTableName") String physicalTableName,
                       @Param("callbackDO") TransactionChannelCallbackDO callbackDO);

    /**
     * 按平台交易 ID 查询渠道回调业务记录。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param transactionId 平台当前交易 ID
     * @return 回调业务记录列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE transaction_id = #{transactionId}
              AND deleted = 0
            ORDER BY callback_received_time DESC
            LIMIT 100
            """)
    List<TransactionChannelCallbackDO> selectByTransactionIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                                     @Param("transactionId") String transactionId);

    /**
     * 按 operation_id 查询同一生命周期的渠道回调业务记录。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @return 渠道回调业务记录列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
              AND deleted = 0
            ORDER BY callback_received_time DESC
            LIMIT 200
            """)
    List<TransactionChannelCallbackDO> selectByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                                   @Param("operationId") String operationId);

    /**
     * 按渠道回调幂等键查询业务记录。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param channelCode 渠道编码
     * @param idempotencyKey 渠道回调幂等键
     * @return 已存在的业务回调记录
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE channel_code = #{channelCode}
              AND idempotency_key = #{idempotencyKey}
              AND deleted = 0
            LIMIT 1
            """)
    TransactionChannelCallbackDO selectByIdempotencyPhysical(@Param("physicalTableName") String physicalTableName,
                                                             @Param("channelCode") String channelCode,
                                                             @Param("idempotencyKey") String idempotencyKey);

    /**
     * 按交易时间范围分页查询渠道回调业务记录。
     *
     * @param physicalTableName    经分表规则解析器校验后的物理表名
     * @param channelCode          渠道编码，可为空
     * @param transactionId        平台交易 ID，可为空
     * @param channelOrderNo       渠道订单号，可为空
     * @param channelTransactionId 渠道交易 ID，可为空
     * @param callbackStatus       回调处理状态，可为空
     * @param beginTime            查询开始交易时间
     * @param endTime              查询结束交易时间
     * @param offset               分页偏移
     * @param limit                分页大小
     * @return 渠道回调业务记录列表
     */
    @Select("""
            <script>
            SELECT *
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt;= #{endTime}
              <if test="channelCode != null and channelCode != ''">
                AND channel_code = #{channelCode}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND transaction_id = #{transactionId}
              </if>
              <if test="channelOrderNo != null and channelOrderNo != ''">
                AND channel_order_no = #{channelOrderNo}
              </if>
              <if test="channelTransactionId != null and channelTransactionId != ''">
                AND channel_transaction_id = #{channelTransactionId}
              </if>
              <if test="callbackStatus != null and callbackStatus != ''">
                AND callback_status = #{callbackStatus}
              </if>
            ORDER BY callback_received_time DESC, id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<TransactionChannelCallbackDO> selectPagePhysical(@Param("physicalTableName") String physicalTableName,
                                                          @Param("channelCode") String channelCode,
                                                          @Param("transactionId") String transactionId,
                                                          @Param("channelOrderNo") String channelOrderNo,
                                                          @Param("channelTransactionId") String channelTransactionId,
                                                          @Param("callbackStatus") String callbackStatus,
                                                          @Param("beginTime") LocalDateTime beginTime,
                                                          @Param("endTime") LocalDateTime endTime,
                                                          @Param("offset") long offset,
                                                          @Param("limit") long limit);

    /**
     * 统计交易时间范围内的渠道回调业务记录数量。
     *
     * @param physicalTableName    经分表规则解析器校验后的物理表名
     * @param channelCode          渠道编码，可为空
     * @param transactionId        平台交易 ID，可为空
     * @param channelOrderNo       渠道订单号，可为空
     * @param channelTransactionId 渠道交易 ID，可为空
     * @param callbackStatus       回调处理状态，可为空
     * @param beginTime            查询开始交易时间
     * @param endTime              查询结束交易时间
     * @return 命中记录数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt;= #{endTime}
              <if test="channelCode != null and channelCode != ''">
                AND channel_code = #{channelCode}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND transaction_id = #{transactionId}
              </if>
              <if test="channelOrderNo != null and channelOrderNo != ''">
                AND channel_order_no = #{channelOrderNo}
              </if>
              <if test="channelTransactionId != null and channelTransactionId != ''">
                AND channel_transaction_id = #{channelTransactionId}
              </if>
              <if test="callbackStatus != null and callbackStatus != ''">
                AND callback_status = #{callbackStatus}
              </if>
            </script>
            """)
    long countPagePhysical(@Param("physicalTableName") String physicalTableName,
                           @Param("channelCode") String channelCode,
                           @Param("transactionId") String transactionId,
                           @Param("channelOrderNo") String channelOrderNo,
                           @Param("channelTransactionId") String channelTransactionId,
                           @Param("callbackStatus") String callbackStatus,
                           @Param("beginTime") LocalDateTime beginTime,
                           @Param("endTime") LocalDateTime endTime);

    /**
     * 更新回调业务处理结果。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param callbackId 回调业务 ID
     * @param callbackStatus 回调处理状态
     * @param parsedTransactionStatus 解析出的平台状态
     * @param previousTransactionStatus 推进前平台动作状态
     * @param targetTransactionStatus 目标平台状态
     * @param processResult 处理结果
     * @param failReason 失败或忽略原因
     * @param processedTime 处理完成时间
     * @return 影响行数
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET callback_status = #{callbackStatus},
                parsed_transaction_status = #{parsedTransactionStatus},
                previous_transaction_status = #{previousTransactionStatus},
                target_transaction_status = #{targetTransactionStatus},
                process_result = #{processResult},
                fail_reason = #{failReason},
                processed_time = #{processedTime},
                version = version + 1,
                update_time = #{processedTime}
            WHERE callback_id = #{callbackId}
              AND deleted = 0
            """)
    int updateProcessResultPhysical(@Param("physicalTableName") String physicalTableName,
                                    @Param("callbackId") String callbackId,
                                    @Param("callbackStatus") String callbackStatus,
                                    @Param("parsedTransactionStatus") String parsedTransactionStatus,
                                    @Param("previousTransactionStatus") String previousTransactionStatus,
                                    @Param("targetTransactionStatus") String targetTransactionStatus,
                                    @Param("processResult") String processResult,
                                    @Param("failReason") String failReason,
                                    @Param("processedTime") LocalDateTime processedTime);
}
