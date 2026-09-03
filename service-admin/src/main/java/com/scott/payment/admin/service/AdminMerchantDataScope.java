package com.scott.payment.admin.service;

import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantDataScope
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Admin 商户数据范围；allMerchants 与有限 merchantIds 明确区分，空集合绝不代表全量。
 * @status : create
 */
public record AdminMerchantDataScope(boolean allMerchants, Set<String> merchantIds) {

    public AdminMerchantDataScope {
        merchantIds = merchantIds == null ? Set.of() : Set.copyOf(merchantIds);
        if (allMerchants && !merchantIds.isEmpty()) {
            throw new IllegalArgumentException("all merchant scope must not carry merchant ids");
        }
    }

    /**
     * 创建可访问全部商户的管理端数据范围。
     * @return 允许访问全部商户的数据范围
     */
    public static AdminMerchantDataScope all() {
        return new AdminMerchantDataScope(true, Set.of());
    }

    /**
     * 创建仅允许访问指定商户集合的管理端数据范围。
     * @param merchantIds 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 仅允许访问指定商户集合的数据范围
     */
    public static AdminMerchantDataScope limited(Set<String> merchantIds) {
        return new AdminMerchantDataScope(false, merchantIds);
    }

    /**
     * 判断当前数据范围是否不允许访问任何商户。
     * @return 不允许访问任何商户时返回 true，否则返回 false
     */
    public boolean empty() {
        return !allMerchants && merchantIds.isEmpty();
    }
}
