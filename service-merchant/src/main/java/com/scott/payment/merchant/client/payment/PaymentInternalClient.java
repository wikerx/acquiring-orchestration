package com.scott.payment.merchant.client.payment;

import com.scott.payment.merchant.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionActionResponse;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundSearchResponse;

import java.time.LocalDateTime;

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

    /** 查询当前商户退款分页和统计。 */
    RefundSearchResponse searchRefunds(RefundQuery query);

    /** 查询当前商户单笔退款详情。 */
    RefundDetailResponse refundDetail(String transactionId,
                                      LocalDateTime transactionDateTime,
                                      String merchantId);

    /**
     * 通过支付核心发起请款动作。
     *
     * @param requestDTO 支付核心内部请款命令
     * @return 请款动作结果
     */
    TransactionActionResponse capture(PaymentTransactionActionClientRequestDTO requestDTO);

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
