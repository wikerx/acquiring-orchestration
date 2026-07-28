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

    @Valid
    @NotNull(message = "merchantInfo", groups = Create.class)
    private MerchantInfoDTO merchantInfo;

    @Valid
    @NotNull(message = "orderInfo", groups = Create.class)
    private OrderInfoDTO orderInfo;

    @Valid
    @NotNull(message = "checkoutInfo", groups = Create.class)
    private CheckoutInfoDTO checkoutInfo;

    @Valid
    private PayerInfoDTO payerInfo;

    @Data
    public static class MerchantInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "merchantInfo.merchantId", groups = Create.class)
        @Pattern(regexp = "^[2-9]\\d{5,16}$", message = "merchantInfo.merchantId format does not match", groups = Format.class)
        private String merchantId;

        private ApiMerchantPaymentRequestDTO.SubMerchantInfoDTO subMerchantInfo;
    }

    @Data
    public static class OrderInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "orderInfo.orderNo", groups = Create.class)
        @Pattern(regexp = "^$|^[A-Za-z0-9]{1,64}$", message = "orderInfo.orderNo format does not match", groups = Format.class)
        private String orderNo;

        @NotBlank(message = "orderInfo.orderId", groups = Create.class)
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,64}$", message = "orderInfo.orderId format does not match", groups = Format.class)
        private String orderId;

        @NotNull(message = "orderInfo.amount", groups = Create.class)
        @DecimalMin(value = "0.00", inclusive = false, message = "orderInfo.amount must be greater than 0", groups = Create.class)
        private BigDecimal amount;

        @NotBlank(message = "orderInfo.currency", groups = Create.class)
        @Pattern(regexp = "^[A-Z]{3}$", message = "orderInfo.currency format does not match", groups = Format.class)
        private String currency;

        private String subject;
        private String description;
        private List<OrderItemDTO> items;
    }

    @Data
    public static class OrderItemDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String name;
        private Integer quantity;
        private BigDecimal amount;
        private String currency;
    }

    @Data
    public static class CheckoutInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        private String locale;
        private Integer expireMinutes;

        @Valid
        @NotNull(message = "checkoutInfo.allowedPaymentMethods", groups = Create.class)
        private List<AllowedPaymentMethodDTO> allowedPaymentMethods;

        private Boolean retryAllowed;
        private Integer maxAttemptCount;

        @NotBlank(message = "checkoutInfo.returnUrl", groups = Create.class)
        @Pattern(regexp = "^https?://[^\\s]{1,256}$", message = "checkoutInfo.returnUrl format does not match", groups = Format.class)
        private String returnUrl;

        @Pattern(regexp = "^$|^https?://[^\\s]{1,256}$", message = "checkoutInfo.cancelUrl format does not match", groups = Format.class)
        private String cancelUrl;

        @Pattern(regexp = "^$|^https?://[^\\s]{1,256}$", message = "checkoutInfo.notifyUrl format does not match", groups = Format.class)
        private String notifyUrl;

        @Pattern(regexp = "^$|^https?://[^\\s]{1,256}$", message = "checkoutInfo.checkoutDomain format does not match", groups = Format.class)
        private String checkoutDomain;
    }

    @Data
    public static class AllowedPaymentMethodDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "checkoutInfo.allowedPaymentMethods.paymentMethod", groups = Create.class)
        private String paymentMethod;

        private String channelCode;
        private List<String> brands;
        private String threeDsMode;
    }

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

        /**
         * 付款人国家/地区，用于收银台展示和后续通道路由辅助。
         */
        private String country;
    }
}
