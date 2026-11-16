package com.scott.payment.data.service.export;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeBusinessRateQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeRawRateQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeUsageSnapshotQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeVolatilityQuery;
import com.scott.payment.data.client.admin.AdminExchangeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.BusinessRatePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.BusinessRatePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.BusinessRateRow;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RawRatePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RawRatePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RawRateRow;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.UsageSnapshotPageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.UsageSnapshotPageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.UsageSnapshotRow;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.VolatilityPageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.VolatilityPageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.VolatilityRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.impl.ExchangeBusinessRateExportProcessor;
import com.scott.payment.data.service.export.impl.ExchangeRawRateExportProcessor;
import com.scott.payment.data.service.export.impl.ExchangeUsageSnapshotExportProcessor;
import com.scott.payment.data.service.export.impl.ExchangeVolatilityExportProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
 * 管理端汇率事实异步导出处理器测试，验证分页请求和 BigDecimal 原值不变。
 */
class ExchangeExportProcessorTest {

    /** 原始汇率导出必须保留极小报价和尾随精度，不得写成科学计数法来源值。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportRawRatesWithoutChangingDecimals() {
        AdminExchangeExportSourceClient client = mock(AdminExchangeExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        RawRateRow source = new RawRateRow();
        source.setSpotBuyRate(new BigDecimal("0.00000001"));
        source.setMiddleRate(new BigDecimal("7.1234567800"));
        source.setCreateMethod("API");
        source.setRateStatus("ENABLED");
        RawRatePageResponse page = new RawRatePageResponse();
        page.setTotal(1L);
        page.setRecords(List.of(source));
        when(client.loadRawRatePage(any())).thenReturn(page);
        ExportArtifact artifact = artifact();
        when(writer.write(any(), any(), any(), any(), anyString(), anyString(), anyString(),
                eq(ExchangeRawRateExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<ExchangeRawRateExportRow>> loader = invocation.getArgument(9);
            ExchangeRawRateExportRow row = loader.apply(2).records().get(0);
            assertThat(row.getSpotBuyRate()).isEqualByComparingTo("0.00000001");
            assertThat(row.getMiddleRate()).isEqualByComparingTo("7.1234567800");
            assertThat(row.getCreateMethod()).isEqualTo("中文:excel.exchange.enum.createMethod.API");
            return artifact;
        });
        ExchangeRawRateExportProcessor processor = new ExchangeRawRateExportProcessor(
                client, writer, resolver(), properties());

        processor.process(task(new ExchangeRawRateQuery()), mock(ExportObjectStorageService.class),
                mock(ExportProgressListener.class), LocalDateTime.now());

        ArgumentCaptor<RawRatePageRequest> captor = ArgumentCaptor.forClass(RawRatePageRequest.class);
        verify(client).loadRawRatePage(captor.capture());
        assertThat(captor.getValue().getPageNo()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
    }

    /** 业务汇率导出必须保持 originalRate 与 finalRate 原值和方向。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportBusinessRatesWithoutRecalculation() {
        AdminExchangeExportSourceClient client = mock(AdminExchangeExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        BusinessRateRow source = new BusinessRateRow();
        source.setRateType("TRANSACTION_RATE");
        source.setOriginalRate(new BigDecimal("1.234567890123"));
        source.setFinalRate(new BigDecimal("1.234500000001"));
        source.setGenerateMethod("AUTO");
        source.setRateStatus("ENABLED");
        BusinessRatePageResponse page = new BusinessRatePageResponse();
        page.setTotal(1L);
        page.setRecords(List.of(source));
        when(client.loadBusinessRatePage(any())).thenReturn(page);
        when(writer.write(any(), any(), any(), any(), anyString(), anyString(), anyString(),
                eq(ExchangeBusinessRateExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<ExchangeBusinessRateExportRow>> loader = invocation.getArgument(9);
            ExchangeBusinessRateExportRow row = loader.apply(3).records().get(0);
            assertThat(row.getOriginalRate()).isEqualByComparingTo("1.234567890123");
            assertThat(row.getFinalRate()).isEqualByComparingTo("1.234500000001");
            assertThat(row.getRateType())
                    .isEqualTo("中文:excel.exchange.enum.rateType.TRANSACTION_RATE");
            return artifact();
        });
        ExchangeBusinessRateExportProcessor processor = new ExchangeBusinessRateExportProcessor(
                client, writer, resolver(), properties());

        processor.process(task(new ExchangeBusinessRateQuery()), mock(ExportObjectStorageService.class),
                mock(ExportProgressListener.class), LocalDateTime.now());

        ArgumentCaptor<BusinessRatePageRequest> captor =
                ArgumentCaptor.forClass(BusinessRatePageRequest.class);
        verify(client).loadBusinessRatePage(captor.capture());
        assertThat(captor.getValue().getPageNo()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
    }

    /** 使用快照导出必须直接复制 usedRate，不能按 INVERSE 方向再次取倒数。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportUsageSnapshotWithoutDirectionConversion() {
        AdminExchangeExportSourceClient client = mock(AdminExchangeExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        UsageSnapshotRow source = new UsageSnapshotRow();
        source.setRateType("SETTLEMENT_RATE");
        source.setUsageScene("SETTLEMENT");
        source.setUsedRate(new BigDecimal("0.712335440000"));
        source.setSourceQuoteDirection("INVERSE");
        UsageSnapshotPageResponse page = new UsageSnapshotPageResponse();
        page.setTotal(1L);
        page.setRecords(List.of(source));
        when(client.loadUsageSnapshotPage(any())).thenReturn(page);
        when(writer.write(any(), any(), any(), any(), anyString(), anyString(), anyString(),
                eq(ExchangeUsageSnapshotExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<ExchangeUsageSnapshotExportRow>> loader = invocation.getArgument(9);
            ExchangeUsageSnapshotExportRow row = loader.apply(4).records().get(0);
            assertThat(row.getUsedRate()).isEqualByComparingTo("0.712335440000");
            assertThat(row.getSourceQuoteDirection()).isEqualTo("INVERSE");
            return artifact();
        });
        ExchangeUsageSnapshotExportProcessor processor = new ExchangeUsageSnapshotExportProcessor(
                client, writer, resolver(), properties());

        processor.process(task(new ExchangeUsageSnapshotQuery()), mock(ExportObjectStorageService.class),
                mock(ExportProgressListener.class), LocalDateTime.now());

        ArgumentCaptor<UsageSnapshotPageRequest> captor =
                ArgumentCaptor.forClass(UsageSnapshotPageRequest.class);
        verify(client).loadUsageSnapshotPage(captor.capture());
        assertThat(captor.getValue().getPageNo()).isEqualTo(4);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
    }

    /** 波动导出必须直接复制权威字符串，避免浮点转换或科学计数法。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportVolatilityWithoutDecimalConversion() {
        AdminExchangeExportSourceClient client = mock(AdminExchangeExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        VolatilityRow source = new VolatilityRow();
        source.setRateType("TRANSACTION_RATE");
        source.setLatestRate("0.00000001");
        source.setMinimumRate("0.00000001");
        source.setMaximumRate("7.1234567800");
        source.setVolatilityRate("3.00000000");
        source.setAlertStatus("OPEN");
        VolatilityPageResponse page = new VolatilityPageResponse();
        page.setTotal(1L);
        page.setRecords(List.of(source));
        when(client.loadVolatilityPage(any())).thenReturn(page);
        when(writer.write(any(), any(), any(), any(), anyString(), anyString(), anyString(),
                eq(ExchangeVolatilityExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<ExchangeVolatilityExportRow>> loader = invocation.getArgument(9);
            ExchangeVolatilityExportRow row = loader.apply(5).records().get(0);
            assertThat(row.getLatestRate()).isEqualTo("0.00000001");
            assertThat(row.getMaximumRate()).isEqualTo("7.1234567800");
            assertThat(row.getVolatilityRate()).isEqualTo("3.00000000");
            return artifact();
        });
        ExchangeVolatilityExportProcessor processor = new ExchangeVolatilityExportProcessor(
                client, writer, resolver(), properties());

        processor.process(task(new ExchangeVolatilityQuery()), mock(ExportObjectStorageService.class),
                mock(ExportProgressListener.class), LocalDateTime.now());

        ArgumentCaptor<VolatilityPageRequest> captor =
                ArgumentCaptor.forClass(VolatilityPageRequest.class);
        verify(client).loadVolatilityPage(captor.capture());
        assertThat(captor.getValue().getPageNo()).isEqualTo(5);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
    }

    /** 创建返回中文标记的枚举解析器，验证国际化键和冻结语言。 */
    private ExcelI18nMessageResolver resolver() {
        ExcelI18nMessageResolver resolver = mock(ExcelI18nMessageResolver.class);
        when(resolver.resolve(anyString(), eq(Locale.SIMPLIFIED_CHINESE)))
                .thenAnswer(invocation -> "中文:" + invocation.getArgument(0));
        return resolver;
    }

    /** 创建含强类型查询、任务创建人和冻结语言的测试任务。 */
    private DataExportTaskDO task(Object query) {
        DataExportTaskDO task = new DataExportTaskDO();
        task.setTaskNo("EXP-EXCHANGE-001");
        task.setTaskName("汇率事实导出");
        task.setCreatorAccountId(88L);
        task.setCreatorAccountName("Exchange Export Operator");
        task.setLocale("zh-CN");
        task.setQuerySnapshot(JsonUtils.toJsonString(query));
        return task;
    }

    /** 创建汇率导出使用的普通分页配置。 */
    private DataExportProperties properties() {
        DataExportProperties properties = new DataExportProperties();
        properties.setPageSize(73);
        return properties;
    }

    /** 创建不依赖真实对象存储的文件元数据。 */
    private ExportArtifact artifact() {
        return new ExportArtifact("exchange.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "exports/admin/exchange.xlsx", 1024L, "a".repeat(64), 1L, 1L,
                LocalDateTime.of(2026, 11, 11, 10, 0));
    }
}
