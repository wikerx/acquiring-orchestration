package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.settlement.SettlementInternalClient;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.DecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.InternalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.InternalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementPrincipalCorrectionDTOs.SubmitRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminSettlementQueryService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.security.GatewayIngressSignature;
import com.scott.payment.component.web.gateway.GatewayIngressAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Admin 仅从可信全商户上下文注入历史本金调整 Maker/Checker 身份。
 */
class AdminSettlementPrincipalCorrectionApplicationServiceTest {

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void submitShouldCheckBatchAndInjectTrustedMaker() {
        Fixture fixture = fixture();
        SubmitRequest request = new SubmitRequest();
        request.setRequestKey(" CREATE-PC-1 ");
        request.setOriginalBatchNo(" SB20261031-00000001 ");
        request.setExpectedBatchVersion(7L);
        request.setReason(" historical label principal mismatch ");

        fixture.service.submit(request, servletRequest());

        verify(fixture.batchQueryService).requireBatchAccess(
                "SB20261031-00000001", AdminMerchantDataScope.all());
        ArgumentCaptor<InternalSubmitRequest> captor =
                ArgumentCaptor.forClass(InternalSubmitRequest.class);
        verify(fixture.client).submitPrincipalCorrection(captor.capture());
        assertThat(captor.getValue().getOperatorId()).isEqualTo(88L);
        assertThat(captor.getValue().getOperatorName()).isEqualTo("Settlement Maker");
        assertThat(captor.getValue().getRoleSnapshot()).isEqualTo("FINANCE,SUPER_ADMIN");
        assertThat(captor.getValue().getClientIp()).isEqualTo("203.0.113.10");
    }

    @Test
    void decisionShouldInjectServerControlledCheckerAndDecision() {
        Fixture fixture = fixture();
        DecisionRequest request = new DecisionRequest();
        request.setRequestKey(" DECIDE-PC-1 ");
        request.setExpectedVersion(0L);
        request.setComment(" source snapshot checked ");

        fixture.service.decide(" SPC0123456789abcdef0123456789abcdef ",
                "APPROVE", request, servletRequest());

        ArgumentCaptor<InternalDecisionRequest> captor =
                ArgumentCaptor.forClass(InternalDecisionRequest.class);
        verify(fixture.client).decidePrincipalCorrection(
                org.mockito.ArgumentMatchers.eq("SPC0123456789abcdef0123456789abcdef"),
                captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo("APPROVE");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(88L);
        assertThat(captor.getValue().getComment()).isEqualTo("source snapshot checked");
    }

    @Test
    void browserRequestsShouldNotExposeOperatorFields() {
        assertThat(Arrays.stream(SubmitRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)).doesNotContain(
                "operatorId", "operatorName", "roleSnapshot", "clientIp", "userAgent", "operationTime");
        assertThat(Arrays.stream(DecisionRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)).doesNotContain(
                "operatorId", "operatorName", "roleSnapshot", "clientIp", "userAgent", "operationTime",
                "decision");
    }

    @Test
    void limitedMerchantScopeShouldBeRejectedBeforeOwnerServiceCall() {
        Fixture fixture = fixture();
        when(fixture.dataScopeResolver.resolve(InternalAuthContextHolder.get()))
                .thenReturn(AdminMerchantDataScope.limited(Set.of("M1001")));
        SubmitRequest request = new SubmitRequest();
        request.setRequestKey("CREATE-PC-LIMITED");
        request.setOriginalBatchNo("SB20261031-00000001");
        request.setExpectedBatchVersion(7L);
        request.setReason("historical label principal mismatch");

        assertThatThrownBy(() -> fixture.service.submit(request, servletRequest()))
                .isInstanceOf(ServiceException.class);
        verify(fixture.client, never()).submitPrincipalCorrection(
                org.mockito.ArgumentMatchers.any(InternalSubmitRequest.class));
    }

    private Fixture fixture() {
        SettlementInternalClient client = mock(SettlementInternalClient.class);
        AdminSettlementQueryService batchQueryService = mock(AdminSettlementQueryService.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("ADMIN");
        account.setAppId(1L);
        account.setAccountId(88L);
        account.setLoginAccount("maker@example.test");
        account.setRealName("Settlement Maker");
        account.setRoles(List.of("SUPER_ADMIN", "FINANCE", "FINANCE"));
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.all());
        return new Fixture(new AdminSettlementPrincipalCorrectionApplicationService(
                client, batchQueryService, resolver), client, batchQueryService, resolver);
    }

    private HttpServletRequest servletRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(GatewayIngressSignature.HEADER_CLIENT_IP)).thenReturn("203.0.113.10");
        when(request.getAttribute(GatewayIngressAuthFilter.GATEWAY_CLIENT_IP_AUTHENTICATED_ATTRIBUTE))
                .thenReturn(Boolean.TRUE);
        when(request.getHeader("User-Agent")).thenReturn("Admin-Test");
        return request;
    }

    private record Fixture(AdminSettlementPrincipalCorrectionApplicationService service,
                           SettlementInternalClient client,
                           AdminSettlementQueryService batchQueryService,
                           AdminMerchantDataScopeResolver dataScopeResolver) {
    }
}
