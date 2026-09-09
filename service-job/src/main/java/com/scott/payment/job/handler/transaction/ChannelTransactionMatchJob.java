package com.scott.payment.job.handler.transaction;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.payment.PaymentInternalClient;
import com.scott.payment.job.client.payment.dto.PaymentChannelMatchClientRequestDTO;
import com.scott.payment.job.client.payment.dto.PaymentChannelMatchClientResultDTO;
import com.scott.payment.job.dto.transaction.ChannelTransactionMatchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelTransactionMatchJob
 * @date : 2026-07-19 22:40
 * @email : scott_x@163.com
 * @description : 渠道交易查询勾兑任务，位于 service-job 任务处理层，按 transaction_date_time 定位分表并触发 service-payment 查询渠道终态；任务本身不决定资金状态。
 * @status : create
 */
@Component
@Slf4j
public class ChannelTransactionMatchJob implements JobHandler {

    /**
     * 任务编码，和 sys_job_task.job_code 保持一致。
     */
    public static final String JOB_CODE = "CHANNEL_TRANSACTION_MATCH";

    /**
     * 处理器编码。
     */
    public static final String HANDLER_CODE = "channelTransactionMatch";

    /**
     * {@code DEFAULT_LIMIT}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int DEFAULT_LIMIT = 100;

    /**
     * {@code MAX_LIMIT}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int MAX_LIMIT = 500;

    /**
     * 默认扫描当前季度和上一季度，覆盖跨季度仍待渠道确认的交易。
     */
    private static final int DEFAULT_LOOKBACK_QUARTERS = 2;

    /**
     * {@code paymentInternalClient} 依赖，用于 渠道交易匹配任务 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * </p>
     */
    private final PaymentInternalClient paymentInternalClient;

    /**
     * 已发布的交易分片拓扑；自动扫描只能使用其中已建且校验通过的季度节点。
     */
    private final TransactionShardingProperties shardingProperties;

    /**
     * 创建渠道交易查询勾兑任务处理器。
     *
     * @param paymentInternalClient service-payment 内部客户端
     * @param shardingProperties 版本化交易分片拓扑
     */
    public ChannelTransactionMatchJob(PaymentInternalClient paymentInternalClient,
                                      TransactionShardingProperties shardingProperties) {
        this.paymentInternalClient = paymentInternalClient;
        this.shardingProperties = shardingProperties;
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
                "渠道交易查询勾兑",
                "transaction",
                "扫描待渠道确认交易并调用支付核心执行查询勾兑"
        );
    }

    /**
     * 执行渠道交易查询勾兑。
     * <p>
     * 该任务只负责把分表时间和渠道过滤条件传给 payment 服务；例如 WPGXML/WPGJSON 能否真实查询，
     * 取决于各自渠道客户端是否已经实现 Inquiry 请求，不能仅凭任务存在判断渠道已生产接通。
     *
     * @param context 任务执行上下文
     * @return 执行结果，包含每个分表时间点的处理数量
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        ChannelTransactionMatchRequest request = context == null ? null : context.parseParams(ChannelTransactionMatchRequest.class);
        if (request == null) {
            request = new ChannelTransactionMatchRequest();
        }
        int limit = normalizeLimit(request.getLimit());
        List<LocalDateTime> transactionDateTimes = resolveTransactionDateTimes(request, context);
        long startNanos = System.nanoTime();
        log.info("event: JOB_HANDLER_SCAN_START traceId: {} jobId: {} handler: {} runId: {} shardIndex: {} shardTotal: {} paramsSummary: {} scanRanges: {} channelCode: {} limit: {}",
                context == null ? TraceContext.getTraceId() : context.getTraceId(),
                context == null ? null : context.getJobId(),
                HANDLER_CODE,
                context == null ? null : context.getRunId(),
                context == null ? null : context.getShardIndex(),
                context == null ? null : context.getShardTotal(),
                context == null ? null : context.getParamsJson(),
                transactionDateTimes,
                request.getChannelCode(),
                limit);
        Map<String, PaymentChannelMatchClientResultDTO> result = new LinkedHashMap<>();
        int matchedCount = 0;
        int scannedCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        for (LocalDateTime transactionDateTime : transactionDateTimes) {
            PaymentChannelMatchClientRequestDTO clientRequestDTO = new PaymentChannelMatchClientRequestDTO();
            clientRequestDTO.setTransactionDateTime(transactionDateTime);
            clientRequestDTO.setChannelCode(request.getChannelCode());
            clientRequestDTO.setLimit(limit);
            PaymentChannelMatchClientResultDTO matchResult;
            try {
                matchResult = paymentInternalClient.matchDueChannelTransactions(clientRequestDTO);
            } catch (RuntimeException exception) {
                failedCount++;
                log.warn("event: JOB_HANDLER_SCAN_ITEM_FAILED traceId: {} jobId: {} handler: {} runId: {} scanRange: {} channelCode: {} failureReason: {}",
                        context == null ? TraceContext.getTraceId() : context.getTraceId(),
                        context == null ? null : context.getJobId(),
                        HANDLER_CODE,
                        context == null ? null : context.getRunId(),
                        transactionDateTime,
                        request.getChannelCode(),
                        exception.getClass().getSimpleName());
                throw exception;
            }
            if (matchResult != null) {
                scannedCount += matchResult.getScannedCount();
                matchedCount += matchResult.getMatchedCount();
                failedCount += matchResult.getFailedCount();
                skippedCount += matchResult.getPendingCount();
            }
            result.put(transactionDateTime.toString(), matchResult);
        }
        log.info("event: JOB_HANDLER_SCAN_END traceId: {} jobId: {} handler: {} runId: {} shardIndex: {} shardTotal: {} scanRanges: {} channelCode: {} scannedCount: {} successCount: {} failureCount: {} skipCount: {} failureReasons: {} durationMs: {}",
                context == null ? TraceContext.getTraceId() : context.getTraceId(),
                context == null ? null : context.getJobId(),
                HANDLER_CODE,
                context == null ? null : context.getRunId(),
                context == null ? null : context.getShardIndex(),
                context == null ? null : context.getShardTotal(),
                transactionDateTimes,
                request.getChannelCode(),
                scannedCount,
                matchedCount,
                failedCount,
                skippedCount,
                failedCount == 0 ? Map.of() : Map.of("CHANNEL_MATCH_FAILED", failedCount),
                elapsedMillis(startNanos));
        return JobExecuteResult.success("channel transaction match finished, matchedCount=" + matchedCount, result);
    }

    /**
     * 计算本次勾兑任务已运行时间。
     *
     * @param startNanos 任务开始时的单调时钟值
     * @return 已运行毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 解析本次需要扫描的交易季度锚点。
     * <p>
     * 显式时间列表优先，其次使用单个交易时间；均未提供时以实际触发时间为基准向前生成
     * 受上限保护的季度范围，避免无界扫描历史分表。
     * </p>
     *
     * @param request 任务请求
     * @param context 调度执行上下文
     * @return 去执行勾兑的交易时间列表
     */
    private List<LocalDateTime> resolveTransactionDateTimes(ChannelTransactionMatchRequest request,
                                                            JobExecuteContext context) {
        if (request.getTransactionDateTimes() != null && !request.getTransactionDateTimes().isEmpty()) {
            return request.getTransactionDateTimes();
        }
        if (request.getTransactionDateTime() != null) {
            return List.of(request.getTransactionDateTime());
        }
        LocalDateTime referenceTime = context != null && context.getActualTriggerTime() != null
                ? context.getActualTriggerTime()
                : LocalDateTime.now();
        int lookbackQuarters = normalizeLookbackQuarters(request.getLookbackQuarters());
        LocalDateTime currentQuarter = quarterAnchor(referenceTime);
        return shardingProperties.getPhysicalNodes().stream()
                .map(this::quarterAnchorFromPublishedNode)
                .filter(transactionDateTime -> !transactionDateTime.isAfter(currentQuarter))
                .sorted(Comparator.reverseOrder())
                .limit(lookbackQuarters)
                .toList();
    }

    /**
     * 将版本化规则中的 yyyyQQ 节点后缀转换为季度锚点。
     *
     * @param suffix 已建且校验通过的物理节点后缀
     * @return 对应季度第一天零点
     */
    private LocalDateTime quarterAnchorFromPublishedNode(String suffix) {
        if (suffix == null || !suffix.matches("\\d{6}")) {
            throw new IllegalStateException("transaction sharding physical node suffix must use yyyyQQ");
        }
        int year = Integer.parseInt(suffix.substring(0, 4));
        int quarter = Integer.parseInt(suffix.substring(4, 6));
        if (quarter < 1 || quarter > 4) {
            throw new IllegalStateException("transaction sharding physical node quarter must be between 01 and 04");
        }
        return LocalDateTime.of(year, (quarter - 1) * 3 + 1, 1, 0, 0);
    }

    /**
     * 将任意时间归一为所在自然季度第一天零点。
     *
     * @param value 原始交易或触发时间
     * @return 同年同季度的分表路由锚点
     */
    private LocalDateTime quarterAnchor(LocalDateTime value) {
        int firstMonth = ((value.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDateTime.of(value.getYear(), firstMonth, 1, 0, 0);
    }

    /**
     * 校验并限制向前扫描季度数。
     *
     * @param lookbackQuarters 请求指定季度数
     * @return 默认值或调用方指定的正季度数；实际扫描仍受已发布节点集合约束
     * @throws ServiceException 输入非正数时抛出
     */
    private int normalizeLookbackQuarters(Integer lookbackQuarters) {
        if (lookbackQuarters == null) {
            return DEFAULT_LOOKBACK_QUARTERS;
        }
        if (lookbackQuarters <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "lookbackQuarters must be greater than zero");
        }
        return lookbackQuarters;
    }

    /**
     * 校验并限制单个季度勾兑批量。
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
