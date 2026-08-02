package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionOrderMapper
 * @date : 2026-07-14 17:40
 * @email : scott_x@163.com
 * @description : 交易生命周期主单 Mapper，位于 service-payment 数据访问层，仅负责 transaction_order 逻辑表及物理分表访问。
 * @status : create
 */
public interface TransactionOrderMapper extends BaseMapper<TransactionOrderDO> {

    /**
     * 通过交易分片时间在逻辑表中查询生命周期主单。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param transactionDateTime 交易分片时间
     * @return 交易生命周期主单，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM transaction_order
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            """)
    TransactionOrderDO selectByOperationId(@Param("operationId") String operationId,
                                           @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 通过交易分片时间在逻辑表中锁定唯一生命周期主单。
     *
     * @param operationId 平台内部生命周期关联标识
     * @param transactionDateTime 交易分片时间
     * @return 已在当前事务内锁定的生命周期主单，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM transaction_order
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    TransactionOrderDO selectByOperationIdForUpdate(@Param("operationId") String operationId,
                                                    @Param("transactionDateTime") LocalDateTime transactionDateTime);

    /**
     * 按半开交易时间范围分页查询生命周期主单逻辑表。
     *
     * @param merchantId 平台商户号，可为空
     * @param merchantOrderNo 商户订单号，可为空
     * @param transactionId 平台交易 ID，可为空
     * @param transactionStatus 交易状态，可为空
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @param offset 分页偏移
     * @param limit 分页大小
     * @return 主单列表
     */
    @Select("""
            <script>
            SELECT *
            FROM transaction_order
            WHERE deleted = 0
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt; #{endTimeExclusive}
              <if test="merchantId != null and merchantId != ''">
                AND merchant_id = #{merchantId}
              </if>
              <if test="merchantOrderNo != null and merchantOrderNo != ''">
                AND merchant_order_no = #{merchantOrderNo}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND (root_transaction_id = #{transactionId} OR latest_transaction_id = #{transactionId})
              </if>
              <if test="transactionStatus != null and transactionStatus != ''">
                AND transaction_status = #{transactionStatus}
              </if>
            ORDER BY transaction_date_time DESC, id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<TransactionOrderDO> selectPageLogical(@Param("merchantId") String merchantId,
                                               @Param("merchantOrderNo") String merchantOrderNo,
                                               @Param("transactionId") String transactionId,
                                               @Param("transactionStatus") String transactionStatus,
                                               @Param("beginTime") LocalDateTime beginTime,
                                               @Param("endTimeExclusive") LocalDateTime endTimeExclusive,
                                               @Param("offset") long offset,
                                               @Param("limit") long limit);

    /**
     * 统计半开交易时间范围内的生命周期主单。
     *
     * @param merchantId 平台商户号，可为空
     * @param merchantOrderNo 商户订单号，可为空
     * @param transactionId 平台交易 ID，可为空
     * @param transactionStatus 交易状态，可为空
     * @param beginTime 查询开始时间
     * @param endTimeExclusive 查询结束时间，不包含
     * @return 命中记录数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM transaction_order
            WHERE deleted = 0
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt; #{endTimeExclusive}
              <if test="merchantId != null and merchantId != ''">
                AND merchant_id = #{merchantId}
              </if>
              <if test="merchantOrderNo != null and merchantOrderNo != ''">
                AND merchant_order_no = #{merchantOrderNo}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND (root_transaction_id = #{transactionId} OR latest_transaction_id = #{transactionId})
              </if>
              <if test="transactionStatus != null and transactionStatus != ''">
                AND transaction_status = #{transactionStatus}
              </if>
            </script>
            """)
    long countPageLogical(@Param("merchantId") String merchantId,
                          @Param("merchantOrderNo") String merchantOrderNo,
                          @Param("transactionId") String transactionId,
                          @Param("transactionStatus") String transactionStatus,
                          @Param("beginTime") LocalDateTime beginTime,
                          @Param("endTimeExclusive") LocalDateTime endTimeExclusive);

    /**
     * 使用分片时间和版本 CAS 汇总请款成功金额。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                captured_amount = captured_amount + #{amount},
                available_capture_amount = available_capture_amount - #{amount},
                available_refund_amount = available_refund_amount + #{amount},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND transaction_status = 'SUCCESS'
              AND available_capture_amount >= #{amount}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int increaseCapturedAmount(@Param("operationId") String operationId,
                               @Param("transactionDateTime") LocalDateTime transactionDateTime,
                               @Param("latestTransactionId") String latestTransactionId,
                               @Param("amount") BigDecimal amount,
                               @Param("expectedVersion") Integer expectedVersion);

    /**
     * 使用分片时间和版本 CAS 汇总退款成功金额。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                refunded_amount = refunded_amount + #{amount},
                available_refund_amount = available_refund_amount - #{amount},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND transaction_status = 'SUCCESS'
              AND available_refund_amount >= #{amount}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int increaseRefundedAmount(@Param("operationId") String operationId,
                               @Param("transactionDateTime") LocalDateTime transactionDateTime,
                               @Param("latestTransactionId") String latestTransactionId,
                               @Param("amount") BigDecimal amount,
                               @Param("expectedVersion") Integer expectedVersion);

    /**
     * 使用分片时间和版本 CAS 汇总增量授权成功金额。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                authorized_amount = authorized_amount + #{amount},
                transaction_amount = transaction_amount + #{amount},
                available_capture_amount = available_capture_amount + #{amount},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND transaction_status = 'SUCCESS'
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int increaseAuthorizedAmount(@Param("operationId") String operationId,
                                 @Param("transactionDateTime") LocalDateTime transactionDateTime,
                                 @Param("latestTransactionId") String latestTransactionId,
                                 @Param("amount") BigDecimal amount,
                                 @Param("expectedVersion") Integer expectedVersion);

    /**
     * 使用分片时间和版本 CAS 汇总撤销成功金额。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                authorized_cancel_amount = authorized_cancel_amount + #{amount},
                available_capture_amount = 0,
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND transaction_status = 'SUCCESS'
              AND captured_amount = 0
              AND refunded_amount = 0
              AND available_capture_amount >= #{amount}
              AND authorized_amount - authorized_cancel_amount = #{amount}
              AND #{amount} > 0
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int markVoidSuccess(@Param("operationId") String operationId,
                        @Param("transactionDateTime") LocalDateTime transactionDateTime,
                        @Param("latestTransactionId") String latestTransactionId,
                        @Param("amount") BigDecimal amount,
                        @Param("expectedVersion") Integer expectedVersion);

    /**
     * 使用分片时间和版本 CAS 将非终态主单推进到目标终态。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                transaction_status = #{transactionStatus},
                process_stage = #{processStage},
                fail_reason_code = #{failReasonCode},
                fail_reason_message = #{failReasonMessage},
                merchant_visible_message = #{merchantVisibleMessage},
                payer_visible_message = #{payerVisibleMessage},
                channel_match_status = #{channelMatchStatus},
                channel_match_result = #{transactionStatus},
                next_channel_match_time = NULL,
                channel_match_fail_reason = NULL,
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND version = #{expectedVersion}
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND deleted = 0
            """)
    int completeStatus(@Param("operationId") String operationId,
                       @Param("transactionDateTime") LocalDateTime transactionDateTime,
                       @Param("latestTransactionId") String latestTransactionId,
                       @Param("expectedVersion") Integer expectedVersion,
                       @Param("transactionStatus") String transactionStatus,
                       @Param("processStage") String processStage,
                       @Param("failReasonCode") String failReasonCode,
                       @Param("failReasonMessage") String failReasonMessage,
                       @Param("merchantVisibleMessage") String merchantVisibleMessage,
                       @Param("payerVisibleMessage") String payerVisibleMessage,
                       @Param("channelMatchStatus") String channelMatchStatus);

    /**
     * 使用分片时间和版本 CAS 推进首次交易成功并初始化金额汇总。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_order
            SET latest_transaction_id = #{latestTransactionId},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                channel_match_status = #{channelMatchStatus},
                channel_match_result = 'SUCCESS',
                next_channel_match_time = NULL,
                channel_match_fail_reason = NULL,
                authorized_amount = CASE
                    WHEN transaction_type IN ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT') THEN #{amount}
                    ELSE authorized_amount
                END,
                captured_amount = CASE
                    WHEN transaction_type = 'PAYMENT' THEN #{amount}
                    ELSE captured_amount
                END,
                available_capture_amount = CASE
                    WHEN transaction_type IN ('AUTHORIZATION', 'PRE_AUTHORIZATION') THEN #{amount}
                    ELSE available_capture_amount
                END,
                available_refund_amount = CASE
                    WHEN transaction_type = 'PAYMENT' THEN #{amount}
                    ELSE available_refund_amount
                END,
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_date_time = #{transactionDateTime}
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int markInitialSuccess(@Param("operationId") String operationId,
                           @Param("transactionDateTime") LocalDateTime transactionDateTime,
                           @Param("latestTransactionId") String latestTransactionId,
                           @Param("amount") BigDecimal amount,
                           @Param("expectedVersion") Integer expectedVersion,
                           @Param("channelMatchStatus") String channelMatchStatus);

    /**
     * 写入交易生命周期主单物理分表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param orderDO           交易生命周期主单
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO ${physicalTableName}
            (
              operation_id, root_transaction_id, latest_transaction_id, merchant_id, merchant_order_no,
              merchant_order_id, source_transaction_id, payment_method, payment_brand,
              transaction_type, transaction_status, process_stage, pending_reason_code, fail_reason_code,
              fail_reason_message, merchant_visible_message, payer_visible_message,
              label_currency, label_amount, transaction_currency, transaction_amount,
              channel_request_currency, channel_request_amount, settlement_currency, settlement_amount,
              currency_exponent, dcc_enabled, edc_enabled, transaction_rate, rate_source, rate_time,
              authorized_amount, authorized_cancel_amount, captured_amount, refunded_amount, chargeback_amount,
              available_capture_amount, available_refund_amount, settlement_status, reconciliation_status,
              accounting_status, channel_match_status, channel_match_result, channel_match_count,
              last_channel_match_request_id, last_channel_match_time, next_channel_match_time,
              channel_match_fail_reason, settlement_batch_no, reconciliation_batch_no, channel_id, channel_code,
              channel_mid_config_id, channel_merchant_id, channel_order_no, internal_risk_decision,
              internal_risk_record_no, callback_url_hash, transaction_date_time, transaction_utc_time,
              transaction_time_zone, transaction_timezone_offset, last_status_time, version, deleted,
              create_time, update_time
            )
            VALUES
            (
              #{orderDO.operationId}, #{orderDO.rootTransactionId}, #{orderDO.latestTransactionId},
              #{orderDO.merchantId}, #{orderDO.merchantOrderNo}, #{orderDO.merchantOrderId},
              #{orderDO.sourceTransactionId}, #{orderDO.paymentMethod}, #{orderDO.paymentBrand},
              #{orderDO.transactionType}, #{orderDO.transactionStatus}, #{orderDO.processStage},
              #{orderDO.pendingReasonCode}, #{orderDO.failReasonCode}, #{orderDO.failReasonMessage},
              #{orderDO.merchantVisibleMessage}, #{orderDO.payerVisibleMessage}, #{orderDO.labelCurrency},
              #{orderDO.labelAmount}, #{orderDO.transactionCurrency}, #{orderDO.transactionAmount},
              #{orderDO.channelRequestCurrency}, #{orderDO.channelRequestAmount}, #{orderDO.settlementCurrency},
              #{orderDO.settlementAmount}, #{orderDO.currencyExponent}, #{orderDO.dccEnabled}, #{orderDO.edcEnabled},
              #{orderDO.transactionRate}, #{orderDO.rateSource}, #{orderDO.rateTime}, #{orderDO.authorizedAmount},
              #{orderDO.authorizedCancelAmount}, #{orderDO.capturedAmount}, #{orderDO.refundedAmount}, #{orderDO.chargebackAmount},
              #{orderDO.availableCaptureAmount}, #{orderDO.availableRefundAmount}, #{orderDO.settlementStatus},
              #{orderDO.reconciliationStatus}, #{orderDO.accountingStatus}, #{orderDO.channelMatchStatus},
              #{orderDO.channelMatchResult}, #{orderDO.channelMatchCount}, #{orderDO.lastChannelMatchRequestId},
              #{orderDO.lastChannelMatchTime}, #{orderDO.nextChannelMatchTime}, #{orderDO.channelMatchFailReason},
              #{orderDO.settlementBatchNo}, #{orderDO.reconciliationBatchNo}, #{orderDO.channelId},
              #{orderDO.channelCode}, #{orderDO.channelMidConfigId}, #{orderDO.channelMerchantId},
              #{orderDO.channelOrderNo}, #{orderDO.internalRiskDecision}, #{orderDO.internalRiskRecordNo},
              #{orderDO.callbackUrlHash}, #{orderDO.transactionDateTime}, #{orderDO.transactionUtcTime},
              #{orderDO.transactionTimeZone}, #{orderDO.transactionTimezoneOffset}, #{orderDO.lastStatusTime},
              #{orderDO.version}, #{orderDO.deleted}, #{orderDO.createTime}, #{orderDO.updateTime}
            )
            """)
    int insertPhysical(@Param("physicalTableName") String physicalTableName,
                       @Param("orderDO") TransactionOrderDO orderDO);

    /**
     * 按 transaction_date_time 路由后的物理表查询交易生命周期主单。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @return 交易生命周期主单，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
              AND deleted = 0
            LIMIT 1
            """)
    TransactionOrderDO selectByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                   @Param("operationId") String operationId);

    /**
     * 按 operation_id 锁定交易生命周期主单。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @return 交易生命周期主单，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    TransactionOrderDO selectByOperationIdForUpdatePhysical(@Param("physicalTableName") String physicalTableName,
                                                            @Param("operationId") String operationId);

    /**
     * 按交易时间范围查询交易生命周期主单列表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param merchantId        平台商户号，可为空
     * @param merchantOrderNo   商户订单号，可为空
     * @param transactionId     平台交易 ID，可为空
     * @param transactionStatus 交易状态，可为空
     * @param beginTime         查询开始时间
     * @param endTime           查询结束时间
     * @param offset            分页偏移
     * @param limit             分页大小
     * @return 主单列表
     */
    @Select("""
            <script>
            SELECT *
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt;= #{endTime}
              <if test="merchantId != null and merchantId != ''">
                AND merchant_id = #{merchantId}
              </if>
              <if test="merchantOrderNo != null and merchantOrderNo != ''">
                AND merchant_order_no = #{merchantOrderNo}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND (root_transaction_id = #{transactionId} OR latest_transaction_id = #{transactionId})
              </if>
              <if test="transactionStatus != null and transactionStatus != ''">
                AND transaction_status = #{transactionStatus}
              </if>
            ORDER BY transaction_date_time DESC, id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<TransactionOrderDO> selectPagePhysical(@Param("physicalTableName") String physicalTableName,
                                                @Param("merchantId") String merchantId,
                                                @Param("merchantOrderNo") String merchantOrderNo,
                                                @Param("transactionId") String transactionId,
                                                @Param("transactionStatus") String transactionStatus,
                                                @Param("beginTime") LocalDateTime beginTime,
                                                @Param("endTime") LocalDateTime endTime,
                                                @Param("offset") long offset,
                                                @Param("limit") long limit);

    /**
     * 统计交易时间范围内的生命周期主单数量。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param merchantId        平台商户号，可为空
     * @param merchantOrderNo   商户订单号，可为空
     * @param transactionId     平台交易 ID，可为空
     * @param transactionStatus 交易状态，可为空
     * @param beginTime         查询开始时间
     * @param endTime           查询结束时间
     * @return 命中记录数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND transaction_date_time &gt;= #{beginTime}
              AND transaction_date_time &lt;= #{endTime}
              <if test="merchantId != null and merchantId != ''">
                AND merchant_id = #{merchantId}
              </if>
              <if test="merchantOrderNo != null and merchantOrderNo != ''">
                AND merchant_order_no = #{merchantOrderNo}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND (root_transaction_id = #{transactionId} OR latest_transaction_id = #{transactionId})
              </if>
              <if test="transactionStatus != null and transactionStatus != ''">
                AND transaction_status = #{transactionStatus}
              </if>
            </script>
            """)
    long countPagePhysical(@Param("physicalTableName") String physicalTableName,
                           @Param("merchantId") String merchantId,
                           @Param("merchantOrderNo") String merchantOrderNo,
                           @Param("transactionId") String transactionId,
                           @Param("transactionStatus") String transactionStatus,
                           @Param("beginTime") LocalDateTime beginTime,
                           @Param("endTime") LocalDateTime endTime);

    /**
     * CAS 更新请款成功后的主单金额汇总和最新状态。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @param latestTransactionId 最新平台交易 ID
     * @param amount            本次请款金额
     * @param expectedVersion   读取主单时的版本号
     * @return 影响行数，1 表示更新成功
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET latest_transaction_id = #{latestTransactionId},
                captured_amount = captured_amount + #{amount},
                available_capture_amount = available_capture_amount - #{amount},
                available_refund_amount = available_refund_amount + #{amount},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_status = 'SUCCESS'
              AND available_capture_amount >= #{amount}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int increaseCapturedAmountPhysical(@Param("physicalTableName") String physicalTableName,
                                       @Param("operationId") String operationId,
                                       @Param("latestTransactionId") String latestTransactionId,
                                       @Param("amount") BigDecimal amount,
                                       @Param("expectedVersion") Integer expectedVersion);

    /**
     * CAS 更新退款成功后的主单金额汇总和最新状态。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @param latestTransactionId 最新平台交易 ID
     * @param amount            本次退款金额
     * @param expectedVersion   读取主单时的版本号
     * @return 影响行数，1 表示更新成功
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET latest_transaction_id = #{latestTransactionId},
                refunded_amount = refunded_amount + #{amount},
                available_refund_amount = available_refund_amount - #{amount},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_status = 'SUCCESS'
              AND available_refund_amount >= #{amount}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int increaseRefundedAmountPhysical(@Param("physicalTableName") String physicalTableName,
                                       @Param("operationId") String operationId,
                                       @Param("latestTransactionId") String latestTransactionId,
                                       @Param("amount") BigDecimal amount,
                                       @Param("expectedVersion") Integer expectedVersion);

    /**
     * CAS 更新增量授权成功后的主单授权金额汇总。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @param latestTransactionId 最新平台交易 ID
     * @param amount            本次增量授权金额
     * @param expectedVersion   读取主单时的版本号
     * @return 影响行数，1 表示更新成功
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET latest_transaction_id = #{latestTransactionId},
                authorized_amount = authorized_amount + #{amount},
                transaction_amount = transaction_amount + #{amount},
                available_capture_amount = available_capture_amount + #{amount},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_status = 'SUCCESS'
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int increaseAuthorizedAmountPhysical(@Param("physicalTableName") String physicalTableName,
                                         @Param("operationId") String operationId,
                                         @Param("latestTransactionId") String latestTransactionId,
                                         @Param("amount") BigDecimal amount,
                                         @Param("expectedVersion") Integer expectedVersion);

    /**
     * CAS 更新撤销成功后的主单状态。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @param latestTransactionId 最新平台交易 ID
     * @param expectedVersion   读取主单时的版本号
     * @return 影响行数，1 表示更新成功
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET latest_transaction_id = #{latestTransactionId},
                authorized_cancel_amount = authorized_cancel_amount + #{amount},
                available_capture_amount = 0,
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_status = 'SUCCESS'
              AND captured_amount = 0
              AND refunded_amount = 0
              AND available_capture_amount >= #{amount}
              AND authorized_amount - authorized_cancel_amount = #{amount}
              AND #{amount} > 0
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int markVoidSuccessPhysical(@Param("physicalTableName") String physicalTableName,
                                @Param("operationId") String operationId,
                                @Param("latestTransactionId") String latestTransactionId,
                                @Param("amount") BigDecimal amount,
                                @Param("expectedVersion") Integer expectedVersion);

    /**
     * CAS 推进主单状态。
     * <p>
     * 渠道回调或查询确认只允许把非终态主单推进到终态；请款、退款等金额类成功结果仍使用金额专用方法更新汇总金额。
     *
     * @param physicalTableName     经分表规则解析器校验后的物理表名
     * @param operationId           平台内部生命周期关联标识
     * @param latestTransactionId   最新平台交易 ID
     * @param expectedVersion       读取主单时的版本号
     * @param transactionStatus     目标交易状态
     * @param processStage          目标处理阶段
     * @param failReasonCode        失败原因码
     * @param failReasonMessage     后台可见失败原因
     * @param merchantVisibleMessage 商户可见失败原因
     * @param payerVisibleMessage   付款人可见失败原因
     * @return 影响行数，1 表示推进成功
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET latest_transaction_id = #{latestTransactionId},
                transaction_status = #{transactionStatus},
                process_stage = #{processStage},
                fail_reason_code = #{failReasonCode},
                fail_reason_message = #{failReasonMessage},
                merchant_visible_message = #{merchantVisibleMessage},
                payer_visible_message = #{payerVisibleMessage},
                channel_match_status = #{channelMatchStatus},
                channel_match_result = #{transactionStatus},
                next_channel_match_time = NULL,
                channel_match_fail_reason = NULL,
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND version = #{expectedVersion}
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND deleted = 0
            """)
    int completeStatusPhysical(@Param("physicalTableName") String physicalTableName,
                               @Param("operationId") String operationId,
                               @Param("latestTransactionId") String latestTransactionId,
                               @Param("expectedVersion") Integer expectedVersion,
                               @Param("transactionStatus") String transactionStatus,
                               @Param("processStage") String processStage,
                               @Param("failReasonCode") String failReasonCode,
                               @Param("failReasonMessage") String failReasonMessage,
                               @Param("merchantVisibleMessage") String merchantVisibleMessage,
                               @Param("payerVisibleMessage") String payerVisibleMessage,
                               @Param("channelMatchStatus") String channelMatchStatus);

    /**
     * CAS 推进首次类交易成功并初始化主单金额汇总。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId 平台内部生命周期关联标识
     * @param latestTransactionId 最新平台交易 ID
     * @param amount 成功交易金额
     * @param expectedVersion 读取主单时的版本号
     * @return 影响行数，1 表示更新成功
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET latest_transaction_id = #{latestTransactionId},
                transaction_status = 'SUCCESS',
                process_stage = 'FINISHED',
                channel_match_status = #{channelMatchStatus},
                channel_match_result = 'SUCCESS',
                next_channel_match_time = NULL,
                channel_match_fail_reason = NULL,
                authorized_amount = CASE
                    WHEN transaction_type IN ('AUTHORIZATION', 'PRE_AUTHORIZATION', 'PAYMENT') THEN #{amount}
                    ELSE authorized_amount
                END,
                captured_amount = CASE
                    WHEN transaction_type = 'PAYMENT' THEN #{amount}
                    ELSE captured_amount
                END,
                available_capture_amount = CASE
                    WHEN transaction_type IN ('AUTHORIZATION', 'PRE_AUTHORIZATION') THEN #{amount}
                    ELSE available_capture_amount
                END,
                available_refund_amount = CASE
                    WHEN transaction_type = 'PAYMENT' THEN #{amount}
                    ELSE available_refund_amount
                END,
                last_status_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE operation_id = #{operationId}
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int markInitialSuccessPhysical(@Param("physicalTableName") String physicalTableName,
                                   @Param("operationId") String operationId,
                                   @Param("latestTransactionId") String latestTransactionId,
                                   @Param("amount") BigDecimal amount,
                                   @Param("expectedVersion") Integer expectedVersion,
                                   @Param("channelMatchStatus") String channelMatchStatus);
}
