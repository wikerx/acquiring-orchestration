package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.openapi.config.HostedCheckoutProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

/**
 * Hosted Checkout 外部 URL 安全策略。
 *
 * <p>生产请求只允许 HTTPS；本地开发可通过显式配置放行回环地址上的 HTTP。</p>
 */
@Component
public class HostedCheckoutUrlPolicy {

    /** Hosted Checkout 环境配置。 */
    private final HostedCheckoutProperties properties;

    public HostedCheckoutUrlPolicy(HostedCheckoutProperties properties) {
        this.properties = properties;
    }

    /**
     * DTO 格式校验：HTTPS 始终可用，HTTP 仅允许本机回环地址，最终环境开关由服务层校验。
     *
     * @param value 待校验 URL
     * @return URL 结构和协议是否满足请求格式约束
     */
    public static boolean isSecureOrLoopbackHttpUrl(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        URI uri = parseHttpUri(value);
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        return "https".equals(scheme) || ("http".equals(scheme) && isLoopbackHost(uri.getHost()));
    }

    /**
     * 校验商户提交的回调或结果页 URL。
     *
     * @param value URL 值，可为空
     * @param fieldName 对外字段名
     * @return 原 URL 值
     */
    public String validateMerchantUrl(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if (!isAllowedForCurrentEnvironment(value)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID,
                    fieldName + " must use HTTPS; loopback HTTP is allowed only in local environments");
        }
        return value;
    }

    /**
     * 校验并规范化平台收银台前端基础地址。
     *
     * @param value 平台系统参数值
     * @return 去除末尾斜杠后的基础地址
     */
    public String normalizePlatformBaseUrl(String value) {
        requirePlatformUri(value, "base url");
        String normalized = value;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * 校验平台收银台地址并提取浏览器消息接收 origin。
     *
     * @param value 平台系统参数值
     * @return scheme、host 和 port 组成的 origin
     */
    public String resolvePlatformOrigin(String value) {
        URI uri = requirePlatformUri(value, "origin");
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getRawAuthority();
    }

    /** 当前环境是否允许使用指定 URL。 */
    private boolean isAllowedForCurrentEnvironment(String value) {
        URI uri = parseHttpUri(value);
        if (uri == null) {
            return false;
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) {
            return true;
        }
        return properties.isAllowLoopbackHttp()
                && "http".equals(scheme)
                && isLoopbackHost(uri.getHost());
    }

    /** 平台配置非法时统一转换为内部配置错误，避免把平台问题归因到商户请求。 */
    private URI requirePlatformUri(String value, String targetName) {
        URI uri = parseHttpUri(value);
        if (uri == null || !isAllowedForCurrentEnvironment(value)) {
            throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR,
                    "system config is not a valid checkout frontend " + targetName);
        }
        return uri;
    }

    /** 严格解析绝对 HTTP(S) URI，拒绝用户信息、无主机和非法端口。 */
    private static URI parseHttpUri(String value) {
        if (!StringUtils.hasText(value) || !value.equals(value.trim())) {
            return null;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            int port = uri.getPort();
            if (!uri.isAbsolute()
                    || uri.isOpaque()
                    || !StringUtils.hasText(scheme)
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || !StringUtils.hasText(uri.getHost())
                    || uri.getRawAuthority() == null
                    || uri.getUserInfo() != null
                    || port == 0
                    || port > 65_535) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** 仅识别显式 localhost、IPv4 127/8 和 IPv6 ::1，不触发 DNS 解析。 */
    private static boolean isLoopbackHost(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if ("localhost".equals(normalized) || "::1".equals(normalized)) {
            return true;
        }
        String[] parts = normalized.split("\\.", -1);
        if (parts.length != 4 || !"127".equals(parts[0])) {
            return false;
        }
        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }
}
