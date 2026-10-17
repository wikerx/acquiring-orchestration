package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionCallbackInboxDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionCallbackInboxMapper
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调 Inbox Mapper，位于 service-payment 数据访问层，只负责全局非分片 Inbox 的原子受理、到期扫描和版本号 CAS 状态更新。
 * @status : create
 */
public interface TransactionCallbackInboxMapper extends BaseMapper<TransactionCallbackInboxDO> {

    /**
     * 原子写入回调；命中数据库唯一键时执行无副作用更新，使并发请求可以在同一事务内读取首条记录。
     *
     * @param row 待受理的标准化回调
     * @return MySQL 影响行数；调用方不得依赖该值区分首次与重复
     */
    @Insert("""
            INSERT INTO transaction_callback_inbox
            (inbox_id, trace_id, channel_code, credential_hint, callback_event_id, idempotency_key,
             payload_json, request_header_json_masked, request_body_length, request_body_sha256,
             inbox_status, retry_count, max_retry_count, next_retry_time, received_time,
             version, create_time, update_time)
            VALUES
            (#{row.inboxId}, #{row.traceId}, #{row.channelCode}, #{row.credentialHint}, #{row.callbackEventId},
             #{row.idempotencyKey}, #{row.payloadJson}, #{row.requestHeaderJsonMasked},
             #{row.requestBodyLength}, #{row.requestBodySha256}, #{row.inboxStatus}, #{row.retryCount},
             #{row.maxRetryCount}, #{row.nextRetryTime}, #{row.receivedTime}, #{row.version},
             #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE inbox_id = inbox_id
            """)
    int upsertInbox(@Param("row") TransactionCallbackInboxDO row);

    /** 按数据库幂等维度读取首次受理记录。 */
    @Select("""
            SELECT * FROM transaction_callback_inbox
            WHERE channel_code = #{channelCode} AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    TransactionCallbackInboxDO selectByIdempotency(@Param("channelCode") String channelCode,
                                                    @Param("idempotencyKey") String idempotencyKey);

    /** 查询到期且尚未被其他实例抢占的 Inbox 记录。 */
    @Select("""
            SELECT * FROM transaction_callback_inbox
            WHERE inbox_status IN (#{receivedStatus}, #{retryStatus})
              AND next_retry_time <= #{now}
            ORDER BY next_retry_time, id
            LIMIT #{limit}
            """)
    List<TransactionCallbackInboxDO> selectDue(@Param("now") LocalDateTime now,
                                               @Param("limit") int limit,
                                               @Param("receivedStatus") String receivedStatus,
                                               @Param("retryStatus") String retryStatus);

    /** 使用版本、原状态和到期时间 CAS 抢占单条回调。 */
    @Update("""
            UPDATE transaction_callback_inbox
            SET inbox_status = #{processingStatus}, version = version + 1, update_time = #{now}
            WHERE id = #{id} AND version = #{version}
              AND inbox_status IN (#{receivedStatus}, #{retryStatus})
              AND next_retry_time <= #{now}
            """)
    int claim(@Param("id") Long id,
              @Param("version") Integer version,
              @Param("now") LocalDateTime now,
              @Param("receivedStatus") String receivedStatus,
              @Param("retryStatus") String retryStatus,
              @Param("processingStatus") String processingStatus);

    /** 使用抢占后的版本号 CAS 标记业务处理完成。 */
    @Update("""
            UPDATE transaction_callback_inbox
            SET inbox_status = #{processedStatus}, processed_time = #{now}, last_error = NULL,
                next_retry_time = NULL, version = version + 1, update_time = #{now}
            WHERE id = #{id} AND version = #{version} AND inbox_status = #{processingStatus}
            """)
    int markProcessed(@Param("id") Long id,
                      @Param("version") Integer version,
                      @Param("now") LocalDateTime now,
                      @Param("processingStatus") String processingStatus,
                      @Param("processedStatus") String processedStatus);

    /** 使用抢占后的版本号 CAS 记录失败并推进至 RETRY 或 CLOSED。 */
    @Update("""
            UPDATE transaction_callback_inbox
            SET inbox_status = #{status}, retry_count = retry_count + 1,
                next_retry_time = #{nextRetryTime}, last_error = #{lastError},
                version = version + 1, update_time = #{now}
            WHERE id = #{id} AND version = #{version} AND inbox_status = #{processingStatus}
              AND retry_count < max_retry_count
            """)
    int markFailed(@Param("id") Long id,
                   @Param("version") Integer version,
                   @Param("status") String status,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("lastError") String lastError,
                   @Param("now") LocalDateTime now,
                   @Param("processingStatus") String processingStatus);

    /** 分批恢复超时 PROCESSING 记录，进程崩溃也不会让回调永久卡住。 */
    @Update("""
            UPDATE transaction_callback_inbox
            SET inbox_status = CASE
                    WHEN retry_count + 1 >= max_retry_count THEN #{closedStatus}
                    ELSE #{retryStatus}
                END,
                retry_count = retry_count + 1,
                next_retry_time = CASE
                    WHEN retry_count + 1 >= max_retry_count THEN NULL
                    ELSE #{now}
                END,
                last_error = 'callback inbox processing timeout',
                version = version + 1,
                update_time = #{now}
            WHERE inbox_status = #{processingStatus}
              AND update_time < #{staleBefore}
              AND retry_count < max_retry_count
            ORDER BY update_time, id
            LIMIT #{limit}
            """)
    int recoverStaleProcessing(@Param("staleBefore") LocalDateTime staleBefore,
                               @Param("now") LocalDateTime now,
                               @Param("processingStatus") String processingStatus,
                               @Param("retryStatus") String retryStatus,
                               @Param("closedStatus") String closedStatus,
                               @Param("limit") int limit);
}
