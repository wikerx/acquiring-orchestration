package com.scott.payment.data.mapper;

import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationMapper
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知任务 Mapper，只负责从主库抢占任务以及按乐观锁推进通知执行状态
 * @status : create
 */
public interface DataMerchantNotificationMapper {

    /**
     * 从逻辑表扫描单个季度内已经到期的通知任务。
     *
     * @param beginTime 季度开始时间（含）
     * @param endTimeExclusive 下一季度开始时间（不含）
     * @param now 当前时间
     * @param limit 最大返回数量
     * @return 按重试时间和主键升序排列的任务
     */
    @Select("""
            SELECT id, notify_id, transaction_id, operation_id, merchant_id, merchant_order_no,
                   notify_config_snapshot_json, target_url_hash, target_url_masked,
                   payload_json_masked, sign_type, last_attempt_no, max_retry_count,
                   transaction_date_time, version
            FROM transaction_merchant_notification
            WHERE transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND deleted = 0
              AND notify_status IN ('INIT', 'FAILED')
              AND next_retry_time IS NOT NULL
              AND next_retry_time <= #{now}
              AND last_attempt_no < max_retry_count
            ORDER BY next_retry_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<DataMerchantNotificationTaskDO> selectDueForNotify(@Param("beginTime") LocalDateTime beginTime,
                                                            @Param("endTimeExclusive") LocalDateTime endTimeExclusive,
                                                            @Param("now") LocalDateTime now,
                                                            @Param("limit") int limit);

    /**
     * 通过交易分片时间从逻辑表查询一条可抢占通知任务。
     *
     * @param transactionId 平台交易 ID
     * @param transactionDateTime 交易分片时间
     * @param now 当前时间
     * @return 可执行任务；未到期、已完成或不存在时返回空
     */
    @Select("""
            SELECT id, notify_id, transaction_id, operation_id, merchant_id, merchant_order_no,
                   notify_config_snapshot_json, target_url_hash, target_url_masked,
                   payload_json_masked, sign_type, last_attempt_no, max_retry_count,
                   transaction_date_time, version
            FROM transaction_merchant_notification
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
              AND notify_status IN ('INIT', 'FAILED')
              AND next_retry_time IS NOT NULL
              AND next_retry_time <= #{now}
              AND last_attempt_no < max_retry_count
            ORDER BY next_retry_time ASC, id ASC
            LIMIT 1
            """)
    DataMerchantNotificationTaskDO selectReadyByTransactionId(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime,
            @Param("now") LocalDateTime now);

    /** 查询与自动重试消息版本完全一致且已到期的通知任务。 */
    @Select("""
            SELECT id, notify_id, transaction_id, operation_id, merchant_id, merchant_order_no,
                   notify_config_snapshot_json, target_url_hash, target_url_masked,
                   payload_json_masked, sign_type, last_attempt_no, max_retry_count,
                   transaction_date_time, version
            FROM transaction_merchant_notification
            WHERE transaction_id = #{transactionId}
              AND notify_id = #{notifyId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND deleted = 0
              AND notify_status = 'FAILED'
              AND next_retry_time IS NOT NULL
              AND next_retry_time <= #{now}
              AND last_attempt_no < max_retry_count
            LIMIT 1
            """)
    DataMerchantNotificationTaskDO selectReadyByRetryEvent(
            @Param("transactionId") String transactionId,
            @Param("notifyId") String notifyId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("now") LocalDateTime now);

    /**
     * 查询一条可由后台人工重发的通知任务。
     *
     * <p>SUCCESS、FAILED、CLOSED 和已经设置重试时间的 INIT 可重发；PROCESSING 必须由超时恢复流程处理，
     * 禁止人工请求绕过正在执行的 CAS 锁。</p>
     *
     * @param transactionId 平台交易 ID
     * @param transactionDateTime 交易分片时间
     * @return 可人工重发任务；不存在或正在执行时返回空
     */
    @Select("""
            SELECT id, notify_id, transaction_id, operation_id, merchant_id, merchant_order_no,
                   notify_config_snapshot_json, target_url_hash, target_url_masked,
                   payload_json_masked, sign_type, last_attempt_no, max_retry_count,
                   transaction_date_time, version
            FROM transaction_merchant_notification
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
              AND (
                    notify_status IN ('SUCCESS', 'FAILED', 'CLOSED')
                    OR (notify_status = 'INIT' AND next_retry_time IS NOT NULL)
                  )
            ORDER BY id DESC
            LIMIT 1
            """)
    DataMerchantNotificationTaskDO selectRetryableByTransactionId(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 使用分片时间、版本号和可执行状态抢占逻辑表通知任务。
     *
     * @param id 通知任务主键
     * @param transactionDateTime 交易分片时间
     * @param expectedVersion 读取时的乐观锁版本
     * @param now 抢占时间
     * @return 1 表示抢占成功，0 表示已被其他实例处理
     */
    @Update("""
            UPDATE transaction_merchant_notification
            SET notify_status = 'PROCESSING',
                last_attempt_no = last_attempt_no + 1,
                next_retry_time = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND notify_status IN ('INIT', 'FAILED')
              AND deleted = 0
            """)
    int markProcessing(@Param("id") Long id,
                       @Param("transactionDateTime") LocalDateTime transactionDateTime,
                       @Param("expectedVersion") Integer expectedVersion,
                       @Param("now") LocalDateTime now);

    /**
     * 使用分片时间、版本和允许状态抢占后台人工重发，并为本次失败保留一次自动补偿预算。
     *
     * @return 1 表示抢占成功，0 表示任务状态或版本已经变化
     */
    @Update("""
            UPDATE transaction_merchant_notification
            SET notify_status = 'PROCESSING',
                max_retry_count = GREATEST(
                    COALESCE(max_retry_count, 0),
                    COALESCE(last_attempt_no, 0) + 2
                ),
                last_attempt_no = COALESCE(last_attempt_no, 0) + 1,
                next_retry_time = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND deleted = 0
              AND (
                    notify_status IN ('SUCCESS', 'FAILED', 'CLOSED')
                    OR (notify_status = 'INIT' AND next_retry_time IS NOT NULL)
                  )
            """)
    int markProcessingForManualRetry(@Param("id") Long id,
                                     @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                     @Param("expectedVersion") Integer expectedVersion,
                                     @Param("now") LocalDateTime now);

    /**
     * 将本次抢占的逻辑表任务推进为成功终态。
     *
     * @param id 通知任务主键
     * @param transactionDateTime 交易分片时间
     * @param expectedVersion 抢占成功后的预期版本号
     * @param successTime 商户端点确认成功的时间
     * @return 1 表示推进成功，0 表示任务状态或版本已变更
     */
    @Update("""
            UPDATE transaction_merchant_notification
            SET notify_status = 'SUCCESS',
                success_time = #{successTime},
                next_retry_time = NULL,
                fail_reason = NULL,
                version = version + 1,
                update_time = #{successTime}
            WHERE id = #{id}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND notify_status = 'PROCESSING'
              AND deleted = 0
            """)
    int markSuccess(@Param("id") Long id,
                    @Param("transactionDateTime") LocalDateTime transactionDateTime,
                    @Param("expectedVersion") Integer expectedVersion,
                    @Param("successTime") LocalDateTime successTime);

    /**
     * 将本次抢占的逻辑表任务推进为可重试失败或关闭终态。
     *
     * @param id 通知任务主键
     * @param transactionDateTime 交易分片时间
     * @param expectedVersion 抢占成功后的预期版本号
     * @param nextStatus FAILED 或 CLOSED
     * @param nextRetryTime 下次可投递时间；关闭任务时为空
     * @param failReason 已截断且不含敏感数据的失败摘要
     * @param now 本次状态更新时间
     * @return 1 表示推进成功，0 表示任务状态或版本已变更
     */
    @Update("""
            UPDATE transaction_merchant_notification
            SET notify_status = #{nextStatus},
                next_retry_time = #{nextRetryTime},
                fail_reason = #{failReason},
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND notify_status = 'PROCESSING'
              AND deleted = 0
            """)
    int markFailed(@Param("id") Long id,
                   @Param("transactionDateTime") LocalDateTime transactionDateTime,
                   @Param("expectedVersion") Integer expectedVersion,
                   @Param("nextStatus") String nextStatus,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("failReason") String failReason,
                   @Param("now") LocalDateTime now);

    /**
     * 在单个季度内按游标顺序读取有界的超时 PROCESSING 候选任务。
     *
     * @param beginTime 季度开始时间（含）
     * @param endTimeExclusive 下一季度开始时间（不含）
     * @param staleBefore PROCESSING 更新时间上界
     * @param limit 单批最大候选数
     * @return 携带主键、分片时间和版本号的候选任务
     */
    @Select("""
            SELECT id, transaction_date_time, version
            FROM transaction_merchant_notification
            WHERE transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND deleted = 0
              AND notify_status = 'PROCESSING'
              AND update_time < #{staleBefore}
            ORDER BY update_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<DataMerchantNotificationTaskDO> selectStaleProcessing(
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("limit") int limit);

    /**
     * 使用候选读取时的主键、分片时间和版本号回收一条超时任务。
     *
     * @return 1 表示恢复成功，0 表示任务已被其他实例推进
     */
    @Update("""
            UPDATE transaction_merchant_notification
            SET notify_status = 'FAILED',
                last_attempt_no = GREATEST(last_attempt_no - 1, 0),
                next_retry_time = #{now},
                fail_reason = 'processing timeout recovered',
                version = version + 1,
                update_time = #{now}
            WHERE id = #{id}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND deleted = 0
              AND notify_status = 'PROCESSING'
              AND update_time < #{staleBefore}
            """)
    int recoverStaleProcessingCas(@Param("id") Long id,
                                  @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                  @Param("expectedVersion") Integer expectedVersion,
                                  @Param("staleBefore") LocalDateTime staleBefore,
                                  @Param("now") LocalDateTime now);
}
