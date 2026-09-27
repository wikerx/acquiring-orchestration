package com.scott.payment.admin.infrastructure.storage;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ObjectStorageService
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : 商户资料对象存储端口，屏蔽 MinIO 与 AWS S3 的实现差异且不暴露预签名直链
 * @status : create
 */
public interface ObjectStorageService {

    /**
     * 保存对象正文。
     *
     * @param objectKey 私有对象键，不允许为空
     * @param contentType 已通过魔数识别的 MIME 类型
     * @param content 文件二进制正文，不允许为空
     */
    void put(String objectKey, String contentType, byte[] content);

    /**
     * 读取对象正文。
     *
     * @param objectKey 私有对象键，不允许为空
     * @return 对象二进制正文
     */
    byte[] get(String objectKey);

    /**
     * 删除对象正文；底层实现需保持重复删除可接受。
     *
     * @param objectKey 私有对象键，不允许为空
     */
    void delete(String objectKey);
}
