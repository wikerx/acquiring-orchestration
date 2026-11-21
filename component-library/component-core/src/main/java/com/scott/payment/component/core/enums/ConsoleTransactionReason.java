package com.scott.payment.component.core.enums;

import com.scott.payment.component.core.exception.ApiException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** 控制台操作原因目录；仅供两端专用入口使用，不用于商户公开 API 校验。 */
public enum ConsoleTransactionReason {
    /** 已发货后请款或完成，保留履约依据。 */
    GOODS_SHIPPED("商品已发货", "Goods shipped", "CAPTURE"),
    /** 商品或服务全部履约后确认本次收款。 */
    SERVICE_COMPLETED("商品或服务已完成", "Goods or services completed", "CAPTURE"),
    /** 商户主动确认，适用于请款、完成及撤销。 */
    MERCHANT_CONFIRMED("商户确认", "Merchant confirmed", "CAPTURE", "VOID"),
    /** 仅对已履约部分请款，不提高剩余额度上限。 */
    PARTIAL_FULFILLMENT("部分商品或服务已履约", "Partial fulfillment", "CAPTURE"),
    /** 订单履约结束后确认本次请款。 */
    ORDER_COMPLETED("订单已完成", "Order completed", "CAPTURE"),
    /** 最终消费金额已经确认后请款或完成。 */
    ACTUAL_AMOUNT_CONFIRMED("实际消费金额已确认", "Actual spending confirmed", "CAPTURE"),
    /** 客户已确认本次收款金额及履约。 */
    CUSTOMER_CONFIRMED_SETTLEMENT("客户确认本次结算", "Customer confirmed settlement", "CAPTURE"),
    /** 客户取消订单，按原授权规则撤销。 */
    CUSTOMER_CANCELLED("客户取消订单", "Customer cancelled order", "VOID"),
    /** 授权接近有效期终点时主动撤销。 */
    AUTHORIZATION_EXPIRED("授权即将过期", "Authorization expiring", "VOID"),
    /** 订单关闭且不再继续履约时撤销。 */
    ORDER_CLOSED("订单关闭或不再继续", "Order closed", "VOID"),
    /** 商品缺货或服务不能提供时撤销。 */
    PRODUCT_UNAVAILABLE("商品缺货或服务无法提供", "Product or service unavailable", "VOID"),
    /** 识别出重复授权后撤销对应原授权。 */
    DUPLICATE_AUTHORIZATION("重复授权", "Duplicate authorization", "VOID"),
    /** 金额有误时撤销原授权，再按业务流程重新授权。 */
    INCORRECT_AMOUNT("交易金额有误，需重新授权", "Incorrect amount; reauthorization required", "VOID"),
    /** 客户更换支付方式时撤销原授权。 */
    PAYMENT_METHOD_CHANGED("客户更换支付方式", "Customer changed payment method", "VOID"),
    /** 风险或异常订单处置时撤销原授权。 */
    RISK_REVIEW("风险或异常订单处理", "Risk or exceptional order handling", "VOID"),
    /** 未列出的业务原因；必须同时填写有效补充说明。 */
    OTHER("其他", "Other", "CAPTURE", "VOID");

    private final String chinese;
    private final String english;
    private final Set<String> groups;

    ConsoleTransactionReason(String chinese, String english, String... groups) {
        this.chinese = chinese;
        this.english = english;
        this.groups = Set.of(groups);
    }

    /** 新版传稳定编码，兼容旧控制台已知选项文本；OTHER 始终要求独立说明。 */
    public static String resolve(String group, String code, String description, String legacy, Locale locale) {
        String notes = description == null ? "" : description.strip();
        boolean zh = locale != null && "zh".equals(locale.getLanguage());
        if (notes.length() > 200) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, zh ? "操作说明最多200字" : "Details must not exceed 200 characters");
        }
        String value = code == null || code.isBlank() ? legacy : code.strip();
        ConsoleTransactionReason reason = Arrays.stream(values()).filter(item -> item.groups.contains(group))
                .filter(item -> item.name().equals(value) || item.chinese.equals(value) || item.english.equals(value))
                .findFirst().orElseThrow(() -> new ApiException(ApiResultEnum.PARAM_INVALID,
                        zh ? "请选择有效操作原因" : "Select a valid operation reason"));
        if (reason == OTHER && notes.isBlank()) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, zh ? "选择其他时必须填写说明" : "Details are required for Other");
        }
        return (zh ? reason.chinese : reason.english) + (notes.isBlank() ? "" : " - " + notes);
    }
}
