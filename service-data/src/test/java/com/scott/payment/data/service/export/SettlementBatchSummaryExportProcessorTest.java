package com.scott.payment.data.service.export;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SettlementBatchSummaryQuery;
import com.scott.payment.data.client.admin.AdminSettlementExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementBatchSummaryPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementBatchSummaryPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementBatchSummaryRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.impl.SettlementBatchSummaryExportProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
 * 结算批次摘要异步处理器测试，验证冻结范围、分页和金额不变性。
 */
class SettlementBatchSummaryExportProcessorTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldLoadFrozenBatchSummaryWithoutRecalculatingAmounts() {
        AdminSettlementExportSourceClient sourceClient = mock(AdminSettlementExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = mock(ExcelI18nMessageResolver.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = new DataExportProperties();
        properties.setPageSize(73);
        SettlementBatchSummaryRow source = new SettlementBatchSummaryRow();
        source.setPaymentMethod("VISA");
        source.setTransactionType("RESERVE_RELEASE");
        source.setOriginalTransactionTypes(List.of("PAYMENT", "PRE_AUTH_COMPLETION"));
        source.setSourceCurrency("EUR");
        source.setSourceAmount(new BigDecimal("80.12340000"));
        source.setTargetCurrency("USD");
        source.setTargetAmount(new BigDecimal("88.13574000"));
        source.setTransactionCount(4L);
        SettlementBatchSummaryPageResponse response = new SettlementBatchSummaryPageResponse();
        response.setTotal(1L);
        response.setSummaryRecords(List.of(source));
        when(sourceClient.loadSettlementBatchSummaryPage(any())).thenReturn(response);
        when(resolver.resolve(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(resolver.resolve("excel.settlement.enum.transactionType.PAYMENT", Locale.SIMPLIFIED_CHINESE))
                .thenReturn("支付");
        when(resolver.resolve("excel.settlement.enum.transactionType.PRE_AUTH_COMPLETION", Locale.SIMPLIFIED_CHINESE))
                .thenReturn("预授权完成");
        ExportArtifact artifact = new ExportArtifact("settlement-summary.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "exports/admin/settlement-summary.xlsx", 1024L, "a".repeat(64), 1L, 1L,
                LocalDateTime.of(2026, 11, 15, 1, 0));
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(),
                anyString(), eq(SettlementBatchSummaryExportRow.class), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<SettlementBatchSummaryExportRow>> loader = invocation.getArgument(8);
            ExportPage<SettlementBatchSummaryExportRow> page = loader.apply(3);
            assertThat(page.total()).isEqualTo(1L);
            assertThat(page.records()).singleElement().satisfies(row -> {
                assertThat(row.getPaymentMethod()).isEqualTo("VISA");
                assertThat(row.getTransactionType()).isEqualTo("支付 / 预授权完成");
                assertThat(row.getSourceAmount()).isEqualByComparingTo("80.12340000");
                assertThat(row.getTargetAmount()).isEqualByComparingTo("88.13574000");
                assertThat(row.getTransactionCount()).isEqualTo(4L);
            });
            return artifact;
        });
        SettlementBatchSummaryExportProcessor processor = new SettlementBatchSummaryExportProcessor(
                sourceClient, writer, resolver, properties);

        ExportArtifact result = processor.process(task(), storage, listener,
                LocalDateTime.of(2026, 11, 15, 1, 0));

        ArgumentCaptor<SettlementBatchSummaryPageRequest> captor =
                ArgumentCaptor.forClass(SettlementBatchSummaryPageRequest.class);
        verify(sourceClient).loadSettlementBatchSummaryPage(captor.capture());
        assertThat(captor.getValue().getQuery().getSettlementBatchNo()).isEqualTo("STB-SUMMARY-1");
        assertThat(captor.getValue().getPageNo()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
        assertThat(captor.getValue().isSummaryProjection()).isTrue();
        assertThat(captor.getValue().getMerchantIds()).containsExactly("M1001");
        assertThat(result).isSameAs(artifact);
    }

    private DataExportTaskDO task() {
        SettlementBatchSummaryQuery query = new SettlementBatchSummaryQuery();
        query.setSettlementBatchNo("STB-SUMMARY-1");
        AdminExportScopeSnapshot scope = new AdminExportScopeSnapshot();
        scope.setMerchantIds(Set.of("M1001"));
        DataExportTaskDO task = new DataExportTaskDO();
        task.setTaskNo("EXP-SUMMARY-1");
        task.setTaskName("结算批次摘要导出");
        task.setCreatorAccountId(88L);
        task.setCreatorAccountName("Admin Operator");
        task.setLocale("zh-CN");
        task.setQuerySnapshot(JsonUtils.toJsonString(query));
        task.setDataScopeSnapshot(JsonUtils.toJsonString(scope));
        return task;
    }
}
