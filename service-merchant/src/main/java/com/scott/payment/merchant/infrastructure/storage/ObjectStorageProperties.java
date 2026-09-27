package com.scott.payment.merchant.infrastructure.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ObjectStorageProperties
 * @date : 2026-09-21 09:40
 * @email : scott_x@163.com
 * @description : 商户端 S3 兼容对象存储配置，支持 MinIO 与 AWS S3 平滑切换
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "acquiring.object-storage")
public class ObjectStorageProperties {

    private boolean enabled;
    private String provider = "MINIO";
    private String endpoint;
    private String region = "us-east-1";
    private String bucket;
    private boolean pathStyleAccess = true;
    private long maxFileSizeBytes = 20L * 1024 * 1024;
    private String accessKey;
    private String secretKey;
}
