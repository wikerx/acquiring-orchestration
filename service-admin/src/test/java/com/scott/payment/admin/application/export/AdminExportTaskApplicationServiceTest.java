package com.scott.payment.admin.application.export;

import com.scott.payment.admin.client.data.DataExportTaskInternalClient;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SearchRequestDTO;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RefundSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TransactionOrderSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.WorkItemSubmitRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
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
