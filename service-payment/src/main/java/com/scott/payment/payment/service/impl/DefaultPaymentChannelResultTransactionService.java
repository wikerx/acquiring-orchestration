package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.service.PaymentChannelResultTransactionService;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次支付渠道结果的独立事务服务。
 *
 * <p>渠道 HTTP 调用必须在数据库事务之外执行；取得结果后，本服务使用独立事务持久化渠道证据、
 * CAS 推进平台状态，并在终态 CAS 获胜时同事务写入生命周期 Outbox。重复或晚到结果只允许补充
 * 渠道事实，不能覆盖已提交终态。</p>
 */
@Service
public class DefaultPaymentChannelResultTransactionService implements PaymentChannelResultTransactionService {

    private final TransactionRecordService transactionRecordService;

    /**
     * 交易状态变更 Outbox 服务，与渠道同步结果在同一数据库事务内写入。
     */
    private final TransactionLifecycleEventService lifecycleEventService;

    /**
     * 创建带终态 Outbox 能力的渠道同步结果事务服务。
     *
     * @param transactionRecordService 交易事实记录服务
     * @param lifecycleEventService    交易状态变更 Outbox 服务
     */
    public DefaultPaymentChannelResultTransactionService(
            TransactionRecordService transactionRecordService,
            TransactionLifecycleEventService lifecycleEventService) {
        this.transactionRecordService = transactionRecordService;
        this.lifecycleEventService = lifecycleEventService;
    }

    /**
     * 在独立事务中保存首次交易渠道同步结果。
     *
     * @param commandDTO 创建交易命令
     * @param routeResultDTO 渠道路由结果
     * @param invokeResultDTO 渠道调用结果
     * @param resultDTO 平台映射后的同步结果
     * @param riskDecisionEnum 本地准备阶段风控决策
     * @param currencyExponent 交易币种默认辅币位
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean recordInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                              PaymentRouteResultDTO routeResultDTO,
                                              PaymentChannelInvokeResultDTO invokeResultDTO,
                                              PaymentCreateResultDTO resultDTO,
                                              PaymentRiskDecisionEnum riskDecisionEnum,
                                              int currencyExponent) {
        // 返回值表示平台状态 CAS 是否获胜，不等同于“渠道调用是否成功”。
        boolean statusChanged = transactionRecordService.completeInitialChannelResultAndReport(
                commandDTO,
                routeResultDTO,
                invokeResultDTO,
                resultDTO,
                riskDecisionEnum,
                currencyExponent);
        if (statusChanged
                && (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(resultDTO.getStatus())
                || PaymentTransactionStatusEnum.FAILED.getCode().equals(resultDTO.getStatus()))) {
            // 事件与终态在当前独立事务内共同提交，避免先发消息后回滚状态。
            lifecycleEventService.saveStatusChanged(
                    resultDTO.getTransactionId(),
                    resultDTO.getOperationId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    resultDTO.getTransactionType(),
                    resultDTO.getStatus(),
                    commandDTO.getTransactionDateTime());
        }
        return statusChanged;
    }

    /** 在独立事务中抢占 INIT 渠道请求。 */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean claimInitialChannelSubmission(String requestId, java.time.LocalDateTime transactionDateTime) {
        // 只有 INIT 请求可以被抢占，防止重放请求对同一渠道动作发起第二次资金调用。
        return transactionRecordService.claimInitialChannelSubmission(requestId, transactionDateTime);
    }

    /**
     * 记录渠道调用前失败。复用已准备的渠道请求身份，但不会伪造 PSP 响应或渠道交互。
     */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean recordInitialPreChannelFailure(PaymentCreateCommandDTO commandDTO,
                                                  PaymentRouteResultDTO routeResultDTO,
                                                  PaymentChannelInvokeResultDTO invokeResultDTO,
                                                  PaymentCreateResultDTO resultDTO,
                                                  PaymentRiskDecisionEnum riskDecisionEnum,
                                                  int currencyExponent) {
        // 前置失败没有真实 PSP 响应，先抢占准备记录，再以平台失败原因收敛，禁止伪造渠道交互。
        if (!transactionRecordService.claimInitialPreChannelFailure(
                invokeResultDTO.getRequestId(), commandDTO.getTransactionDateTime())) {
            return false;
        }
        boolean statusChanged = transactionRecordService.completeInitialChannelResultAndReport(
                commandDTO,
                routeResultDTO,
                invokeResultDTO,
                resultDTO,
                riskDecisionEnum,
                currencyExponent);
        if (statusChanged) {
            lifecycleEventService.saveStatusChanged(
                    resultDTO.getTransactionId(),
                    resultDTO.getOperationId(),
                    commandDTO.getMerchantId(),
                    commandDTO.getMerchantOrderNo(),
                    resultDTO.getTransactionType(),
                    resultDTO.getStatus(),
                    commandDTO.getTransactionDateTime());
        }
        return statusChanged;
    }

    /** 在独立事务中写入通用交易级 3DS 标识。 */
    @Override
    @DS(DataSourceName.TRANSACTION)
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markThreeDsIndicator(String transactionId,
                                     java.time.LocalDateTime transactionDateTime,
                                     String indicator) {
        transactionRecordService.markThreeDsIndicator(transactionId, transactionDateTime, indicator);
    }
}
