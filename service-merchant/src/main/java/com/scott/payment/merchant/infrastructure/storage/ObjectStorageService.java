package com.scott.payment.merchant.infrastructure.storage;

/**
 * 商户资料对象存储端口，隔离资料业务与 MinIO 或 AWS S3 实现。
 */
public interface ObjectStorageService {

    /**
     * 写入私有对象。
     */
    void put(String objectKey, String contentType, byte[] content);

    /**
     * 读取私有对象。
     */
    byte[] get(String objectKey);

    /**
     * 删除私有对象。
     */
    void delete(String objectKey);
}
