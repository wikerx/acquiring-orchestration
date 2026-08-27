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

/** 按单季度游标调用清分服务补偿扫描，Job 自身不访问交易库。 */
@Component
public class ClearingCompensationJob implements JobHandler {

    public static final String JOB_CODE = "CLEARING_COMPENSATION";
    public static final String HANDLER_CODE = "clearingCompensation";
    private static final int DEFAULT_LIMIT = 200;
    private static final int MAX_LIMIT = 1000;
    private static final int DEFAULT_MAX_PAGES = 20;
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

    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(HANDLER_CODE, "清分补偿", "transaction",
                "按单季度游标发现漏清分、超时租约和到期失败并由清分服务幂等恢复");
    }

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
