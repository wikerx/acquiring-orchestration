package com.scott.payment.data.service.export;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CreateRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DispatchRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DispatchResult;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DownloadRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SearchRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.TaskRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.TaskSummary;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportDownloadAuditDO;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.mapper.export.DataExportDownloadAuditMapper;
import com.scott.payment.data.mapper.export.DataExportTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 异步导出任务领域服务，负责提交幂等、租约领取、状态迁移、文件清理和下载审计。
 */
@Slf4j
@Service
public class DataExportTaskService {
    public static final String ADMIN_OPERATIONAL_WORK_ITEM = "ADMIN_OPERATIONAL_WORK_ITEM";
    public static final String ADMIN_TRANSACTION_ORDER = "ADMIN_TRANSACTION_ORDER";
    public static final String ADMIN_TRANSACTION_OPERATION = "ADMIN_TRANSACTION_OPERATION";
    public static final String ADMIN_MERCHANT_NOTIFICATION = "ADMIN_MERCHANT_NOTIFICATION";
    public static final String ADMIN_REFUND = "ADMIN_REFUND";
    public static final String ADMIN_SETTLEMENT_TRANSACTION = "ADMIN_SETTLEMENT_TRANSACTION";
    public static final String ADMIN_SETTLEMENT_RESERVE = "ADMIN_SETTLEMENT_RESERVE";
    private static final Set<String> SUPPORTED_TASK_TYPES = Set.of(
            ADMIN_OPERATIONAL_WORK_ITEM, ADMIN_TRANSACTION_ORDER, ADMIN_TRANSACTION_OPERATION,
            ADMIN_MERCHANT_NOTIFICATION, ADMIN_REFUND,
            ADMIN_SETTLEMENT_TRANSACTION, ADMIN_SETTLEMENT_RESERVE);
    private static final String PENDING = "PENDING";
    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final String EXPIRED = "EXPIRED";
    private static final Set<String> SEARCH_STATUSES = Set.of(PENDING, PROCESSING, SUCCESS, FAILED, EXPIRED);
    private final DataExportTaskMapper taskMapper;
    private final DataExportDownloadAuditMapper auditMapper;
    private final GlobalIdGenerator globalIdGenerator;
    private final DataExportProperties properties;
    private final ObjectProvider<ExportObjectStorageService> storageProvider;
    private final ExportTaskProcessorRegistry processorRegistry;

    /**
     * 创建异步导出任务服务。
     */
    public DataExportTaskService(DataExportTaskMapper taskMapper,
                                 DataExportDownloadAuditMapper auditMapper,
                                 GlobalIdGenerator globalIdGenerator,
                                 DataExportProperties properties,
                                 ObjectProvider<ExportObjectStorageService> storageProvider,
                                 ExportTaskProcessorRegistry processorRegistry) {
        this.taskMapper = taskMapper;
        this.auditMapper = auditMapper;
        this.globalIdGenerator = globalIdGenerator;
        this.properties = properties;
        this.storageProvider = storageProvider;
        this.processorRegistry = processorRegistry;
    }

    /**
     * 幂等创建受支持的管理端异步导出任务。
     */
    @DS(DataSourceName.MASTER)
    public TaskSummary submit(CreateRequest request) {
        requireCreateRequest(request);
        properties.validate();
        DataExportTaskDO existing = taskMapper.selectByRequestId(
                request.getCreatorAccountId(), request.getRequestId().trim());
        if (existing != null) {
            return toSummary(existing);
        }
        LocalDateTime now = LocalDateTime.now();
        DataExportTaskDO task = new DataExportTaskDO();
        task.setTaskNo("EXP" + globalIdGenerator.nextId());
        task.setRequestId(request.getRequestId().trim());
        task.setTaskType(request.getTaskType().trim());
        task.setTaskName(truncate(request.getTaskName(), 128));
        task.setSourceService("service-admin");
        task.setCreatorAccountId(request.getCreatorAccountId());
        task.setCreatorAccountName(truncate(request.getCreatorAccountName(), 128));
        task.setLocale(normalizeLocale(request.getLocale()));
        task.setTimezone(normalizeTimezone(request.getTimezone()));
        task.setQuerySnapshot(JsonUtils.toJsonString(querySnapshot(request)));
        AdminExportScopeSnapshot scope = new AdminExportScopeSnapshot();
        scope.setAllMerchants(request.isAllMerchants());
        scope.setMerchantIds(request.getMerchantIds());
        task.setDataScopeSnapshot(JsonUtils.toJsonString(scope));
        task.setStatus(PENDING);
        task.setProgressPercent(0);
        task.setProcessedRows(0L);
        task.setTotalRows(0L);
        task.setRetryCount(0);
        task.setMaxRetryCount(properties.getMaxRetryCount());
        task.setNextRetryTime(now);
        task.setVersion(0L);
        task.setCreateTime(now);
        task.setUpdateTime(now);
        try {
            if (taskMapper.insert(task) != 1) {
                throw new IllegalStateException("export task was not inserted");
            }
        } catch (DuplicateKeyException exception) {
            DataExportTaskDO duplicate = taskMapper.selectByRequestId(
                    request.getCreatorAccountId(), request.getRequestId().trim());
            if (duplicate == null) {
                throw exception;
            }
            return toSummary(duplicate);
        }
        log.info("异步导出任务已创建, event: DATA_EXPORT_TASK_CREATED, taskNo: {}, taskType: {}, creatorAccountId: {}",
                task.getTaskNo(), task.getTaskType(), task.getCreatorAccountId());
        return toSummary(taskMapper.selectOwned(task.getTaskNo(), task.getCreatorAccountId()));
    }

    /**
     * 分页查询当前创建人的任务，不允许跨账号查看条件或文件。
     */
    @DS(DataSourceName.MASTER)
    public PageResult<TaskSummary> search(SearchRequest request) {
        if (request == null || request.getCreatorAccountId() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        String status = normalizeStatus(request.getStatus());
        int pageNo = request.getPageNo() == null ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null ? 20 : request.getPageSize();
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        String taskType = StringUtils.hasText(request.getTaskType()) ? request.getTaskType().trim() : null;
        long total = taskMapper.countOwned(request.getCreatorAccountId(), taskType, status,
                request.getCreatedStartTime(), request.getCreatedEndTime());
        long offset = (long) (pageNo - 1) * pageSize;
        List<TaskSummary> records = offset >= total ? List.of() : taskMapper.selectOwnedPage(
                        request.getCreatorAccountId(), taskType, status,
                        request.getCreatedStartTime(), request.getCreatedEndTime(), offset, pageSize)
                .stream().map(this::toSummary).toList();
        return PageResult.of(total, pageNo, pageSize, records);
    }

    /**
     * 查询归属于当前创建人的任务详情。
     */
    @DS(DataSourceName.MASTER)
    public TaskSummary detail(TaskRequest request) {
        return toSummary(requiredOwned(request));
    }

    /**
     * 使用版本条件人工重试 FAILED 任务，重置自动重试计数并重新进入队列。
     */
    @DS(DataSourceName.MASTER)
    public TaskSummary retry(TaskRequest request) {
        DataExportTaskDO task = requiredOwned(request);
        if (!FAILED.equals(task.getStatus()) || request.getExpectedVersion() == null
                || taskMapper.retry(task.getTaskNo(), task.getCreatorAccountId(),
                request.getExpectedVersion(), LocalDateTime.now()) != 1) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "export task cannot be retried");
        }
        log.info("异步导出任务已人工重试, event: DATA_EXPORT_TASK_RETRIED, taskNo: {}, creatorAccountId: {}",
                task.getTaskNo(), task.getCreatorAccountId());
        return toSummary(taskMapper.selectOwned(task.getTaskNo(), task.getCreatorAccountId()));
    }

    /**
     * 由 Job 触发一轮有界领取、执行和过期清理；单个任务失败不会阻断同批其他任务。
     */
    @DS(DataSourceName.MASTER)
    public DispatchResult dispatch(DispatchRequest request) {
        if (request == null || !StringUtils.hasText(request.getRequestId()) || request.getDispatchTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        properties.validate();
        int batchSize = request.getBatchSize() == null
                ? properties.getDispatchBatchSize() : request.getBatchSize();
        if (batchSize < 1 || batchSize > 50) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        int claimed = 0;
        int succeeded = 0;
        int failed = recoverStaleProcessing(request.getDispatchTime(), batchSize);
        ExportObjectStorageService storage = requireStorage();
        for (DataExportTaskDO candidate : taskMapper.selectDispatchCandidates(request.getDispatchTime(), batchSize)) {
            String leaseOwner = request.getRequestId().trim() + ":" + candidate.getTaskNo();
            LocalDateTime leaseUntil = request.getDispatchTime().plus(properties.getProcessingLease());
            if (taskMapper.claim(candidate.getId(), candidate.getVersion(), leaseOwner,
                    leaseUntil, request.getDispatchTime()) != 1) {
                continue;
            }
            claimed++;
            DataExportTaskDO task = taskMapper.selectById(candidate.getId());
            long processingVersion = task.getVersion();
            try {
                ExportArtifact artifact = process(task, storage, leaseOwner, processingVersion);
                int updated = taskMapper.markSuccess(task.getId(), processingVersion, leaseOwner,
                        artifact.processedRows(), artifact.totalRows(), artifact.fileName(), artifact.contentType(),
                        artifact.objectKey(), artifact.fileSizeBytes(), artifact.fileSha256(),
                        artifact.expiresAt(), LocalDateTime.now());
                if (updated != 1) {
                    storage.delete(artifact.objectKey());
                    throw new IllegalStateException("export task success state conflict");
                }
                succeeded++;
                log.info("异步导出任务完成, event: DATA_EXPORT_TASK_SUCCEEDED, taskNo: {}, processedRows: {}, fileSizeBytes: {}",
                        task.getTaskNo(), artifact.processedRows(), artifact.fileSizeBytes());
            } catch (RuntimeException exception) {
                failed++;
                recordFailure(task, processingVersion, leaseOwner,
                        exception.getClass().getSimpleName(), executionFailureReason(task), LocalDateTime.now());
                log.warn("异步导出任务执行失败, event: DATA_EXPORT_TASK_FAILED, taskNo: {}, exceptionType: {}",
                        task.getTaskNo(), exception.getClass().getSimpleName());
            }
        }
        int expired = cleanupExpired(request.getDispatchTime(), Math.max(batchSize * 2, 10), storage);
        DispatchResult result = new DispatchResult();
        result.setRequestId(request.getRequestId().trim());
        result.setClaimedCount(claimed);
        result.setSuccessCount(succeeded);
        result.setFailedCount(failed);
        result.setExpiredCount(expired);
        return result;
    }

    /**
     * 校验文件归属和有效期后代理下载，输出前固化请求审计并在结束时记录结果。
     */
    @DS(DataSourceName.MASTER)
    public TaskSummary download(DownloadRequest request, OutputStream outputStream) {
        DataExportTaskDO task = requiredOwned(request);
        LocalDateTime now = LocalDateTime.now();
        if (!SUCCESS.equals(task.getStatus()) || !StringUtils.hasText(task.getObjectKey())
                || task.getExpiresAt() == null || !task.getExpiresAt().isAfter(now)) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "export file is unavailable");
        }
        String auditNo = appendRequestedAudit(task, request, now);
        try {
            requireStorage().copyTo(task.getObjectKey(), outputStream);
            completeAudit(auditNo, "SUCCESS", null);
            return toSummary(task);
        } catch (RuntimeException exception) {
            completeAudit(auditNo, "FAILED", downloadFailureReason(task));
            throw exception;
        }
    }

    /**
     * 将崩溃或超时执行者遗留的过期租约记为一次失败，耗尽次数时进入 FAILED。
     */
    private int recoverStaleProcessing(LocalDateTime now, int limit) {
        int recovered = 0;
        for (DataExportTaskDO task : taskMapper.selectStaleProcessing(now, limit)) {
            if (recordFailure(task, task.getVersion(), task.getLeaseOwner(),
                    "EXPORT_PROCESSING_LEASE_EXPIRED", leaseExpiredReason(task), now)) {
                recovered++;
                log.warn("异步导出任务租约过期已恢复, event: DATA_EXPORT_TASK_LEASE_RECOVERED, taskNo: {}, retryCount: {}",
                        task.getTaskNo(), task.getRetryCount());
            }
        }
        return recovered;
    }

    /**
     * 根据稳定任务类型选择处理器并使用当前租约版本提交分页进度。
     */
    private ExportArtifact process(DataExportTaskDO task,
                                   ExportObjectStorageService storage,
                                   String leaseOwner,
                                   long processingVersion) {
        return processorRegistry.required(task.getTaskType()).process(task, storage, (processed, total) -> {
            int percent = total <= 0 ? 0 : (int) Math.min(99, processed * 100 / total);
            int updated = taskMapper.updateProgress(task.getId(), processingVersion, leaseOwner,
                    processed, total, percent, LocalDateTime.now().plus(properties.getProcessingLease()),
                    LocalDateTime.now());
            if (updated != 1) {
                throw new IllegalStateException("export task progress state conflict");
            }
        }, LocalDateTime.now());
    }

    /**
     * 保存脱敏失败摘要，并按剩余次数决定自动重试或 FAILED 终态。
     */
    private boolean recordFailure(DataExportTaskDO task,
                                  long processingVersion,
                                  String leaseOwner,
                                  String failureCode,
                                  String failureReason,
                                  LocalDateTime now) {
        int nextRetryCount = task.getRetryCount() == null ? 1 : task.getRetryCount() + 1;
        boolean retryable = nextRetryCount < task.getMaxRetryCount();
        int updated = taskMapper.markFailure(task.getId(), processingVersion, leaseOwner,
                retryable ? PENDING : FAILED, truncate(failureCode, 128),
                truncate(failureReason, 500), retryable ? now.plus(properties.getRetryDelay()) : null, now);
        if (updated != 1) {
            log.error("导出任务失败状态提交冲突, event: DATA_EXPORT_TASK_FAILURE_CONFLICT, taskNo: {}, expectedVersion: {}",
                    task.getTaskNo(), processingVersion);
            return false;
        }
        return true;
    }

    /**
     * 清理到期文件；对象删除成功后才允许提交 EXPIRED 状态。
     */
    private int cleanupExpired(LocalDateTime now, int limit, ExportObjectStorageService storage) {
        int expired = 0;
        for (DataExportTaskDO task : taskMapper.selectExpired(now, limit)) {
            try {
                storage.delete(task.getObjectKey());
                if (taskMapper.markExpired(task.getId(), task.getVersion(), now) == 1) {
                    expired++;
                }
            } catch (RuntimeException exception) {
                log.warn("过期导出文件清理失败, event: DATA_EXPORT_FILE_CLEANUP_FAILED, taskNo: {}, exceptionType: {}",
                        task.getTaskNo(), exception.getClass().getSimpleName());
            }
        }
        return expired;
    }

    /**
     * 输出正文前追加下载请求审计；审计失败时禁止开始发送文件。
     */
    private String appendRequestedAudit(DataExportTaskDO task,
                                        DownloadRequest request,
                                        LocalDateTime now) {
        DataExportDownloadAuditDO audit = new DataExportDownloadAuditDO();
        audit.setAuditNo("DLA" + globalIdGenerator.nextId());
        audit.setTaskNo(task.getTaskNo());
        audit.setOperatorAccountId(request.getCreatorAccountId());
        audit.setOperatorAccountName(truncate(request.getOperatorName(), 128));
        audit.setClientIp(truncate(request.getClientIp(), 64));
        audit.setResultStatus("REQUESTED");
        audit.setFileSha256(task.getFileSha256());
        audit.setRequestedAt(now);
        audit.setUpdateTime(now);
        if (auditMapper.insert(audit) != 1) {
            throw new IllegalStateException("export download audit was not inserted");
        }
        return audit.getAuditNo();
    }

    /**
     * 条件提交下载审计最终结果；提交失败视为内部完整性错误并输出稳定事件日志。
     */
    private void completeAudit(String auditNo, String status, String failureReason) {
        if (auditMapper.complete(auditNo, status, failureReason, LocalDateTime.now()) != 1) {
            log.error("导出下载审计状态提交失败, event: DATA_EXPORT_DOWNLOAD_AUDIT_CONFLICT, auditNo: {}, targetStatus: {}",
                    auditNo, status);
            throw new IllegalStateException("export download audit state conflict");
        }
    }

    /**
     * 查询并校验任务归属，未找到时统一返回不存在以防账号枚举。
     */
    private DataExportTaskDO requiredOwned(TaskRequest request) {
        if (request == null || !StringUtils.hasText(request.getTaskNo())
                || request.getCreatorAccountId() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        DataExportTaskDO task = taskMapper.selectOwned(request.getTaskNo().trim(), request.getCreatorAccountId());
        if (task == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND);
        }
        return task;
    }

    /**
     * 获取已启用对象存储实现，缺失时返回明确配置错误。
     */
    private ExportObjectStorageService requireStorage() {
        ExportObjectStorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "export object storage is disabled");
        }
        return storage;
    }

    /**
     * 校验创建命令、任务类型对应查询和服务端冻结的数据范围。
     */
    private void requireCreateRequest(CreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getRequestId())
                || !SUPPORTED_TASK_TYPES.contains(request.getTaskType())
                || request.getCreatorAccountId() == null || !StringUtils.hasText(request.getCreatorAccountName())
                || querySnapshot(request) == null
                || !request.isAllMerchants() && (request.getMerchantIds() == null || request.getMerchantIds().isEmpty())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }

    /**
     * 按任务类型选择唯一的强类型查询快照，禁止错配任务与查询模型。
     */
    private Object querySnapshot(CreateRequest request) {
        if (request == null || request.getTaskType() == null) {
            return null;
        }
        return switch (request.getTaskType()) {
            case ADMIN_OPERATIONAL_WORK_ITEM -> request.getWorkItemQuery();
            case ADMIN_TRANSACTION_ORDER -> request.getTransactionOrderQuery();
            case ADMIN_TRANSACTION_OPERATION -> request.getTransactionOperationQuery();
            case ADMIN_MERCHANT_NOTIFICATION -> request.getMerchantNotificationQuery();
            case ADMIN_REFUND -> request.getRefundQuery();
            case ADMIN_SETTLEMENT_TRANSACTION -> request.getSettlementTransactionQuery();
            case ADMIN_SETTLEMENT_RESERVE -> request.getSettlementReserveQuery();
            default -> null;
        };
    }

    /**
     * 将内部实体转换为不包含条件和对象键的安全响应。
     */
    private TaskSummary toSummary(DataExportTaskDO task) {
        TaskSummary summary = new TaskSummary();
        summary.setTaskNo(task.getTaskNo());
        summary.setTaskType(task.getTaskType());
        summary.setTaskName(task.getTaskName());
        summary.setStatus(task.getStatus());
        summary.setProgressPercent(task.getProgressPercent());
        summary.setProcessedRows(task.getProcessedRows());
        summary.setTotalRows(task.getTotalRows());
        summary.setFileName(task.getFileName());
        summary.setFileSizeBytes(task.getFileSizeBytes());
        summary.setFileSha256(task.getFileSha256());
        summary.setExpiresAt(task.getExpiresAt());
        summary.setFailureCode(task.getFailureCode());
        summary.setFailureReason(task.getFailureReason());
        summary.setRetryCount(task.getRetryCount());
        summary.setMaxRetryCount(task.getMaxRetryCount());
        summary.setVersion(task.getVersion());
        summary.setCreateTime(task.getCreateTime());
        summary.setStartedAt(task.getStartedAt());
        summary.setFinishedAt(task.getFinishedAt());
        return summary;
    }

    /**
     * 只接受已定义任务状态，空值表示不筛选。
     */
    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!SEARCH_STATUSES.contains(normalized)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return normalized;
    }

    /**
     * 规整语言标签，空值默认中文。
     */
    private String normalizeLocale(String locale) {
        return StringUtils.hasText(locale) ? locale.trim() : "zh-CN";
    }

    /**
     * 规整查询时区，空值使用平台时区。
     */
    private String normalizeTimezone(String timezone) {
        return StringUtils.hasText(timezone) ? timezone.trim() : "Asia/Shanghai";
    }

    /**
     * 返回可展示的任务失败摘要，不持久化第三方异常正文、内部地址或响应载荷。
     */
    private String executionFailureReason(DataExportTaskDO task) {
        return isChinese(task) ? "导出任务执行失败，请稍后重试或联系系统管理员"
                : "Export task execution failed. Retry later or contact the system administrator.";
    }

    /**
     * 返回租约恢复原因，说明任务将自动重试但不暴露执行节点信息。
     */
    private String leaseExpiredReason(DataExportTaskDO task) {
        return isChinese(task) ? "导出任务执行超时，系统已按重试策略恢复"
                : "Export task execution timed out and was recovered by the retry policy.";
    }

    /**
     * 返回下载审计安全摘要，底层对象存储异常只写入受控事件日志。
     */
    private String downloadFailureReason(DataExportTaskDO task) {
        return isChinese(task) ? "导出文件下载失败，请稍后重试"
                : "Export file download failed. Retry later.";
    }

    /**
     * 判断任务创建时冻结的语言是否为中文。
     */
    private boolean isChinese(DataExportTaskDO task) {
        return task != null && StringUtils.hasText(task.getLocale())
                && Locale.CHINESE.getLanguage().equals(Locale.forLanguageTag(task.getLocale()).getLanguage());
    }

    /**
     * 按数据库字段上限截断非敏感摘要。
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
