package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelDynamicColumnDefinition;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.RiskConfigQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.DynamicColumn;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.RiskPageRequest;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.RiskPageResponse;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_RISK_CONFIG;

/** 管理端风控配置异步导出处理器，保留各受控功能自己的动态列结构。 */
@Service
public class RiskConfigExportProcessor implements ExportTaskProcessor {
    private final AdminPlatformExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final DataExportProperties properties;

    /** 创建风控配置异步导出处理器。 */
    public RiskConfigExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                     ExcelExportArtifactWriter artifactWriter,
                                     DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.properties = properties;
    }

    @Override
    public String taskType() {
        return ADMIN_RISK_CONFIG;
    }

    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        RiskConfigQuery query = JsonUtils.parseObject(task.getQuerySnapshot(), RiskConfigQuery.class);
        if (query == null || query.getModuleType() == null || query.getFunctionCode() == null) {
            throw new IllegalStateException("risk export task snapshot is invalid");
        }
        RiskPageResponse firstPage = loadSourcePage(task, query, 1);
        if (firstPage.getColumns() == null || firstPage.getColumns().isEmpty()) {
            throw new IllegalStateException("risk export columns are missing");
        }
        List<ExcelDynamicColumnDefinition> columns = firstPage.getColumns().stream()
                .map(this::toColumn).toList();
        String title = firstPage.getTitle() == null || firstPage.getTitle().isBlank()
                ? task.getTaskName() : firstPage.getTitle();
        return artifactWriter.writeDynamic(task, storage, progressListener, now, title, title, columns,
                pageNo -> toExportPage(pageNo == 1 ? firstPage : loadSourcePage(task, query, pageNo)));
    }

    /** 请求 Admin 返回一页已经脱敏并按任务语言格式化的风控数据。 */
    private RiskPageResponse loadSourcePage(DataExportTaskDO task, RiskConfigQuery query, int pageNo) {
        RiskPageRequest request = new RiskPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setLocale(task.getLocale());
        request.setModuleType(query.getModuleType());
        request.setFunctionCode(query.getFunctionCode());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        RiskPageResponse response = sourceClient.loadRiskConfigPage(request);
        if (response == null) {
            throw new IllegalStateException("risk export source response is missing");
        }
        return response;
    }

    /** 将动态列传输模型转换为 Excel 组件列定义。 */
    private ExcelDynamicColumnDefinition toColumn(DynamicColumn source) {
        HorizontalAlignment alignment;
        try {
            alignment = HorizontalAlignment.valueOf(source.getAlign());
        } catch (RuntimeException exception) {
            alignment = HorizontalAlignment.LEFT;
        }
        return new ExcelDynamicColumnDefinition(
                source.getKey(), source.getHeader(), source.getWidth(), alignment);
    }

    /** 将来源响应转换为统一分页载体。 */
    private ExportPage<Map<String, Object>> toExportPage(RiskPageResponse response) {
        return new ExportPage<>(response.getTotal(),
                response.getRecords() == null ? List.of() : response.getRecords());
    }
}
