package com.scott.payment.merchant.api.access;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.merchant.application.access.MerchantAccessConfigApplicationService;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.IpWhitelistSubmitRequest;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlSubmitRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 商户访问配置接口的认证商户隔离测试。
 */
class MerchantAccessConfigControllerTests {

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void shouldUseAuthenticatedMerchantForSourceUrlSubmission() {
        MerchantAccessConfigApplicationService applicationService =
                mock(MerchantAccessConfigApplicationService.class);
        MerchantAccessConfigController controller =
                new MerchantAccessConfigController(applicationService);
        InternalAuthAccount account = new InternalAuthAccount();
        account.setMerchantId("M10000001");
        InternalAuthContextHolder.set(account);
        SourceUrlSubmitRequest request = new SourceUrlSubmitRequest();
        request.setSourceUrls(List.of("https://shop.example.com"));

        controller.submitSourceUrls(request);

        verify(applicationService).submitSourceUrls("M10000001", request);
    }

    @Test
    void shouldRejectRequestWithoutMerchantContext() {
        MerchantAccessConfigApplicationService applicationService =
                mock(MerchantAccessConfigApplicationService.class);
        MerchantAccessConfigController controller =
                new MerchantAccessConfigController(applicationService);

        assertThatThrownBy(controller::sourceUrls)
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("merchant context missing");
    }

    @Test
    void shouldSeparateSourceUrlAndIpWhitelistPermissions() throws NoSuchMethodException {
        assertPermission("sourceUrls", "merchant:access-config:source-url:list");
        assertPermission("submitSourceUrls", "merchant:access-config:source-url:submit",
                SourceUrlSubmitRequest.class);
        assertPermission("ipWhitelists", "merchant:access-config:ip-whitelist:list");
        assertPermission("submitIpWhitelists", "merchant:access-config:ip-whitelist:submit",
                IpWhitelistSubmitRequest.class);
    }

    private void assertPermission(String methodName, String expectedPermission, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        RequiresPermission permission = MerchantAccessConfigController.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(RequiresPermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo(expectedPermission);
    }
}
