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

    private PaymentCreateVO.MerchantInfoVO merchantInfo;
    private PaymentCreateVO.OrderInfoVO orderInfo;
    private List<PaymentCreateVO.GoodsInfoVO> goodsInfo;
    private PaymentCreateVO.BillingCardHolderInfoVO billingCardHolderInfo;
    private PaymentCreateVO.PayerInfoVO payerInfo;
    private PaymentCreateVO.ShippingInfoVO shippingInfo;
    private TransactionInfoVO transactionInfo;
    private String checkoutUrl;

    /** 创建会话请求中允许回显的交易扩展字段。 */
    @Data
    public static class TransactionInfoVO implements Serializable {
        private static final long serialVersionUID = 1L;
        private String description;
        private String callbackUrl;
        private String redirectUrl;
        private String language;
    }
}
