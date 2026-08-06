package com.scott.payment.job.handler.transaction;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.data.DataInternalClient;
import com.scott.payment.job.client.data.dto.DataMerchantNotificationNotifyClientRequestDTO;
import com.scott.payment.job.client.data.dto.DataMerchantNotificationNotifyDueClientRequestDTO;
import com.scott.payment.job.dto.transaction.MerchantNotificationRetryRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationRetryJob
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : 商户通知补偿任务，位于 service-job 任务处理层，按 transaction_date_time 定位分表并触发 service-data 重试到期商户通知。
 * @status : create
 */
@Component
@Slf4j
public class MerchantNotificationRetryJob implements JobHandler {

    /**
     * 任务编码，和 sys_job_task.job_code 保持一致。
     */
    public static final String JOB_CODE = "MERCHANT_NOTIFICATION_RETRY";

    /**
     * 处理器编码。
     */
    public static final String HANDLER_CODE = "merchantNotificationRetry";

    /** 单次季度扫描默认最多处理 5 条通知，限制补偿任务的外发影响范围。 */
    private static final int DEFAULT_LIMIT = 5;

    /** 单次季度扫描硬上限，避免误配置对积压通知进行大批量外发。 */
    private static final int MAX_LIMIT = 5;

    /**
     * service-data 内部客户端，仅用于触发到期商户通知的扫描与补偿投递。
     */
    private final DataInternalClient dataInternalClient;

    /**
     * 创建商户通知补偿任务处理器。
     *
     * @param dataInternalClient service-data 内部补偿客户端
     */
    public MerchantNotificationRetryJob(DataInternalClient dataInternalClient) {
        this.dataInternalClient = dataInternalClient;
    }

    /**
     * 返回处理器注册描述。
     *
     * @return 处理器描述
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(
                HANDLER_CODE,
                "商户通知补偿重试",
                "transaction",
                "扫描到期商户通知任务并调用异步数据服务执行补偿重试"
        );
    }

    /**
     * 执行商户通知补偿。
     *
     * @param context 任务执行上下文
     * @return 执行结果，包含每个分表时间点的成功通知数量
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        MerchantNotificationRetryRequest request = context == null ? null : context.parseParams(MerchantNotificationRetryRequest.class);
        if (request == null) {
            request = new MerchantNotificationRetryRequest();
        }
        int limit = normalizeLimit(request.getLimit());
        List<LocalDateTime> transactionDateTimes = resolveTransactionDateTimes(request);
        long startNanos = System.nanoTime();
        log.info("event: JOB_HANDLER_SCAN_START traceId: {} jobId: {} handler: {} runId: {} shardIndex: {} shardTotal: {} paramsSummary: {} scanRanges: {} limit: {}",
                context == null ? TraceContext.getTraceId() : context.getTraceId(),
                context == null ? null : context.getJobId(),
                HANDLER_CODE,
                context == null ? null : context.getRunId(),
                context == null ? null : context.getShardIndex(),
                context == null ? null : context.getShardTotal(),
                context == null ? null : context.getParamsJson(),
                transactionDateTimes,
                limit);
        Map<String, Integer> result = new LinkedHashMap<>();
        int totalSuccessCount = 0;
        int failCount = 0;
        for (LocalDateTime transactionDateTime : transactionDateTimes) {
            Integer successCount;
            try {
                successCount = notify(transactionDateTime, request, limit);
            } catch (RuntimeException exception) {
                failCount++;
                log.warn("event: JOB_HANDLER_SCAN_ITEM_FAILED traceId: {} jobId: {} handler: {} runId: {} scanRange: {} failureReason: {}",
                        context == null ? TraceContext.getTraceId() : context.getTraceId(),
                        context == null ? null : context.getJobId(),
                        HANDLER_CODE,
                        context == null ? null : context.getRunId(),
                        transactionDateTime,
                        exception.getClass().getSimpleName());
                throw exception;
            }
            int safeSuccessCount = successCount == null ? 0 : successCount;
            totalSuccessCount += safeSuccessCount;
            result.put(transactionDateTime.toString(), safeSuccessCount);
        }
        log.info("event: JOB_HANDLER_SCAN_END traceId: {} jobId: {} handler: {} runId: {} shardIndex: {} shardTotal: {} scanRanges: {} scannedCount: {} successCount: {} failureCount: {} skipCount: {} failureReasons: {} durationMs: {}",
                context == null ? TraceContext.getTraceId() : context.getTraceId(),
                context == null ? null : context.getJobId(),
                HANDLER_CODE,
                context == null ? null : context.getRunId(),
                context == null ? null : context.getShardIndex(),
                context == null ? null : context.getShardTotal(),
                transactionDateTimes,
                transactionDateTimes.size(),
                totalSuccessCount,
                failCount,
                0,
                failCount == 0 ? Map.of() : Map.of("DATA_INTERNAL_CALL_FAILED", failCount),
                elapsedMillis(startNanos));
        return JobExecuteResult.success("merchant notification retry finished, successCount=" + totalSuccessCount, result);
    }

    /** 按请求类型执行单笔精确补偿或季度有界扫描。 */
    private Integer notify(LocalDateTime transactionDateTime,
                           MerchantNotificationRetryRequest request,
                           int limit) {
        if (StringUtils.hasText(request.getTransactionId())) {
            DataMerchantNotificationNotifyClientRequestDTO clientRequestDTO =
                    new DataMerchantNotificationNotifyClientRequestDTO();
            clientRequestDTO.setTransactionId(request.getTransactionId().trim());
            clientRequestDTO.setTransactionDateTime(transactionDateTime);
            return Boolean.TRUE.equals(dataInternalClient.notifyMerchantNotification(clientRequestDTO)) ? 1 : 0;
        }
        DataMerchantNotificationNotifyDueClientRequestDTO clientRequestDTO =
                new DataMerchantNotificationNotifyDueClientRequestDTO();
        clientRequestDTO.setTransactionDateTime(transactionDateTime);
        clientRequestDTO.setLimit(limit);
        return dataInternalClient.notifyDueMerchantNotifications(clientRequestDTO);
    }

    /**
     * 计算商户通知重试任务已运行时间。
     *
     * @param startNanos 任务开始时的单调时钟值
     * @return 已运行毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 解析需要扫描的交易分表时间。
     * <p>
     * 显式时间列表优先，其次使用单个时间；均未提供时只扫描当前时间对应分表，避免默认
     * 跨季度扩大通知重试范围。
     * </p>
     *
     * @param request 任务请求
     * @return 交易分表路由时间列表
     */
    private List<LocalDateTime> resolveTransactionDateTimes(MerchantNotificationRetryRequest request) {
        if (StringUtils.hasText(request.getTransactionId())) {
            if (request.getTransactionDateTime() == null) {
                throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(),
                        "transactionDateTime is required when transactionId is provided");
            }
            if (request.getTransactionDateTimes() != null && !request.getTransactionDateTimes().isEmpty()) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                        "transactionDateTimes is not allowed for a single transaction retry");
            }
            return List.of(request.getTransactionDateTime());
        }
        if (request.getTransactionDateTimes() != null && !request.getTransactionDateTimes().isEmpty()) {
            return request.getTransactionDateTimes();
        }
        if (request.getTransactionDateTime() != null) {
            return List.of(request.getTransactionDateTime());
        }
        return List.of(LocalDateTime.now());
    }

    /**
     * 校验并限制每个分表的通知重试批量。
     *
     * @param limit 请求批量
     * @return 默认值或不超过系统上限的批量
     * @throws ServiceException 输入非正数时抛出
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "limit must be greater than zero");
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
