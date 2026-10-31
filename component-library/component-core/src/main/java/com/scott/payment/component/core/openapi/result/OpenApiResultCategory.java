package com.scott.payment.component.core.openapi.result;

/**
 * OpenAPI V1.3 返回码的职责类别。
 *
 * <p>类别只描述请求、配置、交易或故障的归属边界，不能单独推导成功、失败或可重试语义。</p>
 */
public enum OpenApiResultCategory {
    A,
    C,
    T,
    F
}
