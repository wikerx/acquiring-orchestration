package com.scott.payment.openapi.dto.body;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商户创建 Hosted Checkout 会话请求。字段结构严格对齐商户文档 8.1，支付方式、有效期、重试和 3DS 策略由平台配置决定。
 */
@Data
public class HostedCheckoutSessionCreateRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    public interface Create {
    }

    public interface Format {
    }

    /** 商户身份及可选子商户信息。 */
    @Valid
    @NotNull(message = "merchantInfo", groups = Create.class)
    private MerchantInfoDTO merchantInfo;

    /** 商户订单号、幂等号、金额和币种。 */
    @Valid
    @NotNull(message = "orderInfo", groups = Create.class)
    private OrderInfoDTO orderInfo;

    /** 可选商品或服务明细。 */
    @Valid
    private List<ApiMerchantPaymentRequestDTO.GoodsInfoDTO> goodsInfo;

    /** 可选账单持卡人预填对象；存在时整对象优先。 */
    @Valid
    private ApiMerchantPaymentRequestDTO.BillingCardHolderInfoDTO billingCardHolderInfo;

    /** 付款人快照；ipAddress 必传。 */
    @Valid
    @NotNull(message = "payerInfo", groups = Create.class)
    private ApiMerchantPaymentRequestDTO.PayerInfoDTO payerInfo;

    /** 可选收货人快照。 */
    @Valid
    private ApiMerchantPaymentRequestDTO.ShippingInfoDTO shippingInfo;

    /** 当前版本预留的风控扩展对象，不参与处理且不返回。 */
    @Valid
    private ApiMerchantPaymentRequestDTO.RiskInfoDTO riskInfo;

    /** 可选交易描述、终态通知、结果页返回地址和语言。 */
    @Valid
    private TransactionInfoDTO transactionInfo;

    @Data
    public static class MerchantInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotBlank(message = "merchantInfo.merchantId", groups = Create.class)
        @Pattern(regexp = "^[2-9]\\d{5,15}$", message = "merchantInfo.merchantId format does not match", groups = Format.class)
        private String merchantId;

        @Valid
        private ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO subMerchantInfo;
    }

    @Data
    public static class OrderInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotNull(message = "orderInfo.amount", groups = Create.class)
        @DecimalMin(value = "0", inclusive = false, message = "orderInfo.amount must be greater than 0", groups = Format.class)
        @Digits(integer = 12, fraction = 3, message = "orderInfo.amount format does not match", groups = Format.class)
        private BigDecimal amount;

        @NotBlank(message = "orderInfo.currency", groups = Create.class)
        @Pattern(regexp = "^[A-Z]{3}$", message = "orderInfo.currency format does not match", groups = Format.class)
        private String currency;

        @NotBlank(message = "orderInfo.orderNo", groups = Create.class)
        @Pattern(regexp = "^[A-Za-z0-9]{1,64}$", message = "orderInfo.orderNo format does not match", groups = Format.class)
        private String orderNo;

        @NotBlank(message = "orderInfo.orderId", groups = Create.class)
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,64}$", message = "orderInfo.orderId format does not match", groups = Format.class)
        private String orderId;
    }

    @Data
    public static class TransactionInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Size(max = 128, message = "transactionInfo.description format does not match", groups = Format.class)
        private String description;

        @Size(max = 512, message = "transactionInfo.callbackUrl format does not match", groups = Format.class)
        @Pattern(regexp = "^$|^(?i:https?)://\\S+$", message = "transactionInfo.callbackUrl format does not match", groups = Format.class)
        private String callbackUrl;

        @Size(max = 512, message = "transactionInfo.redirectUrl format does not match", groups = Format.class)
        @Pattern(regexp = "^$|^(?i:https?)://\\S+$", message = "transactionInfo.redirectUrl format does not match", groups = Format.class)
        private String redirectUrl;

        @Size(max = 8, message = "transactionInfo.language format does not match", groups = Format.class)
        @Pattern(regexp = "^$|^[A-Za-z]{2,3}(?:-[A-Za-z]{2,4})?$", message = "transactionInfo.language format does not match", groups = Format.class)
        private String language;
    }
}
