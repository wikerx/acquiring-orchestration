package com.scott.payment.admin.application.export;

import com.scott.payment.admin.client.data.DataExportTaskInternalClient;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SearchRequestDTO;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RefundSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.MerchantNotificationSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SettlementReserveSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SettlementTransactionSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TransactionOrderSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TransactionOperationSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.WorkItemSubmitRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端异步导出编排测试，验证可信身份和商户数据范围冻结。
 */
class AdminExportTaskApplicationServiceTest {

    /**
     * 每个用例结束后清理线程认证与语言上下文，避免污染其他测试。
     */
    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
        LocaleContextHolder.resetLocaleContext();
    }

    /**
     * 创建任务时必须使用服务端解析的账号和商户范围，并剔除列表分页参数。
     */
    @Test
    void shouldFreezeTrustedOperatorAndMerchantScope() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.limited(Set.of("M1001", "M1002")));
        TaskSummary summary = new TaskSummary();
        summary.setTaskNo("EXP1001");
        when(client.submit(org.mockito.ArgumentMatchers.any())).thenReturn(summary);
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(client, resolver);
        SearchRequest query = new SearchRequest();
        query.setKeyword("merchant-order-001");
        query.setPageNo(9);
        query.setPageSize(100);
        WorkItemSubmitRequest request = new WorkItemSubmitRequest();
        request.setRequestId(" request-001 ");
        request.setQuery(query);

        TaskSummary result = service.submitOperationalWorkItems(request);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client).submit(captor.capture());
        CreateRequest command = captor.getValue();
        assertThat(result).isSameAs(summary);
        assertThat(command.getRequestId()).isEqualTo("request-001");
        assertThat(command.getCreatorAccountId()).isEqualTo(88L);
        assertThat(command.getCreatorAccountName()).isEqualTo("Export Operator");
        assertThat(command.isAllMerchants()).isFalse();
        assertThat(command.getMerchantIds()).containsExactlyInAnyOrder("M1001", "M1002");
        assertThat(command.getWorkItemQuery().getKeyword()).isEqualTo("merchant-order-001");
        assertThat(java.util.Arrays.stream(command.getWorkItemQuery().getClass().getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize");
    }

    /**
     * 下载中心查询必须覆盖为当前账号，浏览器没有注入其他创建人的字段入口。
     */
    @Test
    void shouldForceCurrentAccountWhenSearchingTasks() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        InternalAuthContextHolder.set(adminAccount());
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(
                client, mock(AdminMerchantDataScopeResolver.class));
        when(client.search(org.mockito.ArgumentMatchers.any()))
                .thenReturn(PageResult.of(0L, 1, 20, java.util.List.of()));
        com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SearchRequestDTO request =
                new com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SearchRequestDTO();
        request.setStatus("SUCCESS");

        service.search(request);

        ArgumentCaptor<SearchRequestDTO> captor = ArgumentCaptor.forClass(SearchRequestDTO.class);
        verify(client).search(captor.capture());
        assertThat(captor.getValue().getCreatorAccountId()).isEqualTo(88L);
        assertThat(captor.getValue().getStatus()).isEqualTo("SUCCESS");
    }

    /**
     * 交易主单与退款提交必须分别冻结正确类型的查询且复用同一可信商户范围。
     */
    @Test
    void shouldFreezeTransactionAndRefundQueriesWithTrustedScope() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.limited(Set.of("M2001")));
        when(client.submit(org.mockito.ArgumentMatchers.any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(client, resolver);

        TransactionPageQuery orderQuery = new TransactionPageQuery();
        orderQuery.setMerchantOrderNo("ORDER-2001");
        orderQuery.setPageNo(7);
        orderQuery.setPageSize(50);
        TransactionOrderSubmitRequest orderRequest = new TransactionOrderSubmitRequest();
        orderRequest.setRequestId("order-export-1");
        orderRequest.setQuery(orderQuery);
        service.submitTransactionOrders(orderRequest);

        RefundQuery refundQuery = new RefundQuery();
        refundQuery.setRefundTransactionId("RFD-2001");
        refundQuery.setPageNo(8);
        refundQuery.setPageSize(20);
        RefundSubmitRequest refundRequest = new RefundSubmitRequest();
        refundRequest.setRequestId("refund-export-1");
        refundRequest.setQuery(refundQuery);
        service.submitRefunds(refundRequest);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client, org.mockito.Mockito.times(2)).submit(captor.capture());
        CreateRequest orderCommand = captor.getAllValues().get(0);
        CreateRequest refundCommand = captor.getAllValues().get(1);
        assertThat(orderCommand.getTaskType()).isEqualTo("ADMIN_TRANSACTION_ORDER");
        assertThat(orderCommand.getTransactionOrderQuery().getMerchantOrderNo()).isEqualTo("ORDER-2001");
        assertThat(orderCommand.getMerchantIds()).containsExactly("M2001");
        assertThat(java.util.Arrays.stream(orderCommand.getTransactionOrderQuery().getClass().getDeclaredFields())
                .map(java.lang.reflect.Field::getName)).doesNotContain("pageNo", "pageSize");
        assertThat(refundCommand.getTaskType()).isEqualTo("ADMIN_REFUND");
        assertThat(refundCommand.getRefundQuery().getRefundTransactionId()).isEqualTo("RFD-2001");
        assertThat(refundCommand.getMerchantIds()).containsExactly("M2001");
    }

    /**
     * 交易操作与商户通知提交必须分别冻结正确的强类型查询，不能携带页面分页参数。
     */
    @Test
    void shouldFreezeOperationAndMerchantNotificationQueriesWithTrustedScope() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.limited(Set.of("M3001")));
        when(client.submit(org.mockito.ArgumentMatchers.any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(client, resolver);

        TransactionPageQuery operationQuery = new TransactionPageQuery();
        operationQuery.setTransactionId("TXN-3001");
        operationQuery.setPageNo(6);
        operationQuery.setPageSize(100);
        TransactionOperationSubmitRequest operationRequest = new TransactionOperationSubmitRequest();
        operationRequest.setRequestId("operation-export-1");
        operationRequest.setQuery(operationQuery);
        service.submitTransactionOperations(operationRequest);

        MerchantNotificationQuery notificationQuery = new MerchantNotificationQuery();
        notificationQuery.setTransactionId("TXN-3002");
        notificationQuery.setNotifyStatus("FAILED");
        notificationQuery.setPageNo(4);
        notificationQuery.setPageSize(50);
        MerchantNotificationSubmitRequest notificationRequest = new MerchantNotificationSubmitRequest();
        notificationRequest.setRequestId("notification-export-1");
        notificationRequest.setQuery(notificationQuery);
        service.submitMerchantNotifications(notificationRequest);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client, org.mockito.Mockito.times(2)).submit(captor.capture());
        CreateRequest operationCommand = captor.getAllValues().get(0);
        CreateRequest notificationCommand = captor.getAllValues().get(1);
        assertThat(operationCommand.getTaskType()).isEqualTo("ADMIN_TRANSACTION_OPERATION");
        assertThat(operationCommand.getTransactionOperationQuery().getTransactionId()).isEqualTo("TXN-3001");
        assertThat(operationCommand.getMerchantIds()).containsExactly("M3001");
        assertThat(java.util.Arrays.stream(operationCommand.getTransactionOperationQuery()
                        .getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize");
        assertThat(notificationCommand.getTaskType()).isEqualTo("ADMIN_MERCHANT_NOTIFICATION");
        assertThat(notificationCommand.getMerchantNotificationQuery().getTransactionId()).isEqualTo("TXN-3002");
        assertThat(notificationCommand.getMerchantNotificationQuery().getNotifyStatus()).isEqualTo("FAILED");
        assertThat(notificationCommand.getMerchantIds()).containsExactly("M3001");
        assertThat(java.util.Arrays.stream(notificationCommand.getMerchantNotificationQuery()
                        .getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize");
    }

    /**
     * 两类结算明细任务必须冻结财务查询和可信商户范围，不能携带页面分页参数。
     */
    @Test
    void shouldFreezeSettlementQueriesWithTrustedScope() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.limited(Set.of("M4001")));
        when(client.submit(org.mockito.ArgumentMatchers.any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(client, resolver);

        ResultItemSearchRequest transactionQuery = new ResultItemSearchRequest();
        transactionQuery.setSettlementBatchNo("STB4001");
        transactionQuery.setBeginBusinessDate(LocalDate.of(2026, 11, 1));
        transactionQuery.setEndBusinessDate(LocalDate.of(2026, 11, 9));
        transactionQuery.setPageNo(7);
        transactionQuery.setPageSize(100);
        SettlementTransactionSubmitRequest transactionRequest = new SettlementTransactionSubmitRequest();
        transactionRequest.setRequestId("settlement-transaction-export-1");
        transactionRequest.setQuery(transactionQuery);
        service.submitSettlementTransactions(transactionRequest);

        ReserveItemSearchRequest reserveQuery = new ReserveItemSearchRequest();
        reserveQuery.setReserveNo("RSV4001");
        reserveQuery.setCurrency("USD");
        reserveQuery.setBeginBusinessDate(LocalDate.of(2026, 11, 1));
        reserveQuery.setEndBusinessDate(LocalDate.of(2026, 11, 9));
        reserveQuery.setPageNo(4);
        reserveQuery.setPageSize(50);
        SettlementReserveSubmitRequest reserveRequest = new SettlementReserveSubmitRequest();
        reserveRequest.setRequestId("settlement-reserve-export-1");
        reserveRequest.setQuery(reserveQuery);
        service.submitSettlementReserves(reserveRequest);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client, org.mockito.Mockito.times(2)).submit(captor.capture());
        CreateRequest transactionCommand = captor.getAllValues().get(0);
        CreateRequest reserveCommand = captor.getAllValues().get(1);
        assertThat(transactionCommand.getTaskType()).isEqualTo("ADMIN_SETTLEMENT_TRANSACTION");
        assertThat(transactionCommand.getSettlementTransactionQuery().getSettlementBatchNo())
                .isEqualTo("STB4001");
        assertThat(transactionCommand.getMerchantIds()).containsExactly("M4001");
        assertThat(java.util.Arrays.stream(transactionCommand.getSettlementTransactionQuery()
                        .getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize");
        assertThat(reserveCommand.getTaskType()).isEqualTo("ADMIN_SETTLEMENT_RESERVE");
        assertThat(reserveCommand.getSettlementReserveQuery().getReserveNo()).isEqualTo("RSV4001");
        assertThat(reserveCommand.getSettlementReserveQuery().getCurrency()).isEqualTo("USD");
        assertThat(reserveCommand.getMerchantIds()).containsExactly("M4001");
        assertThat(java.util.Arrays.stream(reserveCommand.getSettlementReserveQuery()
                        .getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize");
    }

    /**
     * 缺少可信 Admin 会话时不得创建任务或调用 Data 内部接口。
     */
    @Test
    void shouldRejectSubmissionWithoutAdminSession() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(
                client, mock(AdminMerchantDataScopeResolver.class));
        WorkItemSubmitRequest request = new WorkItemSubmitRequest();
        request.setRequestId("request-unauthorized");
        request.setQuery(new SearchRequest());

        assertThatThrownBy(() -> service.submitOperationalWorkItems(request))
                .isInstanceOf(ServiceException.class);
        verify(client, never()).submit(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 构造具备管理端应用身份的可信测试账号。
     */
    private InternalAuthAccount adminAccount() {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("ADMIN");
        account.setAppId(1L);
        account.setAccountId(88L);
        account.setLoginAccount("export.operator@example.test");
        account.setRealName("Export Operator");
        return account;
    }
}
