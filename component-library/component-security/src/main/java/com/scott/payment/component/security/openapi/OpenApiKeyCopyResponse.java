package com.scott.payment.component.security.openapi;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyCopyResponse
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : Open API Key Copy Response 传输模型，位于 公共组件库，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
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
