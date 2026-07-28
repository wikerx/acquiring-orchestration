package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Hosted Checkout 会话 Mapper。
 */
public interface PaymentCheckoutSessionMapper extends BaseMapper<PaymentCheckoutSessionDO> {

    @Select("""
            SELECT *
            FROM payment_checkout_session
            WHERE checkout_session_id = #{checkoutSessionId}
              AND deleted = 0
            LIMIT 1
            """)
    PaymentCheckoutSessionDO selectByCheckoutSessionId(@Param("checkoutSessionId") String checkoutSessionId);

    @Select("""
            SELECT *
            FROM payment_checkout_session
            WHERE merchant_id = #{merchantId}
              AND merchant_request_id = #{merchantRequestId}
              AND deleted = 0
            LIMIT 1
            """)
    PaymentCheckoutSessionDO selectByMerchantRequest(@Param("merchantId") String merchantId,
                                                     @Param("merchantRequestId") String merchantRequestId);

    @Update("""
            UPDATE payment_checkout_session
            SET last_open_time = #{now},
                update_time = #{now}
            WHERE checkout_session_id = #{checkoutSessionId}
              AND deleted = 0
            """)
    int markOpened(@Param("checkoutSessionId") String checkoutSessionId,
                   @Param("now") LocalDateTime now);

    @Update("""
            UPDATE payment_checkout_session
            SET checkout_status = #{nextStatus},
                process_stage = #{nextProcessStage},
                last_status_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE checkout_session_id = #{checkoutSessionId}
              AND checkout_status = #{currentStatus}
              AND version = #{version}
              AND deleted = 0
            """)
    int updateStatusCas(@Param("checkoutSessionId") String checkoutSessionId,
                        @Param("currentStatus") String currentStatus,
                        @Param("nextStatus") String nextStatus,
                        @Param("nextProcessStage") String nextProcessStage,
                        @Param("version") Integer version,
                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE payment_checkout_session
            SET checkout_status = #{nextStatus},
                process_stage = #{nextProcessStage},
                latest_transaction_id = #{latestTransactionId},
                operation_id = #{operationId},
                transaction_date_time = #{transactionDateTime},
                last_attempt_id = #{lastAttemptId},
                attempt_count = attempt_count + 1,
                last_submit_time = #{now},
                last_status_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE checkout_session_id = #{checkoutSessionId}
              AND checkout_status IN ('PAYABLE', 'PAYABLE_FAILED_RETRYABLE')
              AND version = #{version}
              AND deleted = 0
            """)
    int markSubmittedCas(@Param("checkoutSessionId") String checkoutSessionId,
                         @Param("nextStatus") String nextStatus,
                         @Param("nextProcessStage") String nextProcessStage,
                         @Param("latestTransactionId") String latestTransactionId,
                         @Param("operationId") String operationId,
                         @Param("transactionDateTime") LocalDateTime transactionDateTime,
                         @Param("lastAttemptId") String lastAttemptId,
                         @Param("version") Integer version,
                         @Param("now") LocalDateTime now);

    @Update("""
            UPDATE payment_checkout_session
            SET checkout_status = 'SUCCEEDED',
                process_stage = #{processStage},
                success_attempt_id = #{successAttemptId},
                latest_transaction_id = #{latestTransactionId},
                operation_id = #{operationId},
                transaction_date_time = #{transactionDateTime},
                paid_time = #{now},
                last_status_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE checkout_session_id = #{checkoutSessionId}
              AND checkout_status IN ('PAYING', 'AUTHENTICATING', 'PROCESSING')
              AND success_attempt_id IS NULL
              AND version = #{version}
              AND deleted = 0
            """)
    int markSucceededCas(@Param("checkoutSessionId") String checkoutSessionId,
                         @Param("processStage") String processStage,
                         @Param("successAttemptId") String successAttemptId,
                         @Param("latestTransactionId") String latestTransactionId,
                         @Param("operationId") String operationId,
                         @Param("transactionDateTime") LocalDateTime transactionDateTime,
                         @Param("version") Integer version,
                         @Param("now") LocalDateTime now);

    /**
     * 将会话推进到 3DS 认证中，CAS 条件保护成功会话不被浏览器重试覆盖。
     *
     * @param checkoutSessionId 收银台会话号
     * @param processStage 当前处理阶段
     * @param version 当前记录版本号
     * @param now 状态更新时间
     * @return 更新成功行数，0 表示状态不允许流转或版本已变化
     */
    @Update("""
            UPDATE payment_checkout_session
            SET checkout_status = 'AUTHENTICATING',
                process_stage = #{processStage},
                last_status_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE checkout_session_id = #{checkoutSessionId}
              AND checkout_status IN ('PAYING', 'PROCESSING')
              AND success_attempt_id IS NULL
              AND version = #{version}
              AND deleted = 0
            """)
    int markAuthenticatingCas(@Param("checkoutSessionId") String checkoutSessionId,
                              @Param("processStage") String processStage,
                              @Param("version") Integer version,
                              @Param("now") LocalDateTime now);

    /**
     * 将会话推进到 PROCESSING，保留 success_attempt_id 为空的条件以避免覆盖成功终态。
     *
     * @param checkoutSessionId 收银台会话号
     * @param processStage 当前处理阶段
     * @param version 当前记录版本号
     * @param now 状态更新时间
     * @return 更新成功行数，0 表示状态不允许流转或版本已变化
     */
    @Update("""
            UPDATE payment_checkout_session
            SET checkout_status = 'PROCESSING',
                process_stage = #{processStage},
                last_status_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE checkout_session_id = #{checkoutSessionId}
              AND checkout_status IN ('PAYING', 'AUTHENTICATING', 'PROCESSING')
              AND success_attempt_id IS NULL
              AND version = #{version}
              AND deleted = 0
            """)
    int markProcessingCas(@Param("checkoutSessionId") String checkoutSessionId,
                          @Param("processStage") String processStage,
                          @Param("version") Integer version,
                          @Param("now") LocalDateTime now);

    /**
     * 将会话标记为失败或可重试失败，CAS 条件限制只能从支付中间态进入。
     *
     * @param checkoutSessionId 收银台会话号
     * @param nextStatus 失败类下一状态，由服务层按重试次数和业务规则计算
     * @param processStage 当前处理阶段
     * @param version 当前记录版本号
     * @param now 状态更新时间
     * @return 更新成功行数，0 表示状态不允许流转或版本已变化
     */
    @Update("""
            UPDATE payment_checkout_session
            SET checkout_status = #{nextStatus},
                process_stage = #{processStage},
                last_status_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE checkout_session_id = #{checkoutSessionId}
              AND checkout_status IN ('PAYING', 'AUTHENTICATING', 'PROCESSING')
              AND success_attempt_id IS NULL
              AND version = #{version}
              AND deleted = 0
            """)
    int markFailedCas(@Param("checkoutSessionId") String checkoutSessionId,
                      @Param("nextStatus") String nextStatus,
                      @Param("processStage") String processStage,
                      @Param("version") Integer version,
                      @Param("now") LocalDateTime now);
}
