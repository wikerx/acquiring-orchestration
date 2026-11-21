package com.scott.payment.merchant.application.merchantpayment;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.capability.governance.MerchantPaymentGovernanceService;
import com.scott.payment.capability.governance.MerchantPaymentGovernanceService.Scope;
import com.scott.payment.capability.governance.MerchantPaymentRepository.Actor;
import com.scott.payment.capability.governance.MerchantPaymentScope;
import com.scott.payment.capability.governance.MerchantPaymentModels.Apply;
import com.scott.payment.capability.governance.MerchantPaymentModels.Search;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MerchantPaymentMethodApplicationServiceTests {
    private final MerchantPaymentGovernanceService owner = mock(MerchantPaymentGovernanceService.class);
    private final MerchantPaymentReads reads = mock(MerchantPaymentReads.class);
    private final MerchantPaymentMethodApplicationService app = new MerchantPaymentMethodApplicationService(owner, reads);

    @AfterEach
    void clearSession() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void applicationUsesAccountAndTenantFromSession() {
        authenticate();
        var command = new Apply("request", List.of("METHOD:APPLE_PAY"), null);

        Scope scope = new Scope(MerchantPaymentScope.limited(java.util.Set.of("200045")),
                new Actor("MERCHANT", 17L, 19L, null));
        when(reads.scope("200045")).thenReturn(scope);

        app.apply(command);

        verify(owner).apply(scope, "200045", command);
    }

    @Test
    void anotherMerchantFilterNeverReachesTheOwner() {
        authenticate();
        Search query = new Search();
        query.setMerchantId("200046");

        assertThatThrownBy(() -> app.applications(query))
                .isInstanceOfSatisfying(ServiceException.class,
                        error -> assertThat(error.getCode()).isEqualTo(ApiResultEnum.FORBIDDEN.getCode()));
        verifyNoInteractions(owner, reads);
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
