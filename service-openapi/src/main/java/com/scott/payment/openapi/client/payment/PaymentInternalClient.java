package com.scott.payment.openapi.client.payment;

import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentQueryClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionMerchantApiResponseLogUpdateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.checkout.PaymentCheckoutClientDTOs;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalClient
 * @date : 2026-05-31 21:12
 * @email : scott_x@163.com
 * @description : service-payment 内部交易调用客户端，位于 service-openapi 客户端层，为每个收单交易动作提供独立方法并封装内部签名。
 * @status : create
 */
public interface PaymentInternalClient {

    /**
     * 调用 service-payment 创建收单授权交易。
     *
     * @param requestDTO 创建交易内部请求
     * @return 创建交易内部响应
     */
    PaymentCreateClientResponseDTO createAuthorization(PaymentCreateClientRequestDTO requestDTO);

    /**
     * 调用 service-payment 创建一步支付交易。
     *
     * @param requestDTO 创建交易内部请求
     * @return 创建交易内部响应
     */
    PaymentCreateClientResponseDTO createPayment(PaymentCreateClientRequestDTO requestDTO);

    /**
     * 调用 service-payment 创建预授权交易。
     *
     * @param requestDTO 创建交易内部请求
     * @return 创建交易内部响应
     */
    PaymentCreateClientResponseDTO createPreAuthorization(PaymentCreateClientRequestDTO requestDTO);

    /**
     * 调用 service-payment 创建增量授权交易。
     *
     * @param requestDTO 创建交易内部请求
     * @return 创建交易内部响应
     */
    PaymentCreateClientResponseDTO createIncrementalAuthorization(PaymentCreateClientRequestDTO requestDTO);

    /**
     * 调用 service-payment 发起请款交易。
     *
     * @param requestDTO 请款内部请求
     * @return 请款内部响应
     */
    PaymentCreateClientResponseDTO capture(PaymentCreateClientRequestDTO requestDTO);

    /**
     * 调用 service-payment 发起预授权完成交易。
     *
     * @param requestDTO 预授权完成内部请求
     * @return 预授权完成内部响应
     */
    PaymentCreateClientResponseDTO preAuthCompletion(PaymentCreateClientRequestDTO requestDTO);

    /**
     * 调用 service-payment 发起退款交易。
     *
     * @param requestDTO 退款内部请求
     * @return 退款内部响应
     */
    PaymentCreateClientResponseDTO refund(PaymentCreateClientRequestDTO requestDTO);

    /**
     * 调用 service-payment 发起撤销交易。
     *
     * @param requestDTO 撤销内部请求
     * @return 撤销内部响应
     */
    PaymentCreateClientResponseDTO voidPayment(PaymentCreateClientRequestDTO requestDTO);

    /**
     * 调用 service-payment 查询交易状态。
     *
     * @param requestDTO 查询内部请求
     * @return 查询内部响应
     */
    PaymentQueryClientResponseDTO query(PaymentCreateClientRequestDTO requestDTO);

    /**
     * 调用 service-payment 记录渠道回调。
     *
     * @param requestDTO 渠道回调内部请求
     * @return 渠道回调记录响应
     */
    TransactionChannelCallbackClientResponseDTO recordChannelCallback(TransactionChannelCallbackClientRequestDTO requestDTO);

    /**
     * 回写商户 OpenAPI 响应加密后的密文摘要。
     *
     * @param requestDTO 响应日志回写请求
     * @return true 表示 service-payment 命中并更新日志
     */
    boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO);

    /**
     * 调用 service-payment 创建 Hosted Checkout 会话。
     *
     * @param requestDTO 创建收银台会话内部请求
     * @return 收银台会话创建响应
     */
    PaymentCheckoutClientDTOs.SessionCreateResponse createCheckoutSession(
            PaymentCheckoutClientDTOs.SessionCreateRequest requestDTO);

    /**
     * 调用 service-payment 查询 Hosted Checkout 会话展示状态。
     *
     * @param requestDTO 查询收银台会话内部请求
     * @return 收银台展示响应
     */
    PaymentCheckoutClientDTOs.SessionQueryResponse queryCheckoutSession(
            PaymentCheckoutClientDTOs.SessionQueryRequest requestDTO);

    /**
     * 调用 service-payment 提交 Hosted Checkout 支付。
     *
     * @param requestDTO 支付提交内部请求
     * @return 支付提交响应
     */
    PaymentCheckoutClientDTOs.PaymentResultResponse submitCheckoutPayment(
            PaymentCheckoutClientDTOs.PaymentSubmitRequest requestDTO);

    /**
     * 调用 service-payment 查询 Hosted Checkout 支付状态。
     *
     * @param requestDTO 支付状态查询内部请求
     * @return 支付状态响应
     */
    PaymentCheckoutClientDTOs.PaymentResultResponse queryCheckoutPaymentStatus(
            PaymentCheckoutClientDTOs.PaymentStatusRequest requestDTO);

    /**
     * 调用 service-payment 处理 Hosted Checkout 3DS 回跳。
     *
     * @param requestDTO 3DS 回跳内部请求
     * @return 支付状态响应
     */
    PaymentCheckoutClientDTOs.PaymentResultResponse handleCheckoutThreeDsReturn(
            PaymentCheckoutClientDTOs.ThreeDsReturnRequest requestDTO);

    /** 解析收银台卡 BIN 品牌及 MID 支持状态。 */
    PaymentCheckoutClientDTOs.CardBinResponse resolveCheckoutCardBin(
            PaymentCheckoutClientDTOs.CardBinRequest requestDTO);
}
