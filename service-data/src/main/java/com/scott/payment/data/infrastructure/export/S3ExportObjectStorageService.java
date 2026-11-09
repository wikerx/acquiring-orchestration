package com.scott.payment.data.infrastructure.export;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.data.config.export.DataExportObjectStorageProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * S3 兼容导出文件存储实现，开发环境连接 MinIO，生产可切换 AWS S3。
 */
@Service
@ConditionalOnProperty(prefix = "data.export.object-storage", name = "enabled", havingValue = "true")
public class S3ExportObjectStorageService implements ExportObjectStorageService {
    private final S3Client s3Client;
    private final DataExportObjectStorageProperties properties;

    /**
     * 创建只使用导出 Bucket 的对象存储服务。
     */
    public S3ExportObjectStorageService(@Qualifier("dataExportS3Client") S3Client s3Client,
                                        DataExportObjectStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /**
     * 使用文件请求体上传，避免把完整工作簿读入 JVM 堆内存。
     */
    @Override
    public void put(String objectKey, String contentType, Path file) {
        try {
            s3Client.putObject(PutObjectRequest.builder().bucket(properties.getBucket())
                    .key(objectKey).contentType(contentType).contentLength(Files.size(file)).build(),
                    RequestBody.fromFile(file));
        } catch (IOException | SdkException exception) {
            throw storageFailure("导出文件上传失败", exception);
        }
    }

    /**
     * 从私有 Bucket 流式复制对象，调用链不会返回对象存储永久地址。
     */
    @Override
    public void copyTo(String objectKey, OutputStream outputStream) {
        try (ResponseInputStream<GetObjectResponse> input = s3Client.getObject(
                GetObjectRequest.builder().bucket(properties.getBucket()).key(objectKey).build())) {
            input.transferTo(outputStream);
            outputStream.flush();
        } catch (IOException | SdkException exception) {
            throw storageFailure("导出文件读取失败", exception);
        }
    }

    /**
     * 删除过期对象；S3 删除不存在对象保持幂等成功语义。
     */
    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket()).key(objectKey).build());
        } catch (SdkException exception) {
            throw storageFailure("导出文件删除失败", exception);
        }
    }

    /**
     * 隐藏对象存储端点和供应商响应正文，只返回稳定业务错误。
     */
    private ServiceException storageFailure(String message, Exception exception) {
        return new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                message + "，exceptionType: " + exception.getClass().getSimpleName());
    }
}
