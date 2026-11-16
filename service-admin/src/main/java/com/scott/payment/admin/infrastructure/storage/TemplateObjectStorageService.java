package com.scott.payment.admin.infrastructure.storage;

/**
 * 系统文件模板私有对象存储端口。
 *
 * <p>使用独立 Bucket，禁止生成公共 URL 或把底层对象键返回给浏览器。</p>
 */
public interface TemplateObjectStorageService {

    void put(String objectKey, String contentType, byte[] content);

    byte[] get(String objectKey);

    void delete(String objectKey);
}
