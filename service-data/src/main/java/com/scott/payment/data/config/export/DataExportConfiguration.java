package com.scott.payment.data.config.export;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步导出运行配置，启用时创建只访问导出私有 Bucket 的 S3 兼容客户端。
 */
@Configuration
@EnableConfigurationProperties({DataExportProperties.class, DataExportObjectStorageProperties.class})
public class DataExportConfiguration {

    /**
     * 创建异步导出专用的固定有界线程池。
     *
     * <p>调度接口只提交当前可执行任务后立即返回；专用并发槽位负责背压，执行器不得让
     * 内部 HTTP 线程代跑大文件生成任务。</p>
     *
     * @param properties 异步导出容量配置
     * @return 仅执行已领取导出任务的专用执行器
     */
    @Bean(name = "dataExportTaskExecutor")
    public ThreadPoolTaskExecutor dataExportTaskExecutor(DataExportProperties properties) {
        properties.validate();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getDispatchConcurrency());
        executor.setMaxPoolSize(properties.getDispatchConcurrency());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("data-export-");
        executor.setTaskDecorator(new DataExportTaskContextDecorator());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    /**
     * 构建 MinIO 或 AWS S3 同步客户端；凭据只驻留于 SDK 客户端且禁止进入日志。
     */
    @Bean("dataExportS3Client")
    @ConditionalOnProperty(prefix = "data.export.object-storage", name = "enabled", havingValue = "true")
    public S3Client dataExportS3Client(DataExportObjectStorageProperties properties,
                                       DataExportProperties exportProperties) {
        exportProperties.validate();
        requireText(properties.getRegion(), "data.export.object-storage.region");
        requireText(properties.getBucket(), "data.export.object-storage.bucket");
        requireText(properties.getAccessKey(), "data.export.object-storage.access-key");
        requireText(properties.getSecretKey(), "data.export.object-storage.secret-key");
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        properties.getAccessKey(), properties.getSecretKey())))
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess()).build());
        if (StringUtils.hasText(properties.getEndpoint())) {
            builder.endpointOverride(URI.create(properties.getEndpoint().trim()));
        }
        return builder.build();
    }

    /**
     * 拒绝缺少的对象存储必要配置，避免任务运行后才产生不可恢复失败。
     */
    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required when export object storage is enabled");
        }
    }
}
