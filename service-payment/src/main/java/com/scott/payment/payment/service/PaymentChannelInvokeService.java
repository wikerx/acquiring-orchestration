package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.PaymentPreparedChannelRequestDTO;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelInvokeService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单渠道调用服务，位于 service-payment 服务层，用于把平台交易上下文转换为 payment-channel-api 统一请求并调用 core 执行器，同时返回审计落库所需上下文。
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

    /**
     * 使用已提交的渠道请求身份调用收单渠道。
     * <p>
     * 首次交易准备事务会先生成并保存 request_id 与 channel_transaction_id；渠道调用必须复用这些值，
     * 避免重复请求或恢复查询时产生第二个渠道资金动作身份。
     *
     * @param commandDTO             创建交易命令
     * @param routeResult            路由结果
     * @param operationId            平台内部生命周期关联标识
     * @param transactionId          平台当前交易 ID
     * @param preparedChannelRequest 已提交的渠道请求身份
     * @return 渠道调用结果，包含统一请求、同步响应、请求 ID 和耗时
     */
    default PaymentChannelInvokeResultDTO invoke(PaymentCreateCommandDTO commandDTO,
                                                 PaymentRouteResultDTO routeResult,
                                                 String operationId,
                                                 String transactionId,
                                                 PaymentPreparedChannelRequestDTO preparedChannelRequest) {
        String channelOrderNo = preparedChannelRequest == null ? null : preparedChannelRequest.getChannelOrderNo();
        return invoke(commandDTO, routeResult, operationId, transactionId, channelOrderNo);
    }

    /**
     * 判断当前渠道是否支持使用已持久化渠道身份发起查询。
     * <p>
     * 默认保持兼容，具体实现可委托渠道 SPI 判断渠道差异。
     *
     * @param commandDTO 查询命令
     * @param routeResult 渠道路由快照
     * @param operationId 平台内部生命周期关联标识
     * @param transactionId 平台当前交易 ID
     * @param preparedChannelRequest 已持久化查询身份
     * @return true 表示渠道可以识别当前查询身份
     */
    default boolean supportsQueryReference(PaymentCreateCommandDTO commandDTO,
                                           PaymentRouteResultDTO routeResult,
                                           String operationId,
                                           String transactionId,
                                           PaymentPreparedChannelRequestDTO preparedChannelRequest) {
        return preparedChannelRequest != null;
    }
}
