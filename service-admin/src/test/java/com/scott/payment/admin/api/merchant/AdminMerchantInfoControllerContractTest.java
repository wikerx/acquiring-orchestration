package com.scott.payment.admin.api.merchant;

import com.scott.payment.admin.application.merchant.AdminMerchantInfoApplicationService;
import com.scott.payment.component.core.auth.InternalAuthChecker;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.web.auth.InternalAuthInterceptor;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商户管理高风险动作权限契约，防止激活再次退化为普通状态切换权限。
 */
class AdminMerchantInfoControllerContractTest {

    @Test
    void currentCapabilityUsesMerchantDetailPermission() throws Exception {
        Method detail = AdminMerchantInfoController.class.getMethod("getMerchant", Long.class);
        assertThat(detail.getAnnotation(RequiresPermission.class).value())
                .isEqualTo("merchant:info:detail");
    }

    @Test
    void currentCapabilityDoesNotReachDetailForOperatorWithoutPermission() throws Exception {
        var auth = mock(InternalAuthChecker.class);
        var application = mock(AdminMerchantInfoApplicationService.class);
        String path = "/admin/merchants/1";
        String authorization = "Bearer test-only";
        when(auth.check("ADMIN", authorization, "GET", path, "merchant:info:detail"))
                .thenThrow(new ServiceException(ApiResultEnum.FORBIDDEN));
        var mvc = MockMvcBuilders.standaloneSetup(new AdminMerchantInfoController(application))
                .addInterceptors(new InternalAuthInterceptor("ADMIN", auth, List.of()))
                .build();

        mvc.perform(get(path).header("Authorization", authorization)).andExpect(status().isForbidden());

        verifyNoInteractions(application);
    }

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
