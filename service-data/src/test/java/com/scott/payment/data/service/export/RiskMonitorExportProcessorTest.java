package com.scott.payment.data.service.export;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelDynamicColumnDefinition;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DatasourceSnapshotQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.RiskConfigQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.DatasourcePageRequest;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.DatasourcePageResponse;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.DatasourceRow;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.DynamicColumn;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.RiskPageRequest;
import com.scott.payment.data.client.admin.dto.AdminRiskMonitorExportSourceDTOs.RiskPageResponse;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.impl.DatasourceSnapshotExportProcessor;
import com.scott.payment.data.service.export.impl.RiskConfigExportProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_DATASOURCE_SNAPSHOT;
import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_RISK_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 风控配置与数据源监控异步导出处理器契约测试。
 */
class RiskMonitorExportProcessorTest {

    /**
     * 风控配置导出必须传递冻结的功能身份、分页容量、动态列和已脱敏行。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportRiskDynamicColumnsAndMaskedRows() {
        AdminPlatformExportSourceClient sourceClient = mock(AdminPlatformExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = new DataExportProperties();
        properties.setPageSize(73);
        RiskPageResponse source = new RiskPageResponse();
        source.setTotal(1L);
        source.setTitle("卡号黑名单");
        DynamicColumn column = new DynamicColumn();
        column.setKey("matchValuePlain");
        column.setHeader("卡号");
        column.setWidth(24);
        column.setAlign("LEFT");
        source.setColumns(List.of(column));
        source.setRecords(List.of(new LinkedHashMap<>(Map.of(
                "matchValuePlain", "4111****1111"))));
        when(sourceClient.loadRiskConfigPage(any())).thenReturn(source);
        ExportArtifact expected = artifact("risk.xlsx");
        when(writer.writeDynamic(any(), same(storage), same(listener), any(),
                anyString(), anyString(), anyList(), any())).thenAnswer(invocation -> {
            List<ExcelDynamicColumnDefinition> columns = invocation.getArgument(6);
            IntFunction<ExportPage<Map<String, Object>>> loader = invocation.getArgument(7);
            ExportPage<Map<String, Object>> page = loader.apply(1);
            assertThat(columns).singleElement().satisfies(item -> {
                assertThat(item.key()).isEqualTo("matchValuePlain");
                assertThat(item.header()).isEqualTo("卡号");
            });
            assertThat(page.records()).singleElement().satisfies(row -> {
                assertThat(row.get("matchValuePlain")).isEqualTo("4111****1111");
                assertThat(row.toString()).doesNotContain("4111111111111111");
            });
            return expected;
        });
        RiskConfigQuery query = new RiskConfigQuery();
        query.setModuleType("BLACK");
        query.setFunctionCode("cardNo");
        DataExportTaskDO task = task(ADMIN_RISK_CONFIG, query);

        ExportArtifact actual = new RiskConfigExportProcessor(sourceClient, writer, properties)
                .process(task, storage, listener, LocalDateTime.of(2026, 11, 11, 19, 0));

        ArgumentCaptor<RiskPageRequest> requestCaptor = ArgumentCaptor.forClass(RiskPageRequest.class);
        verify(sourceClient).loadRiskConfigPage(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getModuleType()).isEqualTo("BLACK");
        assertThat(requestCaptor.getValue().getFunctionCode()).isEqualTo("cardNo");
        assertThat(requestCaptor.getValue().getPageNo()).isEqualTo(1);
        assertThat(requestCaptor.getValue().getPageSize()).isEqualTo(73);
        assertThat(actual).isSameAs(expected);
    }

    /**
     * 数据源快照导出必须只传输安全投影，并使用统一分页容量。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportSanitizedDatasourceProjection() {
        AdminPlatformExportSourceClient sourceClient = mock(AdminPlatformExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = new DataExportProperties();
        properties.setPageSize(91);
        DatasourceRow row = new DatasourceRow();
        row.setDataSourceKey("master");
        row.setDatabaseName("acquiring");
        row.setJdbcUrl("jdbc:mysql://db.internal:3306/acquiring");
        DatasourcePageResponse source = new DatasourcePageResponse();
        source.setTotal(1L);
        source.setRecords(List.of(row));
        when(sourceClient.loadDatasourceSnapshotPage(any())).thenReturn(source);
        ExportArtifact expected = artifact("datasource.xlsx");
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(),
                anyString(), eq(DatasourceSnapshotExportRow.class), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<DatasourceSnapshotExportRow>> loader = invocation.getArgument(8);
            ExportPage<DatasourceSnapshotExportRow> page = loader.apply(1);
            assertThat(page.records()).singleElement().satisfies(exportRow -> {
                assertThat(exportRow.getDataSourceKey()).isEqualTo("master");
                assertThat(exportRow.getJdbcUrl()).isEqualTo("jdbc:mysql://db.internal:3306/acquiring");
            });
            return expected;
        });
        DataExportTaskDO task = task(ADMIN_DATASOURCE_SNAPSHOT, new DatasourceSnapshotQuery());

        ExportArtifact actual = new DatasourceSnapshotExportProcessor(sourceClient, writer, properties)
                .process(task, storage, listener, LocalDateTime.of(2026, 11, 11, 19, 0));

        ArgumentCaptor<DatasourcePageRequest> requestCaptor =
                ArgumentCaptor.forClass(DatasourcePageRequest.class);
        verify(sourceClient).loadDatasourceSnapshotPage(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getPageNo()).isEqualTo(1);
        assertThat(requestCaptor.getValue().getPageSize()).isEqualTo(91);
        assertThat(actual).isSameAs(expected);
        assertThat(List.of(DatasourceRow.class.getDeclaredFields()))
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("username", "password", "secret");
    }

    /** 构造包含冻结查询快照的异步任务。 */
    private DataExportTaskDO task(String taskType, Object query) {
        DataExportTaskDO task = new DataExportTaskDO();
        task.setTaskNo("EXP-RISK-MONITOR-001");
        task.setTaskType(taskType);
        task.setTaskName("安全导出");
        task.setCreatorAccountId(88L);
        task.setCreatorAccountName("tester");
        task.setLocale("zh-CN");
        task.setQuerySnapshot(JsonUtils.toJsonString(query));
        return task;
    }

    /** 构造不依赖真实对象存储的文件元数据。 */
    private ExportArtifact artifact(String fileName) {
        return new ExportArtifact(
                fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "exports/admin/" + fileName,
                1024L,
                "a".repeat(64),
                1L,
                1L,
                LocalDateTime.of(2026, 11, 18, 19, 0));
    }
}
