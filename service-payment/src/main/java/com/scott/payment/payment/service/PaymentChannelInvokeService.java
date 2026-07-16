package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelInvokeService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道调用服务，位于 service-payment 服务层，用于把平台交易上下文转换为 payment-channel-library 请求并调用渠道执行器，同时返回审计落库所需上下文。
 * @status : create
 */
public interface PaymentChannelInvokeService {

    /**
     * 调用收单渠道。
     *
     * @param commandDTO          创建交易命令
     * @param routeResult         路由结果
     * @param operationId         平台内部生命周期关联标识
     * @param transactionId       平台当前交易 ID
     * @param channelOrderNo      渠道订单号；MPGS 使用原始授权/支付平台 transactionId
     * @return 渠道调用结果，包含统一请求、同步响应、请求 ID 和耗时
     */
    PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                         PaymentRouteResultDTO routeResult,
                                         String operationId,
                                         String transactionId,
                                         String channelOrderNo);
}
