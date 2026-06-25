package com.scott.payment.component.security.openapi;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * OpenAPI 密钥材料复制响应。
 */
@Data
@AllArgsConstructor
public class OpenApiKeyCopyResponse {

    /**
     * 可复制内容。调用方不得写入日志。
     */
    private String content;

    /**
     * 前端建议展示的复制有效期提示，单位秒。
     */
    private int expireSeconds;
}
