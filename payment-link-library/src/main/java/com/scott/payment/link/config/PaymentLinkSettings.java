package com.scott.payment.link.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/** 默认关闭新入口；开启时要求专用签名密钥与受控公开地址，不影响旧 Checkout 启动。 */
@Data
public class PaymentLinkSettings {
    private boolean enabled;
    /** 完成迁移后保持 true；关闭新入口时继续约束已签发会话。 */
    private boolean storageReady;
    private String frontendBaseUrl;
    private String activeKeyVersion;
    @lombok.ToString.Exclude
    private Map<String, String> signingKeys = new LinkedHashMap<>();

    @PostConstruct
    public void validate() {
        if (!enabled) {
            return;
        }
        if (!storageReady) {
            throw new IllegalStateException("payment.link.storage-ready must be true after applying the migration");
        }
        URI uri;
        try {
            uri = URI.create(frontendBaseUrl == null ? "" : frontendBaseUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("payment.link.frontend-base-url must be an absolute HTTPS origin");
        }
        boolean loopback = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535)
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !("https".equals(uri.getScheme()) || loopback && "http".equals(uri.getScheme()))
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalStateException("payment.link.frontend-base-url must be an HTTPS origin; HTTP is loopback-only");
        }
        if (activeKeyVersion == null || !activeKeyVersion.matches("[A-Za-z0-9_-]{1,32}")
                || signingKeys == null || !signingKeys.containsKey(activeKeyVersion)
                || signingKeys.values().stream().anyMatch(key -> key == null || key.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32)) {
            throw new IllegalStateException("payment.link.signing-keys must contain the active version and keys of at least 32 bytes");
        }
    }
}
