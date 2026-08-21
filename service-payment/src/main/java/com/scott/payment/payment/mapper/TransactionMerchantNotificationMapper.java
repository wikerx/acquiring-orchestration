package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantNotificationMapper
 * @date : 2026-07-14 19:48
 * @email : scott_x@163.com
 * @description : 商户通知任务 Mapper，位于 service-payment 数据访问层，仅访问 transaction_merchant_notification 逻辑表并以分片时间保护 CAS。
 * @status : create
 */
public interface TransactionMerchantNotificationMapper extends BaseMapper<TransactionMerchantNotificationDO> {

    /**
     * 写入商户通知任务逻辑表。
     *
     * @param notificationDO 商户通知任务
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_merchant_notification
            (
              notify_id, transaction_id, operation_id, merchant_id, merchant_order_no,
              notify_type, event_type, notify_status, notify_config_version, callback_url, payload_json,
              target_url_hash, target_url_masked, payload_json_masked, sign_type, last_attempt_no,
              max_retry_count, next_retry_time, success_time, fail_reason, transaction_date_time,
              transaction_utc_time, transaction_time_zone, version, deleted, create_time, update_time
            )
            VALUES
            (
              #{notificationDO.notifyId}, #{notificationDO.transactionId}, #{notificationDO.operationId},
              #{notificationDO.merchantId}, #{notificationDO.merchantOrderNo}, #{notificationDO.notifyType},
              #{notificationDO.eventType}, #{notificationDO.notifyStatus}, #{notificationDO.notifyConfigVersion},
              #{notificationDO.callbackUrl}, #{notificationDO.payloadJson}, #{notificationDO.targetUrlHash},
              #{notificationDO.targetUrlMasked}, #{notificationDO.payloadJsonMasked}, #{notificationDO.signType},
              #{notificationDO.lastAttemptNo}, #{notificationDO.maxRetryCount}, #{notificationDO.nextRetryTime},
              #{notificationDO.successTime}, #{notificationDO.failReason}, #{notificationDO.transactionDateTime},
              #{notificationDO.transactionUtcTime}, #{notificationDO.transactionTimeZone}, #{notificationDO.version},
              #{notificationDO.deleted}, #{notificationDO.createTime}, #{notificationDO.updateTime}
            )
            """)
    int insertLogical(@Param("notificationDO") TransactionMerchantNotificationDO notificationDO);

    /**
     * 按商户、源交易 ID 和精确分片时间读取最近一份可继承通知配置。
     *
     * @param merchantId 商户号
     * @param transactionId 源平台交易 ID
     * @param transactionDateTime 源交易分片时间
     * @return 最近一份含回调地址的通知任务，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM transaction_merchant_notification
            WHERE merchant_id = #{merchantId}
              AND transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND callback_url IS NOT NULL
              AND callback_url != ''
              AND deleted = 0
            ORDER BY create_time DESC, id DESC
            LIMIT 1
            """)
    TransactionMerchantNotificationDO selectLatestConfigByTransactionId(
            @Param("merchantId") String merchantId,
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 精确读取一条尚未激活的通知任务，供终态事务在 CAS 成功后创建首次延时事件。
     *
     * @param transactionId 平台交易 ID
     * @param transactionDateTime 交易分片时间
     * @param expectedVersion 当前通知任务版本
     * @return 待激活通知任务；不存在时返回 null
     */
    @Select("""
            SELECT notify_id, transaction_id, operation_id, merchant_id, merchant_order_no,
                   transaction_date_time, version
            FROM transaction_merchant_notification
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND notify_status = 'INIT'
              AND next_retry_time IS NULL
              AND deleted = 0
            LIMIT 1
            """)
    TransactionMerchantNotificationDO selectPendingActivation(
            @Param("transactionId") String transactionId,
            @Param("transactionDateTime") LocalDateTime transactionDateTime,
            @Param("expectedVersion") Integer expectedVersion);

    /**
     * 激活逻辑表中指定交易分片的终态商户通知。
     *
     * @param transactionId 平台当前交易 ID
     * @param transactionDateTime 交易分片时间
     * @param expectedVersion 当前通知版本号
     * @param callbackPayloadJson 商户正式终态回调载荷明文，禁止写入日志
     * @param payloadJsonMasked 脱敏后的终态通知审计载荷
     * @param nextRetryTime 下一次通知时间
     * @param now 当前时间
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_merchant_notification
            SET payload_json = #{callbackPayloadJson},
                payload_json_masked = #{payloadJsonMasked},
                next_retry_time = #{nextRetryTime},
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND notify_status = 'INIT'
              AND next_retry_time IS NULL
              AND deleted = 0
            """)
    int activateByTransactionId(@Param("transactionId") String transactionId,
                                @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                @Param("expectedVersion") Integer expectedVersion,
                                @Param("callbackPayloadJson") String callbackPayloadJson,
                                @Param("payloadJsonMasked") String payloadJsonMasked,
                                @Param("nextRetryTime") LocalDateTime nextRetryTime,
                                @Param("now") LocalDateTime now);
}
