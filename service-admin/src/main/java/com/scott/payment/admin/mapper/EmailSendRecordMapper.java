package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.email.EmailEntities.EmailSendRecordDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailSendRecordMapper
 * @date : 2026-07-04 16:11
 * @email : scott_x@163.com
 * @description : Email Send Record Mapper 映射组件，位于 运营后台服务，在数据库记录、领域模型、接口 DTO 或渠道协议对象之间转换字段。
 * @status : create
 */
public interface EmailSendRecordMapper extends BaseMapper<EmailSendRecordDO> {

    /** 按消息携带的完整投递键读取未删除记录。 */
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

    /** 仅允许一个消费者将待发送记录抢占为发送中。 */
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

    /** SMTP 成功后只允许从发送中推进到成功终态。 */
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

    /** SMTP 失败后按最大重试次数进入等待或关闭，且不得覆盖其它状态。 */
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

    /** 查询已经到达重投时间的记录。 */
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

    /** 到期记录只有 CAS 成功后才重新进入待发送状态。 */
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

    /** 将超时发送中的记录推进到重试等待或关闭，不触碰其它状态。 */
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
}
