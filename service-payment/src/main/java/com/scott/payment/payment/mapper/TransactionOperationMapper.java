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
              operation_id, transaction_id, source_transaction_id, source_operation_id, merchant_id,
              merchant_order_no, merchant_operation_no, operation_sequence, transaction_type, transaction_status,
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
              #{operationDO.sourceOperationId}, #{operationDO.merchantId}, #{operationDO.merchantOrderNo},
              #{operationDO.merchantOperationNo},
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
                       /**
                        * 完成 m 分支的校验或状态更新。
                        * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                        * <p>
                        * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                        * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                        * </p>
                        * @param operationDO operation DO 输入值，含义由调用方法名称和所属业务对象限定
                        */
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
                                                         /**
                                                          * 完成 m 分支的校验或状态更新。
                                                          * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                          * <p>
                                                          * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                          * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                          * </p>
                                                          * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
                                                          */
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
                                                              /**
                                                               * 完成 m 分支的校验或状态更新。
                                                               * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                               * <p>
                                                               * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                               * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                               * </p>
                                                               * @param channelTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
                                                               */
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
     * @param authCode              授权码
     * @param rrn                   检索参考号或渠道回单号
     * @param acquirerReferenceNo   收单机构参考号
     * @param channelMatchStatus    渠道勾兑状态；同步终态为 NOT_REQUIRED，回调/主动查询确认为 MATCHED
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
                               @Param("channelResponseMessage") String channelResponseMessage,
                               @Param("authCode") String authCode,
                               @Param("rrn") String rrn,
                               @Param("acquirerReferenceNo") String acquirerReferenceNo,
                               /**
                                * 完成 m 分支的校验或状态更新。
                                * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                * <p>
                                * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                * </p>
                                * @param channelMatchStatus 状态编码，取值必须来自对应枚举或数据库受控字典
                                */
                               @Param("channelMatchStatus") String channelMatchStatus);

    /**
     * CAS 记录渠道同步非终态结果。
     * <p>
     * 非终态结果只更新渠道摘要、处理阶段和勾兑入口，不写 complete_time，也不把勾兑状态标记为 MATCHED，
     * 便于后续主动查询或回调继续推进终态。
     *
     * @param physicalTableName     经分表规则解析器校验后的物理表名
     * @param id                    动作单物理主键
     * @param expectedVersion       读取动作单时的版本号
     * @param transactionStatus     目标非终态交易状态
     * @param processStage          目标处理阶段
     * @param pendingReasonCode     挂起原因码
     * @param failReasonCode        失败原因码
     * @param failReasonMessage     后台可见失败原因
     * @param channelStatus         渠道原始状态
     * @param channelResponseCode   渠道响应码
     * @param channelResponseMessage 渠道响应描述
     * @param requestId             原渠道请求 ID
     * @param matchTime             本次记录时间
     * @return 影响行数，1 表示状态记录成功
     */
    @Update("""
            UPDATE ${physicalTableName}
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
              AND version = #{expectedVersion}
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND deleted = 0
            """)
    int updateNonTerminalChannelResultPhysical(@Param("physicalTableName") String physicalTableName,
                                               @Param("id") Long id,
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
                                               /**
                                                * 完成 m 分支的校验或状态更新。
                                                * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                * <p>
                                                * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                * </p>
                                                * @param matchTime 时间值，使用系统约定时区或调用方传入的业务时区解释
                                                */
                                               @Param("matchTime") LocalDateTime matchTime);

    /**
     * 查询待渠道勾兑的动作单。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param channelCode       渠道编码，可为空
     * @param now               当前时间，用于判断 next_channel_match_time
     * @param limit             最大查询数量
     * @return 待勾兑动作单
     */
    @Select("""
            <script>
            SELECT *
            FROM ${physicalTableName}
            WHERE deleted = 0
              AND channel_match_status = 'PENDING'
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND channel_code IS NOT NULL
              AND (next_channel_match_time IS NULL OR next_channel_match_time &lt;= #{now})
              <if test="channelCode != null and channelCode != ''">
                AND channel_code = #{channelCode}
              </if>
            ORDER BY COALESCE(next_channel_match_time, transaction_date_time) ASC, id ASC
            LIMIT #{limit}
            </script>
            """)
    List<TransactionOperationDO> selectPendingChannelMatchPhysical(@Param("physicalTableName") String physicalTableName,
                                                                   @Param("channelCode") String channelCode,
                                                                   @Param("now") LocalDateTime now,
                                                                   /**
                                                                    * 完成 m 分支的校验或状态更新。
                                                                    * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                    * <p>
                                                                    * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                    * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                    * </p>
                                                                    * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
                                                                    */
                                                                   @Param("limit") int limit);

    /**
     * 标记本次渠道勾兑结果。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param id                动作单物理主键
     * @param expectedVersion   读取动作单时的版本号
     * @param matchStatus       勾兑状态
     * @param matchResult       勾兑结果摘要
     * @param requestId         最近一次渠道查询请求 ID
     * @param matchTime         最近一次渠道查询时间
     * @param nextMatchTime     下一次渠道查询时间
     * @param failReason        勾兑失败原因
     * @return 影响行数
     */
    @Update("""
            UPDATE ${physicalTableName}
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
              AND version = #{expectedVersion}
              AND transaction_status NOT IN ('SUCCESS', 'FAILED')
              AND deleted = 0
            """)
    int updateChannelMatchPhysical(@Param("physicalTableName") String physicalTableName,
                                   @Param("id") Long id,
                                   @Param("expectedVersion") Integer expectedVersion,
                                   @Param("matchStatus") String matchStatus,
                                   @Param("matchResult") String matchResult,
                                   @Param("requestId") String requestId,
                                   @Param("matchTime") LocalDateTime matchTime,
                                   @Param("nextMatchTime") LocalDateTime nextMatchTime,
                                   /**
                                    * 完成 m 分支的校验或状态更新。
                                    * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                    * <p>
                                    * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                    * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                    * </p>
                                    * @param failReason fail Reason 输入值，含义由调用方法名称和所属业务对象限定
                                    */
                                   @Param("failReason") String failReason);

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
                                   /**
                                    * 完成 m 分支的校验或状态更新。
                                    * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                    * <p>
                                    * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                    * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                    * </p>
                                    * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
                                    */
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
                                                             /**
                                                              * 完成 m 分支的校验或状态更新。
                                                              * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                              * <p>
                                                              * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                              * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                              * </p>
                                                              * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
                                                              */
                                                             @Param("operationId") String operationId);

    /**
     * 按商户订单号查询交易动作单。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param merchantId        平台商户号
     * @param merchantOrderNo   商户订单号
     * @param transactionId     平台交易 ID，可为空；传入时精确过滤单笔动作
     * @return 交易动作单列表
     */
    @Select("""
            <script>
            SELECT *
            FROM ${physicalTableName}
            WHERE merchant_id = #{merchantId}
              AND merchant_order_no = #{merchantOrderNo}
              AND deleted = 0
              <if test="transactionId != null and transactionId != ''">
                AND transaction_id = #{transactionId}
              </if>
            ORDER BY operation_sequence ASC, operation_time ASC
            </script>
            """)
    List<TransactionOperationDO> selectByMerchantOrderPhysical(@Param("physicalTableName") String physicalTableName,
                                                               @Param("merchantId") String merchantId,
                                                               @Param("merchantOrderNo") String merchantOrderNo,
                                                               /**
                                                                * 完成 m 分支的校验或状态更新。
                                                                * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                * <p>
                                                                * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                * </p>
                                                                * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
                                                                */
                                                               @Param("transactionId") String transactionId);

    /**
     * 按商户订单号查询首次起点动作单。
     * <p>
     * 同一商户订单号下 PAYMENT 与 AUTHORIZATION/PRE_AUTHORIZATION 两条支付语义互斥；支付核心创建首次类交易前
     * 通过该查询判断是否已经存在有效起点流程。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param merchantId        平台商户号
     * @param merchantOrderNo   商户订单号
     * @return 首次起点动作单列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE merchant_id = #{merchantId}
              AND merchant_order_no = #{merchantOrderNo}
              AND transaction_type IN ('PAYMENT', 'AUTHORIZATION', 'PRE_AUTHORIZATION')
              AND deleted = 0
            ORDER BY transaction_date_time ASC, id ASC
            """)
    List<TransactionOperationDO> selectInitialByMerchantOrderPhysical(@Param("physicalTableName") String physicalTableName,
                                                                      @Param("merchantId") String merchantId,
                                                                      /**
                                                                       * 完成 m 分支的校验或状态更新。
                                                                       * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                       * <p>
                                                                       * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                       * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                       * </p>
                                                                       * @param merchantOrderNo 商户订单号，用于商户侧幂等校验和订单查询
                                                                       */
                                                                      @Param("merchantOrderNo") String merchantOrderNo);

    /**
     * 查询同一生命周期下未终态 Capture 动作。
     * <p>
     * 该查询只读取真实动作事实，不按商户订单号推断 Capture 动作号，避免把原 Payment/Auth 订单号误作多次请款标识。
     *
     * @param physicalTableName   经分表规则解析器校验后的物理表名
     * @param merchantId          平台商户号
     * @param operationId         平台内部生命周期关联标识
     * @param sourceTransactionId 原授权或预授权平台交易 ID
     * @return 未终态 Capture 动作列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE merchant_id = #{merchantId}
              AND operation_id = #{operationId}
              AND source_transaction_id = #{sourceTransactionId}
              AND transaction_type IN ('CAPTURE', 'PRE_AUTH_COMPLETION')
              AND transaction_status IN ('PROCESSING', 'PENDING')
              AND deleted = 0
            ORDER BY transaction_date_time ASC, id ASC
            """)
    List<TransactionOperationDO> selectNonTerminalCapturesPhysical(@Param("physicalTableName") String physicalTableName,
                                                                   @Param("merchantId") String merchantId,
                                                                   @Param("operationId") String operationId,
                                                                   /**
                                                                    * 完成 m 分支的校验或状态更新。
                                                                    * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                    * <p>
                                                                    * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                    * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                    * </p>
                                                                    * @param sourceTransactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
                                                                    */
                                                                   @Param("sourceTransactionId") String sourceTransactionId);

    /**
     * 查询同一生命周期下未终态 Refund 动作。
     * <p>
     * Refund 额度按生命周期共享，PROCESSING/PENDING 动作恢复为 SUCCESS/FAILED 前必须纳入占用。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param merchantId        平台商户号
     * @param operationId       平台内部生命周期关联标识
     * @return 未终态 Refund 动作列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE merchant_id = #{merchantId}
              AND operation_id = #{operationId}
              AND transaction_type = 'REFUND'
              AND transaction_status IN ('PROCESSING', 'PENDING')
              AND deleted = 0
            ORDER BY transaction_date_time ASC, id ASC
            """)
    List<TransactionOperationDO> selectNonTerminalRefundsPhysical(@Param("physicalTableName") String physicalTableName,
                                                                  @Param("merchantId") String merchantId,
                                                                  /**
                                                                   * 完成 m 分支的校验或状态更新。
                                                                   * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                   * <p>
                                                                   * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                   * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                   * </p>
                                                                   * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
                                                                   */
                                                                  @Param("operationId") String operationId);

    /**
     * 查询同一生命周期下未终态 Void 动作。
     * <p>
     * Void / Authorization Cancel 恢复为明确终态前必须阻断 Capture、Refund 和新的 Void，避免重复释放授权或重复返还资金。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param merchantId        平台商户号
     * @param operationId       平台内部生命周期关联标识
     * @return 未终态 Void 动作列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE merchant_id = #{merchantId}
              AND operation_id = #{operationId}
              AND transaction_type = 'VOID'
              AND transaction_status IN ('PROCESSING', 'PENDING')
              AND deleted = 0
            ORDER BY transaction_date_time ASC, id ASC
            """)
    List<TransactionOperationDO> selectNonTerminalVoidsPhysical(@Param("physicalTableName") String physicalTableName,
                                                                @Param("merchantId") String merchantId,
                                                                /**
                                                                 * 完成 m 分支的校验或状态更新。
                                                                 * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                 * <p>
                                                                 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                 * </p>
                                                                 * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
                                                                 */
                                                                @Param("operationId") String operationId);

    /**
     * 查询同一授权生命周期下未终态 Incremental Authorization 动作。
     * <p>
     * PROCESSING/PENDING 动作恢复为明确终态前必须阻断新的增量授权，避免 timeout/unknown 重发渠道请求后重复加授权金额。
     *
     * @param physicalTableName 经分表规则解析器校验后的物理表名
     * @param merchantId        平台商户号
     * @param operationId       平台内部生命周期关联标识
     * @return 未终态 Incremental Authorization 动作列表
     */
    @Select("""
            SELECT *
            FROM ${physicalTableName}
            WHERE merchant_id = #{merchantId}
              AND operation_id = #{operationId}
              AND transaction_type = 'INCREMENTAL_AUTHORIZATION'
              AND transaction_status IN ('PROCESSING', 'PENDING')
              AND deleted = 0
            ORDER BY transaction_date_time ASC, id ASC
            """)
    List<TransactionOperationDO> selectNonTerminalIncrementalAuthorizationsPhysical(
            @Param("physicalTableName") String physicalTableName,
            @Param("merchantId") String merchantId,
            /**
             * 完成 m 分支的校验或状态更新。
             * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
             * <p>
             * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
             * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
             * </p>
             * @param operationId 平台交易操作号，用于定位一次授权、请款、退款或撤销操作
             */
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
                                                    /**
                                                     * 完成 m 分支的校验或状态更新。
                                                     * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                     * <p>
                                                     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                     * </p>
                                                     * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
                                                     */
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
                           /**
                            * 完成 m 分支的校验或状态更新。
                            * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                            * <p>
                            * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                            * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                            * </p>
                            * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
                            */
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
                                                                     /**
                                                                      * 完成 m 分支的校验或状态更新。
                                                                      * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                      * <p>
                                                                      * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                      * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                      * </p>
                                                                      * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
                                                                      */
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
                                                                            /**
                                                                             * 完成 m 分支的校验或状态更新。
                                                                             * 接口契约要求实现类保持参数校验、状态变化、异常边界和返回结构一致。
                                                                             * <p>
                                                                             * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
                                                                             * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
                                                                             * </p>
                                                                             * @param endTime 时间值，使用系统约定时区或调用方传入的业务时区解释
                                                                             */
                                                                            @Param("endTime") LocalDateTime endTime);
}
