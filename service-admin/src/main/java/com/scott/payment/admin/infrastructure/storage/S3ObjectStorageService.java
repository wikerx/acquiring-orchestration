package com.scott.payment.admin.infrastructure.storage;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : S3ObjectStorageService
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : S3 API 对象存储实现，业务层无需感知当前连接 MinIO 还是 AWS S3
 * @status : create
 */
@Service
@ConditionalOnProperty(prefix = "acquiring.object-storage", name = "enabled", havingValue = "true")
public class S3ObjectStorageService implements ObjectStorageService {

    /** S3 同步客户端；启用对象存储时由配置类创建，不允许为空。 */
    private final S3Client s3Client;

    /** Bucket、端点和供应商配置；不允许为空，其中访问凭证属于敏感信息。 */
    private final ObjectStorageProperties properties;

    /**
     * 创建 S3 对象存储实现。
     *
     * @param s3Client S3 同步客户端
     * @param properties 对象存储配置
     */
    public S3ObjectStorageService(S3Client s3Client, ObjectStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /**
     * 将文件正文写入配置的私有 Bucket。
     *
     * @param objectKey 私有对象键
     * @param contentType 已校验的 MIME 类型
     * @param content 文件二进制正文
     */
    @Override
    public void put(String objectKey, String contentType, byte[] content) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength((long) content.length)
                    .build(), RequestBody.fromBytes(content));
        } catch (S3Exception exception) {
            throw storageFailure("商户资料上传失败", exception);
        }
    }

    /**
     * 从私有 Bucket 读取对象正文。
     *
     * @param objectKey 私有对象键
     * @return 对象二进制正文
     */
    @Override
    public byte[] get(String objectKey) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build()).asByteArray();
        } catch (S3Exception exception) {
            throw storageFailure("商户资料读取失败", exception);
        }
    }

    /**
     * 删除私有 Bucket 中的对象正文。
     *
     * @param objectKey 私有对象键
     */
    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception exception) {
            throw storageFailure("商户资料删除失败", exception);
        }
    }

    /**
     * 将 S3 协议异常转换为统一业务异常，只保留状态码而不暴露端点、凭证或响应正文。
     *
     * @param message 面向管理端的操作失败说明
     * @param exception S3 SDK 异常，可能包含敏感连接上下文
     * @return 不包含底层敏感信息的统一业务异常
     */
    private ServiceException storageFailure(String message, S3Exception exception) {
        return new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                message + "，对象存储返回状态：" + exception.statusCode());
    }
}
