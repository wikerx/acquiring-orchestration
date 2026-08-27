package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.settlement.SettlementInternalClient;
import com.scott.payment.admin.service.AdminSettlementQueryService;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalBatchCommandRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 验证 Admin 结算命令不信任浏览器操作人，并绑定当前登录账号。 */
class AdminSettlementApplicationServiceTest {

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void searchAndDetailShouldUseAdminLocalQueryServiceOnly() {
        SettlementInternalClient client = mock(SettlementInternalClient.class);
        AdminSettlementQueryService queryService = mock(AdminSettlementQueryService.class);
        AdminSettlementApplicationService service = new AdminSettlementApplicationService(client, queryService);
        BatchSearchRequest request = new BatchSearchRequest();
        request.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        request.setEndBusinessDate(LocalDate.of(2026, 8, 31));

        service.search(request);
        service.detail(" SB20260826-00000001 ");

        verify(queryService).search(request);
        verify(queryService).detail("SB20260826-00000001");
        verifyNoInteractions(client);
    }

    @Test
    void cancelAndReverseShouldBindTrustedOperatorAndNormalizeAuditFields() {
        SettlementInternalClient client = mock(SettlementInternalClient.class);
        AdminSettlementApplicationService service = new AdminSettlementApplicationService(
                client, mock(AdminSettlementQueryService.class));
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAccountId(88L);
        account.setLoginAccount("settlement.ops@example.com");
        account.setRealName("Settlement Operator");
        InternalAuthContextHolder.set(account);
        BatchCommandRequest request = new BatchCommandRequest();
        request.setRequestKey(" REQ-SETTLEMENT-1 ");
        request.setExpectedVersion(5L);
        request.setReason(" approved after ledger review ");

        service.cancel(" SB20260826-00000001 ", request);
        service.reverse(" SB20260826-00000002 ", request);

        ArgumentCaptor<InternalBatchCommandRequest> cancel =
                ArgumentCaptor.forClass(InternalBatchCommandRequest.class);
        verify(client).cancel(org.mockito.ArgumentMatchers.eq("SB20260826-00000001"), cancel.capture());
        assertThat(cancel.getValue().getRequestKey()).isEqualTo("REQ-SETTLEMENT-1");
        assertThat(cancel.getValue().getReason()).isEqualTo("approved after ledger review");
        assertThat(cancel.getValue().getOperator())
                .isEqualTo("admin-account:88/Settlement Operator");
        verify(client).reverse(org.mockito.ArgumentMatchers.eq("SB20260826-00000002"),
                org.mockito.ArgumentMatchers.argThat(command ->
                        "admin-account:88/Settlement Operator".equals(command.getOperator())));
    }
}
