package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.email.EmailEntities.EmailSendRecordDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;


/**
 * Email Send Record 数据访问接口，负责对应持久化记录的查询和写入。
 */
public interface EmailSendRecordMapper extends BaseMapper<EmailSendRecordDO> {

    /**
     * 按业务类型与通知编号查找投递记录，供重复事件复用已有邮件任务。
     */
    @Select("""
            SELECT * FROM msg_email_send_record WHERE business_notification_key = #{key} LIMIT 1
            """)
    EmailSendRecordDO findBusinessNotification(@Param("key") byte[] key);

    /**
     * 锁定对应业务通知记录，防止并发处理者各自创建投递任务。
     */
    @Select("""
            SELECT * FROM msg_email_send_record WHERE business_notification_key = #{key} LIMIT 1 FOR UPDATE
            """)
    EmailSendRecordDO lockBusinessNotification(@Param("key") byte[] key);

    /**
     * 按消息携带的完整投递键读取未删除记录。
     */
    @Select("""
            SELECT *
            FROM msg_email_send_record
            WHERE id = #{id}
              AND email_no = #{emailNo}
              AND app_code = #{appCode}
              AND deleted = 0
            """)
    EmailSendRecordDO selectByDeliveryKey(@Param("id") Long id,
                                          @Param("emailNo") String emailNo,
                                          @Param("appCode") String appCode);

    /**
     * 仅允许一个消费者将待发送记录抢占为发送中。
     */
    @Update("""
            UPDATE msg_email_send_record
            SET send_status = 1,
                send_start_time = #{now},
                send_end_time = NULL,
                next_retry_time = NULL,
                error_code = NULL,
                error_message = NULL,
                update_time = #{now}
            WHERE id = #{id}
              AND email_no = #{emailNo}
              AND app_code = #{appCode}
              AND send_status = 0
              AND deleted = 0
            """)
    int claimForDelivery(@Param("id") Long id,
                         @Param("emailNo") String emailNo,
                         @Param("appCode") String appCode,
                         @Param("now") LocalDateTime now);

    /**
     * SMTP 成功后只允许从发送中推进到成功终态。
     */
    @Update("""
            UPDATE msg_email_send_record
            SET send_status = 2,
                send_end_time = #{now},
                send_success_time = #{now},
                cost_ms = #{costMs},
                next_retry_time = NULL,
                error_code = NULL,
                error_message = NULL,
                delivery_content_cipher = NULL,
                update_time = #{now}
            WHERE id = #{id}
              AND email_no = #{emailNo}
              AND app_code = #{appCode}
              AND send_status = 1
              AND deleted = 0
            """)
    int markDeliverySuccess(@Param("id") Long id,
                            @Param("emailNo") String emailNo,
                            @Param("appCode") String appCode,
                            @Param("now") LocalDateTime now,
                            @Param("costMs") Long costMs);

    /** 取消权威配置已恢复的退化邮件，清理密文并结束重试。 */
    @Update("""
            UPDATE msg_email_send_record
            SET send_status = 5, send_end_time = #{now}, next_retry_time = NULL,
                error_code = 'PAYMENT_CAPABILITY_SUPERSEDED',
                error_message = 'Payment capability notification is superseded', delivery_content_cipher = NULL,
                update_time = #{now}
            WHERE id = #{id} AND email_no = #{emailNo} AND app_code = 'ADMIN'
              AND biz_type = 'MERCHANT_PAYMENT_CAPABILITY' AND send_status = 1 AND deleted = 0
            """)
    int cancelSupersededPaymentCapability(@Param("id") Long id, @Param("emailNo") String emailNo,
                                          @Param("now") LocalDateTime now);

    /**
     * SMTP 失败后按最大重试次数进入等待或关闭，且不得覆盖其它状态。
     */
    @Update("""
            UPDATE msg_email_send_record
            SET send_status = CASE
                    WHEN retry_count >= max_retry_count THEN 3
                    ELSE 4
                END,
                next_retry_time = CASE
                    WHEN retry_count >= max_retry_count THEN NULL
                    ELSE #{nextRetryTime}
                END,
                retry_count = retry_count + 1,
                send_end_time = #{now},
                cost_ms = #{costMs},
                error_code = #{errorCode},
                error_message = #{errorMessage},
                update_time = #{now}
            WHERE id = #{id}
              AND email_no = #{emailNo}
              AND app_code = #{appCode}
              AND send_status = 1
              AND deleted = 0
            """)
    int markDeliveryFailure(@Param("id") Long id,
                            @Param("emailNo") String emailNo,
                            @Param("appCode") String appCode,
                            @Param("now") LocalDateTime now,
                            @Param("nextRetryTime") LocalDateTime nextRetryTime,
                            @Param("costMs") Long costMs,
                            @Param("errorCode") String errorCode,
                            @Param("errorMessage") String errorMessage);

    /**
     * 查询已经到达重投时间的记录。
     */
    @Select("""
            SELECT *
            FROM msg_email_send_record
            WHERE app_code = #{appCode}
              AND send_status = 4
              AND next_retry_time <= #{now}
              AND deleted = 0
            ORDER BY next_retry_time ASC, id ASC
            LIMIT #{limit}
            """)
    java.util.List<EmailSendRecordDO> selectDueForRetry(@Param("appCode") String appCode,
                                                        @Param("now") LocalDateTime now,
                                                        @Param("limit") int limit);

    /**
     * 到期记录只有 CAS 成功后才重新进入待发送状态。
     */
    @Update("""
            UPDATE msg_email_send_record
            SET send_status = 0,
                next_retry_time = NULL,
                update_time = #{now}
            WHERE id = #{id}
              AND email_no = #{emailNo}
              AND app_code = #{appCode}
              AND send_status = 4
              AND next_retry_time <= #{now}
              AND deleted = 0
            """)
    int requeueForDelivery(@Param("id") Long id,
                           @Param("emailNo") String emailNo,
                           @Param("appCode") String appCode,
                           @Param("now") LocalDateTime now);

    /**
     * 将超时发送中的记录推进到重试等待或关闭，不触碰其它状态。
     */
    @Update("""
            UPDATE msg_email_send_record
            SET send_status = CASE
                    WHEN retry_count >= max_retry_count THEN 3
                    ELSE 4
                END,
                next_retry_time = CASE
                    WHEN retry_count >= max_retry_count THEN NULL
                    ELSE #{nextRetryTime}
                END,
                retry_count = retry_count + 1,
                send_end_time = #{now},
                error_code = 'EMAIL_SEND_TIMEOUT',
                error_message = 'email delivery processing timeout',
                update_time = #{now}
            WHERE app_code = #{appCode}
              AND send_status = 1
              AND send_start_time < #{staleBefore}
              AND deleted = 0
            """)
    int recoverStaleDelivery(@Param("appCode") String appCode,
                             @Param("staleBefore") LocalDateTime staleBefore,
                             @Param("nextRetryTime") LocalDateTime nextRetryTime,
                             @Param("now") LocalDateTime now);

    /**
     * 有界筛选到达下次投递时间的支付能力邮件，避免扫描其他邮件场景。
     */
    @Select("""
            SELECT * FROM msg_email_send_record
            WHERE app_code = 'ADMIN' AND biz_type = 'MERCHANT_PAYMENT_CAPABILITY'
              AND send_status = 4 AND next_retry_time <= #{now} AND deleted = 0
            ORDER BY next_retry_time, id
            LIMIT #{limit}
            """)
    java.util.List<EmailSendRecordDO> selectCapabilityDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 将支付能力通知中租约过期的发送任务恢复到可重试状态。
     */
    @Update("""
            UPDATE msg_email_send_record
            SET send_status = CASE WHEN retry_count >= max_retry_count THEN 3 ELSE 4 END,
                next_retry_time = CASE WHEN retry_count >= max_retry_count THEN NULL ELSE #{nextRetryTime} END,
                retry_count = retry_count + 1, send_end_time = #{now}, update_time = #{now},
                error_code = 'EMAIL_SEND_TIMEOUT', error_message = 'email delivery processing timeout'
            WHERE app_code = 'ADMIN' AND biz_type = 'MERCHANT_PAYMENT_CAPABILITY'
              AND send_status = 1 AND send_start_time < #{staleBefore} AND deleted = 0
            ORDER BY send_start_time, id
            LIMIT #{limit}
            """)
    int recoverCapabilityStaleDelivery(@Param("staleBefore") LocalDateTime staleBefore,
                                       @Param("nextRetryTime") LocalDateTime nextRetryTime,
                                       @Param("now") LocalDateTime now,
                                       @Param("limit") int limit);
}
