package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HostedCheckoutUrlPolicy
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : Hosted Checkout 外部 URL 结构策略，允许 HTTP/HTTPS 公网、私网和回环地址，仅拒绝结构非法、携带用户信息或非 HTTP(S) URL。
 * @status : create
 */
@Component
public class HostedCheckoutUrlPolicy {

    /**
     * DTO 格式校验：允许结构完整的 HTTP/HTTPS 公网、私网和回环 URL。
     *
     * @param value 待校验 URL
     * @return URL 结构和协议是否满足请求格式约束
     */
    public static boolean isHttpUrl(String value) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return parseHttpUri(value) != null;
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
        if (parseHttpUri(value) == null) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID,
                    fieldName + " must be a valid HTTP or HTTPS URL");
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

    /**
     * 校验平台受控地址并把非法值转换为内部配置错误，避免归因到商户请求。
     *
     * @param value 平台配置的收银台地址
     * @param targetName 错误消息中的配置用途名称
     * @return 结构完整的绝对 HTTP(S) URI
     * @throws ApiException 平台地址缺失或结构非法时抛出内部错误
     */
    private URI requirePlatformUri(String value, String targetName) {
        URI uri = parseHttpUri(value);
        if (uri == null) {
            throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR,
                    "system config is not a valid checkout frontend " + targetName);
        }
        return uri;
    }

    /**
     * 严格解析绝对 HTTP(S) URI，允许公网、私网和回环主机，拒绝用户信息、无主机和非法端口。
     *
     * @param value 待解析地址；不允许包含首尾空白
     * @return 合法 URI；地址为空或结构非法时返回 {@code null}
     */
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

}
