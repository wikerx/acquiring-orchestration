package com.scott.payment.admin.infrastructure.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ObjectStorageProperties
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : S3 兼容对象存储配置，支持开发环境 MinIO 与生产环境 AWS S3 复用同一协议
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "acquiring.object-storage")
public class ObjectStorageProperties {

    /** 是否启用对象存储；布尔配置，不允许生产环境在资料上传功能启用时为 false。 */
    private boolean enabled;

    /** 存储供应商标识；默认 MINIO，允许配置为 AWS_S3 等受控值，非敏感。 */
    private String provider = "MINIO";

    /** S3 兼容端点；MinIO 必填，AWS S3 可为空以使用 SDK 默认端点，非敏感。 */
    private String endpoint;

    /** AWS 区域编码；默认 us-east-1，不允许为空，非敏感。 */
    private String region = "us-east-1";

    /** 私有 Bucket 名称；启用存储时不允许为空，非敏感。 */
    private String bucket;

    /** 是否启用 Path Style；MinIO 通常为 true，AWS S3 可按部署环境调整。 */
    private boolean pathStyleAccess = true;

    /** 单文件最大字节数；默认 20 MiB，必须大于 0。 */
    private long maxFileSizeBytes = 20L * 1024 * 1024;

    /** S3 Access Key；敏感，启用存储时不允许为空且禁止写入日志。 */
    private String accessKey;

    /** S3 Secret Key；敏感，启用存储时不允许为空且禁止写入日志。 */
    private String secretKey;
}
