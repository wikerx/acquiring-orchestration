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
