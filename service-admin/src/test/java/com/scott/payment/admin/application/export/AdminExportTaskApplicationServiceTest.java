package com.scott.payment.admin.application.export;

import com.scott.payment.admin.client.data.DataExportTaskInternalClient;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SearchRequestDTO;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RefundSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CardBinSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CountryQuery;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CountrySubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CurrencyQuery;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.CurrencySubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DepartmentQuery;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DepartmentSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DictDataQuery;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DictDataSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DictTypeQuery;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DictTypeSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.HolidayCalendarQuery;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.HolidayCalendarSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FeeReviewSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FeeSimulationSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FeeTemplateSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.MccCodeSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.MerchantFeeSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.PostQuery;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.PostSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RegionCurrencyQuery;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RegionCurrencySubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.JobRunLogSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.MerchantNotificationSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SettlementReserveSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SettlementTransactionSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TradeBlackSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TransactionOrderSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TransactionOperationSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.WorkItemSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ChannelMatchAbnormalSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SettlementReviewSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SettlementPostingSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SecurityInterceptEventSubmitRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchRequest;
import com.scott.payment.admin.dto.base.CardBinDTOs.CardBinQueryRequest;
import com.scott.payment.admin.dto.base.MccRequests.MccTreeQueryRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogQueryRequest;
import com.scott.payment.admin.dto.risk.RiskDTOs.TradeBlackQueryRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordQuery;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventQuery;
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
import java.time.LocalDateTime;
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

    /** 系统交易加黑导出必须冻结当前筛选条件并剔除浏览器分页参数。 */
    @Test
    void shouldFreezeTradeBlackFiltersForAsyncExport() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.all());
        when(client.submit(org.mockito.ArgumentMatchers.any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(client, resolver);
        TradeBlackQueryRequest query = new TradeBlackQueryRequest();
        query.setMerchantId("M7001");
        query.setMerchantOrderNo("ORDER-7001");
        query.setBlackTargetType("issuerCountry");
        query.setOperator("Scott");
        query.setOperationStartTime(LocalDateTime.of(2026, 11, 14, 9, 0));
        query.setOperationEndTimeExclusive(LocalDateTime.of(2026, 11, 15, 0, 0));
        query.setPageNo(9);
        query.setPageSize(100);
        TradeBlackSubmitRequest request = new TradeBlackSubmitRequest();
        request.setRequestId("trade-black-export-001");
        request.setQuery(query);

        service.submitTradeBlack(request);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client).submit(captor.capture());
        CreateRequest command = captor.getValue();
        assertThat(command.getTaskType()).isEqualTo("ADMIN_TRADE_BLACK_RECORD");
        assertThat(command.isAllMerchants()).isTrue();
        assertThat(command.getRiskConfigQuery()).satisfies(snapshot -> {
            assertThat(snapshot.getModuleType()).isEqualTo("TRADE_BLACK");
            assertThat(snapshot.getFunctionCode()).isEqualTo("system");
            assertThat(snapshot.getMerchantId()).isEqualTo("M7001");
            assertThat(snapshot.getMerchantOrderNo()).isEqualTo("ORDER-7001");
            assertThat(snapshot.getBlackTargetType()).isEqualTo("issuerCountry");
            assertThat(snapshot.getOperator()).isEqualTo("Scott");
            assertThat(snapshot.getOperationStartTime()).isEqualTo(LocalDateTime.of(2026, 11, 14, 9, 0));
            assertThat(snapshot.getOperationEndTimeExclusive()).isEqualTo(LocalDateTime.of(2026, 11, 15, 0, 0));
        });
        assertThat(java.util.Arrays.stream(command.getRiskConfigQuery().getClass().getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize", "blackTargetHash");
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
     * 卡 BIN 与任务日志任务必须冻结公开筛选字段，不能保存浏览器分页参数。
     */
    @Test
    void shouldFreezePlatformQueriesWithoutPageParameters() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.all());
        when(client.submit(org.mockito.ArgumentMatchers.any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(client, resolver);

        CardBinQueryRequest cardBinQuery = new CardBinQueryRequest();
        cardBinQuery.setCardBrand("VISA");
        cardBinQuery.setIssuerCountryAlpha2("US");
        cardBinQuery.setPageNo(9);
        cardBinQuery.setPageSize(100);
        CardBinSubmitRequest cardBinRequest = new CardBinSubmitRequest();
        cardBinRequest.setRequestId("card-bin-export-1");
        cardBinRequest.setQuery(cardBinQuery);
        service.submitCardBins(cardBinRequest);

        JobRunLogQueryRequest jobLogQuery = new JobRunLogQueryRequest();
        jobLogQuery.setJobCode("DATA_EXPORT_TASK_DISPATCH");
        jobLogQuery.setRunStatus("FAILED");
        jobLogQuery.setPageNo(7);
        jobLogQuery.setPageSize(50);
        JobRunLogSubmitRequest jobLogRequest = new JobRunLogSubmitRequest();
        jobLogRequest.setRequestId("job-log-export-1");
        jobLogRequest.setQuery(jobLogQuery);
        service.submitJobRunLogs(jobLogRequest);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client, org.mockito.Mockito.times(2)).submit(captor.capture());
        CreateRequest cardBinCommand = captor.getAllValues().get(0);
        CreateRequest jobLogCommand = captor.getAllValues().get(1);
        assertThat(cardBinCommand.getTaskType()).isEqualTo("ADMIN_CARD_BIN");
        assertThat(cardBinCommand.getCardBinQuery().getCardBrand()).isEqualTo("VISA");
        assertThat(cardBinCommand.getCardBinQuery().getIssuerCountryAlpha2()).isEqualTo("US");
        assertThat(java.util.Arrays.stream(cardBinCommand.getCardBinQuery().getClass().getDeclaredFields())
                .map(java.lang.reflect.Field::getName)).doesNotContain("pageNo", "pageSize");
        assertThat(jobLogCommand.getTaskType()).isEqualTo("ADMIN_JOB_RUN_LOG");
        assertThat(jobLogCommand.getJobRunLogQuery().getJobCode())
                .isEqualTo("DATA_EXPORT_TASK_DISPATCH");
        assertThat(jobLogCommand.getJobRunLogQuery().getRunStatus()).isEqualTo("FAILED");
        assertThat(java.util.Arrays.stream(jobLogCommand.getJobRunLogQuery().getClass().getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize", "paramsSnapshot", "resultMessage");
    }

    /**
     * 基础数据任务必须分别冻结当前筛选条件并使用稳定任务类型。
     */
    @Test
    void shouldFreezeBaseReferenceDataQueries() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.all());
        when(client.submit(org.mockito.ArgumentMatchers.any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(client, resolver);

        CountryQuery countryQuery = new CountryQuery();
        countryQuery.setKeyword("China");
        countryQuery.setContinentCode("AS");
        CountrySubmitRequest countryRequest = new CountrySubmitRequest();
        countryRequest.setRequestId("country-export-1");
        countryRequest.setQuery(countryQuery);
        service.submitCountries(countryRequest);

        CurrencyQuery currencyQuery = new CurrencyQuery();
        currencyQuery.setKeyword("USD");
        currencyQuery.setStatus(1);
        CurrencySubmitRequest currencyRequest = new CurrencySubmitRequest();
        currencyRequest.setRequestId("currency-export-1");
        currencyRequest.setQuery(currencyQuery);
        service.submitCurrencies(currencyRequest);

        RegionCurrencyQuery regionQuery = new RegionCurrencyQuery();
        regionQuery.setContinentCode("EU");
        RegionCurrencySubmitRequest regionRequest = new RegionCurrencySubmitRequest();
        regionRequest.setRequestId("region-currency-export-1");
        regionRequest.setQuery(regionQuery);
        service.submitRegionCurrencies(regionRequest);

        MccTreeQueryRequest mccQuery = new MccTreeQueryRequest();
        mccQuery.setMccCode("5411");
        mccQuery.setRiskLevel("LOW");
        MccCodeSubmitRequest mccRequest = new MccCodeSubmitRequest();
        mccRequest.setRequestId("mcc-export-1");
        mccRequest.setQuery(mccQuery);
        service.submitMccCodes(mccRequest);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client, org.mockito.Mockito.times(4)).submit(captor.capture());
        assertThat(captor.getAllValues()).extracting(CreateRequest::getTaskType).containsExactly(
                "ADMIN_COUNTRY", "ADMIN_CURRENCY", "ADMIN_REGION_CURRENCY", "ADMIN_MCC_CODE");
        assertThat(captor.getAllValues().get(0).getCountryQuery().getContinentCode()).isEqualTo("AS");
        assertThat(captor.getAllValues().get(1).getCurrencyQuery().getStatus()).isEqualTo(1);
        assertThat(captor.getAllValues().get(2).getRegionCurrencyQuery().getContinentCode()).isEqualTo("EU");
        assertThat(captor.getAllValues().get(3).getMccCodeQuery().getMccCode()).isEqualTo("5411");
        assertThat(captor.getAllValues().get(3).getMccCodeQuery().getRiskLevel()).isEqualTo("LOW");
    }

    /**
     * 组织、字典与日历任务必须冻结页面公开筛选条件，并使用独立稳定的任务类型。
     */
    @Test
    void shouldFreezeSystemReferenceQueries() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.all());
        when(client.submit(org.mockito.ArgumentMatchers.any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(client, resolver);

        DepartmentSubmitRequest departmentRequest = new DepartmentSubmitRequest();
        departmentRequest.setRequestId("department-export-1");
        departmentRequest.setQuery(new DepartmentQuery());
        service.submitDepartments(departmentRequest);

        PostQuery postQuery = new PostQuery();
        postQuery.setPostCode("OPS_001");
        postQuery.setPostName("Operations");
        postQuery.setStatus(1);
        PostSubmitRequest postRequest = new PostSubmitRequest();
        postRequest.setRequestId("post-export-1");
        postRequest.setQuery(postQuery);
        service.submitPosts(postRequest);

        DictTypeQuery dictTypeQuery = new DictTypeQuery();
        dictTypeQuery.setDictType("payment_status");
        dictTypeQuery.setBizDomain("PAYMENT");
        DictTypeSubmitRequest dictTypeRequest = new DictTypeSubmitRequest();
        dictTypeRequest.setRequestId("dict-type-export-1");
        dictTypeRequest.setQuery(dictTypeQuery);
        service.submitDictTypes(dictTypeRequest);

        DictDataQuery dictDataQuery = new DictDataQuery();
        dictDataQuery.setDictType("payment_status");
        dictDataQuery.setDictValue("0001");
        dictDataQuery.setLocale("zh-CN");
        DictDataSubmitRequest dictDataRequest = new DictDataSubmitRequest();
        dictDataRequest.setRequestId("dict-data-export-1");
        dictDataRequest.setQuery(dictDataQuery);
        service.submitDictData(dictDataRequest);

        HolidayCalendarQuery calendarQuery = new HolidayCalendarQuery();
        calendarQuery.setYear(2027);
        HolidayCalendarSubmitRequest calendarRequest = new HolidayCalendarSubmitRequest();
        calendarRequest.setRequestId("calendar-export-1");
        calendarRequest.setQuery(calendarQuery);
        service.submitHolidayCalendar(calendarRequest);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client, org.mockito.Mockito.times(5)).submit(captor.capture());
        var commands = captor.getAllValues();
        assertThat(commands).extracting(CreateRequest::getTaskType).containsExactly(
                "ADMIN_DEPARTMENT", "ADMIN_POST", "ADMIN_DICT_TYPE",
                "ADMIN_DICT_DATA", "ADMIN_HOLIDAY_CALENDAR");
        assertThat(commands.get(1).getPostQuery().getPostCode()).isEqualTo("OPS_001");
        assertThat(commands.get(1).getPostQuery().getStatus()).isEqualTo(1);
        assertThat(commands.get(2).getDictTypeQuery().getBizDomain()).isEqualTo("PAYMENT");
        assertThat(commands.get(3).getDictDataQuery().getDictValue()).isEqualTo("0001");
        assertThat(commands.get(3).getDictDataQuery().getLocale()).isEqualTo("zh-CN");
        assertThat(commands.get(4).getHolidayCalendarQuery().getYear()).isEqualTo(2027);
        assertThat(commands).allSatisfy(command -> {
            assertThat(command.isAllMerchants()).isTrue();
            assertThat(command.getMerchantIds()).isEmpty();
        });
    }

    /**
     * 费用配置与试算任务必须冻结公开筛选条件、可信商户范围和语言，不能保存页面分页参数。
     */
    @Test
    void shouldFreezeFeeQueriesWithTrustedScope() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        LocaleContextHolder.setLocale(Locale.US);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.limited(Set.of("M6001")));
        when(client.submit(org.mockito.ArgumentMatchers.any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(client, resolver);

        FeeTemplateSubmitRequest templateRequest = new FeeTemplateSubmitRequest();
        templateRequest.setRequestId("fee-template-export-1");
        templateRequest.setQuery(feePlanQuery("template", "ENABLED", "PUBLISHED", 9, 100));
        service.submitFeeTemplates(templateRequest);

        MerchantFeeSubmitRequest merchantRequest = new MerchantFeeSubmitRequest();
        merchantRequest.setRequestId("merchant-fee-export-1");
        merchantRequest.setQuery(feePlanQuery("merchant", "ENABLED", "DRAFT", 8, 100));
        service.submitMerchantFees(merchantRequest);

        FeeReviewSubmitRequest reviewRequest = new FeeReviewSubmitRequest();
        reviewRequest.setRequestId("fee-review-export-1");
        reviewRequest.setQuery(feePlanQuery("review", "DISABLED", "PENDING_REVIEW", 7, 100));
        service.submitFeeReviews(reviewRequest);

        FeeSimulationRecordQuery simulationQuery = new FeeSimulationRecordQuery();
        simulationQuery.setKeyword("SIM-6001");
        simulationQuery.setMerchantId("M6001");
        simulationQuery.setTransactionType("PAYMENT");
        simulationQuery.setPageNo(6);
        simulationQuery.setPageSize(100);
        FeeSimulationSubmitRequest simulationRequest = new FeeSimulationSubmitRequest();
        simulationRequest.setRequestId("fee-simulation-export-1");
        simulationRequest.setQuery(simulationQuery);
        service.submitFeeSimulations(simulationRequest);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client, org.mockito.Mockito.times(4)).submit(captor.capture());
        var commands = captor.getAllValues();
        assertThat(commands).extracting(CreateRequest::getTaskType).containsExactly(
                "ADMIN_FEE_TEMPLATE", "ADMIN_MERCHANT_FEE",
                "ADMIN_FEE_REVIEW", "ADMIN_FEE_SIMULATION");
        assertThat(commands).allSatisfy(command -> {
            assertThat(command.isAllMerchants()).isFalse();
            assertThat(command.getMerchantIds()).containsExactly("M6001");
            assertThat(command.getLocale()).isEqualTo("en-US");
        });
        assertThat(commands.get(0).getFeeTemplateQuery().getKeyword()).isEqualTo("template");
        assertThat(commands.get(1).getMerchantFeeQuery().getVersionStatus()).isEqualTo("DRAFT");
        assertThat(commands.get(2).getFeeReviewQuery().getStatus()).isEqualTo("DISABLED");
        assertThat(commands.get(3).getFeeSimulationQuery().getMerchantId()).isEqualTo("M6001");
        assertThat(commands.get(3).getFeeSimulationQuery().getTransactionType()).isEqualTo("PAYMENT");
        assertThat(java.util.Arrays.stream(commands.get(0).getFeeTemplateQuery()
                        .getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize");
        assertThat(java.util.Arrays.stream(commands.get(3).getFeeSimulationQuery()
                        .getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize");
    }

    /** 系统账户、系统参数和商户访问控制任务只冻结公开筛选字段及可信范围。 */
    @Test
    void shouldFreezeSystemAndMerchantAccessQueries() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.limited(Set.of("M7001")));
        when(client.submit(org.mockito.ArgumentMatchers.any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(client, resolver);

        com.scott.payment.admin.dto.SysUserAccountQueryRequest userQuery =
                new com.scott.payment.admin.dto.SysUserAccountQueryRequest();
        userQuery.setLoginAccount("operator");
        userQuery.setDeptId(7L);
        userQuery.setPageNo(9);
        userQuery.setPageSize(100);
        var userRequest = new com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SystemUserSubmitRequest();
        userRequest.setRequestId("system-user-export-1");
        userRequest.setQuery(userQuery);
        service.submitSystemUsers(userRequest);

        com.scott.payment.admin.dto.SysConfigQueryRequest configQuery =
                new com.scott.payment.admin.dto.SysConfigQueryRequest();
        configQuery.setConfigGroup("security");
        configQuery.setConfigKey("security.jwt.secret");
        configQuery.setPageNo(8);
        configQuery.setPageSize(100);
        var configRequest = new com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SystemConfigSubmitRequest();
        configRequest.setRequestId("system-config-export-1");
        configRequest.setQuery(configQuery);
        service.submitSystemConfigs(configRequest);

        var whitelistQuery = new com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistQuery();
        whitelistQuery.setMerchantId("M7001");
        whitelistQuery.setIpType("IPv4");
        whitelistQuery.setPageNo(7);
        whitelistQuery.setPageSize(100);
        var whitelistRequest = new com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.MerchantIpWhitelistSubmitRequest();
        whitelistRequest.setRequestId("merchant-ip-export-1");
        whitelistRequest.setQuery(whitelistQuery);
        service.submitMerchantIpWhitelists(whitelistRequest);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client, org.mockito.Mockito.times(3)).submit(captor.capture());
        var commands = captor.getAllValues();
        assertThat(commands).extracting(CreateRequest::getTaskType).containsExactly(
                "ADMIN_SYSTEM_USER", "ADMIN_SYSTEM_CONFIG", "ADMIN_MERCHANT_IP_WHITELIST");
        assertThat(commands.get(0).getSystemUserQuery().getLoginAccount()).isEqualTo("operator");
        assertThat(commands.get(1).getSystemConfigQuery().getConfigKey()).isEqualTo("security.jwt.secret");
        assertThat(commands.get(2).getMerchantIpWhitelistQuery().getMerchantId()).isEqualTo("M7001");
        assertThat(commands).allSatisfy(command -> {
            assertThat(command.isAllMerchants()).isFalse();
            assertThat(command.getMerchantIds()).containsExactly("M7001");
        });
        assertThat(java.util.Arrays.stream(commands.get(0).getSystemUserQuery()
                        .getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize");
        assertThat(java.util.Arrays.stream(commands.get(2).getMerchantIpWhitelistQuery()
                        .getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize");
    }

    /** 创建带浏览器分页的费用方案查询，验证任务快照只保留业务筛选。 */
    private FeePlanQuery feePlanQuery(
            String keyword, String status, String versionStatus, int pageNo, int pageSize) {
        FeePlanQuery query = new FeePlanQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setVersionStatus(versionStatus);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return query;
    }

    /**
     * 高量运营导出必须分别冻结强类型查询和可信商户范围，页面分页不得进入任务快照。
     */
    @Test
    void shouldFreezeHighVolumeOperationalQueriesWithTrustedScope() {
        DataExportTaskInternalClient client = mock(DataExportTaskInternalClient.class);
        AdminMerchantDataScopeResolver resolver = mock(AdminMerchantDataScopeResolver.class);
        InternalAuthAccount account = adminAccount();
        InternalAuthContextHolder.set(account);
        when(resolver.resolve(account)).thenReturn(AdminMerchantDataScope.limited(Set.of("M5001")));
        when(client.submit(org.mockito.ArgumentMatchers.any())).thenReturn(new TaskSummary());
        AdminExportTaskApplicationService service = new AdminExportTaskApplicationService(client, resolver);

        AbnormalQuery abnormalQuery = new AbnormalQuery();
        abnormalQuery.setEventStatus("OPEN");
        abnormalQuery.setPageNo(8);
        abnormalQuery.setPageSize(100);
        ChannelMatchAbnormalSubmitRequest abnormalRequest = new ChannelMatchAbnormalSubmitRequest();
        abnormalRequest.setRequestId("abnormal-export-1");
        abnormalRequest.setQuery(abnormalQuery);
        service.submitChannelMatchAbnormalities(abnormalRequest);

        ReviewSearchRequest reviewQuery = new ReviewSearchRequest();
        reviewQuery.setReviewStatus("PENDING_APPROVAL");
        reviewQuery.setPageNo(7);
        reviewQuery.setPageSize(100);
        SettlementReviewSubmitRequest reviewRequest = new SettlementReviewSubmitRequest();
        reviewRequest.setRequestId("review-export-1");
        reviewRequest.setQuery(reviewQuery);
        service.submitSettlementReviews(reviewRequest);

        PostingSearchRequest postingQuery = new PostingSearchRequest();
        postingQuery.setCurrency("USD");
        postingQuery.setPageNo(6);
        postingQuery.setPageSize(100);
        SettlementPostingSubmitRequest postingRequest = new SettlementPostingSubmitRequest();
        postingRequest.setRequestId("posting-export-1");
        postingRequest.setQuery(postingQuery);
        service.submitSettlementPostings(postingRequest);

        SecurityInterceptEventQuery securityQuery = new SecurityInterceptEventQuery();
        securityQuery.setRiskLevel("HIGH");
        securityQuery.setPageNo(5);
        securityQuery.setPageSize(100);
        SecurityInterceptEventSubmitRequest securityRequest = new SecurityInterceptEventSubmitRequest();
        securityRequest.setRequestId("security-export-1");
        securityRequest.setQuery(securityQuery);
        service.submitSecurityInterceptEvents(securityRequest);

        ArgumentCaptor<CreateRequest> captor = ArgumentCaptor.forClass(CreateRequest.class);
        verify(client, org.mockito.Mockito.times(4)).submit(captor.capture());
        var commands = captor.getAllValues();
        assertThat(commands).extracting(CreateRequest::getTaskType).containsExactly(
                "ADMIN_CHANNEL_MATCH_ABNORMAL",
                "ADMIN_SETTLEMENT_REVIEW",
                "ADMIN_SETTLEMENT_POSTING",
                "ADMIN_SECURITY_INTERCEPT_EVENT");
        assertThat(commands).allSatisfy(command ->
                assertThat(command.getMerchantIds()).containsExactly("M5001"));
        assertThat(commands.get(0).getChannelMatchAbnormalQuery().getEventStatus()).isEqualTo("OPEN");
        assertThat(commands.get(1).getSettlementReviewQuery().getReviewStatus())
                .isEqualTo("PENDING_APPROVAL");
        assertThat(commands.get(2).getSettlementPostingQuery().getCurrency()).isEqualTo("USD");
        assertThat(commands.get(3).getSecurityInterceptEventQuery().getRiskLevel()).isEqualTo("HIGH");
        assertThat(java.util.Arrays.stream(commands.get(0).getChannelMatchAbnormalQuery()
                        .getClass().getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("pageNo", "pageSize");
        assertThat(java.util.Arrays.stream(commands.get(3).getSecurityInterceptEventQuery()
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
