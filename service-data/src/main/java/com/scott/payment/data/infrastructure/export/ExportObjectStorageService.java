package com.scott.payment.data.infrastructure.export;

import java.io.OutputStream;
import java.nio.file.Path;

/**
 * 导出文件对象存储端口，支持磁盘临时文件流式上传和受控下载代理。
 */
public interface ExportObjectStorageService {

    /**
     * 将本地临时文件流式写入私有对象存储。
     */
    void put(String objectKey, String contentType, Path file);

    /**
     * 将私有对象正文复制到已鉴权的内部响应流，不生成永久访问链接。
     */
    void copyTo(String objectKey, OutputStream outputStream);

    /**
     * 幂等删除过期导出对象。
     */
    void delete(String objectKey);
}
