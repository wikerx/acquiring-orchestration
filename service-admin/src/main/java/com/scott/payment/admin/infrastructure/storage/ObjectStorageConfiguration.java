package com.scott.payment.admin.infrastructure.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ObjectStorageConfiguration
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : 对象存储基础设施配置，按 Nacos 参数构建可连接 MinIO 或 AWS S3 的同步客户端
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfiguration {

    /**
     * 构建商户资料专用 S3 客户端，启用配置缺失时在启动阶段失败。
     *
     * @param properties S3 兼容对象存储配置
     * @return 由 Spring 管理并在关闭时释放连接资源的 S3 客户端
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "acquiring.object-storage", name = "enabled", havingValue = "true")
    public S3Client merchantDocumentS3Client(ObjectStorageProperties properties) {
        requireText(properties.getAccessKey(), "OBJECT_STORAGE_ACCESS_KEY");
        requireText(properties.getSecretKey(), "OBJECT_STORAGE_SECRET_KEY");
        requireText(properties.getBucket(), "acquiring.object-storage.bucket");
        S3ClientBuilder builder = S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
                .region(Region.of(properties.getRegion()))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build());
        if (StringUtils.hasText(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    /**
     * 校验启用对象存储时必须存在的配置，避免运行到首次上传才暴露配置缺失。
     *
     * @param value 待校验配置值；访问凭证属于敏感信息，禁止记录日志
     * @param name 用于异常提示的配置名称
     */
    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required when object storage is enabled");
        }
    }
}
