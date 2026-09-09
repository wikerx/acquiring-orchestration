package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.settlement.SettlementInternalClient;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewDecisionTaskResumeRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalReviewSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalManualReviewPreviewRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ManualReviewPreviewRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ManualReviewStartRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewCandidateReference;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewDecisionTaskResumeRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSubmitRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminSettlementQueryService;
import com.scott.payment.admin.service.AdminSettlementReviewQueryService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementReviewApplicationServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Admin 预审命令先做商户范围校验，再注入登录账号和请求环境快照。
 * @status : create
 */
class AdminSettlementReviewApplicationServiceTest {

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void submitShouldPrecheckCandidateScopeAndInjectTrustedMaker() {
        Fixture fixture = fixture();
        ReviewSubmitRequest request = submitRequest();
        HttpServletRequest servletRequest = servletRequest();
        LocalDateTime before = LocalDateTime.now();

        fixture.service.submitTransactionReview(request, servletRequest);

        LocalDateTime after = LocalDateTime.now();
        verify(fixture.reviewQueryService).requireCandidateAccess(List.of(1L), fixture.scope);
        ArgumentCaptor<InternalReviewSubmitRequest> captor =
                ArgumentCaptor.forClass(InternalReviewSubmitRequest.class);
        verify(fixture.client).submitReview(captor.capture());
        InternalReviewSubmitRequest internal = captor.getValue();
        assertThat(internal.getRequestKey()).isEqualTo("CREATE-1");
        assertThat(internal.getOperatorId()).isEqualTo(88L);
        assertThat(internal.getOperatorName()).isEqualTo("Settlement Maker");
        assertThat(internal.getRoleSnapshot()).isEqualTo("FINANCE,SETTLEMENT_MAKER");
        assertThat(internal.getClientIp()).isEqualTo("203.0.113.10");
        assertThat(internal.getUserAgent()).isEqualTo("Admin-Test");
        assertThat(internal.getOperationTime()).isBetween(before, after);
    }

    @Test
    void decisionShouldPrecheckReviewScopeAndInjectTrustedChecker() {
        Fixture fixture = fixture();
        ReviewDecisionRequest request = new ReviewDecisionRequest();
        request.setRequestKey("DECIDE-1");
        request.setExpectedVersion(3L);
        request.setComment("checked against immutable snapshots");

        fixture.service.decideReview(" SO20260831-00000001 ", "APPROVE", request, servletRequest());

        verify(fixture.reviewQueryService).requireReviewAccess("SO20260831-00000001", fixture.scope);
        ArgumentCaptor<InternalReviewDecisionRequest> captor =
                ArgumentCaptor.forClass(InternalReviewDecisionRequest.class);
        verify(fixture.client).decideReview(
                org.mockito.ArgumentMatchers.eq("SO20260831-00000001"), captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo("APPROVE");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(88L);
        assertThat(captor.getValue().getRoleSnapshot()).isEqualTo("FINANCE,SETTLEMENT_MAKER");
    }

    @Test
    void manualPreviewShouldFreezeServerSideScopeWithTrustedMaker() {
        Fixture fixture = fixture();
        ManualReviewPreviewRequest request = new ManualReviewPreviewRequest();
        request.setRequestKey(" MANUAL-PREVIEW-1 ");
        request.setMerchantId(" M1001 ");
        request.setSettlementProfileId(11L);
        request.setPaymentType(" BANK_CARD ");
        request.setPaymentMethod(" VISA ");
        request.setReason(" settle all matured transactions ");

        fixture.service.previewManualTransactionReview(request, servletRequest());

        verify(fixture.reviewQueryService).requireMerchantAccess("M1001", fixture.scope);
        ArgumentCaptor<InternalManualReviewPreviewRequest> captor =
                ArgumentCaptor.forClass(InternalManualReviewPreviewRequest.class);
        verify(fixture.client).previewManualReview(captor.capture());
        InternalManualReviewPreviewRequest internal = captor.getValue();
        assertThat(internal.getRequestKey()).isEqualTo("MANUAL-PREVIEW-1");
        assertThat(internal.getMerchantId()).isEqualTo("M1001");
        assertThat(internal.getSettlementProfileId()).isEqualTo(11L);
        assertThat(internal.getPaymentType()).isEqualTo("BANK_CARD");
        assertThat(internal.getPaymentMethod()).isEqualTo("VISA");
        assertThat(internal.getOperatorId()).isEqualTo(88L);
        assertThat(internal.getClientIp()).isEqualTo("203.0.113.10");
    }

    @Test
    void manualPreviewShouldTreatBlankPaymentDimensionsAsAll() {
        Fixture fixture = fixture();
        ManualReviewPreviewRequest request = new ManualReviewPreviewRequest();
        request.setRequestKey("MANUAL-PREVIEW-ALL");
        request.setMerchantId("M1001");
        request.setSettlementProfileId(11L);
        request.setPaymentType("  ");
        request.setPaymentMethod(null);
        request.setReason("settle all matured transactions");

        fixture.service.previewManualTransactionReview(request, servletRequest());

        ArgumentCaptor<InternalManualReviewPreviewRequest> captor =
                ArgumentCaptor.forClass(InternalManualReviewPreviewRequest.class);
        verify(fixture.client).previewManualReview(captor.capture());
        assertThat(captor.getValue().getPaymentType()).isNull();
        assertThat(captor.getValue().getPaymentMethod()).isNull();
    }

    @Test
    void reserveManualPreviewShouldUseDedicatedReserveCommandBoundary() {
        Fixture fixture = fixture();
        ManualReviewPreviewRequest request = new ManualReviewPreviewRequest();
        request.setRequestKey("RESERVE-PREVIEW-1");
        request.setMerchantId("M1001");
        request.setSettlementProfileId(11L);
        request.setReason("settle matured reserve releases");

        fixture.service.previewManualReserveReview(request, servletRequest());

        verify(fixture.reviewQueryService).requireMerchantAccess("M1001", fixture.scope);
        ArgumentCaptor<InternalManualReviewPreviewRequest> captor =
                ArgumentCaptor.forClass(InternalManualReviewPreviewRequest.class);
        verify(fixture.client).previewManualReserveReview(captor.capture());
        assertThat(captor.getValue().getRequestKey()).isEqualTo("RESERVE-PREVIEW-1");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(88L);
    }

    @Test
    void reserveManualStartAndQueryShouldEnforceReserveTaskScope() {
        Fixture fixture = fixture();
        String taskNo = "MT" + "a".repeat(32);
        ManualReviewStartRequest request = new ManualReviewStartRequest();
        request.setRequestKey("RESERVE-START-1");
        request.setExpectedVersion(3L);

        fixture.service.startManualReserveReview(taskNo, request);
        fixture.service.manualReserveReviewTask(taskNo);

        verify(fixture.reviewQueryService, times(2)).requireManualTaskAccess(
                taskNo, "RESERVE_RELEASE", fixture.scope);
        ArgumentCaptor<ManualReviewStartRequest> captor =
                ArgumentCaptor.forClass(ManualReviewStartRequest.class);
        verify(fixture.client).startManualReserveReview(
                org.mockito.ArgumentMatchers.eq(taskNo), captor.capture());
        assertThat(captor.getValue().getRequestKey()).isEqualTo("RESERVE-START-1");
        assertThat(captor.getValue().getExpectedVersion()).isEqualTo(3L);
        verify(fixture.client).getManualReserveReviewTask(taskNo);
    }

    @Test
    void asynchronousDecisionShouldPrecheckReviewScopeAndInjectTrustedChecker() {
        Fixture fixture = fixture();
        ReviewDecisionRequest request = new ReviewDecisionRequest();
        request.setRequestKey("DECISION-TASK-1");
        request.setExpectedVersion(4L);
        request.setComment("approve frozen server-side segments");

        fixture.service.submitReviewDecisionTask(
                " SO20260831-00000001 ", "APPROVE", request, servletRequest());

        verify(fixture.reviewQueryService).requireReviewAccess("SO20260831-00000001", fixture.scope);
        ArgumentCaptor<InternalReviewDecisionRequest> captor =
                ArgumentCaptor.forClass(InternalReviewDecisionRequest.class);
        verify(fixture.client).submitReviewDecisionTask(
                org.mockito.ArgumentMatchers.eq("SO20260831-00000001"), captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo("APPROVE");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(88L);
        assertThat(captor.getValue().getOperationTime()).isNotNull();
    }

    @Test
    void decisionTaskRecoveryShouldPrecheckTaskScopeAndInjectTrustedOperator() {
        Fixture fixture = fixture();
        String taskNo = "DT" + "a".repeat(32);
        ReviewDecisionTaskResumeRequest request = new ReviewDecisionTaskResumeRequest();
        request.setRequestKey("RESUME-1");
        request.setExpectedVersion(7L);
        request.setReason("database connectivity restored");

        fixture.service.resumeReviewDecisionTask(taskNo, request, servletRequest());

        verify(fixture.reviewQueryService).requireDecisionTaskAccess(taskNo, fixture.scope);
        ArgumentCaptor<InternalReviewDecisionTaskResumeRequest> captor =
                ArgumentCaptor.forClass(InternalReviewDecisionTaskResumeRequest.class);
        verify(fixture.client).resumeReviewDecisionTask(
                org.mockito.ArgumentMatchers.eq(taskNo), captor.capture());
        assertThat(captor.getValue().getRequestKey()).isEqualTo("RESUME-1");
        assertThat(captor.getValue().getExpectedVersion()).isEqualTo(7L);
        assertThat(captor.getValue().getOperatorId()).isEqualTo(88L);
        assertThat(captor.getValue().getClientIp()).isEqualTo("203.0.113.10");
    }

    @Test
    void browserDtosShouldNotExposeOperatorIdentityFields() {
        assertThat(Arrays.stream(ReviewSubmitRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)).doesNotContain(
                "operatorId", "operatorName", "roleSnapshot", "clientIp", "userAgent", "operationTime");
        assertThat(Arrays.stream(ReviewDecisionRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)).doesNotContain(
                "operatorId", "operatorName", "roleSnapshot", "clientIp", "userAgent", "operationTime");
        assertThat(Arrays.stream(ReviewDecisionTaskResumeRequest.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)).doesNotContain(
                "operatorId", "operatorName", "roleSnapshot", "clientIp", "userAgent", "operationTime");
    }

    private Fixture fixture() {
        SettlementInternalClient client = mock(SettlementInternalClient.class);
        AdminSettlementReviewQueryService reviewQueryService = mock(AdminSettlementReviewQueryService.class);
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
        AdminSettlementApplicationService service = new AdminSettlementApplicationService(
                client, mock(AdminSettlementQueryService.class), resolver, reviewQueryService);
        return new Fixture(service, client, reviewQueryService, scope);
    }

    private ReviewSubmitRequest submitRequest() {
        ReviewCandidateReference candidate = new ReviewCandidateReference();
        candidate.setCandidateId(1L);
        candidate.setExpectedVersion(7L);
        ReviewSubmitRequest request = new ReviewSubmitRequest();
        request.setRequestKey(" CREATE-1 ");
        request.setReviewType("REGULAR");
        request.setBusinessDate(LocalDate.of(2026, 8, 31));
        request.setCutoffBeginTime(LocalDateTime.of(2026, 8, 30, 0, 0));
        request.setCutoffEndTime(LocalDateTime.of(2026, 8, 31, 0, 0));
        request.setCandidates(List.of(candidate));
        request.setReason(" manual settlement requested ");
        return request;
    }

    private HttpServletRequest servletRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Admin-Test");
        return request;
    }

    private record Fixture(AdminSettlementApplicationService service,
                           SettlementInternalClient client,
                           AdminSettlementReviewQueryService reviewQueryService,
                           AdminMerchantDataScope scope) {
    }
}
