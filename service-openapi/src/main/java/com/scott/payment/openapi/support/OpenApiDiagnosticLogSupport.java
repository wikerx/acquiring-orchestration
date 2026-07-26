package com.scott.payment.openapi.support;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.openapi.dto.body.OpenApiEncryptedRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiDiagnosticLogSupport
 * @date : 2026-07-26 17:25
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 诊断日志支撑组件，位于 service-openapi 支撑层，统一生成请求头、请求密文、明文参数、响应明文和响应密文的脱敏摘要。
 * @status : create
 */
@Component
public class OpenApiDiagnosticLogSupport {

    /**
     * 日志摘要最大字符数，避免大报文刷屏。
     */
    private static final int MAX_SUMMARY_LENGTH = 1600;

    /**
     * 请求头摘要最大字符数，避免长 User-Agent 或代理头撑大单行日志。
     */
    private static final int MAX_HEADER_VALUE_LENGTH = 160;

    /**
     * Compact 密文期望段数，格式为 protectedHeader.encryptedKey.iv.cipherText.tag。
     */
    private static final int COMPACT_PART_COUNT = 5;

    /**
     * 生成 OpenAPI 请求头诊断摘要。
     * <p>
     * Authorization 只保留存在性、Bearer 前缀、长度和 SHA-256 短摘要；不会输出 JWT 原文。
     * 其它与商户排障相关的头只记录受控字段，避免 Cookie 或内部签名头泄漏。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 请求头诊断摘要 JSON
     */
    public String headerSummary(HttpServletRequest request) {
        if (request == null) {
            return "{}";
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        putIfPresent(summary, "traceId", TraceContext.getTraceId());
        putIfPresent(summary, "xTraceId", request.getHeader(TraceContext.TRACE_ID_HEADER));
        putIfPresent(summary, "xRequestId", request.getHeader("X-Request-Id"));
        putIfPresent(summary, "gatewayClientIp", request.getHeader(MerchantIpWhitelistAccessService.HEADER_GATEWAY_CLIENT_IP));
        putIfPresent(summary, "xForwardedFor", firstForwardedFor(request.getHeader("X-Forwarded-For")));
        putIfPresent(summary, "xRealIp", request.getHeader("X-Real-IP"));
        putIfPresent(summary, "origin", request.getHeader("Origin"));
        putIfPresent(summary, "referer", request.getHeader("Referer"));
        putIfPresent(summary, "userAgent", limit(request.getHeader("User-Agent"), MAX_HEADER_VALUE_LENGTH));
        putIfPresent(summary, "contentType", request.getContentType());
        putIfPresent(summary, "contentLength", request.getContentLengthLong());
        summary.put("authorization", authorizationSummary(request.getHeader("authorization")));
        summary.put("headerNames", headerNames(request));
        return truncate(JsonUtils.toJsonString(summary));
    }

    /**
     * 生成 JWT 声明诊断摘要。
     * <p>
     * 摘要只包含 merchantId、jti 摘要和签发/过期时间，便于定位商户生成 token 的时钟与防重放问题。
     * </p>
     * @param headerDTO 已验签的请求头上下文
     * @return JWT 摘要 JSON
     */
    public String jwtSummary(OpenApiRequestHeaderDTO headerDTO) {
        if (headerDTO == null) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        putIfPresent(summary, "merchantId", headerDTO.getMerchantId());
        putIfPresent(summary, "jtiDigest", digest16(headerDTO.getJwtId()));
        summary.put("issuedAt", headerDTO.getIssuedAt());
        summary.put("issuedAtIso", epochSecondIso(headerDTO.getIssuedAt()));
        summary.put("expiresAt", headerDTO.getExpiresAt());
        summary.put("expiresAtIso", epochSecondIso(headerDTO.getExpiresAt()));
        return JsonUtils.toJsonString(summary);
    }

    /**
     * 生成 OpenAPI 密文请求体诊断摘要。
     * <p>
     * 只记录 body/data 的长度、摘要、compact 段数和字段结构，不记录完整密文。
     * </p>
     * @param requestBody 商户提交的原始 HTTP body
     * @return 密文诊断摘要 JSON
     */
    public String cipherRequestSummary(String requestBody) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (!StringUtils.hasText(requestBody)) {
            summary.put("bodyPresent", false);
            return JsonUtils.toJsonString(summary);
        }
        String trimmed = requestBody.trim();
        String cipherText = extractCipherTextSafely(trimmed);
        summary.put("bodyPresent", true);
        summary.put("bodyLength", trimmed.length());
        summary.put("bodyDigest", digest16(trimmed));
        summary.put("bodyJson", trimmed.startsWith("{"));
        summary.put("dataPresent", StringUtils.hasText(cipherText));
        if (StringUtils.hasText(cipherText)) {
            summary.put("dataLength", cipherText.length());
            summary.put("dataDigest", digest16(cipherText));
            summary.put("dataMasked", maskCipher(cipherText));
            summary.put("compactPartCount", compactPartCount(cipherText));
            summary.put("compactPartCountValid", compactPartCount(cipherText) == COMPACT_PART_COUNT);
        }
        return JsonUtils.toJsonString(summary);
    }

    /**
     * 生成解密后的业务明文参数摘要。
     * <p>
     * 该摘要会输出商户排障所需业务字段，但卡号、CVV、CAVV、手机号、邮箱、token 和密钥统一脱敏。
     * </p>
     * @param data 解密后的业务 DTO
     * @return 脱敏后的明文参数摘要
     */
    public String plainRequestSummary(Object data) {
        return objectSummary(data);
    }

    /**
     * 生成响应明文 data 摘要。
     * <p>
     * 响应加密前调用，输出平台业务响应的脱敏业务字段，便于商户反馈时和密文响应摘要配对。
     * </p>
     * @param data 响应 data 对象
     * @return 脱敏响应明文摘要
     */
    public String plainResponseSummary(Object data) {
        return objectSummary(data);
    }

    /**
     * 生成响应密文 data 摘要。
     * <p>
     * 只记录响应 data 密文长度、摘要和首尾掩码，不输出完整密文。
     * </p>
     * @param encryptedData 加密后 data compact 密文
     * @return 响应密文诊断摘要 JSON
     */
    public String cipherResponseSummary(String encryptedData) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (!StringUtils.hasText(encryptedData)) {
            summary.put("dataPresent", false);
            return JsonUtils.toJsonString(summary);
        }
        summary.put("dataPresent", true);
        summary.put("dataLength", encryptedData.length());
        summary.put("dataDigest", digest16(encryptedData));
        summary.put("dataMasked", maskCipher(encryptedData));
        summary.put("compactPartCount", compactPartCount(encryptedData));
        return JsonUtils.toJsonString(summary);
    }

    /**
     * 生成平台错误响应摘要。
     * <p>
     * 错误响应不包含业务 data 时记录 code/message，帮助定位请求在哪个阶段被拦截。
     * </p>
     * @param body 控制器或异常处理器准备返回的对象
     * @return 脱敏错误响应摘要
     */
    public String responseEnvelopeSummary(Object body) {
        return objectSummary(body);
    }

    /**
     * 生成对象诊断摘要。
     * <p>
     * 对响应或业务对象先执行 JSON 序列化，再统一脱敏和截断，避免日志输出完整银行卡、手机号、邮箱或大报文。
     * </p>
     * @param value 待记录的业务对象
     * @return 脱敏后的 JSON 摘要，输入为空时返回 null
     */
    private String objectSummary(Object value) {
        if (value == null) {
            return null;
        }
        return truncate(SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(value)));
    }

    /**
     * 生成 Authorization 请求头摘要。
     * <p>
     * 仅记录是否存在、长度、摘要、Bearer 前缀和 JWT 段数，不输出 JWT 原文，便于定位商户鉴权格式问题。
     * </p>
     * @param authorization Authorization 请求头原文
     * @return 可安全打印的鉴权头摘要
     */
    private Map<String, Object> authorizationSummary(String authorization) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("present", StringUtils.hasText(authorization));
        if (!StringUtils.hasText(authorization)) {
            return summary;
        }
        String trimmed = authorization.trim();
        summary.put("length", trimmed.length());
        summary.put("digest", digest16(trimmed));
        summary.put("bearerPrefix", trimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length()));
        int partCount = trimmed.replaceFirst("(?i)^Bearer\\s+", "").split("\\.").length;
        summary.put("jwtPartCount", partCount);
        return summary;
    }

    /**
     * 提取可打印的 HTTP 请求头名称。
     * <p>
     * 只记录头名称，不记录头值，并过滤 Authorization、Cookie 等敏感头，满足商户接入排查的最小必要信息。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 已排序的非敏感请求头名称列表
     */
    private Object headerNames(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return Collections.emptyList();
        }
        return Collections.list(names).stream()
                .filter(name -> !isSensitiveHeader(name))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    /**
     * 判断 HTTP 请求头是否属于敏感头。
     * <p>
     * 敏感头不进入日志字段集合，避免令牌、Cookie 或会话凭证在排查日志中泄露。
     * </p>
     * @param name 请求头名称
     * @return true 表示该头不允许进入日志
     */
    private boolean isSensitiveHeader(String name) {
        return name != null && ("authorization".equalsIgnoreCase(name)
                || "cookie".equalsIgnoreCase(name)
                || "set-cookie".equalsIgnoreCase(name));
    }

    /**
     * 从商户加密请求体中安全提取 data 密文。
     * <p>
     * 支持标准加密包和兼容 Map 结构；解析失败时返回 null，不抛出异常影响正常安全链路。
     * </p>
     * @param requestBody 商户原始请求体
     * @return data 密文或原始非 JSON 请求摘要输入
     */
    private String extractCipherTextSafely(String requestBody) {
        if (!requestBody.startsWith("{")) {
            return requestBody;
        }
        try {
            OpenApiEncryptedRequestDTO requestDTO = JsonUtils.parseObject(requestBody, OpenApiEncryptedRequestDTO.class);
            if (requestDTO != null && StringUtils.hasText(requestDTO.getData())) {
                return requestDTO.getData().trim();
            }
            Map<String, Object> body = JsonUtils.parseObject(requestBody, new TypeReference<>() {
            });
            Object data = body == null ? null : body.get("data");
            return data == null ? null : String.valueOf(data).trim();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 提取代理链中的首个来源地址。
     * <p>
     * 仅用于日志诊断，不替代网关或安全组件的可信代理校验。
     * </p>
     * @param forwardedFor X-Forwarded-For 请求头值
     * @return 首个来源地址，不存在时返回 null
     */
    private String firstForwardedFor(String forwardedFor) {
        if (!StringUtils.hasText(forwardedFor)) {
            return null;
        }
        return forwardedFor.split(",")[0].trim();
    }

    /**
     * 统计 compact 编码密文的段数。
     * <p>
     * 用于判断商户传入 JWT/JWE 或网关密文格式是否符合预期，不记录任意段内容。
     * </p>
     * @param value compact 编码字符串
     * @return 以点号分隔后的段数，输入为空时返回 0
     */
    private int compactPartCount(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return value.split("\\.", -1).length;
    }

    /**
     * 掩码展示密文首尾片段。
     * <p>
     * 仅保留少量首尾字符用于关联同一次接入报文，不暴露完整密文或可复原载荷。
     * </p>
     * @param value 密文原文
     * @return 可安全打印的密文掩码
     */
    private String maskCipher(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= 16) {
            return "***";
        }
        return normalized.substring(0, 8) + "***" + normalized.substring(normalized.length() - 8);
    }

    /**
     * 计算日志关联用 SHA-256 短摘要。
     * <p>
     * 摘要用于跨阶段匹配同一请求或响应内容，不作为安全签名、幂等键或数据库唯一键使用。
     * </p>
     * @param value 待摘要文本
     * @return 16 位十六进制摘要，输入为空时返回 null
     */
    private String digest16(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            return "sha256_unavailable";
        }
    }

    /**
     * 将 JWT 时间戳转换为 ISO-8601 字符串。
     * <p>
     * 仅用于日志展示 token 签发或过期时间，输入小于等于 0 时视为不存在。
     * </p>
     * @param epochSecond Unix 秒级时间戳
     * @return ISO-8601 时间文本，输入无效时返回 null
     */
    private String epochSecondIso(long epochSecond) {
        if (epochSecond <= 0) {
            return null;
        }
        return Instant.ofEpochSecond(epochSecond).toString();
    }

    /**
     * 向日志字段集合写入非空值。
     * <p>
     * 空字符串和 null 不写入，避免摘要 JSON 中出现大量无效字段干扰问题定位。
     * </p>
     * @param target 日志字段集合
     * @param key 字段名称
     * @param value 字段值
     */
    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value instanceof String text && !StringUtils.hasText(text)) {
            return;
        }
        if (value != null) {
            target.put(key, value);
        }
    }

    /**
     * 按指定长度截断日志文本。
     * <p>
     * 用于限制商户 header、声明字段等诊断值的日志体积，避免单条日志过大影响排查检索。
     * </p>
     * @param value 原始文本
     * @param maxLength 最大保留字符数
     * @return 截断后的文本
     */
    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    /**
     * 按统一摘要长度截断日志文本。
     * <p>
     * 主要用于明文参数和响应摘要，配合脱敏逻辑防止大 JSON 报文直接撑爆业务日志。
     * </p>
     * @param value 已脱敏的日志文本
     * @return 限长后的日志文本
     */
    private String truncate(String value) {
        if (value == null || value.length() <= MAX_SUMMARY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_SUMMARY_LENGTH) + "...";
    }
}
