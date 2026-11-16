package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.settlement.SettlementInternalClient;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalProfileCreateRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalProfileVersionCreateRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileCreateRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileUpdateRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminSettlementProfileService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.security.GatewayIngressSignature;
import com.scott.payment.component.web.gateway.GatewayIngressAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证结算档案写命令先执行 Admin 数据范围校验，再注入可信操作审计调用 owner service。
 */
class AdminSettlementProfileApplicationServiceTest {

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void createShouldBindTrustedOperatorAndReadCommittedProfileFromPrimary() {
        AdminSettlementProfileService profileService = mock(AdminSettlementProfileService.class);
        AdminMerchantDataScopeResolver scopeResolver = mock(AdminMerchantDataScopeResolver.class);
        SettlementInternalClient client = mock(SettlementInternalClient.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        AdminMerchantDataScope scope = AdminMerchantDataScope.limited(Set.of("M1001"));
        when(scopeResolver.resolve(account)).thenReturn(scope);
        ProfileCommandResponse commandResponse = new ProfileCommandResponse();
        commandResponse.setSettlementProfileNo("MSP-NEW");
        when(client.createProfile(org.mockito.ArgumentMatchers.any())).thenReturn(commandResponse);
        ProfileSummary stored = new ProfileSummary();
        stored.setSettlementProfileNo("MSP-NEW");
        when(profileService.detail("MSP-NEW", scope)).thenReturn(stored);
        AdminSettlementProfileApplicationService service = new AdminSettlementProfileApplicationService(
                profileService, scopeResolver, client);

        ProfileCreateRequest request = new ProfileCreateRequest();
        request.setRequestKey("REQ-PROFILE-1");
        request.setMerchantId("M1001");
        request.setSettlementAccountId(9L);
        request.setTargetCurrency("USD");
        request.setBusinessTimeZone("Asia/Shanghai");
        request.setDailyCutoffTime(LocalTime.MIDNIGHT);
        request.setProcessingMode("AUTO_POST");
        request.setReason("initial setup");

        ProfileSummary result = service.create(request, servletRequest());

        assertThat(result.getSettlementProfileNo()).isEqualTo("MSP-NEW");
        ArgumentCaptor<InternalProfileCreateRequest> command =
                ArgumentCaptor.forClass(InternalProfileCreateRequest.class);
        verify(client).createProfile(command.capture());
        assertThat(command.getValue().getOperatorId()).isEqualTo(88L);
        assertThat(command.getValue().getOperatorName()).isEqualTo("Settlement Operator");
        assertThat(command.getValue().getRoleSnapshot()).isEqualTo("FINANCE,SETTLEMENT_OPERATOR");
        assertThat(command.getValue().getClientIp()).isEqualTo("10.0.0.8");
        assertThat(command.getValue().getOperationTime()).isNotNull();
        verify(profileService).detail("MSP-NEW", scope);
    }

    @Test
    void updateShouldVerifySourceProfileScopeBeforeCallingSettlementService() {
        AdminSettlementProfileService profileService = mock(AdminSettlementProfileService.class);
        AdminMerchantDataScopeResolver scopeResolver = mock(AdminMerchantDataScopeResolver.class);
        SettlementInternalClient client = mock(SettlementInternalClient.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        AdminMerchantDataScope scope = AdminMerchantDataScope.limited(Set.of("M1001"));
        when(scopeResolver.resolve(account)).thenReturn(scope);
        when(profileService.detail("MSP-OLD", scope)).thenReturn(new ProfileSummary());
        ProfileCommandResponse commandResponse = new ProfileCommandResponse();
        commandResponse.setSettlementProfileNo("MSP-NEW");
        when(client.createProfileVersion(eq("MSP-OLD"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(commandResponse);
        ProfileSummary stored = new ProfileSummary();
        stored.setSettlementProfileNo("MSP-NEW");
        when(profileService.detail("MSP-NEW", scope)).thenReturn(stored);
        AdminSettlementProfileApplicationService service = new AdminSettlementProfileApplicationService(
                profileService, scopeResolver, client);
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setRequestKey("REQ-PROFILE-V2");
        request.setExpectedVersion(3L);
        request.setBusinessTimeZone("Asia/Tokyo");
        request.setDailyCutoffTime(LocalTime.of(23, 30));
        request.setProcessingMode("AUTO_REVIEW");
        request.setReason("change calendar");

        service.update("MSP-OLD", request, servletRequest());

        ArgumentCaptor<InternalProfileVersionCreateRequest> command =
                ArgumentCaptor.forClass(InternalProfileVersionCreateRequest.class);
        verify(client).createProfileVersion(eq("MSP-OLD"), command.capture());
        assertThat(command.getValue().getRequestKey()).isEqualTo("REQ-PROFILE-V2");
        assertThat(command.getValue().getExpectedVersion()).isEqualTo(3L);
        assertThat(command.getValue().getOperatorId()).isEqualTo(88L);
        verify(profileService).detail("MSP-OLD", scope);
        verify(profileService).detail("MSP-NEW", scope);
    }

    @Test
    void createShouldRejectMerchantOutsideDataScopeBeforeRemoteCall() {
        AdminSettlementProfileService profileService = mock(AdminSettlementProfileService.class);
        AdminMerchantDataScopeResolver scopeResolver = mock(AdminMerchantDataScopeResolver.class);
        SettlementInternalClient client = mock(SettlementInternalClient.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        when(scopeResolver.resolve(account)).thenReturn(AdminMerchantDataScope.limited(Set.of("M1001")));
        AdminSettlementProfileApplicationService service = new AdminSettlementProfileApplicationService(
                profileService, scopeResolver, client);
        ProfileCreateRequest request = new ProfileCreateRequest();
        request.setMerchantId("M2002");

        assertThatThrownBy(() -> service.create(request, servletRequest()))
                .hasMessageContaining("不在当前数据范围");

        verifyNoInteractions(client);
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

    private HttpServletRequest servletRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(GatewayIngressSignature.HEADER_CLIENT_IP)).thenReturn("10.0.0.8");
        when(request.getAttribute(GatewayIngressAuthFilter.GATEWAY_CLIENT_IP_AUTHENTICATED_ATTRIBUTE))
                .thenReturn(Boolean.TRUE);
        when(request.getHeader("User-Agent")).thenReturn("JUnit Admin");
        return request;
    }
}
