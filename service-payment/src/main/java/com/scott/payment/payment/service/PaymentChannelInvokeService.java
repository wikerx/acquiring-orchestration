package com.scott.payment.payment.service;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelInvokeService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道调用服务，位于 service-payment 服务层，用于把平台交易上下文转换为 payment-channel-library 请求并调用渠道执行器。
 * @status : create
 */
public interface PaymentChannelInvokeService {

    /**
     * 调用收单渠道。
     *
     * @param commandDTO          创建交易命令
     * @param routeResult         路由结果
     * @param transactionOrderNo  平台交易生命周期主单号
     * @param transactionNo       平台当前交易动作单号
     * @return 渠道统一响应
     */
    ChannelPaymentResponse invoke(PaymentCreateCommandDTO commandDTO,
                                  PaymentRouteResultDTO routeResult,
                                  String transactionOrderNo,
                                  String transactionNo);
}
