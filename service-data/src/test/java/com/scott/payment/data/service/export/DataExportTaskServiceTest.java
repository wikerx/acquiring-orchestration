package com.scott.payment.data.service.export;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CreateRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CardBinQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CountryQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CurrencyQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DispatchRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DispatchResult;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DownloadRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.TaskRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.WorkItemQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantNotificationQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantSettlementReserveQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantFundLedgerQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantTransactionQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.JobRunLogQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MccCodeQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.RegionCurrencyQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FundLedgerQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeBusinessRateQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeRawRateQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeUsageSnapshotQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeSourceQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeVolatilityQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FeePlanQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FeeSimulationQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SystemUserQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SystemConfigQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantIpWhitelistQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.RiskConfigQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DatasourceSnapshotQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SettlementReserveQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SettlementTransactionQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.TransactionOrderQuery;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportDownloadAuditDO;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.mapper.export.DataExportDownloadAuditMapper;
import com.scott.payment.data.mapper.export.DataExportTaskMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_OPERATIONAL_WORK_ITEM;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_CARD_BIN;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_COUNTRY;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_CURRENCY;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_MCC_CODE;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_REGION_CURRENCY;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_JOB_RUN_LOG;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_FUND_LEDGER;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_BUSINESS_RATE;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_RAW_RATE;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_USAGE_SNAPSHOT;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_SOURCE;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_VOLATILITY;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_FEE_REVIEW;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_FEE_SIMULATION;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_FEE_TEMPLATE;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_MERCHANT_FEE;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SYSTEM_USER;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SYSTEM_CONFIG;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_MERCHANT_IP_WHITELIST;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_RISK_CONFIG;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_DATASOURCE_SNAPSHOT;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_TRANSACTION_ORDER;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_TRANSACTION_OPERATION;
import static com.scott.payment.data.service.export.DataExportTaskService.APP_ADMIN;
import static com.scott.payment.data.service.export.DataExportTaskService.APP_MERCHANT;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_MERCHANT_NOTIFICATION;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SETTLEMENT_RESERVE;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SETTLEMENT_TRANSACTION;
import static com.scott.payment.data.service.export.DataExportTaskService.MERCHANT_TRANSACTION_OPERATION;
import static com.scott.payment.data.service.export.DataExportTaskService.MERCHANT_SETTLEMENT_RESERVE;
import static com.scott.payment.data.service.export.DataExportTaskService.MERCHANT_FUND_LEDGER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 异步导出任务状态机测试，覆盖幂等、租约、重试、对象清理和下载审计。
 */
class DataExportTaskServiceTest {
    private static final ExecutorService TEST_EXECUTOR = Executors.newFixedThreadPool(2);

    /**
     * 关闭测试专用执行器，避免测试 JVM 遗留非守护线程。
     */
    @AfterAll
    static void shutdownExecutor() {
        TEST_EXECUTOR.shutdownNow();
    }

    /**
     * 同一创建账号和请求键重复提交时必须返回既有任务，不得再次插入。
     */
    @Test
    void shouldReturnExistingTaskForDuplicateSubmission() {
        Fixture fixture = fixture();
        DataExportTaskDO existing = task("PENDING");
        when(fixture.taskMapper.selectByRequestId(APP_ADMIN, 88L, null, "request-001"))
                .thenReturn(existing);

        var result = fixture.service.submit(createRequest());

        assertThat(result.getTaskNo()).isEqualTo(existing.getTaskNo());
        verify(fixture.taskMapper, never()).insert(any());
    }

    /**
     * 管理端和商户端即使账号主键相同，也必须使用独立应用命名空间查询幂等任务。
     */
    @Test
    void shouldIsolateDuplicateRequestByApplicationAndMerchant() {
        Fixture fixture = fixture();
        CreateRequest request = merchantCreateRequest("M2001");
        DataExportTaskDO existing = task("PENDING");
        existing.setCreatorAppCode(APP_MERCHANT);
        existing.setMerchantId("M2001");
        existing.setTaskType(MERCHANT_TRANSACTION_OPERATION);
        when(fixture.taskMapper.selectByRequestId(
                APP_MERCHANT, 88L, "M2001", "merchant-request-001"))
                .thenReturn(existing);

        var result = fixture.service.submit(request);

        assertThat(result.getTaskNo()).isEqualTo(existing.getTaskNo());
        verify(fixture.taskMapper).selectByRequestId(
                APP_MERCHANT, 88L, "M2001", "merchant-request-001");
        verify(fixture.taskMapper, never()).selectByRequestId(
                APP_ADMIN, 88L, null, "merchant-request-001");
        verify(fixture.taskMapper, never()).insert(any());
    }

    /**
     * 商户任务缺少认证商户号时必须在访问数据库前失败关闭。
     */
    @Test
    void shouldRejectMerchantTaskWithoutMerchantId() {
        Fixture fixture = fixture();
        CreateRequest request = merchantCreateRequest(null);

        assertThatThrownBy(() -> fixture.service.submit(request))
                .isInstanceOf(ServiceException.class);
        verify(fixture.taskMapper, never()).selectByRequestId(any(), anyLong(), any(), anyString());
        verify(fixture.taskMapper, never()).insert(any());
    }

    /**
     * 商户保证金结算任务必须保存匹配的强类型查询和单商户范围快照。
     */
    @Test
    void shouldPersistMerchantSettlementReserveQueryAndScope() {
        Fixture fixture = fixture();
        CreateRequest request = merchantCreateRequest("M2001");
        request.setTaskType(MERCHANT_SETTLEMENT_RESERVE);
        request.setMerchantTransactionQuery(null);
        MerchantSettlementReserveQuery query = new MerchantSettlementReserveQuery();
        query.setSettlementBatchNo("ST20261110-001");
        query.setCurrency("EUR");
        request.setMerchantSettlementReserveQuery(query);
        when(fixture.taskMapper.selectOwned(anyString(), eq(APP_MERCHANT), eq(88L), eq("M2001")))
                .thenAnswer(invocation -> {
                    DataExportTaskDO task = fixture.insertedTask.getValue();
                    task.setId(11L);
                    return task;
                });

        fixture.service.submit(request);

        DataExportTaskDO inserted = fixture.insertedTask.getValue();
        assertThat(inserted.getTaskType()).isEqualTo(MERCHANT_SETTLEMENT_RESERVE);
        assertThat(inserted.getQuerySnapshot()).contains("ST20261110-001", "EUR");
        assertThat(inserted.getQuerySnapshot()).doesNotContain("merchantOrderNo");
        assertThat(inserted.getDataScopeSnapshot()).contains("M2001");
    }

    /**
     * 商户余额流水任务必须保存冻结账户和筛选条件，分页值不能进入快照。
     */
    @Test
    void shouldPersistMerchantFundLedgerQueryAndScope() {
        Fixture fixture = fixture();
        CreateRequest request = merchantCreateRequest("M2001");
        request.setTaskType(MERCHANT_FUND_LEDGER);
        request.setMerchantTransactionQuery(null);
        MerchantFundLedgerQuery query = new MerchantFundLedgerQuery();
        query.setAccountId(7001L);
        query.setKeyword("LEDGER-1001");
        query.setBalanceType("AVAILABLE");
        request.setMerchantFundLedgerQuery(query);
        when(fixture.taskMapper.selectOwned(anyString(), eq(APP_MERCHANT), eq(88L), eq("M2001")))
                .thenAnswer(invocation -> {
                    DataExportTaskDO task = fixture.insertedTask.getValue();
                    task.setId(12L);
                    return task;
                });

        fixture.service.submit(request);

        DataExportTaskDO inserted = fixture.insertedTask.getValue();
        assertThat(inserted.getTaskType()).isEqualTo(MERCHANT_FUND_LEDGER);
        assertThat(inserted.getQuerySnapshot()).contains("7001", "LEDGER-1001", "AVAILABLE");
        assertThat(inserted.getQuerySnapshot()).doesNotContain("pageNo", "pageSize", "merchantId");
        assertThat(inserted.getDataScopeSnapshot()).contains("M2001");
    }

    /**
     * 结算任务与查询模型错配时必须在访问数据库前失败关闭。
     */
    @Test
    void shouldRejectMerchantSettlementTaskWithWrongQueryModel() {
        Fixture fixture = fixture();
        CreateRequest request = merchantCreateRequest("M2001");
        request.setTaskType(MERCHANT_SETTLEMENT_RESERVE);

        assertThatThrownBy(() -> fixture.service.submit(request))
                .isInstanceOf(ServiceException.class);
        verify(fixture.taskMapper, never()).selectByRequestId(any(), anyLong(), any(), anyString());
        verify(fixture.taskMapper, never()).insert(any());
    }

    /**
     * 任务详情必须同时匹配应用、账号和商户号，防止同账号跨商户读取文件元数据。
     */
    @Test
    void shouldRejectTaskOwnedByAnotherMerchant() {
        Fixture fixture = fixture();
        TaskRequest request = new TaskRequest();
        request.setTaskNo("EXP1001");
        request.setCreatorAppCode(APP_MERCHANT);
        request.setCreatorAccountId(88L);
        request.setMerchantId("M2002");
        when(fixture.taskMapper.selectOwned("EXP1001", APP_MERCHANT, 88L, "M2002"))
                .thenReturn(null);

        assertThatThrownBy(() -> fixture.service.detail(request))
                .isInstanceOf(ServiceException.class);
        verify(fixture.taskMapper).selectOwned("EXP1001", APP_MERCHANT, 88L, "M2002");
    }

    /**
     * 交易主单任务必须保存对应类型和强类型查询快照，不能回退为待办查询。
     */
    @Test
    void shouldPersistTransactionOrderTaskTypeAndQuerySnapshot() {
        Fixture fixture = fixture();
        CreateRequest request = createRequest();
        request.setTaskType(ADMIN_TRANSACTION_ORDER);
        request.setWorkItemQuery(null);
        TransactionOrderQuery query = new TransactionOrderQuery();
        query.setMerchantOrderNo("merchant-order-1001");
        request.setTransactionOrderQuery(query);
        when(fixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull())).thenAnswer(invocation -> {
            DataExportTaskDO task = fixture.insertedTask.getValue();
            task.setId(11L);
            return task;
        });

        fixture.service.submit(request);

        DataExportTaskDO inserted = fixture.insertedTask.getValue();
        assertThat(inserted.getTaskType()).isEqualTo(ADMIN_TRANSACTION_ORDER);
        assertThat(inserted.getQuerySnapshot()).contains("merchant-order-1001");
        assertThat(inserted.getQuerySnapshot()).doesNotContain("workItemNo");
    }

    /**
     * 交易操作任务必须保存独立任务类型和对应查询快照。
     */
    @Test
    void shouldPersistTransactionOperationTaskTypeAndQuerySnapshot() {
        Fixture fixture = fixture();
        CreateRequest request = createRequest();
        request.setTaskType(ADMIN_TRANSACTION_OPERATION);
        request.setWorkItemQuery(null);
        TransactionOrderQuery query = new TransactionOrderQuery();
        query.setTransactionId("TXN-OPERATION-1001");
        request.setTransactionOperationQuery(query);
        when(fixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull())).thenAnswer(invocation -> {
            DataExportTaskDO task = fixture.insertedTask.getValue();
            task.setId(11L);
            return task;
        });

        fixture.service.submit(request);

        DataExportTaskDO inserted = fixture.insertedTask.getValue();
        assertThat(inserted.getTaskType()).isEqualTo(ADMIN_TRANSACTION_OPERATION);
        assertThat(inserted.getQuerySnapshot()).contains("TXN-OPERATION-1001");
        assertThat(inserted.getQuerySnapshot()).doesNotContain("workItemNo");
    }

    /**
     * 商户通知任务必须保存独立任务类型和通知筛选快照。
     */
    @Test
    void shouldPersistMerchantNotificationTaskTypeAndQuerySnapshot() {
        Fixture fixture = fixture();
        CreateRequest request = createRequest();
        request.setTaskType(ADMIN_MERCHANT_NOTIFICATION);
        request.setWorkItemQuery(null);
        MerchantNotificationQuery query = new MerchantNotificationQuery();
        query.setTransactionId("TXN-NOTIFY-1001");
        query.setNotifyStatus("FAILED");
        request.setMerchantNotificationQuery(query);
        when(fixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull())).thenAnswer(invocation -> {
            DataExportTaskDO task = fixture.insertedTask.getValue();
            task.setId(11L);
            return task;
        });

        fixture.service.submit(request);

        DataExportTaskDO inserted = fixture.insertedTask.getValue();
        assertThat(inserted.getTaskType()).isEqualTo(ADMIN_MERCHANT_NOTIFICATION);
        assertThat(inserted.getQuerySnapshot()).contains("TXN-NOTIFY-1001", "FAILED");
        assertThat(inserted.getQuerySnapshot()).doesNotContain("workItemNo");
    }

    /**
     * 结算交易与保证金任务必须分别保存强类型查询快照，不能回退到其他任务查询。
     */
    @Test
    void shouldPersistSettlementTaskTypesAndQuerySnapshots() {
        Fixture fixture = fixture();
        CreateRequest transactionRequest = createRequest();
        transactionRequest.setTaskType(ADMIN_SETTLEMENT_TRANSACTION);
        transactionRequest.setWorkItemQuery(null);
        SettlementTransactionQuery transactionQuery = new SettlementTransactionQuery();
        transactionQuery.setSettlementBatchNo("STB5001");
        transactionRequest.setSettlementTransactionQuery(transactionQuery);
        when(fixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull())).thenAnswer(invocation -> {
            DataExportTaskDO task = fixture.insertedTask.getValue();
            task.setId(11L);
            return task;
        });

        fixture.service.submit(transactionRequest);

        DataExportTaskDO transactionTask = fixture.insertedTask.getValue();
        assertThat(transactionTask.getTaskType()).isEqualTo(ADMIN_SETTLEMENT_TRANSACTION);
        assertThat(transactionTask.getQuerySnapshot()).contains("STB5001");
        assertThat(transactionTask.getQuerySnapshot()).doesNotContain("workItemNo");

        Fixture reserveFixture = fixture();
        CreateRequest reserveRequest = createRequest();
        reserveRequest.setRequestId("request-reserve-001");
        reserveRequest.setTaskType(ADMIN_SETTLEMENT_RESERVE);
        reserveRequest.setWorkItemQuery(null);
        SettlementReserveQuery reserveQuery = new SettlementReserveQuery();
        reserveQuery.setReserveNo("RSV5001");
        reserveQuery.setCurrency("EUR");
        reserveRequest.setSettlementReserveQuery(reserveQuery);
        when(reserveFixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull())).thenAnswer(invocation -> {
            DataExportTaskDO task = reserveFixture.insertedTask.getValue();
            task.setId(12L);
            return task;
        });

        reserveFixture.service.submit(reserveRequest);

        DataExportTaskDO reserveTask = reserveFixture.insertedTask.getValue();
        assertThat(reserveTask.getTaskType()).isEqualTo(ADMIN_SETTLEMENT_RESERVE);
        assertThat(reserveTask.getQuerySnapshot()).contains("RSV5001", "EUR");
        assertThat(reserveTask.getQuerySnapshot()).doesNotContain("workItemNo");
    }

    /**
     * 三类汇率事实任务必须持久化各自的强类型查询快照，不能错配为其他任务查询。
     */
    @Test
    void shouldPersistExchangeFactTaskTypesAndQuerySnapshots() {
        Fixture rawFixture = fixture();
        CreateRequest rawRequest = createRequest();
        rawRequest.setTaskType(ADMIN_EXCHANGE_RAW_RATE);
        rawRequest.setWorkItemQuery(null);
        ExchangeRawRateQuery rawQuery = new ExchangeRawRateQuery();
        rawQuery.setSourceCode("BOC");
        rawQuery.setBaseCurrency("EUR");
        rawRequest.setExchangeRawRateQuery(rawQuery);
        when(rawFixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull()))
                .thenAnswer(invocation -> {
                    DataExportTaskDO task = rawFixture.insertedTask.getValue();
                    task.setId(21L);
                    return task;
                });

        rawFixture.service.submit(rawRequest);

        assertThat(rawFixture.insertedTask.getValue().getQuerySnapshot()).contains("BOC", "EUR");
        assertThat(rawFixture.insertedTask.getValue().getQuerySnapshot())
                .doesNotContain("workItemNo", "pageNo", "pageSize");

        Fixture businessFixture = fixture();
        CreateRequest businessRequest = createRequest();
        businessRequest.setRequestId("request-exchange-business-001");
        businessRequest.setTaskType(ADMIN_EXCHANGE_BUSINESS_RATE);
        businessRequest.setWorkItemQuery(null);
        ExchangeBusinessRateQuery businessQuery = new ExchangeBusinessRateQuery();
        businessQuery.setRateType("TRANSACTION_RATE");
        businessQuery.setGenerateMethod("AUTO");
        businessRequest.setExchangeBusinessRateQuery(businessQuery);
        when(businessFixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull()))
                .thenAnswer(invocation -> {
                    DataExportTaskDO task = businessFixture.insertedTask.getValue();
                    task.setId(22L);
                    return task;
                });

        businessFixture.service.submit(businessRequest);

        assertThat(businessFixture.insertedTask.getValue().getQuerySnapshot())
                .contains("TRANSACTION_RATE", "AUTO");
        assertThat(businessFixture.insertedTask.getValue().getQuerySnapshot())
                .doesNotContain("workItemNo");

        Fixture snapshotFixture = fixture();
        CreateRequest snapshotRequest = createRequest();
        snapshotRequest.setRequestId("request-exchange-snapshot-001");
        snapshotRequest.setTaskType(ADMIN_EXCHANGE_USAGE_SNAPSHOT);
        snapshotRequest.setWorkItemQuery(null);
        ExchangeUsageSnapshotQuery snapshotQuery = new ExchangeUsageSnapshotQuery();
        snapshotQuery.setUsageScene("SETTLEMENT");
        snapshotQuery.setBusinessNo("TXN-1001");
        snapshotRequest.setExchangeUsageSnapshotQuery(snapshotQuery);
        when(snapshotFixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull()))
                .thenAnswer(invocation -> {
                    DataExportTaskDO task = snapshotFixture.insertedTask.getValue();
                    task.setId(23L);
                    return task;
                });

        snapshotFixture.service.submit(snapshotRequest);

        assertThat(snapshotFixture.insertedTask.getValue().getQuerySnapshot())
                .contains("SETTLEMENT", "TXN-1001");
        assertThat(snapshotFixture.insertedTask.getValue().getQuerySnapshot())
                .doesNotContain("workItemNo");
    }

    /**
     * 汇率配置与波动任务必须持久化各自的强类型查询快照，不能回退到待办查询。
     */
    @Test
    void shouldPersistExchangeConfigurationTaskSnapshots() {
        Fixture sourceFixture = fixture();
        CreateRequest sourceRequest = createRequest();
        sourceRequest.setRequestId("request-exchange-source-001");
        sourceRequest.setTaskType(ADMIN_EXCHANGE_SOURCE);
        sourceRequest.setWorkItemQuery(null);
        ExchangeSourceQuery sourceQuery = new ExchangeSourceQuery();
        sourceQuery.setKeyword("bank");
        sourceRequest.setExchangeSourceQuery(sourceQuery);
        when(sourceFixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull()))
                .thenAnswer(invocation -> {
                    DataExportTaskDO task = sourceFixture.insertedTask.getValue();
                    task.setId(24L);
                    return task;
                });

        sourceFixture.service.submit(sourceRequest);

        assertThat(sourceFixture.insertedTask.getValue().getQuerySnapshot()).contains("bank");
        assertThat(sourceFixture.insertedTask.getValue().getQuerySnapshot()).doesNotContain("workItemNo");

        Fixture volatilityFixture = fixture();
        CreateRequest volatilityRequest = createRequest();
        volatilityRequest.setRequestId("request-exchange-volatility-001");
        volatilityRequest.setTaskType(ADMIN_EXCHANGE_VOLATILITY);
        volatilityRequest.setWorkItemQuery(null);
        ExchangeVolatilityQuery volatilityQuery = new ExchangeVolatilityQuery();
        volatilityQuery.setRateType("SETTLEMENT_RATE");
        volatilityRequest.setExchangeVolatilityQuery(volatilityQuery);
        when(volatilityFixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull()))
                .thenAnswer(invocation -> {
                    DataExportTaskDO task = volatilityFixture.insertedTask.getValue();
                    task.setId(25L);
                    return task;
                });

        volatilityFixture.service.submit(volatilityRequest);

        assertThat(volatilityFixture.insertedTask.getValue().getQuerySnapshot()).contains("SETTLEMENT_RATE");
        assertThat(volatilityFixture.insertedTask.getValue().getQuerySnapshot()).doesNotContain("workItemNo");
    }

    /** 四类费用任务必须分别保存匹配的强类型查询快照，不能回退到其他费用查询。 */
    @Test
    void shouldPersistFeeTaskTypesAndQuerySnapshots() {
        List<String> taskTypes = List.of(
                ADMIN_FEE_TEMPLATE, ADMIN_MERCHANT_FEE, ADMIN_FEE_REVIEW, ADMIN_FEE_SIMULATION);

        for (int index = 0; index < taskTypes.size(); index++) {
            Fixture fixture = fixture();
            CreateRequest request = createRequest();
            request.setRequestId("request-fee-" + index);
            request.setTaskType(taskTypes.get(index));
            request.setWorkItemQuery(null);
            if (ADMIN_FEE_SIMULATION.equals(taskTypes.get(index))) {
                FeeSimulationQuery query = new FeeSimulationQuery();
                query.setKeyword("SIM-7001");
                query.setMerchantId("M7001");
                query.setTransactionType("PAYMENT");
                request.setFeeSimulationQuery(query);
            } else {
                FeePlanQuery query = new FeePlanQuery();
                query.setKeyword("fee-" + index);
                query.setStatus("ENABLED");
                query.setVersionStatus("PUBLISHED");
                if (ADMIN_FEE_TEMPLATE.equals(taskTypes.get(index))) {
                    request.setFeeTemplateQuery(query);
                } else if (ADMIN_MERCHANT_FEE.equals(taskTypes.get(index))) {
                    request.setMerchantFeeQuery(query);
                } else {
                    request.setFeeReviewQuery(query);
                }
            }
            when(fixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull()))
                    .thenAnswer(invocation -> {
                        DataExportTaskDO task = fixture.insertedTask.getValue();
                        task.setId(31L);
                        return task;
                    });

            fixture.service.submit(request);

            DataExportTaskDO inserted = fixture.insertedTask.getValue();
            assertThat(inserted.getTaskType()).isEqualTo(taskTypes.get(index));
            assertThat(inserted.getQuerySnapshot()).doesNotContain("workItemNo", "pageNo", "pageSize");
            if (ADMIN_FEE_SIMULATION.equals(taskTypes.get(index))) {
                assertThat(inserted.getQuerySnapshot()).contains("SIM-7001", "M7001", "PAYMENT");
            } else {
                assertThat(inserted.getQuerySnapshot()).contains("fee-" + index, "ENABLED", "PUBLISHED");
            }
        }
    }

    /** 三类系统与访问控制任务必须持久化各自强类型查询快照。 */
    @Test
    void shouldPersistSystemAndMerchantAccessTaskSnapshots() {
        List<String> taskTypes = List.of(
                ADMIN_SYSTEM_USER, ADMIN_SYSTEM_CONFIG, ADMIN_MERCHANT_IP_WHITELIST);
        for (int index = 0; index < taskTypes.size(); index++) {
            Fixture fixture = fixture();
            CreateRequest request = createRequest();
            request.setRequestId("request-system-access-" + index);
            request.setTaskType(taskTypes.get(index));
            request.setWorkItemQuery(null);
            if (ADMIN_SYSTEM_USER.equals(taskTypes.get(index))) {
                SystemUserQuery query = new SystemUserQuery();
                query.setLoginAccount("operator-7001");
                request.setSystemUserQuery(query);
            } else if (ADMIN_SYSTEM_CONFIG.equals(taskTypes.get(index))) {
                SystemConfigQuery query = new SystemConfigQuery();
                query.setConfigKey("platform.display.name");
                request.setSystemConfigQuery(query);
            } else {
                MerchantIpWhitelistQuery query = new MerchantIpWhitelistQuery();
                query.setMerchantId("M7001");
                query.setIpType("IPv4");
                request.setMerchantIpWhitelistQuery(query);
            }
            when(fixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull()))
                    .thenAnswer(invocation -> {
                        DataExportTaskDO task = fixture.insertedTask.getValue();
                        task.setId(32L);
                        return task;
                    });

            fixture.service.submit(request);

            DataExportTaskDO inserted = fixture.insertedTask.getValue();
            assertThat(inserted.getTaskType()).isEqualTo(taskTypes.get(index));
            assertThat(inserted.getQuerySnapshot()).doesNotContain("workItemNo", "pageNo", "pageSize");
        }
    }

    /**
     * 风控配置和数据源快照任务必须使用各自强类型快照，不能回退为其他导出类型。
     */
    @Test
    void shouldPersistRiskAndDatasourceTaskSnapshots() {
        Fixture riskFixture = fixture();
        CreateRequest riskRequest = createRequest();
        riskRequest.setRequestId("request-risk-config");
        riskRequest.setTaskType(ADMIN_RISK_CONFIG);
        riskRequest.setWorkItemQuery(null);
        RiskConfigQuery riskQuery = new RiskConfigQuery();
        riskQuery.setModuleType("RULE");
        riskQuery.setFunctionCode("merchantLimit");
        riskQuery.setMerchantId("M7001");
        riskRequest.setRiskConfigQuery(riskQuery);
        when(riskFixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull()))
                .thenAnswer(invocation -> {
                    DataExportTaskDO task = riskFixture.insertedTask.getValue();
                    task.setId(33L);
                    return task;
                });

        riskFixture.service.submit(riskRequest);

        assertThat(riskFixture.insertedTask.getValue().getTaskType()).isEqualTo(ADMIN_RISK_CONFIG);
        assertThat(riskFixture.insertedTask.getValue().getQuerySnapshot())
                .contains("RULE", "merchantLimit", "M7001")
                .doesNotContain("workItemNo");

        Fixture datasourceFixture = fixture();
        CreateRequest datasourceRequest = createRequest();
        datasourceRequest.setRequestId("request-datasource-snapshot");
        datasourceRequest.setTaskType(ADMIN_DATASOURCE_SNAPSHOT);
        datasourceRequest.setWorkItemQuery(null);
        datasourceRequest.setDatasourceSnapshotQuery(new DatasourceSnapshotQuery());
        when(datasourceFixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull()))
                .thenAnswer(invocation -> {
                    DataExportTaskDO task = datasourceFixture.insertedTask.getValue();
                    task.setId(34L);
                    return task;
                });

        datasourceFixture.service.submit(datasourceRequest);

        assertThat(datasourceFixture.insertedTask.getValue().getTaskType())
                .isEqualTo(ADMIN_DATASOURCE_SNAPSHOT);
        assertThat(datasourceFixture.insertedTask.getValue().getQuerySnapshot()).isEqualTo("{}");
    }

    /**
     * 卡 BIN 与任务日志任务必须持久化各自的强类型查询快照，且不能混入其他任务条件。
     */
    @Test
    void shouldPersistPlatformTaskTypesAndQuerySnapshots() {
        Fixture cardBinFixture = fixture();
        CreateRequest cardBinRequest = createRequest();
        cardBinRequest.setTaskType(ADMIN_CARD_BIN);
        cardBinRequest.setWorkItemQuery(null);
        CardBinQuery cardBinQuery = new CardBinQuery();
        cardBinQuery.setCardBrand("VISA");
        cardBinQuery.setIssuerCountryAlpha2("US");
        cardBinRequest.setCardBinQuery(cardBinQuery);
        when(cardBinFixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull())).thenAnswer(invocation -> {
            DataExportTaskDO task = cardBinFixture.insertedTask.getValue();
            task.setId(13L);
            return task;
        });

        cardBinFixture.service.submit(cardBinRequest);

        DataExportTaskDO cardBinTask = cardBinFixture.insertedTask.getValue();
        assertThat(cardBinTask.getTaskType()).isEqualTo(ADMIN_CARD_BIN);
        assertThat(cardBinTask.getQuerySnapshot()).contains("VISA", "US");
        assertThat(cardBinTask.getQuerySnapshot()).doesNotContain("workItemNo", "paramsSnapshot");

        Fixture jobLogFixture = fixture();
        CreateRequest jobLogRequest = createRequest();
        jobLogRequest.setRequestId("request-job-log-001");
        jobLogRequest.setTaskType(ADMIN_JOB_RUN_LOG);
        jobLogRequest.setWorkItemQuery(null);
        JobRunLogQuery jobLogQuery = new JobRunLogQuery();
        jobLogQuery.setJobCode("DATA_EXPORT_TASK_DISPATCH");
        jobLogQuery.setRunStatus("FAILED");
        jobLogRequest.setJobRunLogQuery(jobLogQuery);
        when(jobLogFixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull())).thenAnswer(invocation -> {
            DataExportTaskDO task = jobLogFixture.insertedTask.getValue();
            task.setId(14L);
            return task;
        });

        jobLogFixture.service.submit(jobLogRequest);

        DataExportTaskDO jobLogTask = jobLogFixture.insertedTask.getValue();
        assertThat(jobLogTask.getTaskType()).isEqualTo(ADMIN_JOB_RUN_LOG);
        assertThat(jobLogTask.getQuerySnapshot()).contains("DATA_EXPORT_TASK_DISPATCH", "FAILED");
        assertThat(jobLogTask.getQuerySnapshot())
                .doesNotContain("workItemNo", "paramsSnapshot", "resultMessage");
    }

    /**
     * 基础数据任务必须按任务类型选择唯一的强类型快照，防止跨任务条件错配或丢失。
     */
    @Test
    void shouldPersistEachBaseReferenceQuerySnapshot() {
        Fixture fixture = fixture();
        when(fixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull()))
                .thenAnswer(invocation -> {
                    DataExportTaskDO task = fixture.insertedTask.getValue();
                    task.setId((long) fixture.insertedTask.getAllValues().size());
                    return task;
                });

        CreateRequest countryRequest = createRequest();
        countryRequest.setRequestId("request-country-001");
        countryRequest.setTaskType(ADMIN_COUNTRY);
        countryRequest.setWorkItemQuery(null);
        CountryQuery countryQuery = new CountryQuery();
        countryQuery.setKeyword("China");
        countryQuery.setContinentCode("AS");
        countryRequest.setCountryQuery(countryQuery);
        fixture.service.submit(countryRequest);

        CreateRequest currencyRequest = createRequest();
        currencyRequest.setRequestId("request-currency-001");
        currencyRequest.setTaskType(ADMIN_CURRENCY);
        currencyRequest.setWorkItemQuery(null);
        CurrencyQuery currencyQuery = new CurrencyQuery();
        currencyQuery.setKeyword("USD");
        currencyQuery.setStatus(1);
        currencyRequest.setCurrencyQuery(currencyQuery);
        fixture.service.submit(currencyRequest);

        CreateRequest regionCurrencyRequest = createRequest();
        regionCurrencyRequest.setRequestId("request-region-currency-001");
        regionCurrencyRequest.setTaskType(ADMIN_REGION_CURRENCY);
        regionCurrencyRequest.setWorkItemQuery(null);
        RegionCurrencyQuery regionCurrencyQuery = new RegionCurrencyQuery();
        regionCurrencyQuery.setKeyword("Europe");
        regionCurrencyQuery.setContinentCode("EU");
        regionCurrencyRequest.setRegionCurrencyQuery(regionCurrencyQuery);
        fixture.service.submit(regionCurrencyRequest);

        CreateRequest mccRequest = createRequest();
        mccRequest.setRequestId("request-mcc-001");
        mccRequest.setTaskType(ADMIN_MCC_CODE);
        mccRequest.setWorkItemQuery(null);
        MccCodeQuery mccQuery = new MccCodeQuery();
        mccQuery.setMccCode("5411");
        mccQuery.setRiskLevel("LOW");
        mccRequest.setMccCodeQuery(mccQuery);
        fixture.service.submit(mccRequest);

        List<DataExportTaskDO> tasks = fixture.insertedTask.getAllValues();
        assertThat(tasks).extracting(DataExportTaskDO::getTaskType).containsExactly(
                ADMIN_COUNTRY, ADMIN_CURRENCY, ADMIN_REGION_CURRENCY, ADMIN_MCC_CODE);
        assertThat(tasks.get(0).getQuerySnapshot()).contains("China", "AS").doesNotContain("USD", "5411");
        assertThat(tasks.get(1).getQuerySnapshot()).contains("USD", "1").doesNotContain("China", "5411");
        assertThat(tasks.get(2).getQuerySnapshot()).contains("Europe", "EU").doesNotContain("USD", "5411");
        assertThat(tasks.get(3).getQuerySnapshot()).contains("5411", "LOW").doesNotContain("China", "USD");
    }

    /**
     * 管理端余额流水任务必须持久化账户主键和筛选条件，并冻结可信商户范围。
     */
    @Test
    void shouldPersistAdminFundLedgerQueryAndScope() {
        Fixture fixture = fixture();
        CreateRequest request = createRequest();
        request.setRequestId("request-fund-ledger-001");
        request.setTaskType(ADMIN_FUND_LEDGER);
        request.setWorkItemQuery(null);
        FundLedgerQuery query = new FundLedgerQuery();
        query.setAccountId(7001L);
        query.setBusinessType("TRANSACTION_SETTLEMENT");
        query.setCurrency("USD");
        request.setFundLedgerQuery(query);
        when(fixture.taskMapper.selectOwned(anyString(), eq(APP_ADMIN), eq(88L), isNull()))
                .thenAnswer(invocation -> {
                    DataExportTaskDO task = fixture.insertedTask.getValue();
                    task.setId(15L);
                    return task;
                });

        fixture.service.submit(request);

        DataExportTaskDO inserted = fixture.insertedTask.getValue();
        assertThat(inserted.getTaskType()).isEqualTo(ADMIN_FUND_LEDGER);
        assertThat(inserted.getQuerySnapshot()).contains("7001", "TRANSACTION_SETTLEMENT", "USD");
        assertThat(inserted.getQuerySnapshot()).doesNotContain("workItemNo", "pageNo", "pageSize");
        assertThat(inserted.getDataScopeSnapshot()).contains("M1001");
    }

    /**
     * 成功领取并上传文件后必须一次提交文件元数据，并在同轮清理已到期对象。
     */
    @Test
    void shouldCompleteClaimedTaskAndCleanupExpiredObject() {
        Fixture fixture = fixture();
        DataExportTaskDO candidate = task("PENDING");
        candidate.setVersion(0L);
        DataExportTaskDO processing = task("PROCESSING");
        processing.setVersion(1L);
        DataExportTaskDO expired = task("SUCCESS");
        expired.setId(22L);
        expired.setTaskNo("EXP-EXPIRED");
        expired.setObjectKey("exports/admin/expired.xlsx");
        expired.setVersion(4L);
        ExportArtifact artifact = artifact("exports/admin/current.xlsx");
        when(fixture.taskMapper.selectDispatchCandidates(any(), anyInt())).thenReturn(List.of(candidate));
        when(fixture.taskMapper.claim(eq(candidate.getId()), eq(0L), any(), any(), any())).thenReturn(1);
        when(fixture.taskMapper.selectById(candidate.getId())).thenReturn(processing);
        when(fixture.processor.process(same(processing), same(fixture.storage), any(), any()))
                .thenReturn(artifact);
        when(fixture.taskMapper.markSuccess(eq(processing.getId()), eq(1L), any(),
                eq(20L), eq(20L), eq(artifact.fileName()), eq(artifact.contentType()),
                eq(artifact.objectKey()), eq(artifact.fileSizeBytes()), eq(artifact.fileSha256()),
                eq(artifact.expiresAt()), any())).thenReturn(1);
        when(fixture.taskMapper.selectExpired(any(), anyInt())).thenReturn(List.of(expired));
        when(fixture.taskMapper.markExpired(eq(expired.getId()), eq(expired.getVersion()), any()))
                .thenReturn(1);

        var result = fixture.service.dispatch(dispatchRequest());

        assertThat(result.getClaimedCount()).isEqualTo(1);
        assertThat(result.getSubmittedCount()).isEqualTo(1);
        assertThat(result.getDeferredCount()).isZero();
        assertThat(result.getRecoveredCount()).isZero();
        assertThat(result.getExpiredCount()).isEqualTo(1);
        verify(fixture.storage).delete("exports/admin/expired.xlsx");
        verify(fixture.taskMapper, timeout(1000)).markSuccess(eq(processing.getId()), eq(1L), any(),
                eq(20L), eq(20L), eq(artifact.fileName()), eq(artifact.contentType()),
                eq(artifact.objectKey()), eq(artifact.fileSizeBytes()), eq(artifact.fileSha256()),
                eq(artifact.expiresAt()), any());
    }

    /**
     * 同一轮领取多个任务时必须并行执行，避免大文件长期阻塞后续小任务。
     */
    @Test
    void shouldStartNextClaimedTaskBeforeLargeTaskCompletes() throws Exception {
        Fixture fixture = fixture();
        DataExportTaskDO firstCandidate = task("PENDING");
        firstCandidate.setTaskNo("EXP-LARGE");
        DataExportTaskDO secondCandidate = task("PENDING");
        secondCandidate.setId(12L);
        secondCandidate.setTaskNo("EXP-SMALL");
        DataExportTaskDO firstProcessing = task("PROCESSING");
        firstProcessing.setTaskNo("EXP-LARGE");
        firstProcessing.setVersion(1L);
        DataExportTaskDO secondProcessing = task("PROCESSING");
        secondProcessing.setId(12L);
        secondProcessing.setTaskNo("EXP-SMALL");
        secondProcessing.setVersion(1L);
        CountDownLatch releaseLargeTask = new CountDownLatch(1);
        CountDownLatch smallTaskStarted = new CountDownLatch(1);
        when(fixture.taskMapper.selectDispatchCandidates(any(), anyInt()))
                .thenReturn(List.of(firstCandidate, secondCandidate));
        when(fixture.taskMapper.claim(anyLong(), anyLong(), any(), any(), any())).thenReturn(1);
        when(fixture.taskMapper.selectById(firstCandidate.getId())).thenReturn(firstProcessing);
        when(fixture.taskMapper.selectById(secondCandidate.getId())).thenReturn(secondProcessing);
        when(fixture.processor.process(any(), same(fixture.storage), any(), any()))
                .thenAnswer(invocation -> {
                    DataExportTaskDO task = invocation.getArgument(0);
                    if ("EXP-LARGE".equals(task.getTaskNo())) {
                        assertThat(releaseLargeTask.await(2, TimeUnit.SECONDS)).isTrue();
                    } else {
                        smallTaskStarted.countDown();
                    }
                    return artifact("exports/admin/" + task.getTaskNo() + ".xlsx");
                });
        when(fixture.taskMapper.markSuccess(anyLong(), anyLong(), any(), anyLong(), anyLong(),
                any(), any(), any(), anyLong(), any(), any(), any())).thenReturn(1);
        when(fixture.taskMapper.selectExpired(any(), anyInt())).thenReturn(List.of());
        DispatchRequest request = dispatchRequest();
        request.setBatchSize(2);

        CompletableFuture<Void> dispatch = CompletableFuture.runAsync(() -> fixture.service.dispatch(request));
        try {
            assertThat(smallTaskStarted.await(500, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            releaseLargeTask.countDown();
            dispatch.get(3, TimeUnit.SECONDS);
        }
    }

    /**
     * 调度接口只负责有界领取和提交，不能等待大文件生成后才释放内部 HTTP 请求。
     */
    @Test
    void shouldReturnAfterSubmittingClaimedTaskWithoutWaitingForFileGeneration() throws Exception {
        ExecutorService taskExecutor = Executors.newSingleThreadExecutor();
        Fixture fixture = fixture(taskExecutor);
        DataExportTaskDO candidate = task("PENDING");
        DataExportTaskDO processing = task("PROCESSING");
        processing.setVersion(1L);
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch releaseTask = new CountDownLatch(1);
        when(fixture.taskMapper.selectDispatchCandidates(any(), anyInt())).thenReturn(List.of(candidate));
        when(fixture.taskMapper.claim(anyLong(), anyLong(), any(), any(), any())).thenReturn(1);
        when(fixture.taskMapper.selectById(candidate.getId())).thenReturn(processing);
        when(fixture.processor.process(same(processing), same(fixture.storage), any(), any()))
                .thenAnswer(invocation -> {
                    taskStarted.countDown();
                    assertThat(releaseTask.await(3, TimeUnit.SECONDS)).isTrue();
                    return artifact("exports/admin/current.xlsx");
                });
        when(fixture.taskMapper.markSuccess(anyLong(), anyLong(), any(), anyLong(), anyLong(),
                any(), any(), any(), anyLong(), any(), any(), any())).thenReturn(1);
        when(fixture.taskMapper.selectExpired(any(), anyInt())).thenReturn(List.of());

        CompletableFuture<DispatchResult> dispatch = CompletableFuture.supplyAsync(
                () -> fixture.service.dispatch(dispatchRequest()));
        try {
            assertThat(taskStarted.await(500, TimeUnit.MILLISECONDS)).isTrue();
            DispatchResult result = dispatch.get(500, TimeUnit.MILLISECONDS);
            assertThat(result.getClaimedCount()).isEqualTo(1);
            assertThat(result.getSubmittedCount()).isEqualTo(1);
        } finally {
            releaseTask.countDown();
            dispatch.get(3, TimeUnit.SECONDS);
            verify(fixture.taskMapper, timeout(1000)).markSuccess(eq(processing.getId()), eq(1L), any(),
                    eq(20L), eq(20L), any(), any(), any(), anyLong(), any(), any(), any());
            taskExecutor.shutdownNow();
        }
    }

    /**
     * 专用执行器拒绝提交时必须释放领取并延后，不得消耗业务失败重试次数。
     */
    @Test
    void shouldDeferClaimWithoutConsumingRetryWhenExecutorRejects() {
        Executor rejectingExecutor = command -> {
            throw new RejectedExecutionException("executor busy");
        };
        Fixture fixture = fixture(rejectingExecutor);
        DataExportTaskDO candidate = task("PENDING");
        DataExportTaskDO processing = task("PROCESSING");
        processing.setVersion(1L);
        when(fixture.taskMapper.selectDispatchCandidates(any(), anyInt())).thenReturn(List.of(candidate));
        when(fixture.taskMapper.claim(anyLong(), anyLong(), any(), any(), any())).thenReturn(1);
        when(fixture.taskMapper.selectById(candidate.getId())).thenReturn(processing);
        when(fixture.taskMapper.deferClaim(eq(processing.getId()), eq(1L), any(), any(), any())).thenReturn(1);
        when(fixture.taskMapper.selectExpired(any(), anyInt())).thenReturn(List.of());

        DispatchResult result = fixture.service.dispatch(dispatchRequest());

        assertThat(result.getClaimedCount()).isEqualTo(1);
        assertThat(result.getSubmittedCount()).isZero();
        assertThat(result.getDeferredCount()).isEqualTo(1);
        verify(fixture.taskMapper).deferClaim(eq(processing.getId()), eq(1L), any(), any(), any());
        verify(fixture.taskMapper, never()).markFailure(anyLong(), anyLong(), any(),
                any(), any(), any(), any(), any());
    }

    /**
     * 上传成功但任务成功状态 CAS 冲突时必须删除孤立对象，不能留下无任务引用文件。
     */
    @Test
    void shouldDeleteUploadedObjectWhenSuccessCasConflicts() {
        Fixture fixture = fixture();
        DataExportTaskDO candidate = task("PENDING");
        DataExportTaskDO processing = task("PROCESSING");
        processing.setVersion(1L);
        ExportArtifact artifact = artifact("exports/admin/orphan.xlsx");
        when(fixture.taskMapper.selectDispatchCandidates(any(), anyInt())).thenReturn(List.of(candidate));
        when(fixture.taskMapper.claim(anyLong(), anyLong(), any(), any(), any())).thenReturn(1);
        when(fixture.taskMapper.selectById(candidate.getId())).thenReturn(processing);
        when(fixture.processor.process(same(processing), same(fixture.storage), any(), any()))
                .thenReturn(artifact);
        when(fixture.taskMapper.markSuccess(anyLong(), anyLong(), any(), anyLong(), anyLong(),
                any(), any(), any(), anyLong(), any(), any(), any())).thenReturn(0);
        when(fixture.taskMapper.markFailure(anyLong(), anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);
        when(fixture.taskMapper.selectExpired(any(), anyInt())).thenReturn(List.of());

        var result = fixture.service.dispatch(dispatchRequest());

        assertThat(result.getSubmittedCount()).isEqualTo(1);
        verify(fixture.storage, timeout(1000)).delete("exports/admin/orphan.xlsx");
    }

    /**
     * 自动重试次数耗尽后必须进入 FAILED，等待带版本的人工重试。
     */
    @Test
    void shouldMoveToFailedWhenAutomaticRetriesAreExhausted() {
        Fixture fixture = fixture();
        DataExportTaskDO candidate = task("PENDING");
        DataExportTaskDO processing = task("PROCESSING");
        processing.setVersion(1L);
        processing.setRetryCount(2);
        processing.setMaxRetryCount(3);
        when(fixture.taskMapper.selectDispatchCandidates(any(), anyInt())).thenReturn(List.of(candidate));
        when(fixture.taskMapper.claim(anyLong(), anyLong(), any(), any(), any())).thenReturn(1);
        when(fixture.taskMapper.selectById(candidate.getId())).thenReturn(processing);
        when(fixture.processor.process(same(processing), same(fixture.storage), any(), any()))
                .thenThrow(new IllegalStateException("source unavailable"));
        when(fixture.taskMapper.markFailure(eq(processing.getId()), eq(1L), any(), eq("FAILED"),
                eq("IllegalStateException"), eq("Export task execution failed. Retry later or contact the system administrator."),
                isNull(), any())).thenReturn(1);
        when(fixture.taskMapper.selectExpired(any(), anyInt())).thenReturn(List.of());

        var result = fixture.service.dispatch(dispatchRequest());

        assertThat(result.getSubmittedCount()).isEqualTo(1);
        verify(fixture.taskMapper, timeout(1000)).markFailure(eq(processing.getId()), eq(1L), any(), eq("FAILED"),
                eq("IllegalStateException"), eq("Export task execution failed. Retry later or contact the system administrator."),
                isNull(), any());
    }

    /**
     * PROCESSING 租约过期时必须先记录一次失败并重新排队，不能永久卡在处理中。
     */
    @Test
    void shouldRecoverStaleProcessingLeaseWithRetryPolicy() {
        Fixture fixture = fixture();
        DataExportTaskDO stale = task("PROCESSING");
        stale.setVersion(3L);
        stale.setLeaseOwner(null);
        stale.setRetryCount(0);
        when(fixture.taskMapper.selectStaleProcessing(any(), anyInt())).thenReturn(List.of(stale));
        when(fixture.taskMapper.markFailure(eq(stale.getId()), eq(3L), isNull(), eq("PENDING"),
                eq("EXPORT_PROCESSING_LEASE_EXPIRED"),
                eq("Export task execution timed out and was recovered by the retry policy."), any(), any()))
                .thenReturn(1);
        when(fixture.taskMapper.selectDispatchCandidates(any(), anyInt())).thenReturn(List.of());
        when(fixture.taskMapper.selectExpired(any(), anyInt())).thenReturn(List.of());

        var result = fixture.service.dispatch(dispatchRequest());

        assertThat(result.getRecoveredCount()).isEqualTo(1);
        verify(fixture.taskMapper).markFailure(eq(stale.getId()), eq(3L), isNull(), eq("PENDING"),
                eq("EXPORT_PROCESSING_LEASE_EXPIRED"),
                eq("Export task execution timed out and was recovered by the retry policy."), any(), any());
    }

    /**
     * 人工重试必须同时匹配创建账号、FAILED 状态和预期版本。
     */
    @Test
    void shouldRetryFailedTaskWithExpectedVersion() {
        Fixture fixture = fixture();
        DataExportTaskDO failed = task("FAILED");
        failed.setVersion(7L);
        DataExportTaskDO pending = task("PENDING");
        pending.setVersion(8L);
        when(fixture.taskMapper.selectOwned(failed.getTaskNo(), APP_ADMIN, 88L, null))
                .thenReturn(failed, pending);
        when(fixture.taskMapper.retry(eq(failed.getTaskNo()), eq(APP_ADMIN), eq(88L), isNull(),
                eq(7L), any())).thenReturn(1);
        TaskRequest request = new TaskRequest();
        request.setTaskNo(failed.getTaskNo());
        request.setCreatorAppCode(APP_ADMIN);
        request.setCreatorAccountId(88L);
        request.setExpectedVersion(7L);

        var result = fixture.service.retry(request);

        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getVersion()).isEqualTo(8L);
    }

    /**
     * 归属账号不匹配时不得读取对象或写入下载审计。
     */
    @Test
    void shouldRejectDownloadFromAnotherAccount() {
        Fixture fixture = fixture();
        DownloadRequest request = downloadRequest();
        when(fixture.taskMapper.selectOwned(request.getTaskNo(), APP_ADMIN,
                request.getCreatorAccountId(), null))
                .thenReturn(null);

        assertThatThrownBy(() -> fixture.service.download(request, new ByteArrayOutputStream()))
                .isInstanceOf(ServiceException.class);
        verify(fixture.auditMapper, never()).insert(any());
        verify(fixture.storage, never()).copyTo(any(), any());
    }

    /**
     * 文件完整写出后下载审计必须从 REQUESTED 条件更新为 SUCCESS。
     */
    @Test
    void shouldAuditSuccessfulDownload() {
        Fixture fixture = fixture();
        DataExportTaskDO success = downloadableTask();
        when(fixture.taskMapper.selectOwned(success.getTaskNo(), APP_ADMIN, 88L, null))
                .thenReturn(success);
        when(fixture.auditMapper.insert(any())).thenReturn(1);
        when(fixture.auditMapper.complete(eq("DLA1001"), eq("SUCCESS"), isNull(), any()))
                .thenReturn(1);
        doAnswer(invocation -> {
            ByteArrayOutputStream output = invocation.getArgument(1);
            output.writeBytes(new byte[] {1, 2, 3});
            return null;
        }).when(fixture.storage).copyTo(eq(success.getObjectKey()), any());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        fixture.service.download(downloadRequest(), output);

        ArgumentCaptor<DataExportDownloadAuditDO> auditCaptor =
                ArgumentCaptor.forClass(DataExportDownloadAuditDO.class);
        verify(fixture.auditMapper).insert(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getResultStatus()).isEqualTo("REQUESTED");
        assertThat(auditCaptor.getValue().getOperatorAccountId()).isEqualTo(88L);
        assertThat(output.toByteArray()).containsExactly(1, 2, 3);
        verify(fixture.auditMapper).complete(eq("DLA1001"), eq("SUCCESS"), isNull(), any());
    }

    /**
     * 对象读取失败时必须把同一审计记录更新为 FAILED 并保留原始异常。
     */
    @Test
    void shouldAuditFailedDownload() {
        Fixture fixture = fixture();
        DataExportTaskDO success = downloadableTask();
        when(fixture.taskMapper.selectOwned(success.getTaskNo(), APP_ADMIN, 88L, null))
                .thenReturn(success);
        when(fixture.auditMapper.insert(any())).thenReturn(1);
        when(fixture.auditMapper.complete(eq("DLA1001"), eq("FAILED"),
                eq("Export file download failed. Retry later."), any()))
                .thenReturn(1);
        doThrow(new IllegalStateException("object unavailable"))
                .when(fixture.storage).copyTo(eq(success.getObjectKey()), any());

        assertThatThrownBy(() -> fixture.service.download(downloadRequest(), new ByteArrayOutputStream()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("object unavailable");
        verify(fixture.auditMapper).complete(
                eq("DLA1001"), eq("FAILED"), eq("Export file download failed. Retry later."), any());
    }

    /**
     * 创建具备 Mapper、对象存储和导出处理器替身的测试服务。
     */
    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        return fixture(TEST_EXECUTOR);
    }

    /**
     * 使用指定执行器构造测试服务，便于验证调度线程与文件生成线程的隔离边界。
     */
    @SuppressWarnings("unchecked")
    private Fixture fixture(Executor taskExecutor) {
        DataExportTaskMapper taskMapper = mock(DataExportTaskMapper.class);
        DataExportDownloadAuditMapper auditMapper = mock(DataExportDownloadAuditMapper.class);
        GlobalIdGenerator idGenerator = mock(GlobalIdGenerator.class);
        when(idGenerator.nextId()).thenReturn("1001");
        DataExportProperties properties = new DataExportProperties();
        ObjectProvider<ExportObjectStorageService> storageProvider = mock(ObjectProvider.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        when(storageProvider.getIfAvailable()).thenReturn(storage);
        ExportTaskProcessorRegistry processorRegistry = mock(ExportTaskProcessorRegistry.class);
        ExportTaskProcessor processor = mock(ExportTaskProcessor.class);
        when(processorRegistry.required(anyString())).thenReturn(processor);
        ArgumentCaptor<DataExportTaskDO> insertedTask = ArgumentCaptor.forClass(DataExportTaskDO.class);
        when(taskMapper.insert(insertedTask.capture())).thenReturn(1);
        DataExportTaskService service = new DataExportTaskService(
                taskMapper, auditMapper, idGenerator, properties, storageProvider, processorRegistry,
                taskExecutor);
        return new Fixture(service, taskMapper, auditMapper, storage, processor, insertedTask);
    }

    /**
     * 构造已通过来源服务可信冻结的创建命令。
     */
    private CreateRequest createRequest() {
        CreateRequest request = new CreateRequest();
        request.setRequestId(" request-001 ");
        request.setTaskType(ADMIN_OPERATIONAL_WORK_ITEM);
        request.setTaskName("统一运营待办导出");
        request.setCreatorAppCode(APP_ADMIN);
        request.setCreatorAccountId(88L);
        request.setCreatorAccountName("Export Operator");
        request.setAllMerchants(false);
        request.setMerchantIds(Set.of("M1001"));
        request.setWorkItemQuery(new WorkItemQuery());
        return request;
    }

    /**
     * 构造商户交易导出命令，商户号模拟由 Merchant 认证上下文冻结后的可信值。
     */
    private CreateRequest merchantCreateRequest(String merchantId) {
        CreateRequest request = new CreateRequest();
        request.setRequestId("merchant-request-001");
        request.setTaskType(MERCHANT_TRANSACTION_OPERATION);
        request.setTaskName("交易查询导出");
        request.setCreatorAppCode(APP_MERCHANT);
        request.setCreatorAccountId(88L);
        request.setMerchantId(merchantId);
        request.setCreatorAccountName("Merchant Operator");
        request.setMerchantTransactionQuery(new MerchantTransactionQuery());
        return request;
    }

    /**
     * 构造带稳定请求键和调度时间的一轮执行命令。
     */
    private DispatchRequest dispatchRequest() {
        DispatchRequest request = new DispatchRequest();
        request.setRequestId("DATA_EXPORT_TASK_DISPATCH:run-001");
        request.setBatchSize(1);
        request.setDispatchTime(LocalDateTime.of(2026, 11, 9, 10, 0));
        return request;
    }

    /**
     * 构造指定状态且字段完整的任务持久化对象。
     */
    private DataExportTaskDO task(String status) {
        DataExportTaskDO task = new DataExportTaskDO();
        task.setId(11L);
        task.setTaskNo("EXP1001");
        task.setTaskType(ADMIN_OPERATIONAL_WORK_ITEM);
        task.setTaskName("统一运营待办导出");
        task.setCreatorAppCode(APP_ADMIN);
        task.setCreatorAccountId(88L);
        task.setStatus(status);
        task.setProgressPercent(0);
        task.setProcessedRows(0L);
        task.setTotalRows(0L);
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        task.setVersion(0L);
        task.setCreateTime(LocalDateTime.of(2026, 11, 9, 9, 0));
        return task;
    }

    /**
     * 构造已上传文件的不可变元数据。
     */
    private ExportArtifact artifact(String objectKey) {
        return new ExportArtifact("work-items.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                objectKey, 4096L, "a".repeat(64), 20L, 20L,
                LocalDateTime.of(2026, 11, 16, 10, 0));
    }

    /**
     * 构造可下载且仍在保留期内的成功任务。
     */
    private DataExportTaskDO downloadableTask() {
        DataExportTaskDO task = task("SUCCESS");
        task.setObjectKey("exports/admin/current.xlsx");
        task.setFileName("work-items.xlsx");
        task.setFileSha256("b".repeat(64));
        task.setExpiresAt(LocalDateTime.now().plusDays(1));
        return task;
    }

    /**
     * 构造当前账号下载请求，操作人和来源 IP 只进入审计。
     */
    private DownloadRequest downloadRequest() {
        DownloadRequest request = new DownloadRequest();
        request.setTaskNo("EXP1001");
        request.setCreatorAppCode(APP_ADMIN);
        request.setCreatorAccountId(88L);
        request.setOperatorName("Export Operator");
        request.setClientIp("127.0.0.1");
        return request;
    }

    /**
     * 聚合测试服务和协作对象，避免各用例重复装配。
     */
    private record Fixture(DataExportTaskService service,
                           DataExportTaskMapper taskMapper,
                           DataExportDownloadAuditMapper auditMapper,
                           ExportObjectStorageService storage,
                           ExportTaskProcessor processor,
                           ArgumentCaptor<DataExportTaskDO> insertedTask) {
    }
}
