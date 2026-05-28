package com.sinopay.payment.openapi.dto.body;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

import javax.validation.Valid;
import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiMerchantCardOrganizationRequestDTO
 * @date : 2026-05-28 16:22
 * @email : scott_x@163.com
 * @description : 开放接口卡交易统一请求参数
 * @status : create
 */
@Data
@NoArgsConstructor
public class ApiMerchantCardOrganizationRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    public interface Authorization {
    }

    public interface PreAuthorization {
    }

    public interface Capture {
    }

    public interface Refund {
    }

    public interface AuthorizationCancel {
    }

    public interface Reversal {
    }

    public interface Format {
    }

    @Valid
    @NotNull(message = "merchantInfo", groups = {Authorization.class, PreAuthorization.class, Capture.class, Refund.class, AuthorizationCancel.class, Reversal.class})
    private MerchantInfoDTO merchantInfo;

    @Valid
    @NotNull(message = "orderInfo", groups = {Authorization.class, PreAuthorization.class, Capture.class, Refund.class})
    private OrderInfoDTO orderInfo;

    @Valid
    private ThreeDsInfoDTO threeDsInfo;

    @Valid
    @NotNull(message = "billingCardHolderInfo", groups = {Authorization.class})
    private BillingCardHolderInfoDTO billingCardHolderInfo;

    @Valid
    @NotNull(message = "cardInfo", groups = {Authorization.class})
    private CardInfoDTO cardInfo;

    @Valid
    @NotNull(message = "transactionInfo", groups = {Refund.class, AuthorizationCancel.class, Reversal.class})
    private TransactionInfoDTO transactionInfo;

    @Data
    @NoArgsConstructor
    public static class MerchantInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "merchantInfo.merchantId", groups = {Authorization.class, PreAuthorization.class, Capture.class, Refund.class, AuthorizationCancel.class, Reversal.class})
        @Pattern(regexp = "^[2-9]\\d{5,16}$", message = "merchantInfo.merchantId format does not match", groups = {Format.class})
        private String merchantId;

        @Valid
        @NotNull(message = "merchantInfo.subMerchantInfo", groups = {Authorization.class})
        private SubMerchantInfoDTO subMerchantInfo;
    }

    @Data
    @NoArgsConstructor
    public static class SubMerchantInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,35}$", message = "merchantInfo.subMerchantInfo.subName format does not match", groups = {Format.class})
        private String subName;
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,35}$", message = "merchantInfo.subMerchantInfo.subCompanyName format does not match", groups = {Format.class})
        private String subCompanyName;
        @NotBlank(message = "merchantInfo.subMerchantInfo.subId", groups = {Authorization.class})
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,15}$", message = "merchantInfo.subMerchantInfo.subId format does not match", groups = {Format.class})
        private String subId;
        @NotBlank(message = "merchantInfo.subMerchantInfo.subStreet", groups = {Authorization.class})
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,128}$", message = "merchantInfo.subMerchantInfo.subStreet format does not match", groups = {Format.class})
        private String subStreet;
        @NotBlank(message = "merchantInfo.subMerchantInfo.subCity", groups = {Authorization.class})
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,64}$", message = "merchantInfo.subMerchantInfo.subCity format does not match", groups = {Format.class})
        private String subCity;
        @Pattern(regexp = "^$|^[a-zA-Z0-9]{1,3}$", message = "merchantInfo.subMerchantInfo.subState format does not match", groups = {Format.class})
        private String subState;
        @NotBlank(message = "merchantInfo.subMerchantInfo.subCountryCode", groups = {Authorization.class})
        @Pattern(regexp = "^[A-Z]{3}$", message = "merchantInfo.subMerchantInfo.subCountryCode format does not match", groups = {Format.class})
        private String subCountryCode;
        @Pattern(regexp = "^$|^(?=.{1,64}$)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "merchantInfo.subMerchantInfo.subEmail format does not match", groups = {Format.class})
        private String subEmail;
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,32}$", message = "merchantInfo.subMerchantInfo.subPhone format does not match", groups = {Format.class})
        private String subPhone;
        @NotBlank(message = "merchantInfo.subMerchantInfo.merchantCategory", groups = {Authorization.class})
        @Pattern(regexp = "^\\d{4}$", message = "merchantInfo.subMerchantInfo.merchantCategory format does not match", groups = {Format.class})
        private String merchantCategory;

        @AssertTrue(message = "Must fill in one of merchantInfo.subMerchantInfo.subName or merchantInfo.subMerchantInfo.subCompanyName", groups = {Authorization.class})
        public boolean isSubNameOrCompanyNameValid() {
            return hasText(subName) || hasText(subCompanyName);
        }
    }

    @Data
    @NoArgsConstructor
    public static class OrderInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotNull(message = "orderInfo.amount", groups = {Authorization.class, PreAuthorization.class, Capture.class, Refund.class})
        @Digits(integer = 12, fraction = 3, message = "orderInfo.amount format does not match", groups = {Format.class})
        private BigDecimal amount;
        @NotBlank(message = "orderInfo.currency", groups = {Authorization.class, PreAuthorization.class, Capture.class, Refund.class})
        @Pattern(regexp = "^[A-Z]{3}$", message = "orderInfo.currency format does not match", groups = {Format.class})
        private String currency;
        @NotBlank(message = "orderInfo.tradeNo", groups = {Authorization.class, PreAuthorization.class, Capture.class, Refund.class, AuthorizationCancel.class, Reversal.class})
        @Pattern(regexp = "^[A-Za-z0-9]{1,64}$", message = "orderInfo.tradeNo format does not match", groups = {Format.class})
        private String tradeNo;
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,32}$", message = "orderInfo.sourceReference format does not match", groups = {Format.class})
        private String sourceReference;
    }

    @Data
    @NoArgsConstructor
    public static class BillingCardHolderInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "billingCardHolderInfo.firstName", groups = {Authorization.class})
        @Pattern(regexp = "^.{1,32}$", message = "billingCardHolderInfo.firstName format does not match", groups = {Format.class})
        private String firstName;
        @NotBlank(message = "billingCardHolderInfo.lastName", groups = {Authorization.class})
        @Pattern(regexp = "^.{1,32}$", message = "billingCardHolderInfo.lastName format does not match", groups = {Format.class})
        private String lastName;
        @NotBlank(message = "billingCardHolderInfo.phone", groups = {Authorization.class})
        @Pattern(regexp = "^.{1,32}$", message = "billingCardHolderInfo.phone format does not match", groups = {Format.class})
        private String phone;
        @NotBlank(message = "billingCardHolderInfo.email", groups = {Authorization.class})
        @Pattern(regexp = "^(?=.{1,64}$)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "billingCardHolderInfo.email format does not match", groups = {Format.class})
        private String email;
        @NotBlank(message = "billingCardHolderInfo.country", groups = {Authorization.class})
        @Pattern(regexp = "^[A-Z]{3}$", message = "billingCardHolderInfo.country format does not match", groups = {Format.class})
        private String country;
        @Pattern(regexp = "^$|^.{2,3}$", message = "billingCardHolderInfo.state format does not match", groups = {Format.class})
        private String state;
        @NotBlank(message = "billingCardHolderInfo.city", groups = {Authorization.class})
        @Pattern(regexp = "^.{1,64}$", message = "billingCardHolderInfo.city format does not match", groups = {Format.class})
        private String city;
        @NotBlank(message = "billingCardHolderInfo.street", groups = {Authorization.class})
        @Pattern(regexp = "^.{1,128}$", message = "billingCardHolderInfo.street format does not match", groups = {Format.class})
        private String street;
        @NotBlank(message = "billingCardHolderInfo.postal", groups = {Authorization.class})
        @Pattern(regexp = "^.{1,32}$", message = "billingCardHolderInfo.postal format does not match", groups = {Format.class})
        private String postal;

        @AssertTrue(message = "The total length of billingCardHolderInfo.firstName and billingCardHolderInfo.lastName cannot exceed 64 characters", groups = {Authorization.class})
        public boolean isFirstNameAndLastNameValid() {
            return length(firstName) + length(lastName) <= 64;
        }
    }

    @Data
    @NoArgsConstructor
    public static class CardInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "cardInfo.cardNo", groups = {Authorization.class})
        @Pattern(regexp = "^\\d{11,19}$", message = "cardInfo.cardNo format does not match", groups = {Format.class})
        private String cardNo;
        @NotBlank(message = "cardInfo.expirationMonth", groups = {Authorization.class})
        @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "cardInfo.expirationMonth format does not match", groups = {Format.class})
        private String expirationMonth;
        @NotBlank(message = "cardInfo.expirationYear", groups = {Authorization.class})
        @Pattern(regexp = "^\\d{4}$", message = "cardInfo.expirationYear format does not match", groups = {Format.class})
        private String expirationYear;
        @NotBlank(message = "cardInfo.securityCode", groups = {Authorization.class})
        @Pattern(regexp = "^\\d{3,4}$", message = "cardInfo.securityCode format does not match", groups = {Format.class})
        private String securityCode;
    }

    @Data
    @NoArgsConstructor
    public static class ThreeDsInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @Pattern(regexp = "^$|^\\d{3}$", message = "threeDsInfo.eci format does not match", groups = {Format.class})
        private String eci;
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{28}$", message = "threeDsInfo.cavv format does not match", groups = {Format.class})
        private String cavv;
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{36}$", message = "threeDsInfo.dsTransactionId format does not match", groups = {Format.class})
        private String dsTransactionId;
        @Pattern(regexp = "^$|^2\\.[1-9]\\.[0-9]$", message = "threeDsInfo.threeDsVersion format does not match", groups = {Format.class})
        private String threeDsVersion;
    }

    @Data
    @NoArgsConstructor
    public static class TransactionInfoDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        @NotBlank(message = "transactionInfo.transactionId", groups = {Refund.class, AuthorizationCancel.class, Reversal.class})
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,64}$", message = "transactionInfo.transactionId format does not match", groups = {Format.class})
        private String transactionId;
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,64}$", message = "transactionInfo.sourceTransactionId format does not match", groups = {Format.class})
        private String sourceTransactionId;
        @Pattern(regexp = "^$|^.{1,128}$", message = "transactionInfo.description format does not match", groups = {Format.class})
        private String description;
        @Pattern(regexp = "^$|^(https?):\\/\\/[^\\s]{1,256}$", message = "transactionInfo.callbackUrl format does not match", groups = {Format.class})
        private String callbackUrl;
        @Pattern(regexp = "^$|^(VISA|MASTERCARD|AMEX|JCB|DISCOVER|UNIONPAY)$", message = "transactionInfo.cardBrand format does not match", groups = {Format.class})
        private String cardBrand;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }
}
