package com.scott.payment.merchant.infrastructure.storage;

/** 商户系统只读访问模板专用私有 Bucket 的存储端口。 */
public interface TemplateObjectStorageService {
    byte[] get(String objectKey);
}
