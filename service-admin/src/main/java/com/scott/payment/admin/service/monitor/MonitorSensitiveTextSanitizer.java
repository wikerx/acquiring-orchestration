package com.scott.payment.admin.service.monitor;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorSensitiveTextSanitizer
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 系统监控文本脱敏器，移除常见凭据、URL 敏感参数和支付卡号，并对面向管理端的摘要执行长度限制。
 * @status : create
 */
@Component
public class MonitorSensitiveTextSanitizer {

    /** 名称和值形式的凭据、密码和支付卡安全码匹配规则。 */
    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)(authorization|cookie|set-cookie|token|secret|password|passwd|pwd|api[-_]?key|access[-_]?key|private[-_]?key|cvv|cvc|security[-_]?code)"
                    + "\\s*[:=]\\s*([^,;\\s&]+)");
    /** URL 查询参数中的凭据、密码和支付卡安全码匹配规则。 */
    private static final Pattern URL_SECRET = Pattern.compile(
            "(?i)([?&](?:token|secret|password|passwd|pwd|api[-_]?key|access[-_]?key|cvv|cvc|security[-_]?code)=)[^&#\\s]*");
    /** 可能的 13 至 19 位支付卡号匹配规则，允许空格或短横线分隔。 */
    private static final Pattern PAN = Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");

    /**
     * 脱敏并截断监控摘要。
     *
     * @param value 原始监控文本；允许为空
     * @param maxLength 最大返回字符数，调用方必须传入正数
     * @return 脱敏后的摘要；原始值为空时返回 {@code null}
     */
    public String sanitize(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String scrubbed = NAMED_SECRET.matcher(value.trim()).replaceAll("$1=[REDACTED]");
        scrubbed = URL_SECRET.matcher(scrubbed).replaceAll("$1[REDACTED]");
        scrubbed = PAN.matcher(scrubbed).replaceAll("[REDACTED_PAN]");
        return scrubbed.length() <= maxLength ? scrubbed : scrubbed.substring(0, maxLength);
    }
}
