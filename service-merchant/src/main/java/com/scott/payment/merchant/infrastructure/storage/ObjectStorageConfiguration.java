package com.scott.payment.merchant.infrastructure.storage;

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
 * @date : 2026-09-21 09:40
 * @email : scott_x@163.com
 * @description : 商户端对象存储客户端配置，按 Nacos 参数创建 S3 同步客户端
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfiguration {

    /** 创建商户资料 S3 客户端，启用存储时要求凭证和 Bucket 完整。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "acquiring.object-storage", name = "enabled", havingValue = "true")
    public S3Client merchantProfileS3Client(ObjectStorageProperties properties) {
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

    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required when object storage is enabled");
        }
    }
}
