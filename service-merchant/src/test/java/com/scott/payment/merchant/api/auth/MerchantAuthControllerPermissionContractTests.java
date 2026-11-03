package com.scott.payment.merchant.api.auth;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定商户会话自查询与退出接口只要求有效登录态，不错误依赖任一业务菜单权限。
 */
class MerchantAuthControllerPermissionContractTests {

    /**
     * 会话恢复和退出必须对所有已认证商户账号开放。
     *
     * @throws NoSuchMethodException 控制器契约被意外改名时由测试直接失败
     */
    @Test
    void sessionSelfServiceEndpointsShouldNotRequireDashboardPermission() throws NoSuchMethodException {
        assertThat(MerchantAuthController.class.getMethod("me", HttpServletRequest.class)
                        .isAnnotationPresent(RequiresPermission.class))
                .isFalse();
        assertThat(MerchantAuthController.class.getMethod(
                        "logout", HttpServletRequest.class, HttpServletResponse.class)
                        .isAnnotationPresent(RequiresPermission.class))
                .isFalse();
    }
}
