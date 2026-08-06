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
 * @description : 渠道回调业务 Mapper，位于 service-payment 数据访问层，仅通过 transaction_channel_callback 逻辑表保存幂等记录并执行 CAS。
 * @status : create
 */
public interface TransactionChannelCallbackMapper extends BaseMapper<TransactionChannelCallbackDO> {

    /**
     * 写入渠道回调业务记录逻辑表。
     *
     * @param callbackDO 渠道回调业务记录
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_channel_callback
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
    int insertLogical(@Param("callbackDO") TransactionChannelCallbackDO callbackDO);

    /**
     * 按交易 ID 和精确分片时间查询渠道回调记录。
     *
     * @param transactionId 平台当前交易 ID
     * @param transactionDateTime 交易分片时间
     * @return 回调业务记录列表
     */
    @Select("""
            SELECT *
            FROM transaction_channel_callback
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            ORDER BY callback_received_time DESC
            LIMIT 100
            """)
    List<TransactionChannelCallbackDO> selectByTransactionId(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 按生命周期和半开时间范围查询渠道回调记录。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 渠道回调业务记录列表
     */
    @Select("""
            SELECT *
            FROM transaction_channel_callback
            WHERE operation_id = #{operationId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND deleted = 0
            ORDER BY callback_received_time DESC
            LIMIT 200
            """)
    List<TransactionChannelCallbackDO> selectByOperationId(
            @Param("operationId") String operationId,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 按渠道幂等键和精确分片时间查询回调记录。
     *
     * @param channelCode 渠道编码
     * @param idempotencyKey 渠道回调幂等键
     * @param transactionDateTime 交易分片时间
     * @return 已存在的回调记录
     */
    @Select("""
            SELECT *
            FROM transaction_channel_callback
            WHERE channel_code = #{channelCode}
              AND idempotency_key = #{idempotencyKey}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            """)
    TransactionChannelCallbackDO selectByIdempotency(
            @Param("channelCode") String channelCode,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 按半开交易时间范围分页查询渠道回调逻辑表。
     *
     * @param channelCode 渠道编码，可为空
     * @param transactionId 平台交易 ID，可为空
     * @param channelOrderNo 渠道订单号，可为空
     * @param channelTransactionId 渠道交易 ID，可为空
     * @param callbackStatus 回调处理状态，可为空
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @param offset 分页偏移
     * @param limit 分页大小
     * @return 渠道回调业务记录列表
     */
    @Select("""
            <script>
            SELECT *
            FROM transaction_channel_callback
            WHERE deleted = 0
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt; #{endTimeExclusive}
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
            ORDER BY transaction_date_time DESC, id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<TransactionChannelCallbackDO> selectPageLogical(
            @Param("channelCode") String channelCode,
            @Param("transactionId") String transactionId,
            @Param("channelOrderNo") String channelOrderNo,
            @Param("channelTransactionId") String channelTransactionId,
            @Param("callbackStatus") String callbackStatus,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive,
            @Param("offset") long offset,
            @Param("limit") long limit);

    /**
     * 统计半开交易时间范围内的渠道回调记录。
     *
     * @param channelCode 渠道编码，可为空
     * @param transactionId 平台交易 ID，可为空
     * @param channelOrderNo 渠道订单号，可为空
     * @param channelTransactionId 渠道交易 ID，可为空
     * @param callbackStatus 回调处理状态，可为空
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 命中记录数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM transaction_channel_callback
            WHERE deleted = 0
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt; #{endTimeExclusive}
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
    long countPageLogical(@Param("channelCode") String channelCode,
                          @Param("transactionId") String transactionId,
                          @Param("channelOrderNo") String channelOrderNo,
                          @Param("channelTransactionId") String channelTransactionId,
                          @Param("callbackStatus") String callbackStatus,
                          @Param("beginTime") LocalDateTime beginTime,
                          @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 使用分片时间、版本和当前状态 CAS 更新回调处理结果。
     *
     * @param callbackId 回调业务 ID
     * @param transactionDateTime 交易分片时间
     * @param expectedVersion 当前版本号
     * @param expectedStatuses 允许更新的当前回调状态
     * @param callbackStatus 目标回调状态
     * @param parsedTransactionStatus 解析出的平台状态
     * @param previousTransactionStatus 推进前平台动作状态
     * @param targetTransactionStatus 目标平台状态
     * @param processResult 处理结果
     * @param failReason 失败或忽略原因
     * @param processedTime 处理完成时间
     * @return 影响行数
     */
    @Update("""
            <script>
            UPDATE transaction_channel_callback
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
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND callback_status IN
              <foreach collection="expectedStatuses" item="expectedStatus" open="(" separator="," close=")">
                #{expectedStatus}
              </foreach>
              AND deleted = 0
            </script>
            """)
    int updateProcessResultLogical(@Param("callbackId") String callbackId,
                                   @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                   @Param("expectedVersion") Integer expectedVersion,
                                   @Param("expectedStatuses") List<String> expectedStatuses,
                                   @Param("callbackStatus") String callbackStatus,
                                   @Param("parsedTransactionStatus") String parsedTransactionStatus,
                                   @Param("previousTransactionStatus") String previousTransactionStatus,
                                   @Param("targetTransactionStatus") String targetTransactionStatus,
                                   @Param("processResult") String processResult,
                                   @Param("failReason") String failReason,
                                   @Param("processedTime") LocalDateTime processedTime);
}
