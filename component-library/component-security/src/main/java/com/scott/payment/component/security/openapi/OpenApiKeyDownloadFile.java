package com.scott.payment.component.security.openapi;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyDownloadFile
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : Open API Key Download File 协作组件，位于 公共组件库，封装 openapi密钥downloadfile 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
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
