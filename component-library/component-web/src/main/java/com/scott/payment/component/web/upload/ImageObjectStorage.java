package com.scott.payment.component.web.upload;

/** 独立私有图片 Bucket 的对象端口，不提供公开直链。 */
public interface ImageObjectStorage {
    /** 将已扫描并重新编码的图片写入专用私有存储；对象键由服务端生成。 */
    void put(String key, byte[] content);
    /** 按内部键读取图片正文，调用方仍须验证所属对象范围及完整性。 */
    byte[] get(String key);
    /** 清理不再被数据库引用的图片对象，不向外暴露存储地址。 */
    void delete(String key);
}
