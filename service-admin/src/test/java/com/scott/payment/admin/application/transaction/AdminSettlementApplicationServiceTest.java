package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.settlement.SettlementInternalClient;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminSettlementQueryService;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalBatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementApplicationServiceTest
 * @date : 2026-09-01 23:20
 * @email : scott_x@163.com
 * @description : 验证 Admin 结算查询边界及资金命令绑定可信登录操作人的应用服务测试
 * @status : create
 */
class AdminSettlementApplicationServiceTest {

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void searchAndDetailShouldUseAdminLocalQueryServiceOnly() {
        SettlementInternalClient client = mock(SettlementInternalClient.class);
        AdminSettlementQueryService queryService = mock(AdminSettlementQueryService.class);
        AdminMerchantDataScopeResolver scopeResolver = mock(AdminMerchantDataScopeResolver.class);
        AdminMerchantDataScope scope = AdminMerchantDataScope.all();
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        when(scopeResolver.resolve(account)).thenReturn(scope);
        AdminSettlementApplicationService service = new AdminSettlementApplicationService(
                client, queryService, scopeResolver);
        BatchSearchRequest request = new BatchSearchRequest();
        request.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        request.setEndBusinessDate(LocalDate.of(2026, 8, 31));

        service.search(request);
        service.detail(" SB20260826-00000001 ");

        verify(queryService).search(request, scope);
        verify(queryService).detail("SB20260826-00000001", scope);
        verifyNoInteractions(client);
    }

    @Test
    void cancelShouldBindTrustedOperatorAndNormalizeAuditFields() {
        SettlementInternalClient client = mock(SettlementInternalClient.class);
        AdminSettlementQueryService queryService = mock(AdminSettlementQueryService.class);
        AdminMerchantDataScopeResolver scopeResolver = mock(AdminMerchantDataScopeResolver.class);
        AdminSettlementApplicationService service = new AdminSettlementApplicationService(
                client, queryService, scopeResolver);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        AdminMerchantDataScope scope = AdminMerchantDataScope.limited(java.util.Set.of("M1001"));
        when(scopeResolver.resolve(account)).thenReturn(scope);
        BatchCommandRequest request = new BatchCommandRequest();
        request.setRequestKey(" REQ-SETTLEMENT-1 ");
        request.setExpectedVersion(5L);
        request.setReason(" approved after ledger review ");
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.8, 10.0.0.9");
        when(servletRequest.getHeader("User-Agent")).thenReturn("JUnit Admin");

        service.cancel(" SB20260826-00000001 ", request, servletRequest);

        ArgumentCaptor<InternalBatchCommandRequest> cancel =
                ArgumentCaptor.forClass(InternalBatchCommandRequest.class);
        verify(client).cancel(org.mockito.ArgumentMatchers.eq("SB20260826-00000001"), cancel.capture());
        assertThat(cancel.getValue().getRequestKey()).isEqualTo("REQ-SETTLEMENT-1");
        assertThat(cancel.getValue().getReason()).isEqualTo("approved after ledger review");
        assertThat(cancel.getValue().getOperatorId()).isEqualTo(88L);
        assertThat(cancel.getValue().getOperatorName()).isEqualTo("Settlement Operator");
        assertThat(cancel.getValue().getRoleSnapshot()).isEqualTo("FINANCE,SETTLEMENT_OPERATOR");
        assertThat(cancel.getValue().getClientIp()).isEqualTo("10.0.0.8");
        assertThat(cancel.getValue().getUserAgent()).isEqualTo("JUnit Admin");
        assertThat(cancel.getValue().getOperationTime()).isNotNull();
        verify(queryService).requireBatchAccess("SB20260826-00000001", scope);
    }

    private InternalAuthAccount adminAccount() {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("ADMIN");
        account.setAppId(1L);
        account.setAccountId(88L);
        account.setLoginAccount("settlement.ops@example.com");
        account.setRealName("Settlement Operator");
        account.setRoles(List.of("SETTLEMENT_OPERATOR", "FINANCE", "FINANCE"));
        return account;
    }
}
