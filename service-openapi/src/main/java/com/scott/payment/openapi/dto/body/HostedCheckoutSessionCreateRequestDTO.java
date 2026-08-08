package com.scott.payment.openapi.dto.body;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商户创建 Hosted Checkout 会话请求。
 */
@Data
public class HostedCheckoutSessionCreateRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    public interface Create {
    }

    public interface Format {
    }

    /** 商户身份及可选子商户信息，不允许为空。 */
    @Valid
    @NotNull(message = "merchantInfo", groups = Create.class)
    private MerchantInfoDTO merchantInfo;

    /** 商户订单、金额和商品快照，不允许为空。 */
    @Valid
    @NotNull(message = "orderInfo", groups = Create.class)
    private OrderInfoDTO orderInfo;

    /** 收银台展示、支付方式、有效期和跳转地址配置，不允许为空。 */
    @Valid
    @NotNull(message = "checkoutInfo", groups = Create.class)
    private CheckoutInfoDTO checkoutInfo;

    /** 可选付款人摘要；邮箱进入支付服务前必须转换为掩码和哈希。 */
    @Valid
    private PayerInfoDTO payerInfo;

    /** 可选账单资料；提供后付款页自动预填，付款人仍可修改。 */
    @Valid
    private BillingInfoDTO billingInfo;

    /**
     * Hosted Checkout 会话所属商户信息。
     */
    @Data
    public static class MerchantInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 平台商户号，必须与已完成 OpenAPI 认证的商户身份一致。 */
        @NotBlank(message = "merchantInfo.merchantId", groups = Create.class)
        @Pattern(regexp = "^[2-9]\\d{5,16}$", message = "merchantInfo.merchantId format does not match", groups = Format.class)
        private String merchantId;

        /** 可选子商户资料，用于平台商户的下级经营主体展示和风控。 */
        private ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO subMerchantInfo;
    }

    /**
     * Hosted Checkout 商户订单快照。
     */
    @Data
    public static class OrderInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商户订单号，1 至 64 位字母或数字，用于商户侧幂等关联。 */
        @NotBlank(message = "orderInfo.orderNo", groups = Create.class)
        @Pattern(regexp = "^$|^[A-Za-z0-9]{1,64}$", message = "orderInfo.orderNo format does not match", groups = Format.class)
        private String orderNo;

        /** 商户自定义订单标识，最长 64 个可打印字符。 */
        @NotBlank(message = "orderInfo.orderId", groups = Create.class)
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,64}$", message = "orderInfo.orderId format does not match", groups = Format.class)
        private String orderId;

        /** 订单金额，单位为对应币种主单位，必须大于零且禁止浮点数。 */
        @NotNull(message = "orderInfo.amount", groups = Create.class)
        @DecimalMin(value = "0.00", inclusive = false, message = "orderInfo.amount must be greater than 0", groups = Create.class)
        private BigDecimal amount;

        /** ISO 4217 三位大写币种代码。 */
        @NotBlank(message = "orderInfo.currency", groups = Create.class)
        @Pattern(regexp = "^[A-Z]{3}$", message = "orderInfo.currency format does not match", groups = Format.class)
        private String currency;

        /** 付款页展示的订单主题，可为空。 */
        private String subject;

        /** 付款页展示的订单说明，可为空且不得包含支付密钥。 */
        private String description;

        /** 可选商品明细快照；各项金额口径必须与订单币种一致。 */
        private List<OrderItemDTO> items;
    }

    /**
     * Hosted Checkout 商品明细快照。
     */
    @Data
    public static class OrderItemDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商品或服务名称。 */
        private String name;

        /** 商品数量，单位为件或商户定义的离散数量。 */
        private Integer quantity;

        /** 商品行金额，单位为 {@link #currency} 的主币种单位。 */
        private BigDecimal amount;

        /** 商品行金额的 ISO 4217 三位币种代码。 */
        private String currency;
    }

    /**
     * Hosted Checkout 展示和支付行为配置。
     */
    @Data
    public static class CheckoutInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 收银台语言区域标识，例如 {@code en-US}。 */
        private String locale;

        /** 会话有效分钟数；为空时使用服务端受控默认值。 */
        private Integer expireMinutes;

        /** 商户允许付款人选择的支付方式，不允许为空。 */
        @Valid
        @NotNull(message = "checkoutInfo.allowedPaymentMethods", groups = Create.class)
        private List<AllowedPaymentMethodDTO> allowedPaymentMethods;

        /** 支付失败后是否允许在同一会话内重试。 */
        private Boolean retryAllowed;

        /** 会话最大支付尝试次数，必须由服务端限制上限。 */
        private Integer maxAttemptCount;

        /** 支付完成后返回商户页面的 HTTP(S) 地址。 */
        @NotBlank(message = "checkoutInfo.returnUrl", groups = Create.class)
        @Pattern(regexp = "^https?://[^\\s]{1,256}$", message = "checkoutInfo.returnUrl format does not match", groups = Format.class)
        private String returnUrl;

        /** 付款人取消支付后的可选 HTTP(S) 返回地址。 */
        @Pattern(regexp = "^$|^https?://[^\\s]{1,256}$", message = "checkoutInfo.cancelUrl format does not match", groups = Format.class)
        private String cancelUrl;

        /** 商户异步通知地址；传入支付服务前仅保留哈希，原地址不得写普通日志。 */
        @Pattern(regexp = "^$|^https?://[^\\s]{1,256}$", message = "checkoutInfo.notifyUrl format does not match", groups = Format.class)
        private String notifyUrl;

        /** 商户指定的可选收银台 HTTP(S) 域名，必须通过服务端允许范围校验。 */
        @Pattern(regexp = "^$|^https?://[^\\s]{1,256}$", message = "checkoutInfo.checkoutDomain format does not match", groups = Format.class)
        private String checkoutDomain;
    }

    /**
     * 商户允许的单个 Hosted Checkout 支付方式。
     */
    @Data
    public static class AllowedPaymentMethodDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 平台支付方式编码，例如银行卡。 */
        @NotBlank(message = "checkoutInfo.allowedPaymentMethods.paymentMethod", groups = Create.class)
        private String paymentMethod;

        /** 可选指定渠道编码；为空时由支付服务路由。 */
        private String channelCode;

        /** 允许的卡品牌或支付品牌编码集合。 */
        private List<String> brands;

        /** 3DS 执行模式，例如自动、强制或关闭。 */
        private String threeDsMode;
    }

    /**
     * Hosted Checkout 付款人可选快照。
     */
    @Data
    public static class PayerInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 商户侧付款人标识，仅作为订单快照附加信息，不参与付款幂等键。
         */
        private String payerId;

        /**
         * 付款人邮箱，进入 service-payment 前会转换为掩码和摘要。
         */
        private String email;

        /** 付款人名字。 */
        private String firstName;

        /** 付款人姓氏。 */
        private String lastName;

        /** 付款人联系电话。 */
        private String phone;

        /**
         * 付款人国家/地区，用于收银台展示和后续通道路由辅助。
         */
        private String country;

        private String state;
        private String city;
        private String street;
        private String postal;
    }

    /** Hosted Checkout 账单地址预填快照。 */
    @Data
    public static class BillingInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String firstName;
        private String lastName;
        private String email;
        private String phone;
        private String country;
        private String state;
        private String city;
        private String street;
        private String postal;
    }
}
