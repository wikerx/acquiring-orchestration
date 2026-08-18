package com.scott.payment.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiCallbackProperties
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 渠道回调和商户通知维护入口安全配置，统一管理回调签名密钥与内部维护密钥。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "openapi.callback-security")
public class OpenApiCallbackProperties {

    /**
     * 是否启用渠道回调签名校验；生产和预发环境必须保持开启。
     */
    private boolean channelSignatureRequired = true;

    /**
     * 渠道回调签名时间戳允许偏移毫秒数。
     */
    private long allowedClockSkewMillis = 300_000L;

    /**
     * 商户通知重试维护密钥，请求头 X-Notify-Retry-Token 必须与该值一致。
     */
    private String notifyRetryToken = "dev-notify-retry-token";

    /**
     * 渠道编码到 HMAC-SHA256 共享密钥的映射。
     */
    private Map<String, String> channelSecrets = new HashMap<>();

    /**
     * 渠道编码到事件签名 keyId 和 HMAC-SHA256 共享密钥的映射。
     * <p>
     * 用于携带 keyId 的渠道 Event-Signature；未配置时会回退读取 channelSecrets。
     * </p>
     */
    private Map<String, Map<String, String>> channelEventSecrets = new HashMap<>();

    /**
     * 渠道编码到允许回调源 IP 的映射。
     * <p>
     * 未配置或列表为空时表示暂不启用该渠道 IP 白名单；配置后仅允许精确 IPv4/IPv6 地址命中。
     * </p>
     */
    private Map<String, List<String>> channelAllowedIps = new HashMap<>();
}
