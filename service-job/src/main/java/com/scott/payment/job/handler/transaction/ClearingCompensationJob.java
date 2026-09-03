package com.scott.payment.job.handler.transaction;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.clearing.ClearingInternalClient;
import com.scott.payment.job.client.clearing.dto.ClearingCompensationClientDTOs.Request;
import com.scott.payment.job.client.clearing.dto.ClearingCompensationClientDTOs.Response;
import com.scott.payment.job.dto.transaction.ClearingCompensationRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingCompensationJob
 * @date : 2026-09-01 22:30
 * @email : scott_x@163.com
 * @description : 按单季度复合游标分页触发清分补偿；Job 只限制范围和页数，不访问交易库、不覆盖清分终态也不依赖业务开关。
 * @status : update
 */
@Component
public class ClearingCompensationJob implements JobHandler {

    /**
     * 任务编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String JOB_CODE = "CLEARING_COMPENSATION";
    /**
     * {@code HANDLER_CODE}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String HANDLER_CODE = "clearingCompensation";
    /**
     * {@code DEFAULT_LIMIT}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int DEFAULT_LIMIT = 200;
    /**
     * {@code MAX_LIMIT}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int MAX_LIMIT = 1000;
    /**
     * {@code DEFAULT_MAX_PAGES}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int DEFAULT_MAX_PAGES = 20;
    /**
     * {@code MAX_PAGES}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int MAX_PAGES = 100;
    private static final Set<String> MODES = Set.of("DRY_RUN", "SHADOW_WRITE");

    private final ClearingInternalClient client;
    private final Clock clock;

    @Autowired
    public ClearingCompensationJob(ClearingInternalClient client) {
        this(client, Clock.systemUTC());
    }

    ClearingCompensationJob(ClearingInternalClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    /**
     * 声明同步清分补偿处理器，分布式调度锁、超时和执行记录由统一 Job 框架负责。
     *
     * @return 清分补偿处理器元数据
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(HANDLER_CODE, "清分补偿", "transaction",
                "按单季度游标发现漏清分、超时租约和到期失败并由清分服务幂等恢复");
    }

    /**
     * 在单季度半开区间内按复合游标循环扫描，并以最大页数限制单次任务占用时间。
     *
     * @param context Job 参数和执行上下文
     * @return 扫描、写入、跳过、截断状态及续跑游标
     * @throws ServiceException 参数越界或清分服务返回不完整游标时抛出
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        ClearingCompensationRequest input = context == null ? null
                : context.parseParams(ClearingCompensationRequest.class);
        if (input == null) {
            input = new ClearingCompensationRequest();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime quarterStart = quarterStart(now);
        LocalDateTime begin = input.getBeginTime() == null
                ? later(quarterStart, now.minusMinutes(15)) : input.getBeginTime();
        LocalDateTime end = input.getEndTime() == null ? now : input.getEndTime();
        validateRange(begin, end);
        String mode = normalizeMode(input.getMode());
        int limit = normalize(input.getLimit(), DEFAULT_LIMIT, MAX_LIMIT, "limit");
        int maxPages = normalize(input.getMaxPages(), DEFAULT_MAX_PAGES, MAX_PAGES, "maxPages");

        LocalDateTime cursorTime = null;
        Long cursorId = null;
        int pages = 0;
        int scanned = 0;
        int writes = 0;
        int skipped = 0;
        boolean hasMore;
        do {
            Request request = new Request();
            request.setMode(mode);
            request.setBeginTime(begin);
            request.setEndTime(end);
            request.setCursorTransactionDateTime(cursorTime);
            request.setCursorId(cursorId);
            request.setLimit(limit);
            Response response = client.scan(request);
            pages++;
            scanned += response.getScannedCount();
            writes += response.getWriteCount();
            skipped += response.getSkippedCount();
            hasMore = response.isHasMore();
            cursorTime = response.getNextCursorTransactionDateTime();
            cursorId = response.getNextCursorId();
            if (hasMore && (cursorTime == null || cursorId == null)) {
                throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                        "service-clearing returned an incomplete compensation cursor");
            }
        } while (hasMore && pages < maxPages);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("pages", pages);
        result.put("scanned", scanned);
        result.put("writes", writes);
        result.put("skipped", skipped);
        result.put("truncated", hasMore);
        result.put("nextCursorTransactionDateTime", hasMore ? cursorTime : null);
        result.put("nextCursorId", hasMore ? cursorId : null);
        return JobExecuteResult.success("clearing compensation finished, scanned=" + scanned
                + ", writes=" + writes + ", truncated=" + hasMore, result);
    }

    /** 将空模式收敛为 DRY_RUN，并拒绝任何未显式支持的写入模式。 */
    private String normalizeMode(String value) {
        String normalized = StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "DRY_RUN";
        if (!MODES.contains(normalized)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "clearing compensation mode must be DRY_RUN or SHADOW_WRITE");
        }
        return normalized;
    }

    private int normalize(Integer value, int defaultValue, int max, String field) {
        int normalized = value == null ? defaultValue : value;
        if (normalized < 1 || normalized > max) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    field + " must be between 1 and " + max);
        }
        return normalized;
    }

    /** 校验左闭右开时间范围有效且不跨物理季度，避免跨分表扫描。 */
    private void validateRange(LocalDateTime begin, LocalDateTime end) {
        if (begin == null || end == null || !begin.isBefore(end)
                || !quarterStart(begin).equals(quarterStart(end.minusNanos(1)))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "clearing compensation range must be a valid half-open range in one quarter");
        }
    }

    private LocalDateTime quarterStart(LocalDateTime value) {
        int month = ((value.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDateTime.of(value.getYear(), Month.of(month), 1, 0, 0);
    }

    private LocalDateTime later(LocalDateTime left, LocalDateTime right) {
        return left.isAfter(right) ? left : right;
    }
}
