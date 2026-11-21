package com.scott.payment.job.handler.operation;

import com.alibaba.fastjson2.TypeReference;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.admin.AdminInternalClient;
import com.scott.payment.job.client.admin.dto.PaymentCapabilityOperationsClientDTOs.BoundaryRequest;
import com.scott.payment.job.client.admin.dto.PaymentCapabilityOperationsClientDTOs.BoundaryResponse;
import com.scott.payment.job.client.admin.dto.PaymentCapabilityOperationsClientDTOs.Cursor;
import com.scott.payment.job.entity.SysJobTaskDO;
import com.scott.payment.job.mapper.SysJobRunLogMapper;
import com.scott.payment.job.mapper.SysJobTaskMapper;
import com.scott.payment.job.support.PaymentCapabilityJobCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 推进支付方式自然边界的连续已确认区间。 */
@Component
@Slf4j
public class PaymentCapabilityBoundaryJob implements JobHandler {

    private static final List<String> SOURCES = List.of(
            "AUTH_EFFECTIVE", "AUTH_EXPIRY", "BINDING_EFFECTIVE", "BINDING_EXPIRY", "MID_EFFECTIVE", "MID_EXPIRY",
            "BINDING_CHANGE", "MID_CHANGE", "CHANNEL_CHANGE", "CAPABILITY_CHANGE", "FEE_CHANGE", "DICTIONARY_CHANGE",
            "DICTIONARY_POLICY_CHANGE");
    private static final int LIMIT = 100;
    private final AdminInternalClient admin;
    private final SysJobRunLogMapper runs;
    private final SysJobTaskMapper tasks;

    public PaymentCapabilityBoundaryJob(AdminInternalClient admin, SysJobRunLogMapper runs, SysJobTaskMapper tasks) {
        this.admin = admin;
        this.runs = runs;
        this.tasks = tasks;
    }

    /**
     * 声明支付能力边界扫描任务的调度编码和运行描述。
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(PaymentCapabilityJobCodes.BOUNDARY, "支付方式时间边界复核",
                "operations", "按独立来源游标分批处理支付授权、绑定和 MID 的自然边界");
    }

    /**
     * 用任务持久化检查点推进授权与配置时间边界扫描。
     */
    @Override
    @DS(DataSourceName.MASTER)
    public JobExecuteResult execute(JobExecuteContext context) {
        Long epoch = runs.findRunningLease(context.getRunId(), context.getJobId(), context.getExecutorNode());
        SysJobTaskDO task = epoch == null || epoch <= 0
                ? null : tasks.findFencedTask(context.getJobId(), context.getExecutorNode(), epoch);
        if (task == null) {
            throw new IllegalStateException("payment capability job lease was lost");
        }
        Map<String, Window> windows = loadWindows(task.getCheckpointJson());
        LocalDateTime now = context.getActualTriggerTime().truncatedTo(ChronoUnit.MILLIS);
        long version = task.getCheckpointVersion();
        if (windows.keySet().size() < SOURCES.size()) {
            for (String source : SOURCES) {
                windows.putIfAbsent(source, new Window(new Cursor(now.minusMinutes(1), 0), null));
            }
            saveWindows(context, epoch, version, windows);
            version++;
        }
        int processed = 0;
        int failures = 0;
        for (String source : SOURCES) {
            Window window = windows.get(source);
            LocalDateTime through = window.through() == null ? now : window.through();
            if (window.after().time().isAfter(through)) {
                continue;
            }
            Cursor after = window.after();
            if (window.through() == null && (source.endsWith("_CHANGE"))) {
                // 配置写事务可能略晚于 update_time 提交，重复区间由来源版本幂等消化。
                after = new Cursor(after.time().minusSeconds(2), 0L);
            }
            BoundaryResponse response;
            try {
                response = admin.scanPaymentCapabilityBoundaries(new BoundaryRequest(source, after, through, LIMIT));
            } catch (RuntimeException exception) {
                failures++;
                log.warn("支付方式来源复核待补偿, event: JOB_PAYMENT_CAPABILITY_SOURCE_PENDING, runId: {}, source: {}, errorType: {}",
                        context.getRunId(), source, exception.getClass().getSimpleName());
                continue;
            }
            if (response == null || response.processed() < 0 || response.processed() > LIMIT
                    || (response.hasMore() && !advances(after, response.next(), through))) {
                throw new IllegalStateException("payment capability boundary returned invalid checkpoint");
            }
            Window next = response.hasMore() ? new Window(response.next(), through)
                    : new Window(new Cursor(through, Long.MAX_VALUE), null);
            windows.put(source, next);
            saveWindows(context, epoch, version, windows);
            version++;
            processed += response.processed();
        }
        if (failures > 0) {
            return JobExecuteResult.failed("PAYMENT_CAPABILITY_SOURCE_PENDING", "pending sources: " + failures);
        }
        return JobExecuteResult.success("payment capability boundaries processed: " + processed, null);
    }

    private void saveWindows(JobExecuteContext context, Long epoch, long version, Map<String, Window> windows) {
        if (tasks.advanceFencedCheckpoint(context.getJobId(), context.getExecutorNode(), epoch, version,
                JsonUtils.toJsonString(windows)) != 1) {
            throw new IllegalStateException("payment capability job checkpoint ownership changed");
        }
    }

    private static Map<String, Window> loadWindows(String json) {
        if (json == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Window> snapshot = JsonUtils.parseObject(json, new TypeReference<>() { });
        if (snapshot == null || !SOURCES.containsAll(snapshot.keySet()) || snapshot.values().stream()
                .anyMatch(value -> value == null || value.after() == null || value.after().time() == null)) {
            throw new IllegalStateException("payment capability job checkpoint is invalid");
        }
        return new LinkedHashMap<>(snapshot);
    }

    private static boolean advances(Cursor after, Cursor next, LocalDateTime through) {
        return next != null && next.time() != null && next.sourceId() > 0 && !next.time().isAfter(through)
                && (next.time().isAfter(after.time()) || (next.time().equals(after.time()) && next.sourceId() > after.sourceId()));
    }

    public record Window(Cursor after, LocalDateTime through) {
    }
}
