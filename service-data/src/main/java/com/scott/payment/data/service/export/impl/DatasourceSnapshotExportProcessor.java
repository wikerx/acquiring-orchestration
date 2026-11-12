package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DatasourceSnapshotQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.DatasourcePageRequest;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.DatasourcePageResponse;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.DatasourceRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.DatasourceSnapshotExportRow;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_DATASOURCE_SNAPSHOT;

/** 管理端数据源监控快照异步导出处理器。 */
@Service
public class DatasourceSnapshotExportProcessor implements ExportTaskProcessor {
    private final AdminPlatformExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final DataExportProperties properties;

    /** 创建数据源快照处理器。 */
    public DatasourceSnapshotExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                             ExcelExportArtifactWriter artifactWriter,
                                             DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.properties = properties;
    }

    @Override
    public String taskType() {
        return ADMIN_DATASOURCE_SNAPSHOT;
    }

    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        DatasourceSnapshotQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), DatasourceSnapshotQuery.class);
        if (query == null) {
            throw new IllegalStateException("datasource snapshot task query is invalid");
        }
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.datasource.title", "数据源监控", "Datasource Monitor",
                DatasourceSnapshotExportRow.class,
                pageNo -> loadPage(task, pageNo));
    }

    /** 请求一页脱敏数据源快照并转换为本地 Excel 行。 */
    private ExportPage<DatasourceSnapshotExportRow> loadPage(DataExportTaskDO task, int pageNo) {
        DatasourcePageRequest request = new DatasourcePageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setLocale(task.getLocale());
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        DatasourcePageResponse response = sourceClient.loadDatasourceSnapshotPage(request);
        if (response == null) {
            throw new IllegalStateException("datasource snapshot source response is missing");
        }
        List<DatasourceSnapshotExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(this::toExportRow).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /** 复制已脱敏数据源字段。 */
    private DatasourceSnapshotExportRow toExportRow(DatasourceRow source) {
        DatasourceSnapshotExportRow row = new DatasourceSnapshotExportRow();
        row.setDataSourceKey(source.getDataSourceKey());
        row.setGroupName(source.getGroupName());
        row.setRole(source.getRole());
        row.setPoolName(source.getPoolName());
        row.setDatabaseName(source.getDatabaseName());
        row.setJdbcUrl(source.getJdbcUrl());
        row.setRunning(source.getRunning());
        row.setReachable(source.getReachable());
        row.setActiveConnections(source.getActiveConnections());
        row.setIdleConnections(source.getIdleConnections());
        row.setTotalConnections(source.getTotalConnections());
        row.setThreadsAwaitingConnection(source.getThreadsAwaitingConnection());
        row.setMaximumPoolSize(source.getMaximumPoolSize());
        row.setMinimumIdle(source.getMinimumIdle());
        row.setRelatedShardingTables(source.getRelatedShardingTables());
        return row;
    }
}
