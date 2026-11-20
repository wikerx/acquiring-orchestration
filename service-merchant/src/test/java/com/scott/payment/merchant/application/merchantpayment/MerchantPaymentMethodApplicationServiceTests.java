package com.scott.payment.merchant.application.merchantpayment;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.merchant.client.admin.MerchantPaymentGovernanceClient;
import com.scott.payment.merchant.dto.merchantpayment.MerchantPaymentModels.Apply;
import com.scott.payment.merchant.dto.merchantpayment.MerchantPaymentModels.Search;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MerchantPaymentMethodApplicationServiceTests {
    private final MerchantPaymentGovernanceClient owner = mock(MerchantPaymentGovernanceClient.class);
    private final MerchantPaymentMethodApplicationService app = new MerchantPaymentMethodApplicationService(owner);

    @AfterEach
    void clearSession() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void applicationUsesAccountAndTenantFromSession() {
        authenticate();
        var command = new Apply("request", List.of("METHOD:APPLE_PAY"), null);

        app.apply(command);

        verify(owner).apply("200045", new MerchantPaymentGovernanceClient.Identity(17L, 19L), command);
    }

    @Test
    void anotherMerchantFilterNeverReachesTheOwner() {
        authenticate();
        Search query = new Search();
        query.setMerchantId("200046");

        assertThatThrownBy(() -> app.applications(query))
                .isInstanceOfSatisfying(ServiceException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ApiResultEnum.FORBIDDEN.getCode()));
        verifyNoInteractions(owner);
    }

    private void authenticate() {
        var account = new InternalAuthAccount();
        account.setAppCode("MERCHANT");
        account.setMerchantId("200045");
        account.setAccountId(17L);
        account.setUserId(19L);
        InternalAuthContextHolder.set(account);
    }
}
