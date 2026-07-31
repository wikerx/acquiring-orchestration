package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionMerchantApiResponseLogUpdateCommandDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.TransactionFollowUpRecordDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionRecordService
 * @date : 2026-07-14 17:45
 * @email : scott_x@163.com
 * @description : 交易事实记录服务，位于 service-payment 服务层，负责把交易主单、动作单和状态历史写入 transaction_date_time 对应物理分表。
 * @status : create
 */
public interface TransactionRecordService {

    /**
     * 记录首次类交易事实。
     * <p>
     * 该方法只负责落交易事实，不负责渠道调用、状态机推进或 MQ 投递；调用方必须处于本地事务中。
     *
     * @param commandDTO       创建交易命令
     * @param routeResultDTO   渠道路由结果；风控短路时可为空
     * @param channelInvokeResultDTO 渠道调用结果；未调用渠道时可为空
     * @param resultDTO        交易结果
     * @param riskDecisionEnum 内风控决策
     * @param currencyExponent 交易币种默认小数位
     */
    void recordInitialTransaction(PaymentCreateCommandDTO commandDTO,
                                  PaymentRouteResultDTO routeResultDTO,
                                  PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                  PaymentCreateResultDTO resultDTO,
                                  PaymentRiskDecisionEnum riskDecisionEnum,
                                  int currencyExponent);

    /**
     * 记录首次类交易渠道同步结果。
     * <p>
     * 该方法只处理渠道调用后的本地持久化和状态 CAS 推进；调用方必须通过独立 Spring Bean 提供事务边界，
     * 不得在调用方类中自调用事务方法。
     *
     * @param commandDTO       创建交易命令
     * @param routeResultDTO   渠道路由结果
     * @param channelInvokeResultDTO 渠道调用结果
     * @param resultDTO        渠道映射后的平台结果
     * @param riskDecisionEnum 内风控决策
     * @param currencyExponent 交易币种默认小数位
     */
    void completeInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                      PaymentRouteResultDTO routeResultDTO,
                                      PaymentChannelInvokeResultDTO channelInvokeResultDTO,
                                      PaymentCreateResultDTO resultDTO,
                                      PaymentRiskDecisionEnum riskDecisionEnum,
                                      int currencyExponent);

    /**
     * 记录同步渠道结果并返回状态 CAS 是否实际推进。
     *
     * @return true 表示交易状态发生了有效迁移
     */
    default boolean completeInitialChannelResultAndReport(
            PaymentCreateCommandDTO commandDTO,
            PaymentRouteResultDTO routeResultDTO,
            PaymentChannelInvokeResultDTO channelInvokeResultDTO,
            PaymentCreateResultDTO resultDTO,
            PaymentRiskDecisionEnum riskDecisionEnum,
            int currencyExponent) {
        completeInitialChannelResult(
                commandDTO,
                routeResultDTO,
                channelInvokeResultDTO,
                resultDTO,
                riskDecisionEnum,
                currencyExponent);
        return false;
    }

    /**
     * 按原交易业务时间和内部 operation_id 定位交易生命周期主单。
     *
     * @param transactionDateTime 原交易业务时间，对应 transaction_date_time 分表字段
     * @param operationId         平台内部生命周期关联标识
     * @return 交易生命周期主单
     */
    TransactionOrderDO findOrder(LocalDateTime transactionDateTime, String operationId);

    /**
     * 按平台当前交易 ID 定位所属生命周期主单。
     * <p>
     * 商户后续动作只需要传 sourceTransactionId；支付核心先用平台交易号中的业务时间定位动作分表，
     * 再通过动作单的 operation_id 读取生命周期主单。
     *
     * @param sourceTransactionId 原平台交易 ID
     * @return 原交易生命周期主单
     */
    TransactionOrderDO findSourceOrderByTransactionId(String sourceTransactionId);

    /**
     * 按原交易业务时间和 operation_id 锁定生命周期主单。
     * <p>
     * 同一授权下创建 Capture 前使用数据库行锁串行化余额和未终态动作检查，避免并发绕过未恢复 Capture 阻断。
     *
     * @param transactionDateTime 原交易业务时间
     * @param operationId         平台内部生命周期关联标识
     * @return 加锁后的交易生命周期主单
     */
    TransactionOrderDO lockOrder(LocalDateTime transactionDateTime, String operationId);

    /**
     * 按平台当前交易 ID 定位原交易动作单。
     * <p>
     * 后续动作需要原动作的渠道交易 ID，例如 MPGS VOID 的 targetTransactionId 必须使用原动作的 channel_transaction_id。
     *
     * @param sourceTransactionId 原平台交易 ID
     * @return 原交易动作单
     */
    TransactionOperationDO findSourceOperationByTransactionId(String sourceTransactionId);

    /**
     * 按商户订单号查询同一订单下的交易动作。
     * <p>
     * 商户查询接口不要求传原交易时间；支付核心按分表规则从最小可查表扫描到当前表，并通过 merchantId + merchantOrderNo
     * 控制数据归属，transactionId 仅作为可选精确过滤条件。
     *
     * @param merchantId      平台商户号
     * @param merchantOrderNo 商户订单号
     * @param transactionId   平台交易 ID，可为空
     * @return 交易动作列表
     */
    List<TransactionOperationDO> findOperationsByMerchantOrder(String merchantId, String merchantOrderNo, String transactionId);

    /**
     * 按商户订单号查询同一订单下的首次起点交易动作。
     * <p>
     * 用于支付核心创建首次类交易前校验支付流和授权流互斥；只返回 PAYMENT、AUTHORIZATION、PRE_AUTHORIZATION
     * 这类生命周期起点动作，后续请款、撤销、退款不参与互斥判断。
     *
     * @param merchantId      平台商户号
     * @param merchantOrderNo 商户订单号
     * @return 首次起点交易动作列表
     */
    List<TransactionOperationDO> findInitialOperationsByMerchantOrder(String merchantId, String merchantOrderNo);

    /**
     * 查询同一原始交易生命周期下结果尚未明确的请款动作。
     * <p>
     * Capture 的 PROCESSING/PENDING 动作可能已被渠道受理；恢复为 SUCCESS/FAILED 前必须阻断新的 Capture 渠道请求。
     *
     * @param merchantId           平台商户号
     * @param operationId          平台内部生命周期关联标识
     * @param sourceTransactionId  原授权或预授权平台交易 ID
     * @param beginTime            查询开始时间
     * @param endTime              查询结束时间
     * @return 未终态请款动作列表
     */
    List<TransactionOperationDO> findNonTerminalCaptures(String merchantId,
                                                         String operationId,
                                                         String sourceTransactionId,
                                                         LocalDateTime beginTime,
                                                         LocalDateTime endTime);

    /**
     * 查询同一交易生命周期下结果尚未明确的退款动作。
     * <p>
     * Refund 的 PROCESSING/PENDING 动作可能已经被渠道受理；恢复为 SUCCESS/FAILED 前必须占用可退额度。
     *
     * @param merchantId  平台商户号
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime   查询开始时间
     * @param endTime     查询结束时间
     * @return 未终态退款动作列表
     */
    default List<TransactionOperationDO> findNonTerminalRefunds(String merchantId,
                                                                String operationId,
                                                                LocalDateTime beginTime,
                                                                LocalDateTime endTime) {
        return List.of();
    }

    /**
     * 查询同一交易生命周期下结果尚未明确的 Void 动作。
     * <p>
     * Void / Authorization Cancel 的 PROCESSING/PENDING 动作可能已经被渠道受理；恢复为 SUCCESS/FAILED 前必须阻断
     * Capture、Refund 或新的 Void，避免重复释放授权或重复返还资金。
     *
     * @param merchantId  平台商户号
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime   查询开始时间
     * @param endTime     查询结束时间
     * @return 未终态 Void 动作列表
     */
    default List<TransactionOperationDO> findNonTerminalVoids(String merchantId,
                                                              String operationId,
                                                              LocalDateTime beginTime,
                                                              LocalDateTime endTime) {
        return List.of();
    }

    /**
     * 查询同一授权生命周期下结果尚未明确的 Incremental Authorization 动作。
     * <p>
     * PROCESSING/PENDING/UNKNOWN 等价未确认增量授权可能已经被渠道受理；恢复为 SUCCESS/FAILED 前必须阻断新的
     * Incremental Authorization，避免 timeout/unknown 重试导致重复增加授权金额。
     *
     * @param merchantId  平台商户号
     * @param operationId 平台内部生命周期关联标识
     * @param beginTime   查询开始时间
     * @param endTime     查询结束时间
     * @return 未终态 Incremental Authorization 动作列表
     */
    default List<TransactionOperationDO> findNonTerminalIncrementalAuthorizations(String merchantId,
                                                                                  String operationId,
                                                                                  LocalDateTime beginTime,
                                                                                  LocalDateTime endTime) {
        return List.of();
    }

    /**
     * 按渠道订单号和渠道交易 ID 定位平台交易动作。
     * <p>
     * MPGS 回调的 order.id 对应原始授权/支付平台 transactionId，transaction.id 对应平台生成的
     * channel_transaction_id；回调处理需通过二者定位具体授权、请款、退款或撤销动作。
     *
     * @param channelOrderNo       渠道订单号
     * @param channelTransactionId 渠道交易 ID
     * @return 平台交易动作单
     */
    TransactionOperationDO findOperationByChannelTransaction(String channelOrderNo, String channelTransactionId);

    /**
     * 查询待渠道查询确认的动作单。
     *
     * @param transactionDateTime 交易业务时间，用于定位物理分表
     * @param channelCode 渠道编码，可为空
     * @param now 当前时间
     * @param limit 最大查询数量
     * @return 待勾兑动作单列表
     */
    List<TransactionOperationDO> listPendingChannelMatch(LocalDateTime transactionDateTime,
                                                         String channelCode,
                                                         LocalDateTime now,
                                                         int limit);

    /**
     * 定位主动查询需要关联的原资金动作渠道请求记录。
     * <p>
     * 主动查询只能复用原 request_id 与原渠道身份，不能把本地查询请求号或平台 transaction_id 伪装成渠道交易 ID。
     *
     * @param operationDO 待恢复交易动作单
     * @return 原资金动作渠道请求记录，不存在时返回 null
     */
    default TransactionChannelRequestDO findOriginalChannelRequestForQuery(TransactionOperationDO operationDO) {
        return null;
    }

    /**
     * 记录后续交易动作事实，并在渠道同步成功时使用 CAS 推进主单金额汇总。
     *
     * @param recordDTO 后续交易动作记录上下文
     */
    void recordFollowUpTransaction(TransactionFollowUpRecordDTO recordDTO);

    /**
     * 在独立结果事务中保存 Capture 渠道同步结果。
     * <p>
     * Capture 明确失败只终结 Capture 动作，不终结原授权生命周期；成功结果才通过源主单金额 CAS 增加 captured
     * 并扣减 available_capture。
     *
     * @param operationDO 已预提交的 Capture 动作单
     * @param sourceOrderDO 原授权生命周期主单
     * @param commandDTO Capture 命令
     * @param routeResultDTO 渠道路由结果
     * @param invokeResultDTO 渠道调用结果
     * @param resultDTO 平台映射后的 Capture 结果
     * @param currencyExponent 交易币种默认辅币位
     * @return true 表示动作状态推进成功，false 表示已被回调或查询抢先推进
     */
    default boolean completeCaptureChannelResult(TransactionOperationDO operationDO,
                                                 TransactionOrderDO sourceOrderDO,
                                                 PaymentCreateCommandDTO commandDTO,
                                                 PaymentRouteResultDTO routeResultDTO,
                                                 PaymentChannelInvokeResultDTO invokeResultDTO,
                                                 PaymentCreateResultDTO resultDTO,
                                                 int currencyExponent) {
        return false;
    }

    /**
     * 在独立结果事务中保存 Refund 渠道同步结果。
     * <p>
     * Refund 明确失败只终结本次 Refund 动作；成功结果才通过源主单金额 CAS 增加 refunded
     * 并扣减 available_refund。非终态结果保持查询恢复入口，不重新发起渠道 Refund。
     *
     * @param operationDO 已预提交的 Refund 动作单
     * @param sourceOrderDO 原交易生命周期主单
     * @param commandDTO Refund 命令
     * @param routeResultDTO 渠道路由结果
     * @param invokeResultDTO 渠道调用结果
     * @param resultDTO 平台映射后的 Refund 结果
     * @param currencyExponent 交易币种默认辅币位
     * @return true 表示动作状态推进成功，false 表示已被回调或查询抢先推进
     */
    default boolean completeRefundChannelResult(TransactionOperationDO operationDO,
                                                TransactionOrderDO sourceOrderDO,
                                                PaymentCreateCommandDTO commandDTO,
                                                PaymentRouteResultDTO routeResultDTO,
                                                PaymentChannelInvokeResultDTO invokeResultDTO,
                                                PaymentCreateResultDTO resultDTO,
                                                int currencyExponent) {
        return false;
    }

    /**
     * 在独立结果事务中保存 Void 渠道同步结果。
     * <p>
     * Void 明确失败只终结本次撤销动作；成功结果才通过源主单 CAS 标记可请款金额为 0。
     * 非终态结果保持查询恢复入口，不重新发起渠道 Void / Authorization Cancel。
     *
     * @param operationDO 已预提交的 Void 动作单
     * @param sourceOrderDO 原交易生命周期主单
     * @param commandDTO Void 命令
     * @param routeResultDTO 渠道路由结果
     * @param invokeResultDTO 渠道调用结果
     * @param resultDTO 平台映射后的 Void 结果
     * @param currencyExponent 交易币种默认辅币位
     * @return true 表示动作状态推进成功，false 表示已被回调或查询抢先推进
     */
    default boolean completeVoidChannelResult(TransactionOperationDO operationDO,
                                              TransactionOrderDO sourceOrderDO,
                                              PaymentCreateCommandDTO commandDTO,
                                              PaymentRouteResultDTO routeResultDTO,
                                              PaymentChannelInvokeResultDTO invokeResultDTO,
                                              PaymentCreateResultDTO resultDTO,
                                              int currencyExponent) {
        return false;
    }

    /**
     * 在独立结果事务中保存 Incremental Authorization 渠道同步结果。
     * <p>
     * Incremental Authorization 明确失败只终结本次增量授权动作；成功结果才通过源主单金额 CAS 增加 authorized、
     * transaction_amount 和 available_capture。非终态结果保持查询恢复入口，不重新发起渠道增量授权。
     *
     * @param operationDO 已预提交的 Incremental Authorization 动作单
     * @param sourceOrderDO 原授权生命周期主单
     * @param commandDTO Incremental Authorization 命令
     * @param routeResultDTO 渠道路由结果
     * @param invokeResultDTO 渠道调用结果
     * @param resultDTO 平台映射后的 Incremental Authorization 结果
     * @param currencyExponent 交易币种默认辅币位
     * @return true 表示动作状态推进成功，false 表示已被回调或查询抢先推进
     */
    default boolean completeIncrementalAuthorizationChannelResult(TransactionOperationDO operationDO,
                                                                  TransactionOrderDO sourceOrderDO,
                                                                  PaymentCreateCommandDTO commandDTO,
                                                                  PaymentRouteResultDTO routeResultDTO,
                                                                  PaymentChannelInvokeResultDTO invokeResultDTO,
                                                                  PaymentCreateResultDTO resultDTO,
                                                                  int currencyExponent) {
        return false;
    }

    /**
     * 按渠道回调或渠道查询确认结果推进交易动作终态。
     *
     * @param operationDO             被推进的交易动作单
     * @param orderDO                 所属交易生命周期主单
     * @param callbackId              渠道回调业务 ID
     * @param targetTransactionStatus 目标交易状态
     * @param failReasonCode          失败原因码
     * @param failReasonMessage       后台可见失败原因
     * @param channelStatus           渠道原始状态
     * @param channelResponseCode     渠道响应码
     * @param channelResponseMessage  渠道响应描述
     * @return true 表示状态推进成功，false 表示已被终态或并发处理抢先推进
     */
    boolean completeByChannelCallback(TransactionOperationDO operationDO,
                                      TransactionOrderDO orderDO,
                                      String callbackId,
                                      String targetTransactionStatus,
                                      String failReasonCode,
                                      String failReasonMessage,
                                      String channelStatus,
                                      String channelResponseCode,
                                      String channelResponseMessage);

    /**
     * 更新渠道查询勾兑摘要。
     *
     * @param operationDO 被勾兑的动作单
     * @param matchStatus 勾兑状态
     * @param matchResult 勾兑结果摘要
     * @param requestId 最近一次渠道查询请求 ID
     * @param matchTime 最近一次查询时间
     * @param nextMatchTime 下一次查询时间
     * @param failReason 失败原因
     * @return true 表示更新成功
     */
    boolean updateChannelMatch(TransactionOperationDO operationDO,
                               String matchStatus,
                               String matchResult,
                               String requestId,
                               LocalDateTime matchTime,
                               LocalDateTime nextMatchTime,
                               String failReason);

    /**
     * 根据主动查询结果回写原资金动作渠道请求记录。
     *
     * @param operationDO 待恢复交易动作单
     * @param originalRequestDO 原资金动作渠道请求记录
     * @param invokeResultDTO 渠道查询调用结果
     * @param platformResultCode 平台解析结果
     * @param failReason 平台失败或待恢复原因
     * @return true 表示原请求记录更新成功或无需更新
     */
    default boolean updateOriginalChannelRequestByQuery(TransactionOperationDO operationDO,
                                                        TransactionChannelRequestDO originalRequestDO,
                                                        PaymentChannelInvokeResultDTO invokeResultDTO,
                                                        String platformResultCode,
                                                        String failReason) {
        return true;
    }

    /**
     * 回写商户 OpenAPI 响应加密后的摘要信息。
     * <p>
     * 响应加密发生在 service-openapi 响应切面，支付核心首次落库时只能保存脱敏响应明文；
     * 切面完成加密后通过该方法补写密文掩码和摘要，便于后台核验商户最终收到的数据形态。
     *
     * @param commandDTO 响应日志回写命令
     * @return true 表示命中并更新日志，false 表示未找到对应记录
     */
    boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateCommandDTO commandDTO);

}
