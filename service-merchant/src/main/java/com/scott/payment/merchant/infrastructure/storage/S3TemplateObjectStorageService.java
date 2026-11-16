package com.scott.payment.merchant.infrastructure.storage;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** 商户系统模板只读 S3 实现；不提供上传、删除或公共 URL。 */
@Service
@ConditionalOnProperty(prefix = "acquiring.object-storage", name = "enabled", havingValue = "true")
public class S3TemplateObjectStorageService implements TemplateObjectStorageService {

    private final S3Client s3Client;
    private final ObjectStorageProperties properties;

    public S3TemplateObjectStorageService(S3Client s3Client, ObjectStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public byte[] get(String objectKey) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.getTemplateBucket())
                    .key(objectKey)
                    .build()).asByteArray();
        } catch (S3Exception exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(),
                    "系统模板读取失败，对象存储返回状态：" + exception.statusCode());
        }
    }
}
