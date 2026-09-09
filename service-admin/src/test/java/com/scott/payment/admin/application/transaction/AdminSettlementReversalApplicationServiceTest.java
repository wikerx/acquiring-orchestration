package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.settlement.SettlementInternalClient;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReversalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSubmitRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminSettlementQueryService;
import com.scott.payment.admin.service.AdminSettlementReversalQueryService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementReversalApplicationServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Admin 冲正命令先校验数据范围，再注入可信 Maker/Checker 快照。
 * @status : create
 */
class AdminSettlementReversalApplicationServiceTest {

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void submitShouldCheckBatchScopeAndInjectTrustedMaker() {
        Fixture fixture = fixture();
        ReversalSubmitRequest request = new ReversalSubmitRequest();
        request.setRequestKey(" CREATE-REV-1 ");
        request.setOriginalBatchNo(" SB20260830-00000001 ");
        request.setExpectedBatchVersion(7L);
        request.setReason(" duplicate posting confirmed ");

        fixture.service.submit(request, servletRequest());

        verify(fixture.batchQueryService).requireBatchAccess("SB20260830-00000001", fixture.scope);
        ArgumentCaptor<InternalReversalSubmitRequest> captor =
                ArgumentCaptor.forClass(InternalReversalSubmitRequest.class);
        verify(fixture.client).submitReversal(captor.capture());
        assertThat(captor.getValue().getRequestKey()).isEqualTo("CREATE-REV-1");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(88L);
        assertThat(captor.getValue().getOperatorName()).isEqualTo("Settlement Maker");
        assertThat(captor.getValue().getRoleSnapshot()).isEqualTo("FINANCE,SETTLEMENT_MAKER");
        assertThat(captor.getValue().getClientIp()).isEqualTo("203.0.113.10");
    }

    @Test
    void decisionShouldCheckReversalScopeAndInjectTrustedChecker() {
        Fixture fixture = fixture();
        ReversalDecisionRequest request = new ReversalDecisionRequest();
        request.setRequestKey(" DECIDE-REV-1 ");
        request.setExpectedVersion(0L);
        request.setComment(" immutable snapshots checked ");

        fixture.service.decide(" SRO20260831-00000001 ", "APPROVE", request, servletRequest());

        verify(fixture.reversalQueryService).requireAccess("SRO20260831-00000001", fixture.scope);
        ArgumentCaptor<InternalReversalDecisionRequest> captor =
                ArgumentCaptor.forClass(InternalReversalDecisionRequest.class);
        verify(fixture.client).decideReversal(
                org.mockito.ArgumentMatchers.eq("SRO20260831-00000001"), captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo("APPROVE");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(88L);
        assertThat(captor.getValue().getComment()).isEqualTo("immutable snapshots checked");
    }

    @Test
    void browserDtosShouldNotExposeOperatorIdentityFields() {
        assertThat(Arrays.stream(ReversalSubmitRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)).doesNotContain(
                "operatorId", "operatorName", "roleSnapshot", "clientIp", "userAgent", "operationTime");
        assertThat(Arrays.stream(ReversalDecisionRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)).doesNotContain(
                "operatorId", "operatorName", "roleSnapshot", "clientIp", "userAgent", "operationTime");
    }

    private Fixture fixture() {
        SettlementInternalClient client = mock(SettlementInternalClient.class);
        AdminSettlementQueryService batchQueryService = mock(AdminSettlementQueryService.class);
        AdminSettlementReversalQueryService reversalQueryService =
                mock(AdminSettlementReversalQueryService.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        AdminMerchantDataScope scope = AdminMerchantDataScope.limited(Set.of("M1001"));
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("ADMIN");
        account.setAppId(1L);
        account.setAccountId(88L);
        account.setLoginAccount("maker@example.test");
        account.setRealName("Settlement Maker");
        account.setRoles(List.of("SETTLEMENT_MAKER", "FINANCE", "FINANCE"));
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(scope);
        AdminSettlementReversalApplicationService service = new AdminSettlementReversalApplicationService(
                client, batchQueryService, reversalQueryService, resolver);
        return new Fixture(service, client, batchQueryService, reversalQueryService, scope);
    }

    private HttpServletRequest servletRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Admin-Test");
        return request;
    }

    private record Fixture(AdminSettlementReversalApplicationService service,
                           SettlementInternalClient client,
                           AdminSettlementQueryService batchQueryService,
                           AdminSettlementReversalQueryService reversalQueryService,
                           AdminMerchantDataScope scope) {
    }
}
