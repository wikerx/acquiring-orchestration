package com.scott.payment.admin.api.merchant;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户管理高风险动作权限契约，防止激活再次退化为普通状态切换权限。
 */
class AdminMerchantInfoControllerContractTest {

    @Test
    void activateShouldUseDedicatedPermission() throws Exception {
        Method activate = AdminMerchantInfoController.class.getMethod("activate", Long.class);
        Method updateStatus = AdminMerchantInfoController.class.getMethod(
                "updateStatus", Long.class,
                com.scott.payment.admin.dto.merchant.AdminMerchantStatusRequest.class);

        assertThat(activate.getAnnotation(RequiresPermission.class).value())
                .isEqualTo("merchant:info:activate");
        assertThat(updateStatus.getAnnotation(RequiresPermission.class).value())
                .isEqualTo("merchant:info:changeStatus");
    }
}
