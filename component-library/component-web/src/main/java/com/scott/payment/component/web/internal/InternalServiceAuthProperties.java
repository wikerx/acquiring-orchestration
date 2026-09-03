package com.scott.payment.component.web.internal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalServiceAuthProperties
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 内部服务调用签名配置，约束 /internal/** 接口的共享密钥、时间窗和白名单边界。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "internal-service.auth")
public class InternalServiceAuthProperties {

    /** 内部 HMAC 密钥最小长度，按 UTF-16 字符数执行启动门禁。 */
    public static final int MIN_SECRET_LENGTH = 32;

    /** 历史开发弱密钥，只用于启动时识别并拒绝。 */
    private static final String DEVELOPMENT_SECRET = "dev-internal-service-secret";

    /** 调用方服务标识允许的格式。 */
    private static final Pattern CALLER_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    /** 调用方路径授权使用的 Ant 路径匹配器。 */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 是否启用内部服务签名校验；生产和预发环境必须保持开启。
     */
    private boolean enabled = true;

    /**
     * 按可信调用方隔离的 HMAC 凭据；服务端只接受显式配置的调用方和内部路径。
     */
    private Map<String, CallerCredential> callers = new LinkedHashMap<>();

    /**
     * 请求时间允许偏移，超过该时间窗的请求会被拒绝。
     */
    private Duration allowedClockSkew = Duration.ofMinutes(5);

    /**
     * nonce 防重放记录有效期；必须覆盖正负时钟偏差窗口，默认十分钟。
     */
    private Duration nonceTtl = Duration.ofMinutes(10);

    /**
     * 不需要内部签名的路径，主要用于健康检查或基础探针。
     */
    private List<String> whitelist = List.of("/actuator/health/**", "/error");

    /**
     * 校验内部鉴权服务端配置，禁止空密钥、弱密钥、调用方冒用和无路径边界启动。
     */
    public void validate() {
        if (!enabled) {
            return;
        }
        if (callers == null || callers.isEmpty()) {
            throw new IllegalStateException("internal service caller credentials are required");
        }
        callers.forEach(this::validateCallerCredential);
        requirePositiveDuration(allowedClockSkew, "internal service allowed clock skew");
        requirePositiveDuration(nonceTtl, "internal service nonce ttl");
    }

    /**
     * 返回调用方当前和上一轮有效密钥；上一轮密钥仅用于无停机轮换验签。
     *
     * @param caller 请求声明的可信服务身份
     * @return 有序候选密钥，第一项始终为 active；调用方未配置时为空
     */
    public List<String> resolveSecrets(String caller) {
        CallerCredential credential = callers == null ? null : callers.get(caller);
        if (credential == null) {
            return List.of();
        }
        List<String> secrets = new ArrayList<>(2);
        secrets.add(credential.getActiveSecret());
        if (StringUtils.hasText(credential.getPreviousSecret())) {
            secrets.add(credential.getPreviousSecret());
        }
        return List.copyOf(secrets);
    }

    /**
     * 判断已认证调用方是否允许访问当前内部路径。
     *
     * @param caller 请求调用方
     * @param requestPath 原始请求 URI 路径
     * @return 命中显式授权路径时返回 true；调用方未配置时失败关闭
     */
    public boolean isPathAllowed(String caller, String requestPath) {
        CallerCredential credential = callers == null ? null : callers.get(caller);
        if (credential == null) {
            return false;
        }
        return credential.getAllowedPaths().stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath));
    }

    private void validateCallerCredential(String caller, CallerCredential credential) {
        if (!StringUtils.hasText(caller) || !CALLER_PATTERN.matcher(caller).matches()) {
            throw new IllegalStateException("internal service caller identity is invalid");
        }
        if (credential == null) {
            throw new IllegalStateException("internal service caller credential is required");
        }
        validateSecret(credential.getActiveSecret(), "active");
        if (StringUtils.hasText(credential.getPreviousSecret())) {
            validateSecret(credential.getPreviousSecret(), "previous");
            if (credential.getActiveSecret().equals(credential.getPreviousSecret())) {
                throw new IllegalStateException("internal service active and previous secrets must differ");
            }
        }
        if (credential.getAllowedPaths() == null || credential.getAllowedPaths().isEmpty()) {
            throw new IllegalStateException("internal service caller allowed paths are required");
        }
        for (String path : credential.getAllowedPaths()) {
            if (!StringUtils.hasText(path) || !path.startsWith("/internal/")) {
                throw new IllegalStateException("internal service caller allowed path is invalid");
            }
        }
    }

    private void validateSecret(String value, String slot) {
        if (!StringUtils.hasText(value) || value.contains("${")) {
            throw new IllegalStateException("internal service " + slot + " secret is required");
        }
        String normalized = value.trim();
        if (DEVELOPMENT_SECRET.equals(normalized) || normalized.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("internal service " + slot + " secret is too weak");
        }
    }

    private void requirePositiveDuration(Duration value, String propertyName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(propertyName + " must be positive");
        }
    }

    /**
     * 单个调用方的轮换凭据和最小路径权限。
     */
    @Data
    public static class CallerCredential {

        /** 当前调用方签名使用的有效密钥。 */
        private String activeSecret;

        /** 上一轮密钥，仅在滚动轮换窗口内保留，轮换完成后必须清空。 */
        private String previousSecret;

        /** 当前调用方允许访问的内部路径，支持 Ant 风格匹配。 */
        private List<String> allowedPaths = List.of();
    }
}
