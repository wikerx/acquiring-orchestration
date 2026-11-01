package com.scott.payment.openapi.support;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.openapi.dto.body.OpenApiEncryptedRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.component.web.gateway.TrustedClientIpResolver;
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
import java.util.List;

/**
 * 商户 OpenAPI 诊断日志支撑组件，统一生成请求头、密文和解密后对象的安全元数据摘要。
 *
 * <p>解密后的请求和响应只记录类型、序列化长度及不可逆摘要，不记录业务字段内容。</p>
 */
@Component
public class OpenApiDiagnosticLogSupport {

    /**
     * 日志摘要最大字符数，避免大报文刷屏。
     */
    private static final int MAX_SUMMARY_LENGTH = 1600;

    /**
     * Compact 密文期望段数，格式为 protectedHeader.encryptedKey.iv.cipherText.tag。
     */
    private static final int COMPACT_PART_COUNT = 5;

    /**
     * 生成 OpenAPI 请求头诊断摘要。
     * <p>
     * Authorization 只保留存在性、Bearer 前缀、长度和 SHA-256 短摘要；不会输出 JWT 原文。
     * IP、来源页面、User-Agent 和调用方请求标识只记录不可逆短摘要；其它字段仅保留媒体类型、
     * 内容长度和非敏感请求头名称，避免泄漏来源地址、Cookie 或内部签名头。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 请求头诊断摘要 JSON
     */
    public String headerSummary(HttpServletRequest request) {
        if (request == null) {
            return "{}";
        }
        HeaderDiagnosticSummary summary = new HeaderDiagnosticSummary(
                TraceContext.getTraceId(),
                identifierDigest(request.getHeader(TraceContext.TRACE_ID_HEADER)),
                identifierDigest(request.getHeader("X-Request-Id")),
                identifierDigest(TrustedClientIpResolver.resolve(request)),
                identifierDigest(request.getHeader("Origin")),
                identifierDigest(request.getHeader("Referer")),
                identifierDigest(request.getHeader("User-Agent")),
                request.getContentType(),
                request.getContentLengthLong(),
                authorizationSummary(request.getHeader("authorization")),
                headerNames(request)
        );
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
        JwtDiagnosticSummary summary = new JwtDiagnosticSummary(
                headerDTO.getMerchantId(),
                digest16(headerDTO.getJwtId()),
                headerDTO.getIssuedAt(),
                epochSecondIso(headerDTO.getIssuedAt()),
                headerDTO.getExpiresAt(),
                epochSecondIso(headerDTO.getExpiresAt())
        );
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
        if (!StringUtils.hasText(requestBody)) {
            return JsonUtils.toJsonString(CipherRequestDiagnosticSummary.empty());
        }
        String trimmed = requestBody.trim();
        String cipherText = extractCipherTextSafely(trimmed);
        boolean dataPresent = StringUtils.hasText(cipherText);
        int partCount = dataPresent ? compactPartCount(cipherText) : 0;
        CipherRequestDiagnosticSummary summary = new CipherRequestDiagnosticSummary(
                true,
                trimmed.length(),
                digest16(trimmed),
                trimmed.startsWith("{"),
                dataPresent,
                dataPresent ? cipherText.length() : null,
                dataPresent ? digest16(cipherText) : null,
                dataPresent ? maskCipher(cipherText) : null,
                dataPresent ? partCount : null,
                dataPresent ? partCount == COMPACT_PART_COUNT : null
        );
        return JsonUtils.toJsonString(summary);
    }

    /**
     * 生成解密后请求对象的安全元数据摘要。
     * <p>
     * 摘要不包含任何业务字段值，只用于关联同一对象在解密、校验和内部调用阶段的处理记录。
     * </p>
     * @param data 解密后的业务 DTO
     * @return 不包含业务字段值的请求元数据摘要
     */
    public String plainRequestSummary(Object data) {
        return objectSummary(data);
    }

    /**
     * 生成加密前响应对象的安全元数据摘要。
     * <p>
     * 响应加密前调用，只记录对象类型、序列化长度和不可逆摘要，便于和密文响应摘要配对。
     * </p>
     * @param data 响应 data 对象
     * @return 不包含业务字段值的响应元数据摘要
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
        if (!StringUtils.hasText(encryptedData)) {
            return JsonUtils.toJsonString(CipherResponseDiagnosticSummary.empty());
        }
        CipherResponseDiagnosticSummary summary = new CipherResponseDiagnosticSummary(
                true,
                encryptedData.length(),
                digest16(encryptedData),
                maskCipher(encryptedData),
                compactPartCount(encryptedData)
        );
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
     * 生成日志关联用的不可逆标识摘要。
     * <p>
     * 适用于客户端 IP、请求标识等不应以原文进入日志的字段。相同输入会得到相同摘要，
     * 便于按调用来源关联请求；摘要不能用于认证、签名或业务唯一性判断。
     * </p>
     *
     * @param value 待摘要的标识文本，可为空
     * @return SHA-256 前 16 位十六进制摘要；输入为空时返回 null
     */
    public String identifierDigest(String value) {
        return digest16(value);
    }

    /**
     * 生成解密后对象的诊断元数据。
     * <p>
     * JSON 只在内存中用于计算长度和 SHA-256 摘要，返回值不包含任何请求或响应字段，避免脱敏规则遗漏时
     * 将银行卡、账户、身份信息或其它商户业务数据写入服务端日志。
     * </p>
     * @param value 待记录的业务对象
     * @return 对象类型、JSON 长度和不可逆摘要，输入为空时返回 null
     */
    private String objectSummary(Object value) {
        if (value == null) {
            return null;
        }
        String json = JsonUtils.toJsonString(value);
        PayloadDiagnosticSummary summary = new PayloadDiagnosticSummary(
                value.getClass().getSimpleName(),
                json.length(),
                digest16(json)
        );
        return JsonUtils.toJsonString(summary);
    }

    /**
     * 生成 Authorization 请求头摘要。
     * <p>
     * 仅记录是否存在、长度、摘要、Bearer 前缀和 JWT 段数，不输出 JWT 原文，便于定位商户鉴权格式问题。
     * </p>
     * @param authorization Authorization 请求头原文
     * @return 可安全打印的鉴权头摘要
     */
    private AuthorizationDiagnosticSummary authorizationSummary(String authorization) {
        if (!StringUtils.hasText(authorization)) {
            return AuthorizationDiagnosticSummary.absent();
        }
        String trimmed = authorization.trim();
        int partCount = trimmed.replaceFirst("(?i)^Bearer\\s+", "").split("\\.").length;
        return new AuthorizationDiagnosticSummary(
                true,
                trimmed.length(),
                digest16(trimmed),
                trimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length()),
                partCount
        );
    }

    /**
     * 提取可打印的 HTTP 请求头名称。
     * <p>
     * 只记录头名称，不记录头值，并过滤 Authorization、Cookie 等敏感头，满足商户接入排查的最小必要信息。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 已排序的非敏感请求头名称列表
     */
    private List<String> headerNames(HttpServletRequest request) {
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
     * 只解析稳定的加密请求信封；解析失败时返回 null，不抛出异常影响正常安全链路。
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
            return null;
        } catch (RuntimeException exception) {
            return null;
        }
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
     * 按统一摘要长度截断日志文本。
     * <p>
     * 主要用于请求头和错误信封等结构化元数据，防止异常输入生成过大的单行日志。
     * </p>
     * @param value 不包含业务报文内容的日志文本
     * @return 限长后的日志文本
     */
    private String truncate(String value) {
        if (value == null || value.length() <= MAX_SUMMARY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_SUMMARY_LENGTH) + "...";
    }

    /**
     * 承载请求头诊断字段的固定结构，字段值均为公开元数据或不可逆摘要。
     *
     * @param traceId 当前服务链路标识
     * @param xTraceIdDigest 调用方链路标识摘要
     * @param xRequestIdDigest 调用方请求标识摘要
     * @param trustedClientIpDigest 可信客户端 IP 摘要
     * @param originDigest Origin 请求头摘要
     * @param refererDigest Referer 请求头摘要
     * @param userAgentDigest User-Agent 请求头摘要
     * @param contentType 请求媒体类型
     * @param contentLength 请求体字节数
     * @param authorization Authorization 安全摘要
     * @param headerNames 非敏感请求头名称
     */
    private record HeaderDiagnosticSummary(
            String traceId,
            String xTraceIdDigest,
            String xRequestIdDigest,
            String trustedClientIpDigest,
            String originDigest,
            String refererDigest,
            String userAgentDigest,
            String contentType,
            long contentLength,
            AuthorizationDiagnosticSummary authorization,
            List<String> headerNames) {
    }

    /**
     * 承载已验签 JWT 声明的安全诊断字段，不保存 token 原文。
     *
     * @param merchantId 商户号
     * @param jtiDigest JWT 唯一标识摘要
     * @param issuedAt 签发秒级时间戳
     * @param issuedAtIso 签发 ISO 时间
     * @param expiresAt 过期秒级时间戳
     * @param expiresAtIso 过期 ISO 时间
     */
    private record JwtDiagnosticSummary(
            String merchantId,
            String jtiDigest,
            long issuedAt,
            String issuedAtIso,
            long expiresAt,
            String expiresAtIso) {
    }

    /**
     * 承载商户密文请求信封的格式诊断字段，不保存完整密文。
     *
     * @param bodyPresent 请求体是否存在
     * @param bodyLength 请求体字符数
     * @param bodyDigest 请求体摘要
     * @param bodyJson 请求体是否为 JSON 外形
     * @param dataPresent data 密文字段是否存在
     * @param dataLength data 密文字符数
     * @param dataDigest data 密文摘要
     * @param dataMasked data 密文首尾掩码
     * @param compactPartCount compact 密文段数
     * @param compactPartCountValid compact 密文段数是否符合协议
     */
    private record CipherRequestDiagnosticSummary(
            boolean bodyPresent,
            Integer bodyLength,
            String bodyDigest,
            Boolean bodyJson,
            Boolean dataPresent,
            Integer dataLength,
            String dataDigest,
            String dataMasked,
            Integer compactPartCount,
            Boolean compactPartCountValid) {

        /**
         * 创建空请求体的诊断摘要。
         *
         * @return 仅标记请求体不存在的摘要
         */
        private static CipherRequestDiagnosticSummary empty() {
            return new CipherRequestDiagnosticSummary(
                    false, null, null, null, null, null, null, null, null, null);
        }
    }

    /**
     * 承载响应密文的格式诊断字段，不保存完整密文。
     *
     * @param dataPresent 响应密文是否存在
     * @param dataLength 响应密文字符数
     * @param dataDigest 响应密文摘要
     * @param dataMasked 响应密文首尾掩码
     * @param compactPartCount compact 密文段数
     */
    private record CipherResponseDiagnosticSummary(
            boolean dataPresent,
            Integer dataLength,
            String dataDigest,
            String dataMasked,
            Integer compactPartCount) {

        /**
         * 创建空响应密文的诊断摘要。
         *
         * @return 仅标记响应密文不存在的摘要
         */
        private static CipherResponseDiagnosticSummary empty() {
            return new CipherResponseDiagnosticSummary(false, null, null, null, null);
        }
    }

    /**
     * 承载解密后对象的不可逆元数据，不保存任何业务字段值。
     *
     * @param payloadType 对象类型
     * @param jsonLength 序列化字符数
     * @param jsonDigest 序列化内容摘要
     */
    private record PayloadDiagnosticSummary(String payloadType, int jsonLength, String jsonDigest) {
    }

    /**
     * 承载 Authorization 请求头的格式诊断字段，不保存 JWT 原文。
     *
     * @param present 请求头是否存在
     * @param length 请求头字符数
     * @param digest 请求头摘要
     * @param bearerPrefix 是否使用 Bearer 前缀
     * @param jwtPartCount JWT compact 段数
     */
    private record AuthorizationDiagnosticSummary(
            boolean present,
            Integer length,
            String digest,
            Boolean bearerPrefix,
            Integer jwtPartCount) {

        /**
         * 创建未提供 Authorization 请求头的诊断摘要。
         *
         * @return 仅标记请求头不存在的摘要
         */
        private static AuthorizationDiagnosticSummary absent() {
            return new AuthorizationDiagnosticSummary(false, null, null, null, null);
        }
    }
}
