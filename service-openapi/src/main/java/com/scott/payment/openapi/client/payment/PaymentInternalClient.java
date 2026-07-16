package com.scott.payment.openapi.client.payment;

import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionMerchantApiResponseLogUpdateClientRequestDTO;

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
    PaymentCreateClientResponseDTO query(PaymentCreateClientRequestDTO requestDTO);

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
}
