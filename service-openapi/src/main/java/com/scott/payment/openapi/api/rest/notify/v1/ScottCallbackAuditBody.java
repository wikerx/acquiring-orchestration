package com.scott.payment.openapi.api.rest.notify.v1;

import com.alibaba.fastjson2.JSONObject;
import com.scott.payment.component.core.json.JsonUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Scott 已验签投递正文，只留存明确允许的协议字段。 */
final class ScottCallbackAuditBody {

    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_:-]{1,128}");
    private static final Set<String> CODE_FIELDS = Set.of(
            "eventId", "eventType", "businessType", "paymentScene", "paymentMethod",
            "merchantOrderNo", "transactionId", "merchantRefundNo", "refundId", "authorizationId",
            "operationType", "status", "authorizationStatus", "code", "responseCode", "currency");
    private static final Set<String> AMOUNT_FIELDS = Set.of(
            "amount", "authorizedAmount", "capturedAmount", "availableAmount");

    private ScottCallbackAuditBody() {
    }

    static String mask(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JSONObject raw = JsonUtils.parseObject(new String(body, StandardCharsets.UTF_8), JSONObject.class);
            if (raw == null) {
                return null;
            }
            Map<String, Object> masked = new LinkedHashMap<>();
            raw.forEach((field, value) -> {
                if (CODE_FIELDS.contains(field) && value instanceof String code && CODE.matcher(code).matches()) {
                    masked.put(field, code);
                }
                if (AMOUNT_FIELDS.contains(field) && value instanceof Number number) {
                    BigDecimal decimal = new BigDecimal(number.toString());
                    if (decimal.precision() <= 20 && Math.abs((long) decimal.scale()) <= 8) {
                        masked.put(field, decimal);
                    }
                }
            });
            return masked.isEmpty() ? null : JsonUtils.toJsonString(masked);
        } catch (RuntimeException exception) {
            // 入口协议验证已完成。无法安全生成脱敏副本时继续受理，并保留投递原文摘要。
            return null;
        }
    }
}
