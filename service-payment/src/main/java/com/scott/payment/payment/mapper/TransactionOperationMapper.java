package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationSummaryRow;
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
 * @description : 交易动作单 Mapper，位于 service-payment 数据访问层，仅负责 transaction_operation 逻辑表及物理分表访问。
 * @status : create
 */
public interface TransactionOperationMapper extends BaseMapper<TransactionOperationDO> {

    /**
     * 写入交易动作单物理分表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationDO       交易动作单
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO ${physicalTableName}
            (
              operation_id, transaction_id, source_transaction_id, merchant_id,
              merchant_order_no, merchant_order_id, operation_sequence, transaction_type, transaction_status,
              process_stage, pending_reason_code, fail_reason_code, fail_reason_message, label_currency, label_amount,
              transaction_currency, transaction_amount, approved_currency, approved_amount,
              channel_request_currency, channel_request_amount, settlement_currency, settlement_amount,
              currency_exponent, dcc_enabled, edc_enabled, transaction_rate, channel_id, channel_code,
              channel_mid_config_id, channel_terminal_id, channel_order_no,
              channel_transaction_id, channel_status, channel_response_code, channel_response_message,
              auth_code, rrn, acquirer_reference_no, settlement_status, reconciliation_status, accounting_status,
              channel_match_status, channel_match_result, channel_match_count, last_channel_match_request_id,
              last_channel_match_time, next_channel_match_time, channel_match_fail_reason, transaction_date_time,
              transaction_utc_time, transaction_time_zone, operation_time, complete_time, version, deleted,
              create_time, update_time
            )
            VALUES
            (
              #{operationDO.operationId}, #{operationDO.transactionId}, #{operationDO.sourceTransactionId},
              #{operationDO.merchantId}, #{operationDO.merchantOrderNo}, #{operationDO.merchantOrderId},
              #{operationDO.operationSequence}, #{operationDO.transactionType},
              #{operationDO.transactionStatus}, #{operationDO.processStage}, #{operationDO.pendingReasonCode},
              #{operationDO.failReasonCode}, #{operationDO.failReasonMessage}, #{operationDO.labelCurrency}, #{operationDO.labelAmount},
              #{operationDO.transactionCurrency}, #{operationDO.transactionAmount}, #{operationDO.approvedCurrency},
              #{operationDO.approvedAmount}, #{operationDO.channelRequestCurrency}, #{operationDO.channelRequestAmount},
              #{operationDO.settlementCurrency}, #{operationDO.settlementAmount}, #{operationDO.currencyExponent},
              #{operationDO.dccEnabled}, #{operationDO.edcEnabled}, #{operationDO.transactionRate},
              #{operationDO.channelId}, #{operationDO.channelCode}, #{operationDO.channelMidConfigId},
              #{operationDO.channelTerminalId}, #{operationDO.channelOrderNo}, #{operationDO.channelTransactionId},
              #{operationDO.channelStatus}, #{operationDO.channelResponseCode}, #{operationDO.channelResponseMessage},
              #{operationDO.authCode}, #{operationDO.rrn}, #{operationDO.acquirerReferenceNo},
              #{operationDO.settlementStatus}, #{operationDO.reconciliationStatus}, #{operationDO.accountingStatus},
              #{operationDO.channelMatchStatus}, #{operationDO.channelMatchResult}, #{operationDO.channelMatchCount},
              #{operationDO.lastChannelMatchRequestId}, #{operationDO.lastChannelMatchTime},
              #{operationDO.nextChannelMatchTime}, #{operationDO.channelMatchFailReason},
              #{operationDO.transactionDateTime}, #{operationDO.transactionUtcTime}, #{operationDO.transactionTimeZone},
              #{operationDO.operationTime}, #{operationDO.completeTime}, #{operationDO.version}, #{operationDO.deleted},
              #{operationDO.createTime}, #{operationDO.updateTime}
            )
            """)
    int insertPhysical(@Param("physicalTableName") String physicalTableName,
                       @Param("operationDO") TransactionOperationDO operationDO);

    /**
     * 按平台当前交易 ID 查询动作单。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param transactionId     平台当前交易唯一标识
     * @return 交易动作单，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE transaction_id = #{transactionId}
              AND deleted = 0
            LIMIT 1
            """)
    TransactionOperationDO selectByTransactionIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                         @Param("transactionId") String transactionId);

    /**
     * 按渠道订单号和渠道交易 ID 查询动作单。
     *
     * @param physicalTableName    经分表规则解析器校验后的物理表名
     * @param channelOrderNo       渠道订单号
     * @param channelTransactionId 渠道交易 ID
     * @return 交易动作单，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE channel_order_no = #{channelOrderNo}
              AND channel_transaction_id = #{channelTransactionId}
              AND deleted = 0
            LIMIT 1
            """)
    TransactionOperationDO selectByChannelTransactionPhysical(@Param("physicalTableName") String physicalTableName,
                                                              @Param("channelOrderNo") String channelOrderNo,
                                                              @Param("channelTransactionId") String channelTransactionId);

    /**
     * CAS 推进动作单终态。
     * <p>
     * 渠道回调和渠道查询确认只能推进非终态动作，避免重复回调或延迟失败结果覆盖已成功交易。
     *
     * @param physicalTableName     经分表规则解析器校验后的物理表名
     * @param id                    动作单物理主键
     * @param expectedVersion       读取动作单时的版本号
     * @param transactionStatus     目标交易状态
     * @param processStage          目标处理阶段
     * @param failReasonCode        失败原因码
     * @param failReasonMessage     后台可见失败原因
     * @param channelStatus         渠道原始状态
     * @param channelResponseCode   渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @return 影响行数，1 表示状态推进成功
     */
    @Update("""
            UPDATE ${physicalTableName}
            SET transaction_status = #{transactionStatus},
                process_stage = #{processStage},
                fail_reason_code = #{failReasonCode},
                fail_reason_message = #{failReasonMessage},
                channel_status = #{channelStatus},
                channel_response_code = #{channelResponseCode},
                channel_response_message = #{channelResponseMessage},
                complete_time = CURRENT_TIMESTAMP(3),
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{id}
              AND version = #{expectedVersion}
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND deleted = 0
            """)
    int completeStatusPhysical(@Param("physicalTableName") String physicalTableName,
                               @Param("id") Long id,
                               @Param("expectedVersion") Integer expectedVersion,
                               @Param("transactionStatus") String transactionStatus,
                               @Param("processStage") String processStage,
                               @Param("failReasonCode") String failReasonCode,
                               @Param("failReasonMessage") String failReasonMessage,
                               @Param("channelStatus") String channelStatus,
                               @Param("channelResponseCode") String channelResponseCode,
                               @Param("channelResponseMessage") String channelResponseMessage);

    /**
     * 查询同一交易生命周期下已有动作数量，用于生成动作序号。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @return 已有动作数量
     */
    @Select("""
            SELECT COUNT(1)
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
              AND deleted = 0
            """)
    int countByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
                                   @Param("operationId") String operationId);

    /**
     * 查询同一交易生命周期下的所有动作单。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param operationId       平台内部生命周期关联标识
     * @return 动作单列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE operation_id = #{operationId}
              AND deleted = 0
            ORDER BY operation_sequence ASC, operation_time ASC
            """)
    List<TransactionOperationDO> selectByOperationIdPhysical(@Param("physicalTableName") String physicalTableName,
                                                             @Param("operationId") String operationId);

    /**
     * 按交易时间范围查询动作单列表。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param paymentPhysicalTableName 支付工具摘要物理表名
     * @param merchantId        平台商户号，可为空
     * @param merchantOrderNo   商户订单号，可为空
     * @param transactionId     平台交易 ID，可为空
     * @param sourceTransactionId 原平台交易 ID，可为空
     * @param transactionType   交易类型，可为空
     * @param transactionStatus 交易状态，可为空
     * @param channelCode       渠道编码，可为空
     * @param channelOrderNo    渠道订单号，可为空
     * @param channelResponseCode 渠道响应码，可为空
     * @param authCode          授权码，可为空
     * @param acquirerReferenceNo ARN，可为空
     * @param channelMatchStatus 渠道勾兑状态，可为空
     * @param reconciliationStatus 对账状态，可为空
     * @param settlementStatus  结算状态，可为空
     * @param paymentMethod     支付方式，可为空
     * @param paymentBrand      卡品牌或支付品牌，可为空
     * @param cardBin           卡 BIN，可为空
     * @param beginTime         查询开始时间
     * @param endTime           查询结束时间
     * @param offset            分页偏移
     * @param limit             分页大小
     * @return 动作单列表
     */
    @Select("""
            <script>
            SELECT o.*
            FROM ${physicalTableName} o
            WHERE o.deleted = 0
              AND o.transaction_date_time &gt;= #{beginTime}
              AND o.transaction_date_time &lt;= #{endTime}
              <if test="merchantId != null and merchantId != ''">
                AND o.merchant_id = #{merchantId}
              </if>
              <if test="merchantOrderNo != null and merchantOrderNo != ''">
                AND o.merchant_order_no = #{merchantOrderNo}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND o.transaction_id = #{transactionId}
              </if>
              <if test="sourceTransactionId != null and sourceTransactionId != ''">
                AND o.source_transaction_id = #{sourceTransactionId}
              </if>
              <if test="transactionType != null and transactionType != ''">
                AND o.transaction_type = #{transactionType}
              </if>
              <if test="transactionStatus != null and transactionStatus != ''">
                AND o.transaction_status = #{transactionStatus}
              </if>
              <if test="channelCode != null and channelCode != ''">
                AND o.channel_code = #{channelCode}
              </if>
              <if test="channelOrderNo != null and channelOrderNo != ''">
                AND o.channel_order_no = #{channelOrderNo}
              </if>
              <if test="channelResponseCode != null and channelResponseCode != ''">
                AND o.channel_response_code = #{channelResponseCode}
              </if>
              <if test="authCode != null and authCode != ''">
                AND o.auth_code = #{authCode}
              </if>
              <if test="acquirerReferenceNo != null and acquirerReferenceNo != ''">
                AND o.acquirer_reference_no = #{acquirerReferenceNo}
              </if>
              <if test="channelMatchStatus != null and channelMatchStatus != ''">
                AND o.channel_match_status = #{channelMatchStatus}
              </if>
              <if test="reconciliationStatus != null and reconciliationStatus != ''">
                AND o.reconciliation_status = #{reconciliationStatus}
              </if>
              <if test="settlementStatus != null and settlementStatus != ''">
                AND o.settlement_status = #{settlementStatus}
              </if>
              <if test="paymentMethod != null and paymentMethod != ''">
                AND EXISTS (
                  SELECT 1 FROM ${paymentPhysicalTableName} p
                  WHERE p.transaction_id = o.transaction_id
                    AND p.transaction_date_time = o.transaction_date_time
                    AND p.payment_method = #{paymentMethod}
                )
              </if>
              <if test="paymentBrand != null and paymentBrand != ''">
                AND EXISTS (
                  SELECT 1 FROM ${paymentPhysicalTableName} p
                  WHERE p.transaction_id = o.transaction_id
                    AND p.transaction_date_time = o.transaction_date_time
                    AND p.payment_brand = #{paymentBrand}
                )
              </if>
              <if test="cardBin != null and cardBin != ''">
                AND EXISTS (
                  SELECT 1 FROM ${paymentPhysicalTableName} p
                  WHERE p.transaction_id = o.transaction_id
                    AND p.transaction_date_time = o.transaction_date_time
                    AND p.card_bin LIKE CONCAT(#{cardBin}, '%')
                )
              </if>
            ORDER BY o.transaction_date_time DESC, o.id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<TransactionOperationDO> selectPagePhysical(@Param("physicalTableName") String physicalTableName,
                                                    @Param("paymentPhysicalTableName") String paymentPhysicalTableName,
                                                    @Param("merchantId") String merchantId,
                                                    @Param("merchantOrderNo") String merchantOrderNo,
                                                    @Param("transactionId") String transactionId,
                                                    @Param("sourceTransactionId") String sourceTransactionId,
                                                    @Param("transactionType") String transactionType,
                                                    @Param("transactionStatus") String transactionStatus,
                                                    @Param("channelCode") String channelCode,
                                                    @Param("channelOrderNo") String channelOrderNo,
                                                    @Param("channelResponseCode") String channelResponseCode,
                                                    @Param("authCode") String authCode,
                                                    @Param("acquirerReferenceNo") String acquirerReferenceNo,
                                                    @Param("channelMatchStatus") String channelMatchStatus,
                                                    @Param("reconciliationStatus") String reconciliationStatus,
                                                    @Param("settlementStatus") String settlementStatus,
                                                    @Param("paymentMethod") String paymentMethod,
                                                    @Param("paymentBrand") String paymentBrand,
                                                    @Param("cardBin") String cardBin,
                                                    @Param("beginTime") LocalDateTime beginTime,
                                                    @Param("endTime") LocalDateTime endTime,
                                                    @Param("offset") long offset,
                                                    @Param("limit") long limit);

    /**
     * 统计交易时间范围内的动作单数量。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param paymentPhysicalTableName 支付工具摘要物理表名
     * @param merchantId        平台商户号，可为空
     * @param merchantOrderNo   商户订单号，可为空
     * @param transactionId     平台交易 ID，可为空
     * @param sourceTransactionId 原平台交易 ID，可为空
     * @param transactionType   交易类型，可为空
     * @param transactionStatus 交易状态，可为空
     * @param channelCode       渠道编码，可为空
     * @param channelOrderNo    渠道订单号，可为空
     * @param channelResponseCode 渠道响应码，可为空
     * @param authCode          授权码，可为空
     * @param acquirerReferenceNo ARN，可为空
     * @param channelMatchStatus 渠道勾兑状态，可为空
     * @param reconciliationStatus 对账状态，可为空
     * @param settlementStatus  结算状态，可为空
     * @param paymentMethod     支付方式，可为空
     * @param paymentBrand      卡品牌或支付品牌，可为空
     * @param cardBin           卡 BIN，可为空
     * @param beginTime         查询开始时间
     * @param endTime           查询结束时间
     * @return 命中记录数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM ${physicalTableName} o
            WHERE o.deleted = 0
              AND o.transaction_date_time &gt;= #{beginTime}
              AND o.transaction_date_time &lt;= #{endTime}
              <if test="merchantId != null and merchantId != ''">
                AND o.merchant_id = #{merchantId}
              </if>
              <if test="merchantOrderNo != null and merchantOrderNo != ''">
                AND o.merchant_order_no = #{merchantOrderNo}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND o.transaction_id = #{transactionId}
              </if>
              <if test="sourceTransactionId != null and sourceTransactionId != ''">
                AND o.source_transaction_id = #{sourceTransactionId}
              </if>
              <if test="transactionType != null and transactionType != ''">
                AND o.transaction_type = #{transactionType}
              </if>
              <if test="transactionStatus != null and transactionStatus != ''">
                AND o.transaction_status = #{transactionStatus}
              </if>
              <if test="channelCode != null and channelCode != ''">
                AND o.channel_code = #{channelCode}
              </if>
              <if test="channelOrderNo != null and channelOrderNo != ''">
                AND o.channel_order_no = #{channelOrderNo}
              </if>
              <if test="channelResponseCode != null and channelResponseCode != ''">
                AND o.channel_response_code = #{channelResponseCode}
              </if>
              <if test="authCode != null and authCode != ''">
                AND o.auth_code = #{authCode}
              </if>
              <if test="acquirerReferenceNo != null and acquirerReferenceNo != ''">
                AND o.acquirer_reference_no = #{acquirerReferenceNo}
              </if>
              <if test="channelMatchStatus != null and channelMatchStatus != ''">
                AND o.channel_match_status = #{channelMatchStatus}
              </if>
              <if test="reconciliationStatus != null and reconciliationStatus != ''">
                AND o.reconciliation_status = #{reconciliationStatus}
              </if>
              <if test="settlementStatus != null and settlementStatus != ''">
                AND o.settlement_status = #{settlementStatus}
              </if>
              <if test="paymentMethod != null and paymentMethod != ''">
                AND EXISTS (
                  SELECT 1 FROM ${paymentPhysicalTableName} p
                  WHERE p.transaction_id = o.transaction_id
                    AND p.transaction_date_time = o.transaction_date_time
                    AND p.payment_method = #{paymentMethod}
                )
              </if>
              <if test="paymentBrand != null and paymentBrand != ''">
                AND EXISTS (
                  SELECT 1 FROM ${paymentPhysicalTableName} p
                  WHERE p.transaction_id = o.transaction_id
                    AND p.transaction_date_time = o.transaction_date_time
                    AND p.payment_brand = #{paymentBrand}
                )
              </if>
              <if test="cardBin != null and cardBin != ''">
                AND EXISTS (
                  SELECT 1 FROM ${paymentPhysicalTableName} p
                  WHERE p.transaction_id = o.transaction_id
                    AND p.transaction_date_time = o.transaction_date_time
                    AND p.card_bin LIKE CONCAT(#{cardBin}, '%')
                )
              </if>
            </script>
            """)
    long countPagePhysical(@Param("physicalTableName") String physicalTableName,
                           @Param("paymentPhysicalTableName") String paymentPhysicalTableName,
                           @Param("merchantId") String merchantId,
                           @Param("merchantOrderNo") String merchantOrderNo,
                           @Param("transactionId") String transactionId,
                           @Param("sourceTransactionId") String sourceTransactionId,
                           @Param("transactionType") String transactionType,
                           @Param("transactionStatus") String transactionStatus,
                           @Param("channelCode") String channelCode,
                           @Param("channelOrderNo") String channelOrderNo,
                           @Param("channelResponseCode") String channelResponseCode,
                           @Param("authCode") String authCode,
                           @Param("acquirerReferenceNo") String acquirerReferenceNo,
                           @Param("channelMatchStatus") String channelMatchStatus,
                           @Param("reconciliationStatus") String reconciliationStatus,
                           @Param("settlementStatus") String settlementStatus,
                           @Param("paymentMethod") String paymentMethod,
                           @Param("paymentBrand") String paymentBrand,
                           @Param("cardBin") String cardBin,
                           @Param("beginTime") LocalDateTime beginTime,
                           @Param("endTime") LocalDateTime endTime);

    /**
     * 按交易状态和币种聚合当前查询条件下的动作单金额。
     *
     * @param physicalTableName        经分表规则解析器校验后的动作单物理表名
     * @param paymentPhysicalTableName 支付工具摘要物理表名
     * @param merchantId               平台商户号，可为空
     * @param merchantOrderNo          商户订单号，可为空
     * @param transactionId            平台交易 ID，可为空
     * @param sourceTransactionId      原平台交易 ID，可为空
     * @param transactionType          交易类型，可为空
     * @param transactionStatus        交易状态，可为空
     * @param channelCode              渠道编码，可为空
     * @param channelOrderNo           渠道订单号，可为空
     * @param channelResponseCode      渠道响应码，可为空
     * @param authCode                 授权码，可为空
     * @param acquirerReferenceNo      ARN，可为空
     * @param channelMatchStatus       渠道勾兑状态，可为空
     * @param reconciliationStatus     对账状态，可为空
     * @param settlementStatus         结算状态，可为空
     * @param paymentMethod            支付方式，可为空
     * @param paymentBrand             卡品牌或支付品牌，可为空
     * @param cardBin                  卡 BIN，可为空
     * @param beginTime                查询开始时间
     * @param endTime                  查询结束时间
     * @return 状态与币种聚合行
     */
    @Select("""
            <script>
            SELECT
              o.transaction_status AS transactionStatus,
              COALESCE(o.transaction_currency, 'UNKNOWN') AS currency,
              o.currency_exponent AS currencyExponent,
              COUNT(1) AS count,
              COALESCE(SUM(COALESCE(o.transaction_amount, 0)), 0) AS amount
            FROM ${physicalTableName} o
            WHERE o.deleted = 0
              AND o.transaction_date_time &gt;= #{beginTime}
              AND o.transaction_date_time &lt;= #{endTime}
              <if test="merchantId != null and merchantId != ''">
                AND o.merchant_id = #{merchantId}
              </if>
              <if test="merchantOrderNo != null and merchantOrderNo != ''">
                AND o.merchant_order_no = #{merchantOrderNo}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND o.transaction_id = #{transactionId}
              </if>
              <if test="sourceTransactionId != null and sourceTransactionId != ''">
                AND o.source_transaction_id = #{sourceTransactionId}
              </if>
              <if test="transactionType != null and transactionType != ''">
                AND o.transaction_type = #{transactionType}
              </if>
              <if test="transactionStatus != null and transactionStatus != ''">
                AND o.transaction_status = #{transactionStatus}
              </if>
              <if test="channelCode != null and channelCode != ''">
                AND o.channel_code = #{channelCode}
              </if>
              <if test="channelOrderNo != null and channelOrderNo != ''">
                AND o.channel_order_no = #{channelOrderNo}
              </if>
              <if test="channelResponseCode != null and channelResponseCode != ''">
                AND o.channel_response_code = #{channelResponseCode}
              </if>
              <if test="authCode != null and authCode != ''">
                AND o.auth_code = #{authCode}
              </if>
              <if test="acquirerReferenceNo != null and acquirerReferenceNo != ''">
                AND o.acquirer_reference_no = #{acquirerReferenceNo}
              </if>
              <if test="channelMatchStatus != null and channelMatchStatus != ''">
                AND o.channel_match_status = #{channelMatchStatus}
              </if>
              <if test="reconciliationStatus != null and reconciliationStatus != ''">
                AND o.reconciliation_status = #{reconciliationStatus}
              </if>
              <if test="settlementStatus != null and settlementStatus != ''">
                AND o.settlement_status = #{settlementStatus}
              </if>
              <if test="paymentMethod != null and paymentMethod != ''">
                AND EXISTS (
                  SELECT 1 FROM ${paymentPhysicalTableName} p
                  WHERE p.transaction_id = o.transaction_id
                    AND p.transaction_date_time = o.transaction_date_time
                    AND p.payment_method = #{paymentMethod}
                )
              </if>
              <if test="paymentBrand != null and paymentBrand != ''">
                AND EXISTS (
                  SELECT 1 FROM ${paymentPhysicalTableName} p
                  WHERE p.transaction_id = o.transaction_id
                    AND p.transaction_date_time = o.transaction_date_time
                    AND p.payment_brand = #{paymentBrand}
                )
              </if>
              <if test="cardBin != null and cardBin != ''">
                AND EXISTS (
                  SELECT 1 FROM ${paymentPhysicalTableName} p
                  WHERE p.transaction_id = o.transaction_id
                    AND p.transaction_date_time = o.transaction_date_time
                    AND p.card_bin LIKE CONCAT(#{cardBin}, '%')
                )
              </if>
            GROUP BY o.transaction_status, COALESCE(o.transaction_currency, 'UNKNOWN'), o.currency_exponent
            </script>
            """)
    List<TransactionOperationSummaryRow> selectAmountSummaryPhysical(@Param("physicalTableName") String physicalTableName,
                                                                     @Param("paymentPhysicalTableName") String paymentPhysicalTableName,
                                                                     @Param("merchantId") String merchantId,
                                                                     @Param("merchantOrderNo") String merchantOrderNo,
                                                                     @Param("transactionId") String transactionId,
                                                                     @Param("sourceTransactionId") String sourceTransactionId,
                                                                     @Param("transactionType") String transactionType,
                                                                     @Param("transactionStatus") String transactionStatus,
                                                                     @Param("channelCode") String channelCode,
                                                                     @Param("channelOrderNo") String channelOrderNo,
                                                                     @Param("channelResponseCode") String channelResponseCode,
                                                                     @Param("authCode") String authCode,
                                                                     @Param("acquirerReferenceNo") String acquirerReferenceNo,
                                                                     @Param("channelMatchStatus") String channelMatchStatus,
                                                                     @Param("reconciliationStatus") String reconciliationStatus,
                                                                     @Param("settlementStatus") String settlementStatus,
                                                                     @Param("paymentMethod") String paymentMethod,
                                                                     @Param("paymentBrand") String paymentBrand,
                                                                     @Param("cardBin") String cardBin,
                                                                     @Param("beginTime") LocalDateTime beginTime,
                                                                     @Param("endTime") LocalDateTime endTime);

    /**
     * 按支付方式、卡品牌和币种聚合当前查询条件下的动作单金额。
     *
     * @param physicalTableName        经分表规则解析器校验后的动作单物理表名
     * @param paymentPhysicalTableName 支付工具摘要物理表名
     * @param merchantId               平台商户号，可为空
     * @param merchantOrderNo          商户订单号，可为空
     * @param transactionId            平台交易 ID，可为空
     * @param sourceTransactionId      原平台交易 ID，可为空
     * @param transactionType          交易类型，可为空
     * @param transactionStatus        交易状态，可为空
     * @param channelCode              渠道编码，可为空
     * @param channelOrderNo           渠道订单号，可为空
     * @param channelResponseCode      渠道响应码，可为空
     * @param authCode                 授权码，可为空
     * @param acquirerReferenceNo      ARN，可为空
     * @param channelMatchStatus       渠道勾兑状态，可为空
     * @param reconciliationStatus     对账状态，可为空
     * @param settlementStatus         结算状态，可为空
     * @param paymentMethod            支付方式，可为空
     * @param paymentBrand             卡品牌或支付品牌，可为空
     * @param cardBin                  卡 BIN，可为空
     * @param beginTime                查询开始时间
     * @param endTime                  查询结束时间
     * @return 支付方式、卡品牌与币种聚合行
     */
    @Select("""
            <script>
            SELECT
              COALESCE(p.payment_method, 'UNKNOWN') AS paymentMethod,
              p.payment_brand AS paymentBrand,
              COALESCE(o.transaction_currency, 'UNKNOWN') AS currency,
              o.currency_exponent AS currencyExponent,
              COUNT(1) AS count,
              COALESCE(SUM(COALESCE(o.transaction_amount, 0)), 0) AS amount
            FROM ${physicalTableName} o
            LEFT JOIN ${paymentPhysicalTableName} p
              ON p.transaction_id = o.transaction_id
             AND p.transaction_date_time = o.transaction_date_time
            WHERE o.deleted = 0
              AND o.transaction_date_time &gt;= #{beginTime}
              AND o.transaction_date_time &lt;= #{endTime}
              <if test="merchantId != null and merchantId != ''">
                AND o.merchant_id = #{merchantId}
              </if>
              <if test="merchantOrderNo != null and merchantOrderNo != ''">
                AND o.merchant_order_no = #{merchantOrderNo}
              </if>
              <if test="transactionId != null and transactionId != ''">
                AND o.transaction_id = #{transactionId}
              </if>
              <if test="sourceTransactionId != null and sourceTransactionId != ''">
                AND o.source_transaction_id = #{sourceTransactionId}
              </if>
              <if test="transactionType != null and transactionType != ''">
                AND o.transaction_type = #{transactionType}
              </if>
              <if test="transactionStatus != null and transactionStatus != ''">
                AND o.transaction_status = #{transactionStatus}
              </if>
              <if test="channelCode != null and channelCode != ''">
                AND o.channel_code = #{channelCode}
              </if>
              <if test="channelOrderNo != null and channelOrderNo != ''">
                AND o.channel_order_no = #{channelOrderNo}
              </if>
              <if test="channelResponseCode != null and channelResponseCode != ''">
                AND o.channel_response_code = #{channelResponseCode}
              </if>
              <if test="authCode != null and authCode != ''">
                AND o.auth_code = #{authCode}
              </if>
              <if test="acquirerReferenceNo != null and acquirerReferenceNo != ''">
                AND o.acquirer_reference_no = #{acquirerReferenceNo}
              </if>
              <if test="channelMatchStatus != null and channelMatchStatus != ''">
                AND o.channel_match_status = #{channelMatchStatus}
              </if>
              <if test="reconciliationStatus != null and reconciliationStatus != ''">
                AND o.reconciliation_status = #{reconciliationStatus}
              </if>
              <if test="settlementStatus != null and settlementStatus != ''">
                AND o.settlement_status = #{settlementStatus}
              </if>
              <if test="paymentMethod != null and paymentMethod != ''">
                AND p.payment_method = #{paymentMethod}
              </if>
              <if test="paymentBrand != null and paymentBrand != ''">
                AND p.payment_brand = #{paymentBrand}
              </if>
              <if test="cardBin != null and cardBin != ''">
                AND p.card_bin LIKE CONCAT(#{cardBin}, '%')
              </if>
            GROUP BY COALESCE(p.payment_method, 'UNKNOWN'), p.payment_brand,
                     COALESCE(o.transaction_currency, 'UNKNOWN'), o.currency_exponent
            </script>
            """)
    List<TransactionOperationSummaryRow> selectPaymentMethodSummaryPhysical(@Param("physicalTableName") String physicalTableName,
                                                                            @Param("paymentPhysicalTableName") String paymentPhysicalTableName,
                                                                            @Param("merchantId") String merchantId,
                                                                            @Param("merchantOrderNo") String merchantOrderNo,
                                                                            @Param("transactionId") String transactionId,
                                                                            @Param("sourceTransactionId") String sourceTransactionId,
                                                                            @Param("transactionType") String transactionType,
                                                                            @Param("transactionStatus") String transactionStatus,
                                                                            @Param("channelCode") String channelCode,
                                                                            @Param("channelOrderNo") String channelOrderNo,
                                                                            @Param("channelResponseCode") String channelResponseCode,
                                                                            @Param("authCode") String authCode,
                                                                            @Param("acquirerReferenceNo") String acquirerReferenceNo,
                                                                            @Param("channelMatchStatus") String channelMatchStatus,
                                                                            @Param("reconciliationStatus") String reconciliationStatus,
                                                                            @Param("settlementStatus") String settlementStatus,
                                                                            @Param("paymentMethod") String paymentMethod,
                                                                            @Param("paymentBrand") String paymentBrand,
                                                                            @Param("cardBin") String cardBin,
                                                                            @Param("beginTime") LocalDateTime beginTime,
                                                                            @Param("endTime") LocalDateTime endTime);
}
