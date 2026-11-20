package com.scott.payment.admin.infrastructure.storage;

/**
 * 系统文件模板私有对象存储端口。
 *
 * <p>使用独立 Bucket，禁止生成公共 URL 或把底层对象键返回给浏览器。</p>
 */
public interface TemplateObjectStorageService {

    /** 将已通过安全校验的版本资产写入模板专用私有存储，不生成公开 URL。 */
    void put(String objectKey, String contentType, byte[] content);

    /** 按服务端内部键读取模板正文，调用方负责下载授权和完整性核对。 */
    byte[] get(String objectKey);

    /** 清理未被有效版本引用的模板对象，不能用于改写已发布资产。 */
    void delete(String objectKey);
}
