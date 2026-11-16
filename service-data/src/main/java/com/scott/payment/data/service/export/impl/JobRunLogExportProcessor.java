package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.JobRunLogQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.JobRunLogPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.JobRunLogPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.JobRunLogRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.JobRunLogExportRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_JOB_RUN_LOG;

/**
 * 管理端任务运行日志异步导出处理器，按冻结条件分页读取并本地化枚举展示值。
 */
@Service
public class JobRunLogExportProcessor implements ExportTaskProcessor {
    private final AdminPlatformExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建任务运行日志异步导出处理器。
     *
     * @param sourceClient Admin 平台级导出源客户端
     * @param artifactWriter 统一分页 Excel 写入器
     * @param messageResolver Excel 国际化文案解析器
     * @param properties 异步导出分页和容量配置
     */
    public JobRunLogExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                    ExcelExportArtifactWriter artifactWriter,
                                    ExcelI18nMessageResolver messageResolver,
                                    DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 返回管理端任务运行日志稳定任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_JOB_RUN_LOG;
    }

    /**
     * 解析冻结快照，逐页读取任务日志并生成最终 Excel 文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        JobRunLogQuery query = JsonUtils.parseObject(task.getQuerySnapshot(), JobRunLogQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.jobLog.title", "任务运行日志", "Job Run Logs",
                JobRunLogExportRow.class, properties.getPlatformPageSize(),
                pageNo -> loadPage(task, query, pageNo, locale));
    }

    /**
     * 请求一页任务日志安全投影并转换为任务冻结语言下的 Excel 行。
     */
    private ExportPage<JobRunLogExportRow> loadPage(
            DataExportTaskDO task, JobRunLogQuery query, int pageNo, Locale locale) {
        JobRunLogPageRequest request = new JobRunLogPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPlatformPageSize());
        JobRunLogPageResponse response = sourceClient.loadJobRunLogPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<JobRunLogExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将任务日志安全投影转换为 Excel 行，并本地化触发类型和执行状态。
     */
    private JobRunLogExportRow toExportRow(JobRunLogRow source, Locale locale) {
        JobRunLogExportRow row = new JobRunLogExportRow();
        row.setRunId(source.getRunId());
        row.setJobCode(source.getJobCode());
        row.setJobName(source.getJobName());
        row.setTriggerType(triggerTypeLabel(source.getTriggerType(), locale));
        row.setRunStatus(runStatusLabel(source.getRunStatus(), locale));
        row.setExecutorNode(source.getExecutorNode());
        row.setStartTime(source.getStartTime());
        row.setEndTime(source.getEndTime());
        row.setDurationMs(source.getDurationMs());
        row.setErrorMessage(source.getErrorMessage());
        row.setCreateTime(source.getCreateTime());
        return row;
    }

    /**
     * 解析触发类型展示文案，未知非空编码保持原值以便排障。
     */
    private String triggerTypeLabel(String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return switch (value.trim()) {
            case "MANUAL" -> messageResolver.resolve("excel.jobLog.triggerManual", locale);
            case "RETRY" -> messageResolver.resolve("excel.jobLog.triggerRetry", locale);
            case "SCHEDULE" -> messageResolver.resolve("excel.jobLog.triggerSchedule", locale);
            default -> value.trim();
        };
    }

    /**
     * 解析运行状态展示文案，未知非空编码保持原值以便排障。
     */
    private String runStatusLabel(String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return switch (value.trim()) {
            case "WAITING" -> messageResolver.resolve("excel.jobLog.runWaiting", locale);
            case "RUNNING" -> messageResolver.resolve("excel.jobLog.runRunning", locale);
            case "SUCCESS" -> messageResolver.resolve("excel.jobLog.runSuccess", locale);
            case "FAILED" -> messageResolver.resolve("excel.jobLog.runFailed", locale);
            case "TIMEOUT" -> messageResolver.resolve("excel.jobLog.runTimeout", locale);
            case "CANCELLED" -> messageResolver.resolve("excel.jobLog.runCancelled", locale);
            default -> value.trim();
        };
    }

    /**
     * 解析任务创建时冻结的语言，未知语言回退简体中文。
     */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
