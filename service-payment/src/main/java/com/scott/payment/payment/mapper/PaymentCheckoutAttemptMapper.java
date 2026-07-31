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

    /**
     * 按尝试号查询未删除的支付尝试。
     *
     * @param checkoutAttemptId 支付尝试号
     * @return 支付尝试记录；不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM payment_checkout_attempt
            WHERE checkout_attempt_id = #{checkoutAttemptId}
              AND deleted = 0
            LIMIT 1
            """)
    PaymentCheckoutAttemptDO selectByCheckoutAttemptId(@Param("checkoutAttemptId") String checkoutAttemptId);

    /**
     * 按会话号和尝试请求号查询数据库幂等记录。
     *
     * @param checkoutSessionId 会话号
     * @param attemptRequestId  付款端尝试请求号
     * @return 已存在尝试；不存在时返回 null
     */
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

    /**
     * 查询会话当前最大尝试序号。
     *
     * @param checkoutSessionId 会话号
     * @return 最大尝试序号，无记录时返回 0
     */
    @Select("""
            SELECT COALESCE(MAX(attempt_no), 0)
            FROM payment_checkout_attempt
            WHERE checkout_session_id = #{checkoutSessionId}
              AND deleted = 0
            """)
    Integer selectMaxAttemptNo(@Param("checkoutSessionId") String checkoutSessionId);

    /**
     * 按期望状态和版本号 CAS 推进支付尝试状态。
     *
     * @param checkoutAttemptId 支付尝试号
     * @param currentStatus     期望当前状态
     * @param nextStatus        目标状态
     * @param nextProcessStage  目标处理阶段
     * @param version           期望乐观锁版本
     * @param now               状态变更时间
     * @return 更新行数，0 表示状态或版本已变化
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

    /**
     * 将卡数据已提交的尝试 CAS 推进为需要 3DS 挑战。
     *
     * <p>仅持久化一次性回跳令牌和跳转地址的摘要，不保存令牌或带敏感参数的地址明文。</p>
     *
     * @return 更新行数，0 表示尝试状态不允许推进或版本冲突
     */
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

    /**
     * 持久化服务端确认的 3DS 认证结果。
     *
     * <p>SQL 排除支付尝试终态，避免迟到回跳覆盖成功、失败或已放弃状态。</p>
     *
     * @return 更新行数，0 表示尝试已终结或版本冲突
     */
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

    /**
     * 将支付尝试 CAS 写入最终或处理中结果。
     *
     * <p>响应说明和结果快照必须在调用前完成脱敏，SQL 排除既有终态以保持终态不可逆。</p>
     *
     * @return 更新行数，0 表示尝试已终结或版本冲突
     */
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
