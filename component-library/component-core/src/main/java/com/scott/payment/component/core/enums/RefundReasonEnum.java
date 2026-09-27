package com.scott.payment.component.core.enums;

import com.scott.payment.component.core.exception.ApiException;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundReasonEnum
 * @date : 2026-09-27 18:00
 * @email : scott_x@163.com
 * @description : 管理端和商户端共用的退款原因契约，校验原因编码和补充说明并生成现有审计字段；不改变支付核心或渠道协议。
 * @status : create
 */
public enum RefundReasonEnum {
    CUSTOMER_CANCELLED("客户取消订单", "Customer cancelled order"),
    DUPLICATE_PAYMENT("重复支付", "Duplicate payment"),
    PRODUCT_UNAVAILABLE("商品缺货或服务无法提供", "Product or service unavailable"),
    QUALITY_ISSUE("商品质量或服务质量问题", "Product or service quality issue"),
    NOT_AS_DESCRIBED("商品或服务与描述不符", "Product or service not as described"),
    PARTIAL_RETURN("部分商品退货", "Partial return of goods"),
    PARTIAL_CANCELLATION("部分商品取消或缺货", "Partial cancellation or stock shortage"),
    UNUSED_SERVICE("部分服务未使用或提前终止", "Unused service or early termination"),
    PRICE_ADJUSTMENT("优惠补退或价格差额退还", "Discount or price adjustment"),
    AGREED_COMPENSATION("协商补偿退款", "Agreed compensation"),
    OTHER("其他", "Other");

    private final String chineseLabel;
    private final String englishLabel;

    RefundReasonEnum(String chineseLabel, String englishLabel) {
        this.chineseLabel = chineseLabel;
        this.englishLabel = englishLabel;
    }

    /**
     * 校验控制台退款原因并生成审计文案。旧版客户端的非空文本保留原意；新版 OTHER 必须提供独立说明。
     * @param code 新版原因编码，旧版客户端可为空
     * @param description 补充退款说明，最多 200 个 UTF-16 字符
     * @param legacyReason 旧版客户端的完整审计文案
     * @param locale 当前请求语言
     * @return 可写入现有退款描述和审批原因字段的文案
     */
    public static String resolveRequestReason(String code, String description, String legacyReason, Locale locale) {
        boolean chinese = locale != null && "zh".equals(locale.getLanguage());
        String notes = stripWhitespace(description);
        if (notes.length() > 200) {
            throw invalid(chinese, "补充退款说明不能超过 200 个字符", "Additional refund details must not exceed 200 characters");
        }
        if (code == null || code.isBlank()) {
            String legacy = stripWhitespace(legacyReason);
            if (legacy.isEmpty() || legacy.length() > 400) {
                throw invalid(chinese, "请选择退款原因", "Select a refund reason");
            }
            if (legacy.matches("(?i)(其他|other)\\s*(-\\s*)?")) {
                throw invalid(chinese, "选择其他时，请填写具体退款原因", "Provide refund details when selecting Other");
            }
            return legacy;
        }
        RefundReasonEnum reason;
        try {
            reason = valueOf(code.strip());
        } catch (IllegalArgumentException exception) {
            throw invalid(chinese, "退款原因无效，请重新选择", "Invalid refund reason; please select again");
        }
        if (reason == OTHER && notes.isBlank()) {
            throw invalid(chinese, "选择其他时，请填写具体退款原因", "Provide refund details when selecting Other");
        }
        String label = chinese ? reason.chineseLabel : reason.englishLabel;
        return notes.isEmpty() ? label : label + " - " + notes;
    }

    /** 与浏览器 trim 保持一致，拒绝仅含不换行空格或 Unicode 空白的说明。 */
    private static String stripWhitespace(String value) {
        return value == null ? "" : value.replaceAll("(?U)^[\\s\\x{FEFF}]+|[\\s\\x{FEFF}]+$", "");
    }

    private static ApiException invalid(boolean chinese, String zh, String en) {
        return new ApiException(ApiResultEnum.PARAM_INVALID.getCode(), chinese ? zh : en);
    }
}
