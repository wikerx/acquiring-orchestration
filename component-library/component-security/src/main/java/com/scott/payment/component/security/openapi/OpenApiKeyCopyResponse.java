package com.scott.payment.component.security.openapi;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * openAPI密钥副本响应模型，位于 公共组件库，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
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
