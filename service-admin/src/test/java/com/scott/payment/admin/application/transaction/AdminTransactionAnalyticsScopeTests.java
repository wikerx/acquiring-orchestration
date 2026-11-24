package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ChannelDiagnosisQuery;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminTransactionAnalyticsQueryService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminTransactionAnalyticsScopeTests {
    private final AdminTransactionAnalyticsQueryService queries = mock(AdminTransactionAnalyticsQueryService.class);
    private final AdminMerchantDataScopeResolver scopes = mock(AdminMerchantDataScopeResolver.class);
    private final AdminTransactionAnalyticsApplicationService service = new AdminTransactionAnalyticsApplicationService(queries, scopes);

    @AfterEach
    void clear() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void requiresPageAccessBeforeResolvingMerchantScope() {
        setAccount("transaction:analytics:channels");
        assertThatThrownBy(() -> service.channelBreakdown(new AnalyticsQuery())).isInstanceOf(ServiceException.class);
        verifyNoInteractions(queries, scopes);
    }

    @Test
    void protectsFailureRowsWithExistingTransactionListPermission() {
        var account = setAccount("transaction:analytics:view", "transaction:analytics:channels");
        var scope = AdminMerchantDataScope.limited(Set.of("M_A"));
        when(scopes.resolve(account)).thenReturn(scope);
        var query = new ChannelDiagnosisQuery();
        assertThatThrownBy(() -> service.channelFailedTransactions(query)).isInstanceOf(ServiceException.class);
        verifyNoInteractions(queries);

        account.setPermissions(List.of("transaction:analytics:view", "transaction:analytics:channels", "transaction:operation:list"));
        service.channelFailedTransactions(query);
        verify(queries).channelFailedTransactions(query, scope);
    }

    private InternalAuthAccount setAccount(String... permissions) {
        var account = new InternalAuthAccount();
        account.setAppCode("ADMIN");
        account.setPermissions(List.of(permissions));
        InternalAuthContextHolder.set(account);
        return account;
    }
}
