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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * 异步导出任务领域服务，负责提交幂等、租约领取、状态迁移、文件清理和下载审计。
 */
@Slf4j
@Service
public class DataExportTaskService {
    public static final String APP_ADMIN = "ADMIN";
    public static final String APP_MERCHANT = "MERCHANT";
    public static final String ADMIN_OPERATIONAL_WORK_ITEM = "ADMIN_OPERATIONAL_WORK_ITEM";
    public static final String ADMIN_TRANSACTION_ORDER = "ADMIN_TRANSACTION_ORDER";
    public static final String ADMIN_TRANSACTION_OPERATION = "ADMIN_TRANSACTION_OPERATION";
    public static final String ADMIN_MERCHANT_NOTIFICATION = "ADMIN_MERCHANT_NOTIFICATION";
    public static final String ADMIN_REFUND = "ADMIN_REFUND";
    public static final String ADMIN_SETTLEMENT_TRANSACTION = "ADMIN_SETTLEMENT_TRANSACTION";
    public static final String ADMIN_SETTLEMENT_RESERVE = "ADMIN_SETTLEMENT_RESERVE";
    public static final String ADMIN_CARD_BIN = "ADMIN_CARD_BIN";
    public static final String ADMIN_COUNTRY = "ADMIN_COUNTRY";
    public static final String ADMIN_CURRENCY = "ADMIN_CURRENCY";
    public static final String ADMIN_REGION_CURRENCY = "ADMIN_REGION_CURRENCY";
    public static final String ADMIN_MCC_CODE = "ADMIN_MCC_CODE";
    public static final String ADMIN_DEPARTMENT = "ADMIN_DEPARTMENT";
    public static final String ADMIN_POST = "ADMIN_POST";
    public static final String ADMIN_DICT_TYPE = "ADMIN_DICT_TYPE";
    public static final String ADMIN_DICT_DATA = "ADMIN_DICT_DATA";
    public static final String ADMIN_HOLIDAY_CALENDAR = "ADMIN_HOLIDAY_CALENDAR";
    public static final String ADMIN_JOB_RUN_LOG = "ADMIN_JOB_RUN_LOG";
    public static final String ADMIN_CHANNEL_MATCH_ABNORMAL = "ADMIN_CHANNEL_MATCH_ABNORMAL";
    public static final String ADMIN_SETTLEMENT_REVIEW = "ADMIN_SETTLEMENT_REVIEW";
    public static final String ADMIN_SETTLEMENT_POSTING = "ADMIN_SETTLEMENT_POSTING";
    public static final String ADMIN_SECURITY_INTERCEPT_EVENT = "ADMIN_SECURITY_INTERCEPT_EVENT";
    public static final String ADMIN_FUND_ACCOUNT = "ADMIN_FUND_ACCOUNT";
    public static final String ADMIN_FUND_LEDGER = "ADMIN_FUND_LEDGER";
    public static final String ADMIN_FUND_RECHARGE = "ADMIN_FUND_RECHARGE";
    public static final String ADMIN_FUND_DEDUCTION = "ADMIN_FUND_DEDUCTION";
    public static final String ADMIN_EXCHANGE_RAW_RATE = "ADMIN_EXCHANGE_RAW_RATE";
    public static final String ADMIN_EXCHANGE_BUSINESS_RATE = "ADMIN_EXCHANGE_BUSINESS_RATE";
    public static final String ADMIN_EXCHANGE_USAGE_SNAPSHOT = "ADMIN_EXCHANGE_USAGE_SNAPSHOT";
    public static final String ADMIN_EXCHANGE_SOURCE = "ADMIN_EXCHANGE_SOURCE";
    public static final String ADMIN_EXCHANGE_RULE = "ADMIN_EXCHANGE_RULE";
    public static final String ADMIN_EXCHANGE_VOLATILITY = "ADMIN_EXCHANGE_VOLATILITY";
    public static final String ADMIN_EXCHANGE_ALERT_RULE = "ADMIN_EXCHANGE_ALERT_RULE";
    public static final String ADMIN_EXCHANGE_ALERT_EVENT = "ADMIN_EXCHANGE_ALERT_EVENT";
    public static final String ADMIN_FEE_TEMPLATE = "ADMIN_FEE_TEMPLATE";
    public static final String ADMIN_MERCHANT_FEE = "ADMIN_MERCHANT_FEE";
    public static final String ADMIN_FEE_REVIEW = "ADMIN_FEE_REVIEW";
    public static final String ADMIN_FEE_SIMULATION = "ADMIN_FEE_SIMULATION";
    public static final String ADMIN_SYSTEM_USER = "ADMIN_SYSTEM_USER";
    public static final String ADMIN_SYSTEM_CONFIG = "ADMIN_SYSTEM_CONFIG";
    public static final String ADMIN_MERCHANT_IP_WHITELIST = "ADMIN_MERCHANT_IP_WHITELIST";
    public static final String ADMIN_RISK_CONFIG = "ADMIN_RISK_CONFIG";
    public static final String ADMIN_TRADE_BLACK_RECORD = "ADMIN_TRADE_BLACK_RECORD";
    public static final String ADMIN_DATASOURCE_SNAPSHOT = "ADMIN_DATASOURCE_SNAPSHOT";
    public static final String MERCHANT_TRANSACTION_OPERATION = "MERCHANT_TRANSACTION_OPERATION";
    public static final String MERCHANT_REFUND = "MERCHANT_REFUND";
    public static final String MERCHANT_SETTLEMENT_BATCH = "MERCHANT_SETTLEMENT_BATCH";
    public static final String MERCHANT_SETTLEMENT_SUMMARY = "MERCHANT_SETTLEMENT_SUMMARY";
    public static final String MERCHANT_SETTLEMENT_TRANSACTION = "MERCHANT_SETTLEMENT_TRANSACTION";
    public static final String MERCHANT_SETTLEMENT_RESERVE = "MERCHANT_SETTLEMENT_RESERVE";
    public static final String MERCHANT_FUND_LEDGER = "MERCHANT_FUND_LEDGER";
    private static final Set<String> SUPPORTED_TASK_TYPES = Set.of(
            ADMIN_OPERATIONAL_WORK_ITEM, ADMIN_TRANSACTION_ORDER, ADMIN_TRANSACTION_OPERATION,
            ADMIN_MERCHANT_NOTIFICATION, ADMIN_REFUND,
            ADMIN_SETTLEMENT_TRANSACTION, ADMIN_SETTLEMENT_RESERVE,
            ADMIN_CARD_BIN, ADMIN_COUNTRY, ADMIN_CURRENCY, ADMIN_REGION_CURRENCY, ADMIN_MCC_CODE,
            ADMIN_DEPARTMENT, ADMIN_POST, ADMIN_DICT_TYPE, ADMIN_DICT_DATA, ADMIN_HOLIDAY_CALENDAR,
            ADMIN_JOB_RUN_LOG, ADMIN_CHANNEL_MATCH_ABNORMAL,
            ADMIN_SETTLEMENT_REVIEW, ADMIN_SETTLEMENT_POSTING, ADMIN_SECURITY_INTERCEPT_EVENT,
            ADMIN_FUND_ACCOUNT, ADMIN_FUND_LEDGER, ADMIN_FUND_RECHARGE, ADMIN_FUND_DEDUCTION,
            ADMIN_EXCHANGE_RAW_RATE, ADMIN_EXCHANGE_BUSINESS_RATE, ADMIN_EXCHANGE_USAGE_SNAPSHOT,
            ADMIN_EXCHANGE_SOURCE, ADMIN_EXCHANGE_RULE, ADMIN_EXCHANGE_VOLATILITY,
            ADMIN_EXCHANGE_ALERT_RULE, ADMIN_EXCHANGE_ALERT_EVENT,
            ADMIN_FEE_TEMPLATE, ADMIN_MERCHANT_FEE, ADMIN_FEE_REVIEW, ADMIN_FEE_SIMULATION,
            ADMIN_SYSTEM_USER, ADMIN_SYSTEM_CONFIG, ADMIN_MERCHANT_IP_WHITELIST,
            ADMIN_RISK_CONFIG, ADMIN_TRADE_BLACK_RECORD, ADMIN_DATASOURCE_SNAPSHOT,
            MERCHANT_TRANSACTION_OPERATION, MERCHANT_REFUND,
            MERCHANT_SETTLEMENT_BATCH, MERCHANT_SETTLEMENT_SUMMARY,
            MERCHANT_SETTLEMENT_TRANSACTION, MERCHANT_SETTLEMENT_RESERVE,
            MERCHANT_FUND_LEDGER);
    private static final Set<String> OWNER_APP_CODES = Set.of(APP_ADMIN, APP_MERCHANT);
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
    private final Executor taskExecutor;
    private final Semaphore executionSlots;

    /**
     * 创建异步导出任务服务。
     */
    public DataExportTaskService(DataExportTaskMapper taskMapper,
                                 DataExportDownloadAuditMapper auditMapper,
                                 GlobalIdGenerator globalIdGenerator,
                                 DataExportProperties properties,
                                 ObjectProvider<ExportObjectStorageService> storageProvider,
                                 ExportTaskProcessorRegistry processorRegistry,
                                 @Qualifier("dataExportTaskExecutor") Executor taskExecutor) {
        this.taskMapper = taskMapper;
        this.auditMapper = auditMapper;
        this.globalIdGenerator = globalIdGenerator;
        this.properties = properties;
        this.storageProvider = storageProvider;
        this.processorRegistry = processorRegistry;
        this.taskExecutor = taskExecutor;
        this.executionSlots = new Semaphore(properties.getDispatchConcurrency());
    }

    /**
     * 幂等创建受支持的管理端或商户端异步导出任务。
     */
    @DS(DataSourceName.MASTER)
    public TaskSummary submit(CreateRequest request) {
        requireCreateRequest(request);
        properties.validate();
        String creatorAppCode = normalizeAppCode(request.getCreatorAppCode());
        String merchantId = normalizeMerchantId(creatorAppCode, request.getMerchantId());
        DataExportTaskDO existing = taskMapper.selectByRequestId(
                creatorAppCode, request.getCreatorAccountId(), merchantId, request.getRequestId().trim());
        if (existing != null) {
            return toSummary(existing);
        }
        LocalDateTime now = LocalDateTime.now();
        DataExportTaskDO task = new DataExportTaskDO();
        task.setTaskNo("EXP" + globalIdGenerator.nextId());
        task.setRequestId(request.getRequestId().trim());
        task.setTaskType(request.getTaskType().trim());
        task.setTaskName(truncate(request.getTaskName(), 128));
        task.setSourceService(APP_MERCHANT.equals(creatorAppCode) ? "service-merchant" : "service-admin");
        task.setCreatorAppCode(creatorAppCode);
        task.setCreatorAccountId(request.getCreatorAccountId());
        task.setMerchantId(merchantId);
        task.setCreatorAccountName(truncate(request.getCreatorAccountName(), 128));
        task.setLocale(normalizeLocale(request.getLocale()));
        task.setTimezone(normalizeTimezone(request.getTimezone()));
        task.setQuerySnapshot(JsonUtils.toJsonString(querySnapshot(request)));
        task.setDataScopeSnapshot(JsonUtils.toJsonString(scopeSnapshot(request, creatorAppCode, merchantId)));
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
                    creatorAppCode, request.getCreatorAccountId(), merchantId, request.getRequestId().trim());
            if (duplicate == null) {
                throw exception;
            }
            return toSummary(duplicate);
        }
        log.info("异步导出任务已创建, event: DATA_EXPORT_TASK_CREATED, taskNo: {}, taskType: {}, creatorAppCode: {}, creatorAccountId: {}, merchantId: {}",
                task.getTaskNo(), task.getTaskType(), task.getCreatorAppCode(),
                task.getCreatorAccountId(), task.getMerchantId());
        return toSummary(taskMapper.selectOwned(task.getTaskNo(), task.getCreatorAppCode(),
                task.getCreatorAccountId(), task.getMerchantId()));
    }

    /**
     * 分页查询当前创建人的任务，不允许跨账号查看条件或文件。
     */
    @DS(DataSourceName.MASTER)
    public PageResult<TaskSummary> search(SearchRequest request) {
        if (request == null || request.getCreatorAccountId() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        String creatorAppCode = normalizeAppCode(request.getCreatorAppCode());
        String merchantId = normalizeMerchantId(creatorAppCode, request.getMerchantId());
        String status = normalizeStatus(request.getStatus());
        int pageNo = request.getPageNo() == null ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null ? 20 : request.getPageSize();
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        String taskType = StringUtils.hasText(request.getTaskType()) ? request.getTaskType().trim() : null;
        long total = taskMapper.countOwned(creatorAppCode, request.getCreatorAccountId(), merchantId, taskType, status,
                request.getCreatedStartTime(), request.getCreatedEndTime());
        long offset = (long) (pageNo - 1) * pageSize;
        List<TaskSummary> records = offset >= total ? List.of() : taskMapper.selectOwnedPage(
                        creatorAppCode, request.getCreatorAccountId(), merchantId, taskType, status,
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
                || taskMapper.retry(task.getTaskNo(), task.getCreatorAppCode(), task.getCreatorAccountId(),
                task.getMerchantId(),
                request.getExpectedVersion(), LocalDateTime.now()) != 1) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "export task cannot be retried");
        }
        log.info("异步导出任务已人工重试, event: DATA_EXPORT_TASK_RETRIED, taskNo: {}, creatorAppCode: {}, creatorAccountId: {}, merchantId: {}",
                task.getTaskNo(), task.getCreatorAppCode(), task.getCreatorAccountId(), task.getMerchantId());
        return toSummary(taskMapper.selectOwned(task.getTaskNo(), task.getCreatorAppCode(),
                task.getCreatorAccountId(), task.getMerchantId()));
    }

    /**
     * 由 Job 触发一轮有界领取、异步提交和过期清理，不等待文件生成后才释放内部请求。
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
        int recovered = recoverStaleProcessing(request.getDispatchTime(), batchSize);
        ExportObjectStorageService storage = requireStorage();
        int claimed = 0;
        int submitted = 0;
        int deferred = 0;
        int claimLimit = Math.min(batchSize, executionSlots.availablePermits());
        for (DataExportTaskDO candidate : taskMapper.selectDispatchCandidates(request.getDispatchTime(), claimLimit)) {
            if (!executionSlots.tryAcquire()) {
                break;
            }
            String leaseOwner = request.getRequestId().trim() + ":" + candidate.getTaskNo();
            LocalDateTime leaseUntil = request.getDispatchTime().plus(properties.getProcessingLease());
            if (taskMapper.claim(candidate.getId(), candidate.getVersion(), leaseOwner,
                    leaseUntil, request.getDispatchTime()) != 1) {
                executionSlots.release();
                continue;
            }
            claimed++;
            DataExportTaskDO task = taskMapper.selectById(candidate.getId());
            long processingVersion = task == null ? candidate.getVersion() + 1 : task.getVersion();
            ClaimedTask claimedTask = new ClaimedTask(task, leaseOwner, processingVersion);
            if (task == null || !submitClaimedTask(claimedTask, storage)) {
                deferClaimedTask(candidate.getId(), processingVersion, leaseOwner, LocalDateTime.now());
                executionSlots.release();
                deferred++;
                continue;
            }
            submitted++;
        }
        int expired = cleanupExpired(request.getDispatchTime(), Math.max(batchSize * 2, 10), storage);
        DispatchResult result = new DispatchResult();
        result.setRequestId(request.getRequestId().trim());
        result.setClaimedCount(claimed);
        result.setSubmittedCount(submitted);
        result.setDeferredCount(deferred);
        result.setRecoveredCount(recovered);
        result.setExpiredCount(expired);
        return result;
    }

    /**
     * 将已领取任务提交至专用线程池；提交成功后由执行线程归还并发槽位。
     */
    private boolean submitClaimedTask(ClaimedTask claimedTask, ExportObjectStorageService storage) {
        try {
            taskExecutor.execute(() -> {
                try {
                    executeClaimedTask(claimedTask, storage);
                } finally {
                    executionSlots.release();
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            log.warn("异步导出执行器繁忙，任务已延后, event: DATA_EXPORT_TASK_EXECUTOR_BUSY, taskNo: {}",
                    claimedTask.task() == null ? null : claimedTask.task().getTaskNo());
            return false;
        } catch (RuntimeException exception) {
            log.error("异步导出任务提交异常，任务已延后, event: DATA_EXPORT_TASK_SUBMIT_FAILED, taskNo: {}, exceptionType: {}",
                    claimedTask.task() == null ? null : claimedTask.task().getTaskNo(),
                    exception.getClass().getSimpleName(), exception);
            return false;
        }
    }

    /**
     * 执行器未接受任务时按原重试次数延后，避免容量背压被误记为业务执行失败。
     */
    private void deferClaimedTask(Long taskId,
                                  long processingVersion,
                                  String leaseOwner,
                                  LocalDateTime now) {
        int updated = taskMapper.deferClaim(taskId, processingVersion, leaseOwner,
                now.plus(properties.getRetryDelay()), now);
        if (updated != 1) {
            log.error("异步导出任务延后失败, event: DATA_EXPORT_TASK_DEFER_CONFLICT, taskId: {}", taskId);
        }
    }

    /**
     * 执行一个已领取任务并独立提交最终状态，失败不会中断同一批次的其他任务。
     *
     * @param claimedTask 已冻结租约所有者和版本的任务
     * @param storage 私有对象存储
     */
    private void executeClaimedTask(ClaimedTask claimedTask, ExportObjectStorageService storage) {
        DataExportTaskDO task = claimedTask.task();
        try {
            ExportArtifact artifact = process(
                    task, storage, claimedTask.leaseOwner(), claimedTask.processingVersion());
            int updated = taskMapper.markSuccess(task.getId(), claimedTask.processingVersion(),
                    claimedTask.leaseOwner(), artifact.processedRows(), artifact.totalRows(),
                    artifact.fileName(), artifact.contentType(), artifact.objectKey(), artifact.fileSizeBytes(),
                    artifact.fileSha256(), artifact.expiresAt(), LocalDateTime.now());
            if (updated != 1) {
                storage.delete(artifact.objectKey());
                throw new IllegalStateException("export task success state conflict");
            }
            log.info("异步导出任务完成, event: DATA_EXPORT_TASK_SUCCEEDED, taskNo: {}, processedRows: {}, fileSizeBytes: {}",
                    task.getTaskNo(), artifact.processedRows(), artifact.fileSizeBytes());
        } catch (RuntimeException exception) {
            recordFailure(task, claimedTask.processingVersion(), claimedTask.leaseOwner(),
                    exception.getClass().getSimpleName(), executionFailureReason(task), LocalDateTime.now());
            log.warn("异步导出任务执行失败, event: DATA_EXPORT_TASK_FAILED, taskNo: {}, exceptionType: {}",
                    task.getTaskNo(), exception.getClass().getSimpleName());
        }
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
        audit.setOperatorAppCode(task.getCreatorAppCode());
        audit.setOperatorAccountId(request.getCreatorAccountId());
        audit.setMerchantId(task.getMerchantId());
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
        String creatorAppCode = normalizeAppCode(request.getCreatorAppCode());
        String merchantId = normalizeMerchantId(creatorAppCode, request.getMerchantId());
        DataExportTaskDO task = taskMapper.selectOwned(
                request.getTaskNo().trim(), creatorAppCode, request.getCreatorAccountId(), merchantId);
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
                || querySnapshot(request) == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        String creatorAppCode = normalizeAppCode(request.getCreatorAppCode());
        normalizeMerchantId(creatorAppCode, request.getMerchantId());
        if (APP_ADMIN.equals(creatorAppCode)
                && !request.isAllMerchants()
                && (request.getMerchantIds() == null || request.getMerchantIds().isEmpty())) {
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
            case ADMIN_CARD_BIN -> request.getCardBinQuery();
            case ADMIN_COUNTRY -> request.getCountryQuery();
            case ADMIN_CURRENCY -> request.getCurrencyQuery();
            case ADMIN_REGION_CURRENCY -> request.getRegionCurrencyQuery();
            case ADMIN_MCC_CODE -> request.getMccCodeQuery();
            case ADMIN_DEPARTMENT -> request.getDepartmentQuery();
            case ADMIN_POST -> request.getPostQuery();
            case ADMIN_DICT_TYPE -> request.getDictTypeQuery();
            case ADMIN_DICT_DATA -> request.getDictDataQuery();
            case ADMIN_HOLIDAY_CALENDAR -> request.getHolidayCalendarQuery();
            case ADMIN_JOB_RUN_LOG -> request.getJobRunLogQuery();
            case ADMIN_CHANNEL_MATCH_ABNORMAL -> request.getChannelMatchAbnormalQuery();
            case ADMIN_SETTLEMENT_REVIEW -> request.getSettlementReviewQuery();
            case ADMIN_SETTLEMENT_POSTING -> request.getSettlementPostingQuery();
            case ADMIN_SECURITY_INTERCEPT_EVENT -> request.getSecurityInterceptEventQuery();
            case ADMIN_FUND_ACCOUNT -> request.getFundAccountQuery();
            case ADMIN_FUND_LEDGER -> request.getFundLedgerQuery();
            case ADMIN_FUND_RECHARGE -> request.getFundRechargeQuery();
            case ADMIN_FUND_DEDUCTION -> request.getFundDeductionQuery();
            case ADMIN_EXCHANGE_RAW_RATE -> request.getExchangeRawRateQuery();
            case ADMIN_EXCHANGE_BUSINESS_RATE -> request.getExchangeBusinessRateQuery();
            case ADMIN_EXCHANGE_USAGE_SNAPSHOT -> request.getExchangeUsageSnapshotQuery();
            case ADMIN_EXCHANGE_SOURCE -> request.getExchangeSourceQuery();
            case ADMIN_EXCHANGE_RULE -> request.getExchangeRuleQuery();
            case ADMIN_EXCHANGE_VOLATILITY -> request.getExchangeVolatilityQuery();
            case ADMIN_EXCHANGE_ALERT_RULE -> request.getExchangeAlertRuleQuery();
            case ADMIN_EXCHANGE_ALERT_EVENT -> request.getExchangeAlertEventQuery();
            case ADMIN_FEE_TEMPLATE -> request.getFeeTemplateQuery();
            case ADMIN_MERCHANT_FEE -> request.getMerchantFeeQuery();
            case ADMIN_FEE_REVIEW -> request.getFeeReviewQuery();
            case ADMIN_FEE_SIMULATION -> request.getFeeSimulationQuery();
            case ADMIN_SYSTEM_USER -> request.getSystemUserQuery();
            case ADMIN_SYSTEM_CONFIG -> request.getSystemConfigQuery();
            case ADMIN_MERCHANT_IP_WHITELIST -> request.getMerchantIpWhitelistQuery();
            case ADMIN_RISK_CONFIG, ADMIN_TRADE_BLACK_RECORD -> request.getRiskConfigQuery();
            case ADMIN_DATASOURCE_SNAPSHOT -> request.getDatasourceSnapshotQuery();
            case MERCHANT_TRANSACTION_OPERATION -> request.getMerchantTransactionQuery();
            case MERCHANT_REFUND -> request.getMerchantRefundQuery();
            case MERCHANT_SETTLEMENT_BATCH -> request.getMerchantSettlementBatchQuery();
            case MERCHANT_SETTLEMENT_SUMMARY -> request.getMerchantSettlementSummaryQuery();
            case MERCHANT_SETTLEMENT_TRANSACTION -> request.getMerchantSettlementTransactionQuery();
            case MERCHANT_SETTLEMENT_RESERVE -> request.getMerchantSettlementReserveQuery();
            case MERCHANT_FUND_LEDGER -> request.getMerchantFundLedgerQuery();
            default -> null;
        };
    }

    /**
     * 根据可信应用身份冻结对应的数据范围；商户任务只能包含一个认证商户号。
     */
    private Object scopeSnapshot(CreateRequest request, String creatorAppCode, String merchantId) {
        if (APP_MERCHANT.equals(creatorAppCode)) {
            MerchantExportScopeSnapshot scope = new MerchantExportScopeSnapshot();
            scope.setMerchantId(merchantId);
            return scope;
        }
        AdminExportScopeSnapshot scope = new AdminExportScopeSnapshot();
        scope.setAllMerchants(request.isAllMerchants());
        scope.setMerchantIds(request.getMerchantIds());
        return scope;
    }

    /**
     * 规整任务创建应用，拒绝未知应用绕过账号命名空间隔离。
     */
    private String normalizeAppCode(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        String normalized = appCode.trim().toUpperCase(Locale.ROOT);
        if (!OWNER_APP_CODES.contains(normalized)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return normalized;
    }

    /**
     * 校验应用与商户归属组合：Admin 不绑定商户，Merchant 必须绑定唯一商户。
     */
    private String normalizeMerchantId(String appCode, String merchantId) {
        if (APP_ADMIN.equals(appCode)) {
            if (StringUtils.hasText(merchantId)) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID);
            }
            return null;
        }
        if (!StringUtils.hasText(merchantId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return merchantId.trim();
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

    /**
     * 已领取任务的不可变执行上下文，确保并发线程使用同一租约版本提交状态。
     *
     * @param task 已切换为处理中的任务
     * @param leaseOwner 本轮唯一租约所有者
     * @param processingVersion 领取成功后的乐观锁版本
     */
    private record ClaimedTask(DataExportTaskDO task, String leaseOwner, long processingVersion) {
    }
}
