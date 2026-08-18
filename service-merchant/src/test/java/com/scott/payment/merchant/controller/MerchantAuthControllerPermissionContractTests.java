package com.scott.payment.merchant.controller;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAuthControllerPermissionContractTests
 * @date : 2026-08-02 19:45
 * @description : 锁定商户会话自查询与退出接口只要求有效登录态，不错误依赖任一业务菜单权限。
 * @status : create
 */
class MerchantAuthControllerPermissionContractTests {

    /**
     * 会话恢复和退出必须对所有已认证商户账号开放。
     *
     * @throws NoSuchMethodException 控制器契约被意外改名时由测试直接失败
     */
    @Test
    void sessionSelfServiceEndpointsShouldNotRequireDashboardPermission() throws NoSuchMethodException {
        assertThat(MerchantAuthController.class.getMethod("me", String.class)
                        .isAnnotationPresent(RequiresPermission.class))
                .isFalse();
        assertThat(MerchantAuthController.class.getMethod("logout", String.class)
                        .isAnnotationPresent(RequiresPermission.class))
                .isFalse();
    }
}
