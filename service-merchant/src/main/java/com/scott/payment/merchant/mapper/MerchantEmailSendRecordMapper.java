package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailSendRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantEmailSendRecordMapper
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户邮件发送记录 Mapper，位于 service-merchant 数据访问层；记录商户 MFA 通知发送结果供管理系统邮件记录页面查询。
 * @status : create
 */
@Mapper
public interface MerchantEmailSendRecordMapper extends BaseMapper<MerchantEmailSendRecordDO> {

    /**
     * 按消息携带的完整投递键读取未删除记录。
     *
     * @param id 发送记录主键
     * @param emailNo 邮件流水号
     * @param appCode 固定 MERCHANT 应用编码
     * @return 匹配记录，不存在时返回 null
     */
    @Select("""
            SELECT * FROM msg_email_send_record
            WHERE id = #{id} AND email_no = #{emailNo} AND app_code = #{appCode} AND deleted = 0
            """)
    MerchantEmailSendRecordDO selectByDeliveryKey(@Param("id") Long id,
                                                   @Param("emailNo") String emailNo,
                                                   @Param("appCode") String appCode);

    /**
     * 仅允许一个消费者将待发送记录抢占为发送中。
     *
     * @return 1 表示抢占成功，0 表示状态已被其它消费者推进
     */
    @Update("""
            UPDATE msg_email_send_record
            SET send_status = 1, send_start_time = #{now}, send_end_time = NULL,
                next_retry_time = NULL, error_code = NULL, error_message = NULL, update_time = #{now}
            WHERE id = #{id} AND email_no = #{emailNo} AND app_code = #{appCode}
              AND send_status = 0 AND deleted = 0
            """)
    int claimForDelivery(@Param("id") Long id,
                         @Param("emailNo") String emailNo,
                         @Param("appCode") String appCode,
                         @Param("now") LocalDateTime now);

    /**
     * SMTP 成功后只允许从发送中推进到成功终态并清除正文密文。
     *
     * @return 1 表示终态写入成功，0 表示当前记录已不处于发送中
     */
    @Update("""
            UPDATE msg_email_send_record
            SET send_status = 2, send_end_time = #{now}, send_success_time = #{now}, cost_ms = #{costMs},
                next_retry_time = NULL, error_code = NULL, error_message = NULL,
                delivery_content_cipher = NULL, update_time = #{now}
            WHERE id = #{id} AND email_no = #{emailNo} AND app_code = #{appCode}
              AND send_status = 1 AND deleted = 0
            """)
    int markDeliverySuccess(@Param("id") Long id,
                            @Param("emailNo") String emailNo,
                            @Param("appCode") String appCode,
                            @Param("now") LocalDateTime now,
                            @Param("costMs") Long costMs);

    /**
     * SMTP 失败后按最大重试次数进入等待或关闭，不覆盖其它状态。
     *
     * @return 1 表示失败状态写入成功，0 表示 CAS 条件不成立
     */
    @Update("""
            UPDATE msg_email_send_record
            SET send_status = CASE WHEN retry_count >= max_retry_count THEN 3 ELSE 4 END,
                next_retry_time = CASE WHEN retry_count >= max_retry_count THEN NULL ELSE #{nextRetryTime} END,
                retry_count = retry_count + 1, send_end_time = #{now}, cost_ms = #{costMs},
                error_code = #{errorCode}, error_message = #{errorMessage}, update_time = #{now}
            WHERE id = #{id} AND email_no = #{emailNo} AND app_code = #{appCode}
              AND send_status = 1 AND deleted = 0
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
     * 查询已到达重投时间的有界记录集合。
     *
     * @param appCode 固定 MERCHANT 应用编码
     * @param now 当前扫描时间
     * @param limit 单轮最大记录数
     * @return 按重试时间和主键排序的待重投记录
     */
    @Select("""
            SELECT * FROM msg_email_send_record
            WHERE app_code = #{appCode} AND send_status = 4
              AND next_retry_time <= #{now} AND deleted = 0
            ORDER BY next_retry_time ASC, id ASC LIMIT #{limit}
            """)
    List<MerchantEmailSendRecordDO> selectDueForRetry(@Param("appCode") String appCode,
                                                      @Param("now") LocalDateTime now,
                                                      @Param("limit") int limit);

    /**
     * 到期记录只有 CAS 成功后才重新进入待发送状态。
     *
     * @return 1 表示重入队成功，0 表示记录未到期或已被其它实例处理
     */
    @Update("""
            UPDATE msg_email_send_record
            SET send_status = 0, next_retry_time = NULL, update_time = #{now}
            WHERE id = #{id} AND email_no = #{emailNo} AND app_code = #{appCode}
              AND send_status = 4 AND next_retry_time <= #{now} AND deleted = 0
            """)
    int requeueForDelivery(@Param("id") Long id,
                           @Param("emailNo") String emailNo,
                           @Param("appCode") String appCode,
                           @Param("now") LocalDateTime now);

    /**
     * 将超时发送中的记录推进到重试等待或关闭，不触碰成功等其它状态。
     *
     * @return 本轮恢复的记录数
     */
    @Update("""
            UPDATE msg_email_send_record
            SET send_status = CASE WHEN retry_count >= max_retry_count THEN 3 ELSE 4 END,
                next_retry_time = CASE WHEN retry_count >= max_retry_count THEN NULL ELSE #{nextRetryTime} END,
                retry_count = retry_count + 1, send_end_time = #{now},
                error_code = 'EMAIL_SEND_TIMEOUT', error_message = 'email delivery processing timeout',
                update_time = #{now}
            WHERE app_code = #{appCode} AND send_status = 1
              AND send_start_time < #{staleBefore} AND deleted = 0
            """)
    int recoverStaleDelivery(@Param("appCode") String appCode,
                             @Param("staleBefore") LocalDateTime staleBefore,
                             @Param("nextRetryTime") LocalDateTime nextRetryTime,
                             @Param("now") LocalDateTime now);
}
