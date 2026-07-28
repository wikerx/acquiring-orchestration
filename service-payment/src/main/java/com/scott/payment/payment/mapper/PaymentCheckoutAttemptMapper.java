package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * Hosted Checkout 支付尝试 Mapper。
 */
public interface PaymentCheckoutAttemptMapper extends BaseMapper<PaymentCheckoutAttemptDO> {

    @Select("""
            SELECT *
            FROM payment_checkout_attempt
            WHERE checkout_attempt_id = #{checkoutAttemptId}
              AND deleted = 0
            LIMIT 1
            """)
    PaymentCheckoutAttemptDO selectByCheckoutAttemptId(@Param("checkoutAttemptId") String checkoutAttemptId);

    @Select("""
            SELECT *
            FROM payment_checkout_attempt
            WHERE checkout_session_id = #{checkoutSessionId}
              AND attempt_request_id = #{attemptRequestId}
              AND deleted = 0
            LIMIT 1
            """)
    PaymentCheckoutAttemptDO selectByAttemptRequest(@Param("checkoutSessionId") String checkoutSessionId,
                                                    @Param("attemptRequestId") String attemptRequestId);

    @Select("""
            SELECT COALESCE(MAX(attempt_no), 0)
            FROM payment_checkout_attempt
            WHERE checkout_session_id = #{checkoutSessionId}
              AND deleted = 0
            """)
    Integer selectMaxAttemptNo(@Param("checkoutSessionId") String checkoutSessionId);

    /**
     * 写入一次付款尝试的终态结果，CAS 条件同时保护版本号和终态不可逆。
     *
     * @param checkoutAttemptId 收银台付款尝试号
     * @param nextStatus 下一尝试状态，只允许由服务层传入受控枚举值
     * @param nextProcessStage 下一页面/处理阶段，用于前端结果页和后台审计对齐
     * @param channelStatus 渠道统一状态
     * @param channelResponseCode 渠道响应码
     * @param channelResponseMessage 渠道响应信息，调用方必须传入脱敏后的可记录文本
     * @param failureReasonCode 平台失败原因码
     * @param failureReasonMessage 平台失败原因说明
     * @param payerVisibleMessage 可展示给付款人的失败提示
     * @param resultSnapshot 结果页快照，禁止包含 PAN、CVV 等敏感明文
     * @param version 当前记录版本号
     * @param completeTime 尝试完成时间
     * @return 更新成功行数，0 表示版本冲突或记录已进入终态
     */
    @Update("""
            UPDATE payment_checkout_attempt
            SET attempt_status = #{nextStatus},
                process_stage = #{nextProcessStage},
                version = version + 1,
                update_time = #{now}
            WHERE checkout_attempt_id = #{checkoutAttemptId}
              AND attempt_status = #{currentStatus}
              AND version = #{version}
              AND deleted = 0
            """)
    int updateStatusCas(@Param("checkoutAttemptId") String checkoutAttemptId,
                        @Param("currentStatus") String currentStatus,
                        @Param("nextStatus") String nextStatus,
                        @Param("nextProcessStage") String nextProcessStage,
                        @Param("version") Integer version,
                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE payment_checkout_attempt
            SET attempt_status = #{nextStatus},
                process_stage = #{nextProcessStage},
                three_ds_required = 1,
                three_ds_status = #{threeDsStatus},
                three_ds_return_token_hash = #{threeDsReturnTokenHash},
                authentication_redirect_url_hash = #{authenticationRedirectUrlHash},
                authentication_start_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE checkout_attempt_id = #{checkoutAttemptId}
              AND attempt_status IN ('CARD_SUBMITTED', 'THREE_DS_INITIATED')
              AND version = #{version}
              AND deleted = 0
            """)
    int markThreeDsRequiredCas(@Param("checkoutAttemptId") String checkoutAttemptId,
                               @Param("nextStatus") String nextStatus,
                               @Param("nextProcessStage") String nextProcessStage,
                               @Param("threeDsStatus") String threeDsStatus,
                               @Param("threeDsReturnTokenHash") String threeDsReturnTokenHash,
                               @Param("authenticationRedirectUrlHash") String authenticationRedirectUrlHash,
                               @Param("version") Integer version,
                               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE payment_checkout_attempt
            SET attempt_status = #{nextStatus},
                process_stage = #{nextProcessStage},
                channel_mid_config_id = #{channelMidConfigId},
                channel_order_no = #{channelOrderNo},
                channel_transaction_id = #{channelTransactionId},
                channel_request_id = #{channelRequestId},
                three_ds_required = #{threeDsRequired},
                three_ds_status = #{threeDsStatus},
                three_ds_version = #{threeDsVersion},
                three_ds_transaction_id = #{threeDsTransactionId},
                three_ds_server_transaction_id = #{threeDsServerTransactionId},
                acs_transaction_id = #{acsTransactionId},
                ds_transaction_id = #{dsTransactionId},
                eci = #{eci},
                liability_shift = #{liabilityShift},
                authentication_complete_time = #{authenticationCompleteTime},
                version = version + 1,
                update_time = #{now}
            WHERE checkout_attempt_id = #{checkoutAttemptId}
              AND attempt_status NOT IN ('SUCCEEDED', 'FAILED', 'ABANDONED')
              AND version = #{version}
              AND deleted = 0
            """)
    int markAuthenticationResultCas(@Param("checkoutAttemptId") String checkoutAttemptId,
                                    @Param("nextStatus") String nextStatus,
                                    @Param("nextProcessStage") String nextProcessStage,
                                    @Param("channelMidConfigId") Long channelMidConfigId,
                                    @Param("channelOrderNo") String channelOrderNo,
                                    @Param("channelTransactionId") String channelTransactionId,
                                    @Param("channelRequestId") String channelRequestId,
                                    @Param("threeDsRequired") Integer threeDsRequired,
                                    @Param("threeDsStatus") String threeDsStatus,
                                    @Param("threeDsVersion") String threeDsVersion,
                                    @Param("threeDsTransactionId") String threeDsTransactionId,
                                    @Param("threeDsServerTransactionId") String threeDsServerTransactionId,
                                    @Param("acsTransactionId") String acsTransactionId,
                                    @Param("dsTransactionId") String dsTransactionId,
                                    @Param("eci") String eci,
                                    @Param("liabilityShift") Integer liabilityShift,
                                    @Param("authenticationCompleteTime") LocalDateTime authenticationCompleteTime,
                                    @Param("version") Integer version,
                                    @Param("now") LocalDateTime now);

    @Update("""
            UPDATE payment_checkout_attempt
            SET attempt_status = #{nextStatus},
                process_stage = #{nextProcessStage},
                channel_status = #{channelStatus},
                channel_response_code = #{channelResponseCode},
                channel_response_message = #{channelResponseMessage},
                failure_reason_code = #{failureReasonCode},
                failure_reason_message = #{failureReasonMessage},
                payer_visible_message = #{payerVisibleMessage},
                complete_time = #{completeTime},
                result_snapshot = #{resultSnapshot},
                version = version + 1,
                update_time = #{completeTime}
            WHERE checkout_attempt_id = #{checkoutAttemptId}
              AND attempt_status NOT IN ('SUCCEEDED', 'FAILED', 'ABANDONED')
              AND version = #{version}
              AND deleted = 0
            """)
    int markResultCas(@Param("checkoutAttemptId") String checkoutAttemptId,
                      @Param("nextStatus") String nextStatus,
                      @Param("nextProcessStage") String nextProcessStage,
                      @Param("channelStatus") String channelStatus,
                      @Param("channelResponseCode") String channelResponseCode,
                      @Param("channelResponseMessage") String channelResponseMessage,
                      @Param("failureReasonCode") String failureReasonCode,
                      @Param("failureReasonMessage") String failureReasonMessage,
                      @Param("payerVisibleMessage") String payerVisibleMessage,
                      @Param("resultSnapshot") String resultSnapshot,
                      @Param("version") Integer version,
                      @Param("completeTime") LocalDateTime completeTime);

    /**
     * 标记 3DS 浏览器回跳已到达，只把尝试推进到等待渠道结果。
     *
     * @param checkoutAttemptId 收银台付款尝试号
     * @param nextProcessStage 回跳后的处理阶段
     * @param version 当前记录版本号
     * @param now 状态更新时间
     * @return 更新成功行数，0 表示尝试不在 THREE_DS_REQUIRED 或版本已变化
     */
    @Update("""
            UPDATE payment_checkout_attempt
            SET attempt_status = 'PROCESSING',
                process_stage = #{nextProcessStage},
                version = version + 1,
                update_time = #{now}
            WHERE checkout_attempt_id = #{checkoutAttemptId}
              AND attempt_status = 'THREE_DS_REQUIRED'
              AND version = #{version}
              AND deleted = 0
            """)
    int markThreeDsReturnedCas(@Param("checkoutAttemptId") String checkoutAttemptId,
                               @Param("nextProcessStage") String nextProcessStage,
                               @Param("version") Integer version,
                               @Param("now") LocalDateTime now);
}
