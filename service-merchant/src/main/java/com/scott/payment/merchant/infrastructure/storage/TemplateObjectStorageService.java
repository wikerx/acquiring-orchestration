package com.scott.payment.merchant.infrastructure.storage;

/** 商户系统只读访问模板专用私有 Bucket 的存储端口。 */
public interface TemplateObjectStorageService {
    /** 按服务端内部键读取模板正文，调用方负责下载授权和完整性核对。 */
    byte[] get(String objectKey);
}
