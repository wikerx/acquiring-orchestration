package com.scott.payment.openapi.dto.body;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiMerchantPaymentRequestDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 收单交易统一请求 DTO，承载授权、预授权、请款、退款、撤销和冲正的外部入参校验。
 * @status : create
 */
@Data
@NoArgsConstructor
public class ApiMerchantPaymentRequestDTO implements Serializable {

    /**
     * 序列化版本号，用于保证统一卡交易请求对象在接口、日志、消息等链路中的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 一步支付交易校验分组，适用于商户希望渠道一次完成授权和请款的请求。
     */
    public interface Payment {
    }

    /**
     * 授权交易校验分组，适用于首次授权或后续请款前的额度确认请求。
     */
    public interface Authorization {
    }

    /**
     * 预授权交易校验分组，适用于先冻结额度、后续再完成请款的请求。
     */
    public interface PreAuthorization {
    }

    /**
     * 增量授权校验分组，适用于对同一原始交易生命周期追加授权额度。
     */
    public interface IncrementalAuthorization {
    }

    /**
     * 请款交易校验分组，适用于对授权或预授权订单执行资金捕获。
     */
    public interface Capture {
    }

    /**
     * 退款交易校验分组，适用于对成功交易发起全额或部分退款。
     */
    public interface Refund {
    }

    /**
     * 授权撤销校验分组，适用于撤销未完成清算的授权交易。
     */
    public interface AuthorizationCancel {
    }

    /**
     * 查询交易校验分组，适用于按商户订单号或平台交易号查询交易状态。
     */
    public interface Query {
    }

    /**
     * 冲正交易校验分组，适用于异常交易的反向更正。
     */
    public interface Reversal {
    }

    /**
     * 格式校验分组，统一执行字段长度、正则和枚举值合法性校验。
     */
    public interface Format {
    }

    /**
     * 商户信息，包含支付平台商户号以及子商户信息，所有收单类交易都需要用它定位商户配置。
     */
    @Valid
    @NotNull(message = "merchantInfo", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, Refund.class, AuthorizationCancel.class, Query.class, Reversal.class})
    private MerchantInfoDTO merchantInfo;

    /**
     * 订单信息，包含金额、币种、商户订单号和商户本次请求唯一标识。
     */
    @Valid
    @NotNull(message = "orderInfo", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, Refund.class, AuthorizationCancel.class, Query.class, Reversal.class})
    private OrderInfoDTO orderInfo;

    /**
     * 3D Secure 认证信息，商户使用 3DS 交易时传入，用于渠道风控和责任转移判断。
     * <p>
     * API 文档使用字段名 threeDSInfo，这里保留 threeDsInfo Java 命名并通过 JSONField 兼容商户报文。
     */
    @Valid
    @JSONField(name = "threeDSInfo", alternateNames = {"threeDsInfo"})
    private ThreeDsInfoDTO threeDsInfo;

    /**
     * 持卡人账单信息，用于卡组织风控、AVS 校验和交易补充数据。
     */
    @Valid
    @NotNull(message = "billingCardHolderInfo", groups = {Payment.class, Authorization.class, PreAuthorization.class})
    private BillingCardHolderInfoDTO billingCardHolderInfo;

    /**
     * 卡信息，包含 PAN、有效期和安全码，是授权交易的核心敏感数据，日志中必须脱敏。
     */
    @Valid
    @NotNull(message = "cardInfo", groups = {Payment.class, Authorization.class, PreAuthorization.class})
    private CardInfoDTO cardInfo;

    /**
     * 平台交易信息。首次类交易不要求商户传入；后续动作通过 sourceTransactionId 定位原平台交易。
     */
    @Valid
    @NotNull(message = "transactionInfo", groups = {IncrementalAuthorization.class, Capture.class, Refund.class, AuthorizationCancel.class, Query.class, Reversal.class})
    private TransactionInfoDTO transactionInfo;

    @Data
    @NoArgsConstructor
    public static class MerchantInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证商户信息对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 支付平台颁发的商户号，必须与 JWT 中的 merchantId 保持一致，避免商户冒用其他商户配置。
         */
        @NotBlank(message = "merchantInfo.merchantId", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, Refund.class, AuthorizationCancel.class, Query.class, Reversal.class})
        @Pattern(regexp = "^[2-9]\\d{5,16}$", message = "merchantInfo.merchantId format does not match", groups = {Format.class})
        private String merchantId;

        /**
         * 子商户信息，代表实际收款或履约主体，授权交易必传。
         */
        @Valid
        @NotNull(message = "merchantInfo.subMerchantInfo", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        private SubMerchantInfoDTO subMerchantInfo;
    }

    @Data
    @NoArgsConstructor
    public static class SubMerchantInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证子商户对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 子商户个人名称，个人商户可传；与 subCompanyName 至少填写一个。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,35}$", message = "merchantInfo.subMerchantInfo.subName format does not match", groups = {Format.class})
        private String subName;

        /**
         * 子商户公司名称，企业商户可传；与 subName 至少填写一个。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,35}$", message = "merchantInfo.subMerchantInfo.subCompanyName format does not match", groups = {Format.class})
        private String subCompanyName;

        /**
         * 商户侧子商户编号，用于聚合平台或平台代理模式下识别实际经营主体。
         */
        @NotBlank(message = "merchantInfo.subMerchantInfo.subId", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,15}$", message = "merchantInfo.subMerchantInfo.subId format does not match", groups = {Format.class})
        private String subId;

        /**
         * 子商户街道地址，用于卡组织和渠道侧商户补充信息。
         */
        @NotBlank(message = "merchantInfo.subMerchantInfo.subStreet", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,128}$", message = "merchantInfo.subMerchantInfo.subStreet format does not match", groups = {Format.class})
        private String subStreet;

        /**
         * 子商户城市，建议传英文或渠道要求的标准城市名称。
         */
        @NotBlank(message = "merchantInfo.subMerchantInfo.subCity", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,64}$", message = "merchantInfo.subMerchantInfo.subCity format does not match", groups = {Format.class})
        private String subCity;

        /**
         * 子商户州/省代码，美国、加拿大等国家建议使用标准州代码。
         */
        @Pattern(regexp = "^$|^[a-zA-Z0-9]{1,3}$", message = "merchantInfo.subMerchantInfo.subState format does not match", groups = {Format.class})
        private String subState;

        /**
         * 子商户国家三字码，使用 ISO 3166-1 alpha-3 格式，例如 USA、CAN、GBR。
         */
        @NotBlank(message = "merchantInfo.subMerchantInfo.subCountryCode", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^[A-Z]{3}$", message = "merchantInfo.subMerchantInfo.subCountryCode format does not match", groups = {Format.class})
        private String subCountryCode;

        /**
         * 子商户邮箱，用于渠道风控和商户资料补充，可为空。
         */
        @Pattern(regexp = "^$|^(?=.{1,64}$)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "merchantInfo.subMerchantInfo.subEmail format does not match", groups = {Format.class})
        private String subEmail;

        /**
         * 子商户联系电话，用于渠道风控和商户资料补充，可为空。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,32}$", message = "merchantInfo.subMerchantInfo.subPhone format does not match", groups = {Format.class})
        private String subPhone;

        /**
         * 子商户邮编，卡组织和通道可能按国家、卡品牌执行不同长度规则，当前先按通用 ASCII 长度校验。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,32}$", message = "merchantInfo.subMerchantInfo.subPostal format does not match", groups = {Format.class})
        private String subPostal;

        /**
         * 子商户税号，供部分国家、区域或卡组织扩展风控使用。
         */
        @Pattern(regexp = "^$|^(?:[^\\u4e00-\\u9fa5·]{1,32})$", message = "merchantInfo.subMerchantInfo.subTaxId format does not match", groups = {Format.class})
        private String subTaxId;

        /**
         * 子商户 MCC 行业类别码，四位数字，用于卡组织行业识别和费率规则。
         */
        @NotBlank(message = "merchantInfo.subMerchantInfo.merchantCategory", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^\\d{4}$", message = "merchantInfo.subMerchantInfo.merchantCategory format does not match", groups = {Format.class})
        private String merchantCategory;

        /**
         * Intes 代码，当前主要服务 Diners 等卡组扩展参数，非相关卡组可不传。
         */
        @Pattern(regexp = "^$|^[a-zA-Z0-9]{3,4}$", message = "merchantInfo.subMerchantInfo.intesCode format does not match", groups = {Format.class})
        private String intesCode;

        /**
         * 费用类型，Diners 等卡组在请款或后续扩展交易中可能要求传入。
         */
        @Pattern(regexp = "^$|^[a-zA-Z0-9]{3}$", message = "merchantInfo.subMerchantInfo.chargeType format does not match", groups = {Format.class})
        private String chargeType;

        /**
         * 校验个人名称和公司名称二选一。
         * <p>
         * 卡组织规则要求至少存在一个真实经营主体名称，避免只传子商户号但缺少可识别主体信息。
         *
         * @return true 表示子商户名称信息满足接口要求
         */
        @JSONField(serialize = false)
        @AssertTrue(message = "Must fill in one of merchantInfo.subMerchantInfo.subName or merchantInfo.subMerchantInfo.subCompanyName", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        public boolean isSubNameOrCompanyNameValid() {
            return hasText(subName) || hasText(subCompanyName);
        }
    }

    @Data
    @NoArgsConstructor
    public static class OrderInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证订单信息对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 订单金额，主币种单位，最多 12 位整数和 3 位小数，进入核心后可转换为最小币种单位。
         */
        @NotNull(message = "orderInfo.amount", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, Refund.class})
        @Digits(integer = 12, fraction = 3, message = "orderInfo.amount format does not match", groups = {Format.class})
        private BigDecimal amount;

        /**
         * 订单币种，使用 ISO 4217 三位大写币种代码，例如 USD、EUR、CNY。
         */
        @NotBlank(message = "orderInfo.currency", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, Refund.class})
        @Pattern(regexp = "^[A-Z]{3}$", message = "orderInfo.currency format does not match", groups = {Format.class})
        private String currency;

        /**
         * 商户订单号，由商户侧生成，是商户业务订单在平台侧的主要查询和对账字段。
         */
        @NotBlank(message = "orderInfo.orderNo", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, Refund.class, AuthorizationCancel.class, Query.class, Reversal.class})
        @Pattern(regexp = "^[A-Za-z0-9]{1,64}$", message = "orderInfo.orderNo format does not match", groups = {Format.class})
        private String orderNo;

        /**
         * 商户本次 API 请求唯一标识，由商户侧生成，用作创建、请款、退款、撤销、查询等 OpenAPI 幂等键。
         */
        @NotBlank(message = "orderInfo.orderId", groups = {Payment.class, Authorization.class, PreAuthorization.class, IncrementalAuthorization.class, Capture.class, Refund.class, AuthorizationCancel.class, Query.class, Reversal.class})
        @Pattern(regexp = "^[\\x21-\\x7E\\s]{1,64}$", message = "orderInfo.orderId format does not match", groups = {Format.class})
        private String orderId;
    }

    @Data
    @NoArgsConstructor
    public static class BillingCardHolderInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证账单持卡人对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 持卡人名，用于卡组织风控、AVS 和渠道资料补充。
         */
        @NotBlank(message = "billingCardHolderInfo.firstName", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^.{1,32}$", message = "billingCardHolderInfo.firstName format does not match", groups = {Format.class})
        private String firstName;

        /**
         * 持卡人姓，用于卡组织风控、AVS 和渠道资料补充。
         */
        @NotBlank(message = "billingCardHolderInfo.lastName", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^.{1,32}$", message = "billingCardHolderInfo.lastName format does not match", groups = {Format.class})
        private String lastName;

        /**
         * 持卡人联系电话，用于卡组织风控和渠道补充资料。
         */
        @NotBlank(message = "billingCardHolderInfo.phone", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^.{1,32}$", message = "billingCardHolderInfo.phone format does not match", groups = {Format.class})
        private String phone;

        /**
         * 持卡人邮箱，用于交易风险识别、通知或渠道补充资料。
         */
        @NotBlank(message = "billingCardHolderInfo.email", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^(?=.{1,64}$)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "billingCardHolderInfo.email format does not match", groups = {Format.class})
        private String email;

        /**
         * 持卡人账单国家三字码，使用 ISO 3166-1 alpha-3 格式。
         */
        @NotBlank(message = "billingCardHolderInfo.country", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^[A-Z]{3}$", message = "billingCardHolderInfo.country format does not match", groups = {Format.class})
        private String country;

        /**
         * 持卡人账单州/省代码，美国、加拿大等国家建议传标准州代码。
         */
        @Pattern(regexp = "^$|^.{2,3}$", message = "billingCardHolderInfo.state format does not match", groups = {Format.class})
        private String state;

        /**
         * 持卡人账单城市，建议传英文或渠道要求的标准城市名称。
         */
        @NotBlank(message = "billingCardHolderInfo.city", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^.{1,64}$", message = "billingCardHolderInfo.city format does not match", groups = {Format.class})
        private String city;

        /**
         * 持卡人账单街道地址，用于 AVS 地址校验和渠道风控。
         */
        @NotBlank(message = "billingCardHolderInfo.street", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^.{1,128}$", message = "billingCardHolderInfo.street format does not match", groups = {Format.class})
        private String street;

        /**
         * 持卡人账单邮编，用于 AVS 地址校验和渠道风控。
         */
        @NotBlank(message = "billingCardHolderInfo.postal", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^.{1,32}$", message = "billingCardHolderInfo.postal format does not match", groups = {Format.class})
        private String postal;

        /**
         * 校验持卡人姓名总长度，避免渠道侧因 firstName + lastName 超长拒绝交易。
         *
         * @return true 表示姓名总长度满足限制
         */
        @AssertTrue(message = "The total length of billingCardHolderInfo.firstName and billingCardHolderInfo.lastName cannot exceed 64 characters", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @JSONField(serialize = false)
        public boolean isFirstNameAndLastNameValid() {
            return length(firstName) + length(lastName) <= 64;
        }
    }

    @Data
    @NoArgsConstructor
    public static class CardInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证卡信息对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 持卡人 PAN 卡号，长度 11-19 位，只允许数字，日志和落库必须按 PCI 要求脱敏或加密。
         */
        @NotBlank(message = "cardInfo.cardNo", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^\\d{11,19}$", message = "cardInfo.cardNo format does not match", groups = {Format.class})
        private String cardNo;

        /**
         * 卡有效期月份，格式 MM，取值 01-12。
         */
        @NotBlank(message = "cardInfo.expirationMonth", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^(0[1-9]|1[0-2])$", message = "cardInfo.expirationMonth format does not match", groups = {Format.class})
        private String expirationMonth;

        /**
         * 卡有效期年份，格式 yyyy，例如 2028。
         */
        @NotBlank(message = "cardInfo.expirationYear", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^\\d{4}$", message = "cardInfo.expirationYear format does not match", groups = {Format.class})
        private String expirationYear;

        /**
         * 卡安全码 CVV/CVC，长度 3-4 位，只允许数字，严禁落库和明文日志输出。
         */
        @NotBlank(message = "cardInfo.securityCode", groups = {Payment.class, Authorization.class, PreAuthorization.class})
        @Pattern(regexp = "^\\d{3,4}$", message = "cardInfo.securityCode format does not match", groups = {Format.class})
        private String securityCode;
    }

    @Data
    @NoArgsConstructor
    public static class ThreeDsInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证 3DS 信息对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * ECI 电子商务指示符，用于说明 3DS 认证结果和交易责任归属。
         */
        @Pattern(regexp = "^$|^\\d{3}$", message = "threeDsInfo.eci format does not match", groups = {Format.class})
        private String eci;

        /**
         * CAVV 持卡人认证验证值，由 3DS 认证结果返回，用于渠道提交授权。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{28}$", message = "threeDsInfo.cavv format does not match", groups = {Format.class})
        private String cavv;

        /**
         * 3DS 交易唯一标识，由目录服务器或 3DS Server 返回，用于认证链路追踪。
         */
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{36}$", message = "threeDsInfo.dsTransactionId format does not match", groups = {Format.class})
        private String dsTransactionId;

        /**
         * 3DS 协议版本，例如 2.1.0、2.2.0，用于渠道判断支持能力。
         */
        @Pattern(regexp = "^$|^2\\.[1-9]\\.[0-9]$", message = "threeDsInfo.threeDsVersion format does not match", groups = {Format.class})
        private String threeDsVersion;
    }

    @Data
    @NoArgsConstructor
    public static class TransactionInfoDTO implements Serializable {

        /**
         * 序列化版本号，用于保证交易扩展信息对象在链路传递时的反序列化兼容性。
         */
        private static final long serialVersionUID = 1L;

        /**
         * 原平台交易 ID，后续请款、退款、撤销、冲正和查询场景用于定位原交易。
         */
        @NotBlank(message = "transactionInfo.sourceTransactionId", groups = {IncrementalAuthorization.class, Capture.class, Refund.class, AuthorizationCancel.class, Query.class, Reversal.class})
        @Pattern(regexp = "^$|^[\\x21-\\x7E\\s]{1,64}$", message = "transactionInfo.sourceTransactionId format does not match", groups = {Format.class})
        private String sourceTransactionId;

        /**
         * 交易描述或备注，用于商户侧订单说明、渠道补充信息和客服排查。
         */
        @Pattern(regexp = "^$|^.{1,128}$", message = "transactionInfo.description format does not match", groups = {Format.class})
        private String description;

        /**
         * 商户通知回调地址，交易状态变化后系统可按该地址推送异步通知。
         */
        @Pattern(regexp = "^$|^(https?):\\/\\/[^\\s]{1,256}$", message = "transactionInfo.callbackUrl format does not match", groups = {Format.class})
        private String callbackUrl;

        /**
         * 卡品牌，当前支持 VISA、MASTERCARD、AMEX、JCB、DISCOVER、UNIONPAY。
         */
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
