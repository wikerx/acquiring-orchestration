package com.scott.payment.component.core.openapi.result;

/**
 * OpenAPI 返回码定义的发布生命周期。
 *
 * <p>生命周期区分草案、登记、启用和停用状态；已经发布的编码不得被重新分配为其他语义。</p>
 */
public enum OpenApiResultLifecycle {
    DRAFT,
    REGISTERED,
    ACTIVE,
    RETIRED
}
