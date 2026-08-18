package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AssignClientCommand;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AssignRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalClientRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalResult;
import com.scott.payment.admin.service.AdminChannelMatchAbnormalQueryService;
import com.scott.payment.admin.service.AdminRefundQueryService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.redis.concurrency.RedisConcurrencyLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionManagementIdentityTests
 * @date : 2026-08-06 00:00
 * @description : 管理端退款审批和异常领取身份测试，验证操作人只从认证上下文生成。
 * @status : create
 */
class AdminTransactionManagementIdentityTests {

    @AfterEach
    void clearAuthenticationContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void approvalShouldUseAuthenticatedAdminIdentity() {
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        AdminRefundApplicationService service = refundService(paymentClient);
        authenticate(42L, "reviewer", "Reviewer Zhang");
        ApprovalDecisionRequest request = new ApprovalDecisionRequest();
        request.setDecisionRequestId("DEC-1");
        request.setExpectedVersion(3);
        request.setApprovalReason("approved");
        when(paymentClient.approveRefund(org.mockito.ArgumentMatchers.eq("RA-1"),
                org.mockito.ArgumentMatchers.any(ApprovalClientRequest.class)))
                .thenReturn(new ApprovalResult());

        service.approve("RA-1", request);

        ArgumentCaptor<ApprovalClientRequest> captor = ArgumentCaptor.forClass(ApprovalClientRequest.class);
        verify(paymentClient).approveRefund(org.mockito.ArgumentMatchers.eq("RA-1"), captor.capture());
        assertThat(captor.getValue().getOperatorId()).isEqualTo("admin-account:42");
        assertThat(captor.getValue().getOperatorName()).isEqualTo("Reviewer Zhang");
    }

    @Test
    void claimWithoutAssigneeShouldUseAuthenticatedAdminIdentity() {
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        AdminChannelMatchAbnormalApplicationService service = abnormalService(paymentClient);
        authenticate(43L, "operator", "Operator Li");
        AssignRequest request = new AssignRequest();
        request.setTransactionDateTime(LocalDateTime.of(2026, 8, 6, 16, 0));
        request.setExpectedVersion(2);

        service.assign("ABN-1", request);

        ArgumentCaptor<AssignClientCommand> captor = ArgumentCaptor.forClass(AssignClientCommand.class);
        verify(paymentClient).assignChannelMatchAbnormality(
                org.mockito.ArgumentMatchers.eq("ABN-1"), captor.capture());
        assertThat(captor.getValue().getOperatorId()).isEqualTo("admin-account:43");
        assertThat(captor.getValue().getOperatorName()).isEqualTo("Operator Li");
    }

    private void authenticate(long accountId, String loginAccount, String realName) {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAccountId(accountId);
        account.setLoginAccount(loginAccount);
        account.setRealName(realName);
        InternalAuthContextHolder.set(account);
    }

    private AdminRefundApplicationService refundService(PaymentInternalClient paymentClient) {
        return new AdminRefundApplicationService(
                mock(AdminRefundQueryService.class),
                paymentClient,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class),
                new TransactionShardingProperties(),
                mock(RedisConcurrencyLimiter.class));
    }

    private AdminChannelMatchAbnormalApplicationService abnormalService(PaymentInternalClient paymentClient) {
        return new AdminChannelMatchAbnormalApplicationService(
                mock(AdminChannelMatchAbnormalQueryService.class),
                paymentClient,
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class),
                new TransactionShardingProperties(),
                mock(RedisConcurrencyLimiter.class));
    }
}
