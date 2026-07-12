package com.scott.payment.component.security.openapi;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyDownloadFile
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Key Download File，位于 component-library/component-security 的安全组件层，用于承载该模块对应的业务职责和数据流转边界。
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
