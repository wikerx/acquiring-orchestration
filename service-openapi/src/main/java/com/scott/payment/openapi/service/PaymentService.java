package com.scott.payment.openapi.service;

import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import com.scott.payment.openapi.vo.payment.PaymentQueryVO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentService
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 收单交易服务接口，位于 service-openapi 服务层，负责把独立交易 API 转换为 service-payment 内部调用。
 * @status : create
 */
public interface PaymentService {

    /**
     * 提交一步支付交易。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹，禁止打印和落库明文
     * @param requestDTO 解密后的一步支付请求参数
     * @return 一步支付受理响应
     */
    PaymentCreateVO createPayment(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO);

    /**
     * 提交授权交易。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹，禁止打印和落库明文
     * @param requestDTO 解密后的授权请求参数
     * @return 授权交易受理响应
     */
    PaymentCreateVO createAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO);

    /**
     * 提交预授权交易。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹，禁止打印和落库明文
     * @param requestDTO 解密后的预授权请求参数
     * @return 预授权交易受理响应
     */
    PaymentCreateVO createPreAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO);

    /**
     * 提交增量授权交易。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹，禁止打印和落库明文
     * @param requestDTO 解密后的增量授权请求参数
     * @return 增量授权交易受理响应
     */
    PaymentCreateVO createIncrementalAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO);

    /**
     * 提交请款交易。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹，禁止打印和落库明文
     * @param requestDTO 解密后的请款请求参数
     * @return 请款交易受理响应
     */
    PaymentCreateVO capture(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO);

    /**
     * 提交预授权完成交易。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹，禁止打印和落库明文
     * @param requestDTO 解密后的预授权完成请求参数
     * @return 预授权完成交易受理响应
     */
    PaymentCreateVO preAuthCompletion(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO);

    /**
     * 提交退款交易。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹，禁止打印和落库明文
     * @param requestDTO 解密后的退款请求参数
     * @return 退款交易受理响应
     */
    PaymentCreateVO refund(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO);

    /**
     * 提交撤销交易。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹，禁止打印和落库明文
     * @param requestDTO 解密后的撤销请求参数
     * @return 撤销交易受理响应
     */
    PaymentCreateVO voidPayment(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO);

    /**
     * 查询收单交易状态。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹，禁止打印和落库明文
     * @param requestDTO 解密后的查询请求参数
     * @return 交易查询响应；当前阶段先占位，正式详情会在交易分表查询仓储落地后补齐
     */
    PaymentQueryVO queryTransaction(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO);
}
