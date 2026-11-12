package com.scott.payment.data.service.export;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FeePlanQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FeeSimulationQuery;
import com.scott.payment.data.client.admin.AdminFeeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeePlanPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeReviewPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeReviewRow;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeSimulationPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeSimulationPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeSimulationRow;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeTemplatePageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeTemplateRow;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.MerchantFeePageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.MerchantFeeRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.impl.FeeReviewExportProcessor;
import com.scott.payment.data.service.export.impl.FeeSimulationExportProcessor;
import com.scott.payment.data.service.export.impl.FeeTemplateExportProcessor;
import com.scott.payment.data.service.export.impl.MerchantFeeExportProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端费用配置与试算异步导出处理器测试，验证分页、商户范围和金额精度不变。
 */
class FeeExportProcessorTest {

    /** 费用模板处理器必须使用平台分页配置并保持版本字段。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportFeeTemplatesWithConfiguredPagination() {
        AdminFeeExportSourceClient client = mock(AdminFeeExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        FeeTemplateRow source = new FeeTemplateRow();
        source.setPlanCode("TPL-1001");
        source.setCurrentVersionNo(5);
        FeeTemplatePageResponse response = new FeeTemplatePageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(source));
        when(client.loadFeeTemplatePage(any())).thenReturn(response);
        when(writer.write(any(), any(), any(), any(), anyString(), anyString(), anyString(),
                eq(FeeTemplateExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<FeeTemplateExportRow>> loader = invocation.getArgument(9);
            ExportPage<FeeTemplateExportRow> page = loader.apply(2);
            assertThat(page.total()).isEqualTo(1L);
            assertThat(page.records().get(0).getCurrentVersionNo()).isEqualTo(5);
            return artifact(1L);
        });
        FeeTemplateExportProcessor processor = new FeeTemplateExportProcessor(
                client, writer, mock(ExcelI18nMessageResolver.class), properties());

        processor.process(task(new FeePlanQuery(), true, Set.of()),
                mock(ExportObjectStorageService.class), mock(ExportProgressListener.class), LocalDateTime.now());

        ArgumentCaptor<FeePlanPageRequest> captor = ArgumentCaptor.forClass(FeePlanPageRequest.class);
        verify(client).loadFeeTemplatePage(captor.capture());
        assertThat(captor.getValue().getPageNo()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
        assertThat(captor.getValue().isAllMerchants()).isTrue();
    }

    /** 商户费率处理器必须传递冻结商户范围并保持方案归属。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportMerchantFeesWithinFrozenScope() {
        AdminFeeExportSourceClient client = mock(AdminFeeExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        MerchantFeeRow source = new MerchantFeeRow();
        source.setMerchantId("M1001");
        source.setPlanCode("MFP-1001");
        MerchantFeePageResponse response = new MerchantFeePageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(source));
        when(client.loadMerchantFeePage(any())).thenReturn(response);
        when(writer.write(any(), any(), any(), any(), anyString(), anyString(), anyString(),
                eq(MerchantFeeExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<MerchantFeeExportRow>> loader = invocation.getArgument(9);
            assertThat(loader.apply(3).records().get(0).getMerchantId()).isEqualTo("M1001");
            return artifact(1L);
        });
        MerchantFeeExportProcessor processor = new MerchantFeeExportProcessor(
                client, writer, mock(ExcelI18nMessageResolver.class), properties());

        processor.process(task(new FeePlanQuery(), false, Set.of("M1001")),
                mock(ExportObjectStorageService.class), mock(ExportProgressListener.class), LocalDateTime.now());

        ArgumentCaptor<FeePlanPageRequest> captor = ArgumentCaptor.forClass(FeePlanPageRequest.class);
        verify(client).loadMerchantFeePage(captor.capture());
        assertThat(captor.getValue().getMerchantIds()).containsExactly("M1001");
        assertThat(captor.getValue().getPageNo()).isEqualTo(3);
    }

    /** 费率审核处理器必须使用独立任务行模型，不能混入模板或商户费率字段。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportFeeReviewsWithDedicatedProjection() {
        AdminFeeExportSourceClient client = mock(AdminFeeExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        FeeReviewRow source = new FeeReviewRow();
        source.setPlanCode("MFP-2001");
        source.setVersionNo(7);
        source.setSubmitByName("Reviewer");
        FeeReviewPageResponse response = new FeeReviewPageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(source));
        when(client.loadFeeReviewPage(any())).thenReturn(response);
        when(writer.write(any(), any(), any(), any(), anyString(), anyString(), anyString(),
                eq(FeeReviewExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<FeeReviewExportRow>> loader = invocation.getArgument(9);
            FeeReviewExportRow row = loader.apply(4).records().get(0);
            assertThat(row.getVersionNo()).isEqualTo(7);
            assertThat(row.getSubmitByName()).isEqualTo("Reviewer");
            return artifact(1L);
        });
        FeeReviewExportProcessor processor = new FeeReviewExportProcessor(
                client, writer, mock(ExcelI18nMessageResolver.class), properties());

        processor.process(task(new FeePlanQuery(), false, Set.of("M2001")),
                mock(ExportObjectStorageService.class), mock(ExportProgressListener.class), LocalDateTime.now());

        verify(client).loadFeeReviewPage(any());
    }

    /** 费用试算处理器必须保持高精度金额并使用主记录总数控制来源分页。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportExpandedSimulationRowsWithoutRounding() {
        AdminFeeExportSourceClient client = mock(AdminFeeExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        FeeSimulationRow first = simulationRow(1, "1.23456789");
        FeeSimulationRow second = simulationRow(2, "0.00000001");
        FeeSimulationPageResponse response = new FeeSimulationPageResponse();
        response.setSourceTotal(9L);
        response.setRecords(List.of(first, second));
        when(client.loadFeeSimulationPage(any())).thenReturn(response);
        when(writer.write(any(), any(), any(), any(), anyString(), anyString(), anyString(),
                eq(FeeSimulationExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<FeeSimulationExportRow>> loader = invocation.getArgument(9);
            ExportPage<FeeSimulationExportRow> page = loader.apply(5);
            assertThat(page.total()).isEqualTo(9L);
            assertThat(page.records()).hasSize(2);
            assertThat(page.records().get(0).getFinalFeeUsd()).isEqualByComparingTo("1.23456789");
            assertThat(page.records().get(1).getFinalFeeUsd()).isEqualByComparingTo("0.00000001");
            return artifact(2L);
        });
        FeeSimulationExportProcessor processor = new FeeSimulationExportProcessor(
                client, writer, mock(ExcelI18nMessageResolver.class), properties());
        FeeSimulationQuery query = new FeeSimulationQuery();
        query.setMerchantId("M3001");

        processor.process(task(query, false, Set.of("M3001")),
                mock(ExportObjectStorageService.class), mock(ExportProgressListener.class), LocalDateTime.now());

        ArgumentCaptor<FeeSimulationPageRequest> captor =
                ArgumentCaptor.forClass(FeeSimulationPageRequest.class);
        verify(client).loadFeeSimulationPage(captor.capture());
        assertThat(captor.getValue().getLocale()).isEqualTo("zh-CN");
        assertThat(captor.getValue().getPageNo()).isEqualTo(5);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
        assertThat(captor.getValue().getMerchantIds()).containsExactly("M3001");
    }

    /** 创建费用试算逐项来源行。 */
    private FeeSimulationRow simulationRow(int lineNo, String finalFeeUsd) {
        FeeSimulationRow row = new FeeSimulationRow();
        row.setSimulationNo("SIM-3001");
        row.setLineNo(lineNo);
        row.setFinalFeeUsd(new BigDecimal(finalFeeUsd));
        return row;
    }

    /** 创建含强类型查询、冻结商户范围和语言的任务。 */
    private DataExportTaskDO task(Object query, boolean allMerchants, Set<String> merchantIds) {
        AdminExportScopeSnapshot scope = new AdminExportScopeSnapshot();
        scope.setAllMerchants(allMerchants);
        scope.setMerchantIds(merchantIds);
        DataExportTaskDO task = new DataExportTaskDO();
        task.setTaskNo("EXP-FEE-001");
        task.setTaskName("费用导出");
        task.setCreatorAccountId(88L);
        task.setCreatorAccountName("Fee Export Operator");
        task.setLocale("zh-CN");
        task.setQuerySnapshot(JsonUtils.toJsonString(query));
        task.setDataScopeSnapshot(JsonUtils.toJsonString(scope));
        return task;
    }

    /** 创建费用域使用的平台分页配置。 */
    private DataExportProperties properties() {
        DataExportProperties properties = new DataExportProperties();
        properties.setPlatformPageSize(73);
        return properties;
    }

    /** 创建不依赖真实对象存储的文件元数据。 */
    private ExportArtifact artifact(long rowCount) {
        return new ExportArtifact("fee.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "exports/admin/fee.xlsx", 1024L, "a".repeat(64), rowCount, rowCount,
                LocalDateTime.of(2026, 11, 11, 10, 0));
    }
}
