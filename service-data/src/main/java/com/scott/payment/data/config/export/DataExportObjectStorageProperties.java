package com.scott.payment.data.config.export;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 导出文件私有对象存储配置，使用独立 Bucket 与商户资料保留策略隔离。
 */
@Data
@ConfigurationProperties(prefix = "data.export.object-storage")
public class DataExportObjectStorageProperties {
    private boolean enabled;
    private String provider = "MINIO";
    private String endpoint;
    private String region = "us-east-1";
    private String bucket = "vexra-export-files";
    private boolean pathStyleAccess = true;
    private String accessKey;
    private String secretKey;
}
