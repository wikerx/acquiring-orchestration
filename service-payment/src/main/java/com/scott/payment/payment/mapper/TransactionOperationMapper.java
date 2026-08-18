package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionOperationDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionOperationMapper
 * @date : 2026-07-14 17:40
 * @email : scott_x@163.com
 * @description : 交易动作单 Mapper，位于 service-payment 数据访问层，仅访问 transaction_operation 逻辑表并显式携带交易分片时间。
 * @status : create
 */
public interface TransactionOperationMapper extends BaseMapper<TransactionOperationDO> {

    /**
     * 审批通过后将退款动作推进到等待 MQ 执行阶段。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_operation
            SET process_stage = 'WAITING_EXECUTION',
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND transaction_type = 'REFUND'
              AND transaction_status = 'PENDING'
              AND process_stage = 'WAITING_APPROVAL'
              AND deleted = 0
            """)
    int approveRefundExecution(@Param("transactionId") String transactionId,
                               @Param("transactionDateTime") LocalDateTime transactionDateTime,
                               @Param("expectedVersion") Integer expectedVersion,
                               @Param("now") LocalDateTime now);

    /**
     * MQ 消费者抢占已批准退款，重复消息只有一个消费者能进入渠道执行。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_operation
            SET transaction_status = 'PROCESSING',
                process_stage = 'CHANNEL_REQUESTING',
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND transaction_type = 'REFUND'
              AND transaction_status = 'PENDING'
              AND process_stage = 'WAITING_EXECUTION'
              AND deleted = 0
            """)
    int claimApprovedRefundExecution(@Param("transactionId") String transactionId,
                                     @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                     @Param("expectedVersion") Integer expectedVersion,
                                     @Param("now") LocalDateTime now);

    /**
     * 审批拒绝或过期时终结退款动作；不修改主单可退金额。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_operation
            SET transaction_status = 'FAILED',
                process_stage = 'FINISHED',
                fail_reason_code = #{reasonCode},
                fail_reason_message = #{reasonMessage},
                channel_match_status = 'NOT_REQUIRED',
                next_channel_match_time = NULL,
                complete_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND transaction_type = 'REFUND'
              AND transaction_status = 'PENDING'
              AND process_stage = 'WAITING_APPROVAL'
              AND deleted = 0
            """)
    int terminatePendingRefundApproval(@Param("transactionId") String transactionId,
                                       @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                       @Param("expectedVersion") Integer expectedVersion,
                                       @Param("reasonCode") String reasonCode,
                                       @Param("reasonMessage") String reasonMessage,
                                       @Param("now") LocalDateTime now);

    /**
     * 通过交易分片时间在逻辑表中查询动作单。
     *
     * @param transactionId 平台当前交易唯一标识
     * @param transactionDateTime 交易分片时间
     * @return 交易动作单，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM transaction_operation
            WHERE transaction_id = #{transactionId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            """)
    TransactionOperationDO selectByTransactionId(@Param("transactionId") String transactionId,
                                                 @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 在受控半开时间范围内按渠道身份查询动作单。
     *
     * @param channelOrderNo 渠道订单号
     * @param channelTransactionId 渠道交易 ID
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 动作单，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM transaction_operation
            WHERE channel_order_no = #{channelOrderNo}
              AND channel_transaction_id = #{channelTransactionId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND deleted = 0
            ORDER BY transaction_date_time DESC, id DESC
            LIMIT 1
            """)
    TransactionOperationDO selectByChannelTransaction(
            @Param("channelOrderNo") String channelOrderNo,
            @Param("channelTransactionId") String channelTransactionId,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 使用分片时间和版本 CAS 将非终态动作推进到目标终态。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_operation
            SET transaction_status = #{transactionStatus},
                process_stage = #{processStage},
                fail_reason_code = #{failReasonCode},
                fail_reason_message = #{failReasonMessage},
                channel_status = #{channelStatus},
                channel_response_code = #{channelResponseCode},
                channel_response_message = #{channelResponseMessage},
                auth_code = #{authCode},
                rrn = #{rrn},
                acquirer_reference_no = #{acquirerReferenceNo},
                channel_match_status = #{channelMatchStatus},
                channel_match_result = #{transactionStatus},
                next_channel_match_time = NULL,
                channel_match_fail_reason = NULL,
                complete_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND deleted = 0
            """)
    int completeStatus(@Param("id") Long id,
                       @Param("transactionDateTime") LocalDateTime transactionDateTime,
                       @Param("expectedVersion") Integer expectedVersion,
                       @Param("transactionStatus") String transactionStatus,
                       @Param("processStage") String processStage,
                       @Param("failReasonCode") String failReasonCode,
                       @Param("failReasonMessage") String failReasonMessage,
                       @Param("channelStatus") String channelStatus,
                       @Param("channelResponseCode") String channelResponseCode,
                       @Param("channelResponseMessage") String channelResponseMessage,
                       @Param("authCode") String authCode,
                       @Param("rrn") String rrn,
                       @Param("acquirerReferenceNo") String acquirerReferenceNo,
                       @Param("channelMatchStatus") String channelMatchStatus);

    /**
     * 使用分片时间和版本 CAS 记录同步渠道非终态结果。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_operation
            SET transaction_status = #{transactionStatus},
                process_stage = #{processStage},
                pending_reason_code = #{pendingReasonCode},
                fail_reason_code = #{failReasonCode},
                fail_reason_message = #{failReasonMessage},
                channel_status = #{channelStatus},
                channel_response_code = #{channelResponseCode},
                channel_response_message = #{channelResponseMessage},
                channel_match_status = 'PENDING',
                channel_match_result = #{transactionStatus},
                last_channel_match_request_id = #{requestId},
                last_channel_match_time = #{matchTime},
                next_channel_match_time = COALESCE(next_channel_match_time, #{matchTime}),
                channel_match_fail_reason = #{failReasonMessage},
                version = version + 1,
                update_time = #{matchTime}
            WHERE id = #{id}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND deleted = 0
            """)
    int updateNonTerminalChannelResult(@Param("id") Long id,
                                       @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                       @Param("expectedVersion") Integer expectedVersion,
                                       @Param("transactionStatus") String transactionStatus,
                                       @Param("processStage") String processStage,
                                       @Param("pendingReasonCode") String pendingReasonCode,
                                       @Param("failReasonCode") String failReasonCode,
                                       @Param("failReasonMessage") String failReasonMessage,
                                       @Param("channelStatus") String channelStatus,
                                       @Param("channelResponseCode") String channelResponseCode,
                                       @Param("channelResponseMessage") String channelResponseMessage,
                                       @Param("requestId") String requestId,
                                       @Param("matchTime") LocalDateTime matchTime);

    /**
     * 在单季度半开范围内查询待渠道勾兑动作。
     *
     * @param channelCode 渠道编码，可为空
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @param now 当前时间
     * @param limit 最大查询数量
     * @return 待勾兑动作单
     */
    @Select("""
            <script>
            SELECT o.*
            FROM transaction_operation o
            WHERE o.deleted = 0
              AND o.transaction_date_time &gt;= #{beginTime}
              AND o.transaction_date_time &lt; #{endTimeExclusive}
              AND o.channel_match_status = 'PENDING'
              AND o.transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND o.channel_code IS NOT NULL
              AND (o.next_channel_match_time IS NULL OR o.next_channel_match_time &lt;= #{now})
              <if test="channelCode != null and channelCode != ''">
                AND o.channel_code = #{channelCode}
              </if>
            ORDER BY COALESCE(o.next_channel_match_time, o.transaction_date_time) ASC, o.id ASC
            LIMIT #{limit}
            </script>
            """)
    List<TransactionOperationDO> selectPendingChannelMatch(
            @Param("channelCode") String channelCode,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive,
            @Param("now") LocalDateTime now,
            @Param("limit") int limit);

    /**
     * 使用分片时间和版本 CAS 标记渠道勾兑结果。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_operation
            SET channel_match_status = #{matchStatus},
                channel_match_result = #{matchResult},
                channel_match_count = COALESCE(channel_match_count, 0) + 1,
                last_channel_match_request_id = #{requestId},
                last_channel_match_time = #{matchTime},
                next_channel_match_time = #{nextMatchTime},
                channel_match_fail_reason = #{failReason},
                version = version + 1,
                update_time = #{matchTime}
            WHERE id = #{id}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND channel_match_status IN ('PENDING', 'REVIEW_REQUIRED', 'MISMATCHED', 'FAILED')
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND deleted = 0
            """)
    int updateChannelMatch(@Param("id") Long id,
                           @Param("transactionDateTime") LocalDateTime transactionDateTime,
                           @Param("expectedVersion") Integer expectedVersion,
                           @Param("matchStatus") String matchStatus,
                           @Param("matchResult") String matchResult,
                           @Param("requestId") String requestId,
                           @Param("matchTime") LocalDateTime matchTime,
                           @Param("nextMatchTime") LocalDateTime nextMatchTime,
                           @Param("failReason") String failReason);

    /**
     * 统计半开时间范围内同一生命周期的动作数。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 动作数量
     */
    @Select("""
            SELECT COUNT(1)
            FROM transaction_operation
            WHERE operation_id = #{operationId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND deleted = 0
            """)
    int countByOperationId(@Param("operationId") String operationId,
                           @Param("beginTime") LocalDateTime beginTime,
                           @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 查询半开时间范围内同一生命周期的全部动作。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 动作单列表
     */
    @Select("""
            SELECT *
            FROM transaction_operation
            WHERE operation_id = #{operationId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND deleted = 0
            ORDER BY operation_sequence ASC, operation_time ASC
            """)
    List<TransactionOperationDO> selectByOperationId(@Param("operationId") String operationId,
                                                     @Param("beginTime") LocalDateTime beginTime,
                                                     @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 按商户订单号和半开时间范围查询动作单。
     *
     * @param merchantId 平台商户号
     * @param merchantOrderNo 商户订单号
     * @param transactionId 平台交易 ID，可为空
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 动作单列表
     */
    @Select("""
            <script>
            SELECT *
            FROM transaction_operation
            WHERE merchant_id = #{merchantId}
              AND merchant_order_no = #{merchantOrderNo}
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt; #{endTimeExclusive}
              AND deleted = 0
              <if test="transactionId != null and transactionId != ''">
                AND transaction_id = #{transactionId}
              </if>
            ORDER BY operation_sequence ASC, operation_time ASC
            </script>
            """)
    List<TransactionOperationDO> selectByMerchantOrder(
            @Param("merchantId") String merchantId,
            @Param("merchantOrderNo") String merchantOrderNo,
            @Param("transactionId") String transactionId,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 按商户订单号和半开时间范围查询首次起点动作。
     *
     * @param merchantId 平台商户号
     * @param merchantOrderNo 商户订单号
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 首次起点动作列表
     */
    @Select("""
            SELECT *
            FROM transaction_operation
            WHERE merchant_id = #{merchantId}
              AND merchant_order_no = #{merchantOrderNo}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND transaction_type IN ('PAYMENT', 'AUTHORIZATION', 'PRE_AUTHORIZATION')
              AND deleted = 0
            ORDER BY transaction_date_time ASC, id ASC
            """)
    List<TransactionOperationDO> selectInitialByMerchantOrder(
            @Param("merchantId") String merchantId,
            @Param("merchantOrderNo") String merchantOrderNo,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 查询半开时间范围内未终态的 Capture 动作。
     *
     * @return 未终态 Capture 动作列表
     */
    @Select("""
            SELECT *
            FROM transaction_operation
            WHERE merchant_id = #{merchantId}
              AND operation_id = #{operationId}
              AND source_transaction_id = #{sourceTransactionId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND transaction_type IN ('CAPTURE', 'PRE_AUTH_COMPLETION')
              AND transaction_status IN ('PROCESSING', 'PENDING')
              AND deleted = 0
            ORDER BY transaction_date_time ASC, id ASC
            """)
    List<TransactionOperationDO> selectNonTerminalCaptures(
            @Param("merchantId") String merchantId,
            @Param("operationId") String operationId,
            @Param("sourceTransactionId") String sourceTransactionId,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 查询半开时间范围内未终态的 Refund 动作。
     *
     * @return 未终态 Refund 动作列表
     */
    @Select("""
            SELECT *
            FROM transaction_operation
            WHERE merchant_id = #{merchantId}
              AND operation_id = #{operationId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND transaction_type = 'REFUND'
              AND transaction_status IN ('PROCESSING', 'PENDING')
              AND deleted = 0
            ORDER BY transaction_date_time ASC, id ASC
            """)
    List<TransactionOperationDO> selectNonTerminalRefunds(
            @Param("merchantId") String merchantId,
            @Param("operationId") String operationId,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 查询半开时间范围内未终态的 Void 动作。
     *
     * @return 未终态 Void 动作列表
     */
    @Select("""
            SELECT *
            FROM transaction_operation
            WHERE merchant_id = #{merchantId}
              AND operation_id = #{operationId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND transaction_type = 'VOID'
              AND transaction_status IN ('PROCESSING', 'PENDING')
              AND deleted = 0
            ORDER BY transaction_date_time ASC, id ASC
            """)
    List<TransactionOperationDO> selectNonTerminalVoids(
            @Param("merchantId") String merchantId,
            @Param("operationId") String operationId,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 查询半开时间范围内未终态的增量授权动作。
     *
     * @return 未终态 Incremental Authorization 动作列表
     */
    @Select("""
            SELECT *
            FROM transaction_operation
            WHERE merchant_id = #{merchantId}
              AND operation_id = #{operationId}
              AND transaction_date_time >= #{beginTime}
              AND transaction_date_time < #{endTimeExclusive}
              AND transaction_type = 'INCREMENTAL_AUTHORIZATION'
              AND transaction_status IN ('PROCESSING', 'PENDING')
              AND deleted = 0
            ORDER BY transaction_date_time ASC, id ASC
            """)
    List<TransactionOperationDO> selectNonTerminalIncrementalAuthorizations(
            @Param("merchantId") String merchantId,
            @Param("operationId") String operationId,
            @Param("beginTime") LocalDateTime beginTime,
            @Param("endTimeExclusive") LocalDateTime endTimeExclusive);


}
