package com.scott.payment.openapi.security;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiAccessPolicy
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 白名单运行时策略。
 * @status : create
 *
 *
 * <p>该对象只包含访问开关和规范化 IP，不包含商户联系人或任何密钥材料。</p>
 */
@Data
public class MerchantOpenApiAccessPolicy {

    /**
     * 是否启用 IP 白名单校验。
     */
    private boolean whitelistEnabled;

    /**
     * 启用且未删除的规范化精确 IP 集合。
     */
    private Set<String> allowedIps = new LinkedHashSet<>();
}
