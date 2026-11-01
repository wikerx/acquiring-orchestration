package com.scott.payment.component.security.openapi;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * openAPI密钥下载文件协作组件，位于 公共组件库，封装该业务的本地校验、转换或运行时协作入口。
 */
@Data
@AllArgsConstructor
public class OpenApiKeyDownloadFile {

    /**
     * 下载文件名。
     */
    private String fileName;

    /**
     * HTTP Content-Type。
     */
    private String contentType;

    /**
     * 文件字节内容。
     */
    private byte[] content;
}
