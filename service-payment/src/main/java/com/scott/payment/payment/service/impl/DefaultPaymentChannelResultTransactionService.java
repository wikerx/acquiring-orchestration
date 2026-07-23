package com.scott.payment.payment.service.impl;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.service.PaymentChannelResultTransactionService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentChannelResultTransactionService
 * @date : 2026-07-23 00:00
 * @email : scott_x@163.com
 * @description : 渠道同步结果事务默认实现，位于 service-payment 服务实现层，使用 REQUIRES_NEW 保证渠道结果持久化不依赖调用方事务。
 * @status : create
 */
@Service
public class DefaultPaymentChannelResultTransactionService implements PaymentChannelResultTransactionService {

    private final TransactionRecordService transactionRecordService;

    /**
     * 创建渠道同步结果事务默认实现。
     *
     * @param transactionRecordService 交易事实记录服务
     */
    public DefaultPaymentChannelResultTransactionService(TransactionRecordService transactionRecordService) {
        this.transactionRecordService = transactionRecordService;
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
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void recordInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                           PaymentRouteResultDTO routeResultDTO,
                                           PaymentChannelInvokeResultDTO invokeResultDTO,
                                           PaymentCreateResultDTO resultDTO,
                                           PaymentRiskDecisionEnum riskDecisionEnum,
                                           int currencyExponent) {
        transactionRecordService.completeInitialChannelResult(
                commandDTO,
                routeResultDTO,
                invokeResultDTO,
                resultDTO,
                riskDecisionEnum,
                currencyExponent);
    }
}
