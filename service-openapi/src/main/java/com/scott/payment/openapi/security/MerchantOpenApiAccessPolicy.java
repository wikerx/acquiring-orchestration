package com.scott.payment.openapi.security;

import com.fasterxml.jackson.annotation.JsonSetter;
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
 * <p>该对象只包含规范化 IP，不包含商户联系人或任何密钥材料。</p>
 */
@Data
public class MerchantOpenApiAccessPolicy {

    /**
     * 审核通过、启用且未删除的规范化精确 IP 集合。
     */
    private Set<String> allowedIps = new LinkedHashSet<>();

    /**
     * 接收旧版缓存中的配置存在标识；当前访问规则不再使用该开关。
     *
     * @param ignored 旧版字段值
     */
    @JsonSetter("configured")
    void acceptLegacyConfigured(Boolean ignored) {
        // Compatibility-only input: an empty effective whitelist must still be denied.
    }

    /**
     * 接收旧版缓存中的白名单开关；当前访问规则只以有效 IP 集合为准。
     *
     * @param ignored 旧版字段值
     */
    @JsonSetter("whitelistEnabled")
    void acceptLegacyWhitelistEnabled(Boolean ignored) {
        // Compatibility-only input: never restore the retired allow/skip switch.
    }
}
