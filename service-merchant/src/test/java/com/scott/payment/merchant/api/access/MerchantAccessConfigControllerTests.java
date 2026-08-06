package com.scott.payment.merchant.api.access;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.merchant.application.access.MerchantAccessConfigApplicationService;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlSubmitRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
