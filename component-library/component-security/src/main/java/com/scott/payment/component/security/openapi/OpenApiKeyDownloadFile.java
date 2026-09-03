package com.scott.payment.component.security.openapi;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyDownloadFile
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : openAPI密钥下载文件协作组件，位于 公共组件库，封装该业务的本地校验、转换或运行时协作入口。
 * @status : create
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
