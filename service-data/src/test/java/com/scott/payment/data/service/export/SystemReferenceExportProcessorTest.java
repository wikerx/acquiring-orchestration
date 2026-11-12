package com.scott.payment.data.service.export;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DepartmentQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DictDataQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DictTypeQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.HolidayCalendarQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.PostQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DepartmentPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DepartmentPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictDataPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictDataPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictTypePageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.DictTypePageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.HolidayCalendarPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.HolidayCalendarPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.PostPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.PostPageResponse;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.impl.DepartmentExportProcessor;
import com.scott.payment.data.service.export.impl.DictDataExportProcessor;
import com.scott.payment.data.service.export.impl.DictTypeExportProcessor;
import com.scott.payment.data.service.export.impl.HolidayCalendarExportProcessor;
import com.scott.payment.data.service.export.impl.PostExportProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组织、字典与日历导出处理器测试，验证强类型分页请求和 Excel 文本语义。
 */
class SystemReferenceExportProcessorTest {

    /**
     * 五类基础配置导出必须使用平台分页容量，并调用各自独立的权威来源接口。
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldUsePlatformPageSizeForEverySystemReferenceExport() {
        AdminPlatformExportSourceClient sourceClient = mock(AdminPlatformExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = mock(ExcelI18nMessageResolver.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = new DataExportProperties();
        properties.setPlatformPageSize(321);
        when(sourceClient.loadDepartmentPage(any())).thenReturn(new DepartmentPageResponse());
        when(sourceClient.loadPostPage(any())).thenReturn(new PostPageResponse());
        when(sourceClient.loadDictTypePage(any())).thenReturn(new DictTypePageResponse());
        when(sourceClient.loadDictDataPage(any())).thenReturn(new DictDataPageResponse());
        when(sourceClient.loadHolidayCalendarPage(any())).thenReturn(new HolidayCalendarPageResponse());
        ExportArtifact artifact = artifact();
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                any(Class.class), eq(321), any())).thenAnswer(invocation -> {
            IntFunction loader = invocation.getArgument(9);
            loader.apply(3);
            return artifact;
        });

        new DepartmentExportProcessor(sourceClient, writer, resolver, properties)
                .process(task(new DepartmentQuery()), storage, listener, LocalDateTime.now());
        PostQuery postQuery = new PostQuery();
        postQuery.setPostCode("0010");
        new PostExportProcessor(sourceClient, writer, resolver, properties)
                .process(task(postQuery), storage, listener, LocalDateTime.now());
        new DictTypeExportProcessor(sourceClient, writer, resolver, properties)
                .process(task(new DictTypeQuery()), storage, listener, LocalDateTime.now());
        DictDataQuery dictDataQuery = new DictDataQuery();
        dictDataQuery.setDictValue("0001");
        new DictDataExportProcessor(sourceClient, writer, resolver, properties)
                .process(task(dictDataQuery), storage, listener, LocalDateTime.now());
        HolidayCalendarQuery calendarQuery = new HolidayCalendarQuery();
        calendarQuery.setYear(2027);
        new HolidayCalendarExportProcessor(sourceClient, writer, resolver, properties)
                .process(task(calendarQuery), storage, listener, LocalDateTime.now());

        ArgumentCaptor<DepartmentPageRequest> departmentCaptor =
                ArgumentCaptor.forClass(DepartmentPageRequest.class);
        ArgumentCaptor<PostPageRequest> postCaptor = ArgumentCaptor.forClass(PostPageRequest.class);
        ArgumentCaptor<DictTypePageRequest> typeCaptor =
                ArgumentCaptor.forClass(DictTypePageRequest.class);
        ArgumentCaptor<DictDataPageRequest> dataCaptor =
                ArgumentCaptor.forClass(DictDataPageRequest.class);
        ArgumentCaptor<HolidayCalendarPageRequest> calendarCaptor =
                ArgumentCaptor.forClass(HolidayCalendarPageRequest.class);
        verify(sourceClient).loadDepartmentPage(departmentCaptor.capture());
        verify(sourceClient).loadPostPage(postCaptor.capture());
        verify(sourceClient).loadDictTypePage(typeCaptor.capture());
        verify(sourceClient).loadDictDataPage(dataCaptor.capture());
        verify(sourceClient).loadHolidayCalendarPage(calendarCaptor.capture());

        assertThat(List.of(
                departmentCaptor.getValue().getPageNo(), postCaptor.getValue().getPageNo(),
                typeCaptor.getValue().getPageNo(), dataCaptor.getValue().getPageNo(),
                calendarCaptor.getValue().getPageNo())).containsOnly(3);
        assertThat(List.of(
                departmentCaptor.getValue().getPageSize(), postCaptor.getValue().getPageSize(),
                typeCaptor.getValue().getPageSize(), dataCaptor.getValue().getPageSize(),
                calendarCaptor.getValue().getPageSize())).containsOnly(321);
        assertThat(postCaptor.getValue().getQuery().getPostCode()).isEqualTo("0010");
        assertThat(dataCaptor.getValue().getQuery().getDictValue()).isEqualTo("0001");
        assertThat(calendarCaptor.getValue().getQuery().getYear()).isEqualTo(2027);
    }

    /**
     * 可能含前导零的编码、字典值和联系电话必须强制按 Excel 文本单元格写出。
     */
    @Test
    void shouldPreserveLeadingZeroFieldsAsExcelText() throws Exception {
        assertThat(forceText(DepartmentExportRow.class, "phone")).isTrue();
        assertThat(forceText(PostExportRow.class, "postCode")).isTrue();
        assertThat(forceText(DictTypeExportRow.class, "dictType")).isTrue();
        assertThat(forceText(DictDataExportRow.class, "dictType")).isTrue();
        assertThat(forceText(DictDataExportRow.class, "dictValue")).isTrue();
    }

    /**
     * 读取 Excel 列的文本格式声明，用于约束不可计算标识符的写出语义。
     */
    private boolean forceText(Class<?> rowType, String fieldName) throws Exception {
        return rowType.getDeclaredField(fieldName).getAnnotation(ExcelExportColumn.class).forceText();
    }

    /**
     * 构造包含强类型查询和全平台范围的可执行导出任务。
     */
    private DataExportTaskDO task(Object query) {
        AdminExportScopeSnapshot scope = new AdminExportScopeSnapshot();
        scope.setAllMerchants(true);
        scope.setMerchantIds(Set.of());
        DataExportTaskDO task = new DataExportTaskDO();
        task.setTaskNo("EXP-SYSTEM-001");
        task.setCreatorAccountId(88L);
        task.setLocale("zh-CN");
        task.setQuerySnapshot(JsonUtils.toJsonString(query));
        task.setDataScopeSnapshot(JsonUtils.toJsonString(scope));
        return task;
    }

    /**
     * 构造不依赖真实对象存储的最终文件元数据。
     */
    private ExportArtifact artifact() {
        return new ExportArtifact(
                "system.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "exports/admin/system.xlsx",
                1024L,
                "a".repeat(64),
                0L,
                0L,
                LocalDateTime.of(2026, 11, 11, 10, 0));
    }
}
