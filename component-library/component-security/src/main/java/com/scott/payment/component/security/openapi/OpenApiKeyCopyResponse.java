package com.scott.payment.component.security.openapi;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyCopyResponse
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Key Copy 响应对象，位于 component-library/component-security 的安全组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
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
