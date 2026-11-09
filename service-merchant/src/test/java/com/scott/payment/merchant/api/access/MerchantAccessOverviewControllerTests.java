package com.scott.payment.merchant.api.access;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.merchant.application.access.MerchantAccessOverviewApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 商户接入概览接口的认证商户边界和权限契约测试。
 */
class MerchantAccessOverviewControllerTests {

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void shouldUseAuthenticatedMerchantWithoutAcceptingMerchantParameter() {
        MerchantAccessOverviewApplicationService applicationService =
                mock(MerchantAccessOverviewApplicationService.class);
        MerchantAccessOverviewController controller =
                new MerchantAccessOverviewController(applicationService);
        InternalAuthAccount account = new InternalAuthAccount();
        account.setMerchantId("M10000001");
        InternalAuthContextHolder.set(account);

        controller.overview();

        verify(applicationService).queryOverview("M10000001");
    }

    @Test
    void shouldRejectRequestWithoutMerchantContext() {
        MerchantAccessOverviewController controller = new MerchantAccessOverviewController(
                mock(MerchantAccessOverviewApplicationService.class));

        assertThatThrownBy(controller::overview)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("merchant context missing");
    }

    @Test
    void shouldRequireDedicatedOverviewPermission() throws NoSuchMethodException {
        RequiresPermission permission = MerchantAccessOverviewController.class
                .getMethod("overview")
                .getAnnotation(RequiresPermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo("merchant:access-config:overview:view");
    }
}
