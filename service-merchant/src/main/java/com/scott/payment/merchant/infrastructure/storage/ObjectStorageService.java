package com.scott.payment.merchant.infrastructure.storage;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ObjectStorageService
 * @date : 2026-09-21 09:40
 * @email : scott_x@163.com
 * @description : 商户端对象存储端口，隔离资料业务与 MinIO 或 AWS S3 实现
 * @status : create
 */
public interface ObjectStorageService {

    /** 写入私有对象。 */
    void put(String objectKey, String contentType, byte[] content);

    /** 读取私有对象。 */
    byte[] get(String objectKey);

    /** 删除私有对象。 */
    void delete(String objectKey);
}
