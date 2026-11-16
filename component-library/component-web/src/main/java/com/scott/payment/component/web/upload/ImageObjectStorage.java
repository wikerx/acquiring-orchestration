package com.scott.payment.component.web.upload;

/** 独立私有图片 Bucket 的对象端口，不提供公开直链。 */
public interface ImageObjectStorage {
    void put(String key, byte[] content);
    byte[] get(String key);
    void delete(String key);
}
