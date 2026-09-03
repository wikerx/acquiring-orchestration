package com.scott.payment.merchant.client.payment;

import com.scott.payment.merchant.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionActionResponse;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalClient
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : service-payment 内部客户端契约，位于 service-merchant 客户端层，只承载商户后台发起退款等需要支付核心变更状态的动作入口。
 * @status : create
 */
public interface PaymentInternalClient {

    /**
     * 通过支付核心发起请款动作。
     *
     * @param requestDTO 支付核心内部请款命令
     * @return 请款动作结果
     */
    TransactionActionResponse capture(PaymentTransactionActionClientRequestDTO requestDTO);

    /**
     * 通过支付核心发起预授权完成动作。
     *
     * @param requestDTO 支付核心内部预授权完成命令
     * @return 预授权完成动作结果
     */
    TransactionActionResponse preAuthCompletion(PaymentTransactionActionClientRequestDTO requestDTO);

    /**
     * 通过支付核心发起退款动作。
     *
     * @param requestDTO 支付核心内部退款命令
     * @return 退款动作结果
     */
    TransactionActionResponse refund(PaymentTransactionActionClientRequestDTO requestDTO);

    /**
     * 通过支付核心发起撤销动作。
     *
     * @param requestDTO 支付核心内部撤销命令
     * @return 撤销动作结果
     */
    TransactionActionResponse voidPayment(PaymentTransactionActionClientRequestDTO requestDTO);
}
