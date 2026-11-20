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

/** S3 兼容的模板专用私有对象存储实现。 */
@Service
@ConditionalOnProperty(prefix = "acquiring.object-storage", name = "enabled", havingValue = "true")
public class S3TemplateObjectStorageService implements TemplateObjectStorageService {

    private final S3Client s3Client;
    private final ObjectStorageProperties properties;

    public S3TemplateObjectStorageService(S3Client s3Client, ObjectStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    /** 写入模板专用 Bucket，使用真实正文长度并将存储异常映射为受控业务错误。 */
    @Override
    public void put(String objectKey, String contentType, byte[] content) {
        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(properties.getTemplateBucket())
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength((long) content.length)
                    .build(), RequestBody.fromBytes(content));
        } catch (S3Exception exception) {
            throw storageFailure("系统模板上传失败", exception);
        }
    }

    /** 从模板专用 Bucket 读取正文，不返回底层地址或对象存储异常原文。 */
    @Override
    public byte[] get(String objectKey) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.getTemplateBucket())
                    .key(objectKey)
                    .build()).asByteArray();
        } catch (S3Exception exception) {
            throw storageFailure("系统模板读取失败", exception);
        }
    }

    /** 删除待清理的模板对象，失败时保留受控错误供补偿与审计。 */
    @Override
    public void delete(String objectKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getTemplateBucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception exception) {
            throw storageFailure("系统模板清理失败", exception);
        }
    }

    private ServiceException storageFailure(String message, S3Exception exception) {
        return new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                message + "，对象存储返回状态：" + exception.statusCode());
    }
}
