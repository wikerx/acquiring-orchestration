package com.scott.payment.admin.infrastructure.storage;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.web.upload.ImageObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** 图片和 KYB、模板使用不同私有 Bucket，读取不使用公开 URL。 */
@Service
@ConditionalOnProperty(prefix = "acquiring.object-storage", name = "enabled", havingValue = "true")
public class S3ImageObjectStorage implements ImageObjectStorage {
    private final S3Client client;
    private final ObjectStorageProperties properties;
    public S3ImageObjectStorage(S3Client client, ObjectStorageProperties properties) { this.client = client; this.properties = properties; }
    private String bucket() {
        String bucket = properties.getMediaBucket();
        if (bucket == null || bucket.isBlank() || bucket.equals(properties.getBucket()) || bucket.equals(properties.getTemplateBucket())) throw failure();
        return bucket;
    }
    @Override public void put(String key, byte[] content) {
        try { client.putObject(PutObjectRequest.builder().bucket(bucket()).key(key).contentType("image/png").build(), RequestBody.fromBytes(content)); }
        catch (software.amazon.awssdk.core.exception.SdkException exception) { throw failure(); }
    }
    @Override public byte[] get(String key) {
        try { return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket()).key(key).range("bytes=0-2097152").build()).asByteArray(); }
        catch (software.amazon.awssdk.core.exception.SdkException exception) { throw failure(); }
    }
    @Override public void delete(String key) {
        try { client.deleteObject(DeleteObjectRequest.builder().bucket(bucket()).key(key).build()); }
        catch (software.amazon.awssdk.core.exception.SdkException exception) { throw failure(); }
    }
    private static ServiceException failure() { return new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "图片存储暂不可用，请稍后重试"); }
}
