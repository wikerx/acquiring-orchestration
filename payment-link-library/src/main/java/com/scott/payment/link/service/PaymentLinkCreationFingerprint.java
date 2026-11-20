package com.scott.payment.link.service;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.link.entity.PaymentLinkDO;
import com.scott.payment.link.model.PaymentLinkManagementModels.Create;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 指纹版本只进入哈希输入；不扩列、不回填历史，也不延长已冻结的有效期。 */
public final class PaymentLinkCreationFingerprint {
    private PaymentLinkCreationFingerprint() { }

    /** 按创建契约版本对业务字段及规范化有效期生成摘要，供请求号重放核对。 */
    public static String canonical(Create input, BigDecimal amount) {
        var fields = fields(input, amount);
        fields.put("contract", "PAYMENT_LINK_CREATE_V2");
        fields.put("expiry", input.getExpireTime() == null
                ? Objects.requireNonNullElse(input.getExpireMinutes(), PaymentLinkExpiryPolicy.DEFAULT_MINUTES)
                : input.getExpireTime().truncatedTo(ChronoUnit.MILLIS).toString());
        return digest(fields);
    }

    /** 仅接受当前或可证明等价的旧摘要；同请求号不同业务参数拒绝重放。 */
    public static void requireMatch(PaymentLinkDO row, Create input, BigDecimal amount) {
        if (canonical(input, amount).equals(row.getRequestFingerprint())) return;
        // 旧绝对时刻只接受能够由原摘要证明的请求；无法还原的亚毫秒原值不能猜测为相同请求。
        if (input.getExpireTime() != null) {
            if (input.getExpireTime().truncatedTo(ChronoUnit.MILLIS).equals(row.getExpireTime())
                    && legacy(input, amount, input.getExpireTime().toString()).equals(row.getRequestFingerprint())) return;
        } else if (row.getCreateTime() != null && row.getExpireTime() != null) {
            int minutes = Objects.requireNonNullElse(input.getExpireMinutes(), PaymentLinkExpiryPolicy.DEFAULT_MINUTES);
            if (row.getCreateTime().plusMinutes(minutes).equals(row.getExpireTime())) {
                if (legacy(input, amount, minutes).equals(row.getRequestFingerprint())) return;
                if (minutes == PaymentLinkExpiryPolicy.DEFAULT_MINUTES
                        && legacy(input, amount, 1440).equals(row.getRequestFingerprint())) return;
            }
        }
        throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT);
    }

    private static String legacy(Create input, BigDecimal amount, Object expiry) {
        var fields = fields(input, amount); fields.put("expiry", expiry); return digest(fields);
    }
    private static TreeMap<String, Object> fields(Create input, BigDecimal amount) {
        var fields = new TreeMap<String, Object>();
        fields.put("order", blank(input.getMerchantOrderNo()));
        fields.put("name", input.getLinkName() == null ? "" : input.getLinkName().trim());
        fields.put("description", blank(input.getDescription()));
        fields.put("amount", amount.stripTrailingZeros().toPlainString()); fields.put("currency", input.getCurrency());
        fields.put("locale", input.getLocale());
        fields.put("brands", input.getCardBrands() == null ? List.of() : input.getCardBrands().stream().distinct().sorted().toList());
        fields.put("notify", blank(input.getNotifyUrl())); fields.put("redirect", blank(input.getRedirectUrl()));
        return fields;
    }
    private static String blank(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String digest(Map<String, Object> fields) { return PaymentLinkTokenService.hash(JsonUtils.toJsonString(fields)); }
}
