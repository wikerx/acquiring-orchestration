package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.clearing.ClearingInternalClient;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.InternalReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.InternalSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.Response;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.ReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminLabelPrincipalRepairDTOs.SubmitRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证历史标签本金修复只接受全商户数据范围操作人，并由服务端注入可信 Maker/Checker。
 */
class AdminLabelPrincipalRepairApplicationServiceTest {

    private static final String REPAIR_NO = "LPR0123456789abcdef0123456789abcdef";

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void submitShouldNormalizeRequestAndInjectTrustedMaker() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = account(88L, "Maker");
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.all());
        Response response = new Response();
        response.setRepairNo("LPR-1");
        when(client.submitLabelPrincipalRepair(org.mockito.ArgumentMatchers.any())).thenReturn(response);
        AdminLabelPrincipalRepairApplicationService service =
                new AdminLabelPrincipalRepairApplicationService(client, resolver);
        SubmitRequest request = submitRequest();
        request.setRequestKey("  REQ-1  ");
        request.setReason("  historical principal  ");

        assertThat(service.submit(request).getRepairNo()).isEqualTo("LPR-1");

        ArgumentCaptor<InternalSubmitRequest> command =
                ArgumentCaptor.forClass(InternalSubmitRequest.class);
        verify(client).submitLabelPrincipalRepair(command.capture());
        assertThat(command.getValue().getRequestKey()).isEqualTo("REQ-1");
        assertThat(command.getValue().getReason()).isEqualTo("historical principal");
        assertThat(command.getValue().getSubmitOperator()).isEqualTo("admin-account:88");
    }

    @Test
    void reviewShouldInjectDifferentTrustedChecker() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = account(99L, "Checker");
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.all());
        when(client.reviewLabelPrincipalRepair(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new Response());
        AdminLabelPrincipalRepairApplicationService service =
                new AdminLabelPrincipalRepairApplicationService(client, resolver);
        ReviewRequest request = new ReviewRequest();
        request.setExpectedRequestVersion(0L);
        request.setDecision(" approve ");
        request.setReviewComment(" reviewed ");

        service.review(" " + REPAIR_NO + " ", request);

        ArgumentCaptor<InternalReviewRequest> command =
                ArgumentCaptor.forClass(InternalReviewRequest.class);
        verify(client).reviewLabelPrincipalRepair(
                org.mockito.ArgumentMatchers.eq(REPAIR_NO), command.capture());
        assertThat(command.getValue().getDecision()).isEqualTo("APPROVE");
        assertThat(command.getValue().getReviewComment()).isEqualTo("reviewed");
        assertThat(command.getValue().getReviewOperator()).isEqualTo("admin-account:99");
    }

    @Test
    void submitShouldRejectLimitedMerchantScopeEvenWhenEndpointPermissionWasGranted() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = account(88L, "Maker");
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.limited(Set.of("M-1")));
        AdminLabelPrincipalRepairApplicationService service =
                new AdminLabelPrincipalRepairApplicationService(client, resolver);

        assertThatExceptionOfType(ServiceException.class)
                .isThrownBy(() -> service.submit(submitRequest()))
                .satisfies(exception -> assertThat(exception.getCode())
                        .isEqualTo(ApiResultEnum.FORBIDDEN.getCode()));
        verifyNoInteractions(client);
    }

    @Test
    void submitShouldRejectBrowserModelWithInvalidVersionBeforeRemoteCall() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        AdminLabelPrincipalRepairApplicationService service =
                new AdminLabelPrincipalRepairApplicationService(client, resolver);
        SubmitRequest request = submitRequest();
        request.setExpectedCandidateVersion(-1L);

        assertThatExceptionOfType(ServiceException.class)
                .isThrownBy(() -> service.submit(request))
                .satisfies(exception -> assertThat(exception.getCode())
                        .isEqualTo(ApiResultEnum.PARAM_INVALID.getCode()));
        verifyNoInteractions(client, resolver);
    }

    private SubmitRequest submitRequest() {
        SubmitRequest request = new SubmitRequest();
        request.setRequestKey("REQ-1");
        request.setTransactionId("TX-1");
        request.setTransactionDateTime(LocalDateTime.of(2026, 4, 1, 10, 0));
        request.setExpectedFinanceStateVersion(7);
        request.setExpectedClearingRevision(2);
        request.setExpectedCandidateVersion(3L);
        request.setReason("historical principal");
        return request;
    }

    private InternalAuthAccount account(long id, String name) {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("ADMIN");
        account.setAppId(1L);
        account.setAccountId(id);
        account.setLoginAccount(name.toLowerCase() + "@example.com");
        account.setRealName(name);
        account.setRoles(List.of("SUPER_ADMIN"));
        return account;
    }
}
