package com.scott.payment.merchant.infrastructure.storage;

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
 * @date : 2026-09-21 09:40
 * @email : scott_x@163.com
 * @description : 商户端 S3 协议对象存储实现，不向业务层暴露供应商差异
 * @status : create
 */
@Service
@ConditionalOnProperty(prefix = "acquiring.object-storage", name = "enabled", havingValue = "true")
public class S3ObjectStorageService implements ObjectStorageService {

    private final S3Client s3Client;
    private final ObjectStorageProperties properties;

    /** 创建 S3 对象存储实现。 */
    public S3ObjectStorageService(S3Client s3Client, ObjectStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /** 写入私有对象。 */
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

    /** 读取私有对象。 */
    @Override
    public byte[] get(String objectKey) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.getBucket()).key(objectKey).build()).asByteArray();
        } catch (S3Exception exception) {
            throw storageFailure("商户资料读取失败", exception);
        }
    }

    /** 删除私有对象。 */
    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket()).key(objectKey).build());
        } catch (S3Exception exception) {
            throw storageFailure("商户资料删除失败", exception);
        }
    }

    private ServiceException storageFailure(String message, S3Exception exception) {
        return new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                message + "，对象存储返回状态：" + exception.statusCode());
    }
}
