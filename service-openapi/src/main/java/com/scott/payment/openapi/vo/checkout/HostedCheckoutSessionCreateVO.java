package com.scott.payment.openapi.vo.checkout;

import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 商户创建 Hosted Checkout 会话响应，只回显文档 8.1 定义的请求快照和平台生成的 checkoutUrl。
 */
@Data
public class HostedCheckoutSessionCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应中的商户信息，用于管理端或商户端展示当前处理结果。
     */
    private PaymentCreateVO.MerchantInfoVO merchantInfo;
    /**
     * 响应中的订单信息，用于管理端或商户端展示当前处理结果。
     */
    private PaymentCreateVO.OrderInfoVO orderInfo;
    /**
     * {@code goodsInfo}集合，承载 {@code HostedCheckoutSessionCreateVO} 当前请求或响应中的多值数据。
     */
    private List<PaymentCreateVO.GoodsInfoVO> goodsInfo;
    /**
     * 响应中的账单持卡人信息，用于管理端或商户端展示当前处理结果。
     */
    private PaymentCreateVO.BillingCardHolderInfoVO billingCardHolderInfo;
    /**
     * 响应中的{@code payerInfo}，用于管理端或商户端展示当前处理结果。
     */
    private PaymentCreateVO.PayerInfoVO payerInfo;
    /**
     * 响应中的{@code shippingInfo}，用于管理端或商户端展示当前处理结果。
     */
    private PaymentCreateVO.ShippingInfoVO shippingInfo;
    /**
     * 响应中的交易信息，用于管理端或商户端展示当前处理结果。
     */
    private TransactionInfoVO transactionInfo;

    private String checkoutUrl;

    /**
     * 创建会话请求中允许回显的交易扩展字段。
     */
    @Data
    public static class TransactionInfoVO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String description;

        private String callbackUrl;

        private String redirectUrl;
        /**
         * 响应中的{@code language}，用于管理端或商户端展示当前处理结果。
         */
        private String language;
    }
}
