package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SecurityInterceptEventQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SecurityInterceptEventPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SecurityInterceptEventPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SecurityInterceptEventRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.SecurityInterceptEventExportRow;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SECURITY_INTERCEPT_EVENT;

/**
 * 管理端安全拦截事件异步导出处理器，只消费 Admin 返回的脱敏安全投影。
 */
@Service
public class SecurityInterceptEventExportProcessor implements ExportTaskProcessor {
    private final AdminPlatformExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /** 创建安全拦截事件异步导出处理器。 */
    public SecurityInterceptEventExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                                 ExcelExportArtifactWriter artifactWriter,
                                                 ExcelI18nMessageResolver messageResolver,
                                                 DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /** 返回安全拦截事件稳定任务类型。 */
    @Override
    public String taskType() {
        return ADMIN_SECURITY_INTERCEPT_EVENT;
    }

    /** 解析冻结快照并分页生成脱敏安全事件文件。 */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        SecurityInterceptEventQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), SecurityInterceptEventQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.securityIntercept.title", "安全拦截事件", "Security Intercept Events",
                SecurityInterceptEventExportRow.class, properties.getPlatformPageSize(),
                pageNo -> loadPage(task, query, scope, pageNo, locale));
    }

    /** 读取一页安全投影并转换为冻结语言下的 Excel 行。 */
    private ExportPage<SecurityInterceptEventExportRow> loadPage(
            DataExportTaskDO task,
            SecurityInterceptEventQuery query,
            AdminExportScopeSnapshot scope,
            int pageNo,
            Locale locale) {
        SecurityInterceptEventPageRequest request = new SecurityInterceptEventPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPlatformPageSize());
        SecurityInterceptEventPageResponse response = sourceClient.loadSecurityInterceptEventPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<SecurityInterceptEventExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /** 将安全投影复制为 Excel 行并本地化处理状态。 */
    private SecurityInterceptEventExportRow toExportRow(
            SecurityInterceptEventRow source,
            Locale locale) {
        SecurityInterceptEventExportRow row = new SecurityInterceptEventExportRow();
        row.setEventNo(source.getEventNo());
        row.setEventTime(source.getEventTime());
        row.setSourceLayer(source.getSourceLayer());
        row.setEventType(source.getEventType());
        row.setRiskLevel(source.getRiskLevel());
        row.setAction(source.getAction());
        row.setMerchantId(source.getMerchantId());
        row.setClientIp(source.getClientIp());
        row.setRequestPath(source.getRequestPath());
        row.setTraceId(source.getTraceId());
        row.setHitRuleCode(source.getHitRuleCode());
        row.setReasonCode(source.getReasonCode());
        row.setReasonMessage(source.getReasonMessage());
        row.setProcessStatus(processStatusLabel(source.getProcessStatus(), locale));
        row.setProcessedBy(source.getProcessedBy());
        row.setProcessedTime(source.getProcessedTime());
        return row;
    }

    /** 将安全事件处理状态转换为任务冻结语言。 */
    private String processStatusLabel(Integer status, Locale locale) {
        if (status == null || status == 0) {
            return messageResolver.resolve("excel.securityIntercept.unhandled", locale);
        }
        if (status == 1) {
            return messageResolver.resolve("excel.securityIntercept.processed", locale);
        }
        if (status == 2) {
            return messageResolver.resolve("excel.securityIntercept.ignored", locale);
        }
        return String.valueOf(status);
    }

    /** 解析任务冻结语言，未知语言回退简体中文。 */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null
                ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
