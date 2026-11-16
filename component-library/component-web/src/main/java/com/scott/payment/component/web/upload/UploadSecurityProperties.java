package com.scott.payment.component.web.upload;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 上传扫描配置；关闭扫描表示上传不可用，不能绕过安全检查。 */
@Data
@ConfigurationProperties(prefix = "acquiring.upload-security")
public class UploadSecurityProperties {
    private boolean enabled = true;
    private String host = "127.0.0.1";
    private int port = 3310;
    private int timeoutMillis = 10000;
    private int maxConcurrentScans = 4;
    private int uploadsPerMinute = 20;
    private long maxFileBytes = 20L * 1024 * 1024;
    private long maxDatabaseAgeHours = 72;
    private String scannerTimezone = "UTC";
}
