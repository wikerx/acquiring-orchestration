package com.scott.payment.admin.application.monitor;

import com.scott.payment.admin.client.job.JobSchedulerInternalClient;
import com.scott.payment.admin.client.job.dto.JobTaskRemoteSaveRequest;
import com.scott.payment.admin.converter.JobSchedulerConverter;
import com.scott.payment.admin.dto.export.JobRunLogExportRow;
import com.scott.payment.admin.dto.monitor.JobExecutorNodeResponse;
import com.scott.payment.admin.dto.monitor.JobHandlerOptionResponse;
import com.scott.payment.admin.dto.monitor.JobManualTriggerRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogQueryRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogResponse;
import com.scott.payment.admin.dto.monitor.JobTaskQueryRequest;
import com.scott.payment.admin.dto.monitor.JobTaskResponse;
import com.scott.payment.admin.dto.monitor.JobTaskSaveRequest;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminJobSchedulerApplicationService
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务调度应用编排服务
 * @status : create
 */

@Service
public class AdminJobSchedulerApplicationService {

    private final JobSchedulerInternalClient jobSchedulerInternalClient;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建管理后台任务调度应用服务。
     *
     * @param jobSchedulerInternalClient 调度中心内部客户端
     */
    public AdminJobSchedulerApplicationService(JobSchedulerInternalClient jobSchedulerInternalClient,
                                               ExcelExportService excelExportService,
                                               ExcelI18nMessageResolver excelI18nMessageResolver,
                                               ExcelLocaleResolver excelLocaleResolver) {
        this.jobSchedulerInternalClient = jobSchedulerInternalClient;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 查询任务处理器白名单。
     *
     * @return 处理器列表
     */
    public List<JobHandlerOptionResponse> listHandlers() {
        return jobSchedulerInternalClient.listHandlers();
    }

    /**
     * 分页查询任务定义。
     *
     * @param request 查询条件
     * @return 任务分页结果
     */
    public PageResult<JobTaskResponse> pageTasks(JobTaskQueryRequest request) {
        return jobSchedulerInternalClient.pageTasks(request);
    }

    /**
     * 新增任务定义。
     *
     * @param request 保存请求
     * @param operator 操作人
     * @return 任务响应
     */
    public JobTaskResponse createTask(JobTaskSaveRequest request, String operator) {
        request.setDescription(trimToNull(request.getDescription()));
        request.setParams(trimToNull(request.getParams()));
        request.setCronExpression(trimToNull(request.getCronExpression()));
        return jobSchedulerInternalClient.createTask(toRemoteSaveRequest(request, operator));
    }

    /**
     * 更新任务定义。
     *
     * @param taskId   任务主键
     * @param request  保存请求
     * @param operator 操作人
     * @return 任务响应
     */
    public JobTaskResponse updateTask(Long taskId, JobTaskSaveRequest request, String operator) {
        request.setDescription(trimToNull(request.getDescription()));
        request.setParams(trimToNull(request.getParams()));
        request.setCronExpression(trimToNull(request.getCronExpression()));
        return jobSchedulerInternalClient.updateTask(taskId, toRemoteSaveRequest(request, operator));
    }

    /**
     * 切换任务状态。
     *
     * @param taskId   任务主键
     * @param status   目标状态
     * @param operator 操作人
     * @return 任务响应
     */
    public JobTaskResponse changeStatus(Long taskId, String status, String operator) {
        return jobSchedulerInternalClient.changeStatus(taskId, status, operator);
    }

    /**
     * 手动执行一次任务。
     *
     * @param taskId      任务主键
     * @param request     执行请求
     * @param operatorId  当前操作人 ID
     * @param operatorName 当前操作人名称
     * @return 执行批次号
     */
    public String trigger(Long taskId, JobManualTriggerRequest request, String operatorId, String operatorName) {
        request.setOperatorId(operatorId);
        request.setOperatorName(operatorName);
        return jobSchedulerInternalClient.trigger(taskId, request);
    }

    /**
     * 删除任务定义。
     *
     * @param taskId   任务主键
     * @param operator 操作人
     */
    public void deleteTask(Long taskId, String operator) {
        jobSchedulerInternalClient.deleteTask(taskId, operator);
    }

    /**
     * 分页查询执行日志。
     *
     * @param request 查询条件
     * @return 日志分页结果
     */
    public PageResult<JobRunLogResponse> pageRunLogs(JobRunLogQueryRequest request) {
        return jobSchedulerInternalClient.pageRunLogs(request);
    }

    /**
     * 按条件查询执行日志列表，供导出使用。
     *
     * @param request 查询条件
     * @return 执行日志列表
     */
    public List<JobRunLogResponse> listRunLogs(JobRunLogQueryRequest request) {
        return jobSchedulerInternalClient.listRunLogs(request);
    }

    /**
     * 删除单条执行日志。
     *
     * @param id 日志主键
     */
    public void removeRunLog(Long id) {
        jobSchedulerInternalClient.removeRunLog(id);
    }

    /**
     * 按条件清空执行日志。
     *
     * @param request 查询条件
     * @return 删除数量
     */
    public int cleanRunLogs(JobRunLogQueryRequest request) {
        return jobSchedulerInternalClient.cleanRunLogs(request);
    }

    /**
     * 导出任务执行日志。
     *
     * @param request 查询条件
     * @param operator 导出人
     * @param response HTTP 响应
     */
    public void exportRunLogs(JobRunLogQueryRequest request,
                              String operator,
                              HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<JobRunLogExportRow> rows = jobSchedulerInternalClient.listRunLogs(request).stream()
                .map(JobSchedulerConverter.INSTANCE::toRunLogExportRow)
                .peek(row -> fillRunLogDisplayValue(row, locale))
                .toList();
        excelExportService.export(
                ExcelExportRequest.<JobRunLogExportRow>builder()
                        .fileName("任务日志_" + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()))
                        .sheetName("任务日志")
                        .titleKey("excel.jobLog.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(buildRunLogQuerySummary(request, locale))
                        .rowClass(JobRunLogExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 查询执行节点列表。
     *
     * @return 执行节点列表
     */
    public List<JobExecutorNodeResponse> listNodes() {
        return jobSchedulerInternalClient.listNodes();
    }

    /**
     * 构造给 service-job 使用的保存请求。
     *
     * @param request  管理后台保存请求
     * @param operator 操作人
     * @return 内部保存请求
     */
    private JobTaskRemoteSaveRequest toRemoteSaveRequest(JobTaskSaveRequest request, String operator) {
        JobTaskRemoteSaveRequest remoteSaveRequest = JobSchedulerConverter.INSTANCE.toRemoteSaveRequest(request, operator);
        remoteSaveRequest.setDescription(trimToNull(remoteSaveRequest.getDescription()));
        remoteSaveRequest.setParams(trimToNull(remoteSaveRequest.getParams()));
        remoteSaveRequest.setCronExpression(trimToNull(remoteSaveRequest.getCronExpression()));
        remoteSaveRequest.setOperator(trimToNull(operator));
        return remoteSaveRequest;
    }

    /**
     * 将空白字符串转为 null。
     *
     * @param value 原始值
     * @return 处理后的值
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 填充运行日志导出展示文案。
     *
     * @param row 导出行对象
     * @param locale 当前语言
     */
    private void fillRunLogDisplayValue(JobRunLogExportRow row, Locale locale) {
        row.setTriggerType(switch (row.getTriggerType()) {
            case "MANUAL" -> excelI18nMessageResolver.resolve("excel.jobLog.triggerManual", locale);
            case "RETRY" -> excelI18nMessageResolver.resolve("excel.jobLog.triggerRetry", locale);
            default -> excelI18nMessageResolver.resolve("excel.jobLog.triggerSchedule", locale);
        });
        row.setRunStatus(switch (row.getRunStatus()) {
            case "WAITING" -> excelI18nMessageResolver.resolve("excel.jobLog.runWaiting", locale);
            case "RUNNING" -> excelI18nMessageResolver.resolve("excel.jobLog.runRunning", locale);
            case "SUCCESS" -> excelI18nMessageResolver.resolve("excel.jobLog.runSuccess", locale);
            case "FAILED" -> excelI18nMessageResolver.resolve("excel.jobLog.runFailed", locale);
            case "TIMEOUT" -> excelI18nMessageResolver.resolve("excel.jobLog.runTimeout", locale);
            case "CANCELLED" -> excelI18nMessageResolver.resolve("excel.jobLog.runCancelled", locale);
            default -> row.getRunStatus();
        });
    }

    /**
     * 构造运行日志查询摘要。
     *
     * @param request 查询条件
     * @param locale 当前语言
     * @return 查询摘要
     */
    private String buildRunLogQuerySummary(JobRunLogQueryRequest request, Locale locale) {
        if (request == null) {
            return excelI18nMessageResolver.resolve("excel.common.noCondition", locale);
        }
        StringBuilder builder = new StringBuilder();
        if (request.getJobCode() != null && !request.getJobCode().isBlank()) {
            builder.append("任务编码=").append(request.getJobCode().trim());
        }
        if (request.getRunStatus() != null && !request.getRunStatus().isBlank()) {
            if (!builder.isEmpty()) {
                builder.append("，");
            }
            builder.append("执行状态=").append(request.getRunStatus().trim());
        }
        if (request.getTriggerType() != null && !request.getTriggerType().isBlank()) {
            if (!builder.isEmpty()) {
                builder.append("，");
            }
            builder.append("触发方式=").append(request.getTriggerType().trim());
        }
        return builder.isEmpty() ? excelI18nMessageResolver.resolve("excel.common.noCondition", locale) : builder.toString();
    }
}
