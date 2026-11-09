package com.scott.payment.data.service.export;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CreateRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DispatchRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DownloadRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.TaskRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.WorkItemQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantNotificationQuery;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_OPERATIONAL_WORK_ITEM;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_TRANSACTION_ORDER;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_TRANSACTION_OPERATION;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_MERCHANT_NOTIFICATION;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SETTLEMENT_RESERVE;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SETTLEMENT_TRANSACTION;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 异步导出任务状态机测试，覆盖幂等、租约、重试、对象清理和下载审计。
 */
class DataExportTaskServiceTest {

    /**
     * 同一创建账号和请求键重复提交时必须返回既有任务，不得再次插入。
     */
    @Test
    void shouldReturnExistingTaskForDuplicateSubmission() {
        Fixture fixture = fixture();
        DataExportTaskDO existing = task("PENDING");
        when(fixture.taskMapper.selectByRequestId(88L, "request-001")).thenReturn(existing);

        var result = fixture.service.submit(createRequest());

        assertThat(result.getTaskNo()).isEqualTo(existing.getTaskNo());
        verify(fixture.taskMapper, never()).insert(any());
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
        when(fixture.taskMapper.selectOwned(anyString(), eq(88L))).thenAnswer(invocation -> {
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
        when(fixture.taskMapper.selectOwned(anyString(), eq(88L))).thenAnswer(invocation -> {
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
        when(fixture.taskMapper.selectOwned(anyString(), eq(88L))).thenAnswer(invocation -> {
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
        when(fixture.taskMapper.selectOwned(anyString(), eq(88L))).thenAnswer(invocation -> {
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
        when(reserveFixture.taskMapper.selectOwned(anyString(), eq(88L))).thenAnswer(invocation -> {
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
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        assertThat(result.getExpiredCount()).isEqualTo(1);
        verify(fixture.storage).delete("exports/admin/expired.xlsx");
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

        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(fixture.storage).delete("exports/admin/orphan.xlsx");
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

        assertThat(result.getFailedCount()).isEqualTo(1);
        verify(fixture.taskMapper).markFailure(eq(processing.getId()), eq(1L), any(), eq("FAILED"),
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

        assertThat(result.getFailedCount()).isEqualTo(1);
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
        when(fixture.taskMapper.selectOwned(failed.getTaskNo(), 88L)).thenReturn(failed, pending);
        when(fixture.taskMapper.retry(eq(failed.getTaskNo()), eq(88L), eq(7L), any())).thenReturn(1);
        TaskRequest request = new TaskRequest();
        request.setTaskNo(failed.getTaskNo());
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
        when(fixture.taskMapper.selectOwned(request.getTaskNo(), request.getCreatorAccountId()))
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
        when(fixture.taskMapper.selectOwned(success.getTaskNo(), 88L)).thenReturn(success);
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
        when(fixture.taskMapper.selectOwned(success.getTaskNo(), 88L)).thenReturn(success);
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
                taskMapper, auditMapper, idGenerator, properties, storageProvider, processorRegistry);
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
        request.setCreatorAccountId(88L);
        request.setCreatorAccountName("Export Operator");
        request.setAllMerchants(false);
        request.setMerchantIds(Set.of("M1001"));
        request.setWorkItemQuery(new WorkItemQuery());
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
