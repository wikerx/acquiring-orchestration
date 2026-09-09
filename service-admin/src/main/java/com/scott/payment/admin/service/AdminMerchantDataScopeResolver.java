package com.scott.payment.admin.service;

import com.scott.payment.component.core.auth.InternalAuthAccount;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantDataScopeResolver
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 从当前 Admin 账号的有效角色分配解析商户数据范围。
 * @status : create
 */
public interface AdminMerchantDataScopeResolver {

    /**
     * 根据可信登录账号的有效角色解析可访问商户范围。
     *
     * @param account 内部鉴权拦截器解析出的当前 Admin 账号
     * @return 全部商户、限定商户集合或空范围；不得返回 null
     */
    AdminMerchantDataScope resolve(InternalAuthAccount account);
}
