package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentRiskDecisionEnum;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelResultTransactionService
 * @date : 2026-07-23 00:00
 * @email : scott_x@163.com
 * @description : 渠道同步结果事务服务，位于 service-payment 服务层，负责在独立本地事务中持久化渠道结果并通过 CAS 推进平台交易状态。
 * @status : create
 */
public interface PaymentChannelResultTransactionService {

    /**
     * 在独立事务中保存首次交易渠道同步结果。
     * <p>
     * 调用方必须已经完成本地准备事务并在事务外完成渠道调用；本方法只处理本地结果持久化，不发起 Payment 渠道请求。
     *
     * @param commandDTO 创建交易命令
     * @param routeResultDTO 渠道路由结果
     * @param invokeResultDTO 渠道调用结果
     * @param resultDTO 平台映射后的同步结果
     * @param riskDecisionEnum 本地准备阶段风控决策
     * @param currencyExponent 交易币种默认辅币位
     */
    void recordInitialChannelResult(PaymentCreateCommandDTO commandDTO,
                                    PaymentRouteResultDTO routeResultDTO,
                                    PaymentChannelInvokeResultDTO invokeResultDTO,
                                    PaymentCreateResultDTO resultDTO,
                                    PaymentRiskDecisionEnum riskDecisionEnum,
                                    int currencyExponent);
}
