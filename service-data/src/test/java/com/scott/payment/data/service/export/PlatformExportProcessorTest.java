package com.scott.payment.data.service.export;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CardBinQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CountryQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CurrencyQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ChannelMatchAbnormalQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.JobRunLogQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MccCodeQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.RegionCurrencyQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SecurityInterceptEventQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SettlementPostingQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SettlementReviewQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.AdminSettlementExportSourceClient;
import com.scott.payment.data.client.admin.AdminTransactionExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CardBinPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CardBinPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CountryPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CountryPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CurrencyPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CurrencyPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.JobRunLogPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.JobRunLogPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MccCodePageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MccCodePageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.RegionCurrencyPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.RegionCurrencyPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SecurityInterceptEventPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SecurityInterceptEventPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.SecurityInterceptEventRow;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementPostingPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementPostingPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementPostingRow;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReviewPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReviewPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReviewRow;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.ChannelMatchAbnormalPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.ChannelMatchAbnormalPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.ChannelMatchAbnormalRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.impl.CardBinExportProcessor;
import com.scott.payment.data.service.export.impl.ChannelMatchAbnormalExportProcessor;
import com.scott.payment.data.service.export.impl.CountryExportProcessor;
import com.scott.payment.data.service.export.impl.CurrencyExportProcessor;
import com.scott.payment.data.service.export.impl.JobRunLogExportProcessor;
import com.scott.payment.data.service.export.impl.MccCodeExportProcessor;
import com.scott.payment.data.service.export.impl.RegionCurrencyExportProcessor;
import com.scott.payment.data.service.export.impl.SecurityInterceptEventExportProcessor;
import com.scott.payment.data.service.export.impl.SettlementPostingExportProcessor;
import com.scott.payment.data.service.export.impl.SettlementReviewExportProcessor;
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
 * 平台大数据导出处理器测试，验证经评审的独立分页容量真正传递到来源服务。
 */
class PlatformExportProcessorTest {

    /**
     * 卡 BIN 导出的 Excel 分页与 Admin 来源请求必须统一使用 500 条。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldUsePlatformPageSizeForCardBinExport() {
        AdminPlatformExportSourceClient sourceClient = mock(AdminPlatformExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = new DataExportProperties();
        CardBinPageResponse response = new CardBinPageResponse();
        response.setRecords(List.of());
        when(sourceClient.loadCardBinPage(any())).thenReturn(response);
        ExportArtifact artifact = artifact();
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(CardBinExportRow.class), eq(500), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<CardBinExportRow>> loader = invocation.getArgument(9);
            loader.apply(1);
            return artifact;
        });
        CardBinExportProcessor processor = new CardBinExportProcessor(sourceClient, writer, properties);

        ExportArtifact result = processor.process(task(new CardBinQuery()), storage, listener, LocalDateTime.now());

        ArgumentCaptor<CardBinPageRequest> captor = ArgumentCaptor.forClass(CardBinPageRequest.class);
        verify(sourceClient).loadCardBinPage(captor.capture());
        assertThat(captor.getValue().getPageNo()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(500);
        assertThat(result).isSameAs(artifact);
    }

    /**
     * 四类基础数据导出必须统一使用平台分页容量并调用各自的强类型来源路径。
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void shouldUsePlatformPageSizeForBaseReferenceExports() {
        AdminPlatformExportSourceClient sourceClient = mock(AdminPlatformExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = mock(ExcelI18nMessageResolver.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = new DataExportProperties();
        when(sourceClient.loadCountryPage(any())).thenReturn(new CountryPageResponse());
        when(sourceClient.loadCurrencyPage(any())).thenReturn(new CurrencyPageResponse());
        when(sourceClient.loadRegionCurrencyPage(any())).thenReturn(new RegionCurrencyPageResponse());
        when(sourceClient.loadMccCodePage(any())).thenReturn(new MccCodePageResponse());
        ExportArtifact artifact = artifact();
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                any(Class.class), eq(500), any())).thenAnswer(invocation -> {
            IntFunction loader = invocation.getArgument(9);
            loader.apply(2);
            return artifact;
        });

        new CountryExportProcessor(sourceClient, writer, resolver, properties)
                .process(task(new CountryQuery()), storage, listener, LocalDateTime.now());
        new CurrencyExportProcessor(sourceClient, writer, resolver, properties)
                .process(task(new CurrencyQuery()), storage, listener, LocalDateTime.now());
        new RegionCurrencyExportProcessor(sourceClient, writer, resolver, properties)
                .process(task(new RegionCurrencyQuery()), storage, listener, LocalDateTime.now());
        new MccCodeExportProcessor(sourceClient, writer, resolver, properties)
                .process(task(new MccCodeQuery()), storage, listener, LocalDateTime.now());

        ArgumentCaptor<CountryPageRequest> countryCaptor = ArgumentCaptor.forClass(CountryPageRequest.class);
        ArgumentCaptor<CurrencyPageRequest> currencyCaptor = ArgumentCaptor.forClass(CurrencyPageRequest.class);
        ArgumentCaptor<RegionCurrencyPageRequest> regionCaptor =
                ArgumentCaptor.forClass(RegionCurrencyPageRequest.class);
        ArgumentCaptor<MccCodePageRequest> mccCaptor = ArgumentCaptor.forClass(MccCodePageRequest.class);
        verify(sourceClient).loadCountryPage(countryCaptor.capture());
        verify(sourceClient).loadCurrencyPage(currencyCaptor.capture());
        verify(sourceClient).loadRegionCurrencyPage(regionCaptor.capture());
        verify(sourceClient).loadMccCodePage(mccCaptor.capture());
        assertThat(List.of(countryCaptor.getValue().getPageSize(), currencyCaptor.getValue().getPageSize(),
                regionCaptor.getValue().getPageSize(), mccCaptor.getValue().getPageSize()))
                .containsOnly(500);
        assertThat(List.of(countryCaptor.getValue().getPageNo(), currencyCaptor.getValue().getPageNo(),
                regionCaptor.getValue().getPageNo(), mccCaptor.getValue().getPageNo()))
                .containsOnly(2);
    }

    /**
     * 任务运行日志导出的 Excel 分页与 Admin 来源请求必须统一使用 500 条。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldUsePlatformPageSizeForJobRunLogExport() {
        AdminPlatformExportSourceClient sourceClient = mock(AdminPlatformExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = mock(ExcelI18nMessageResolver.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = new DataExportProperties();
        JobRunLogPageResponse response = new JobRunLogPageResponse();
        response.setRecords(List.of());
        when(sourceClient.loadJobRunLogPage(any())).thenReturn(response);
        ExportArtifact artifact = artifact();
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(JobRunLogExportRow.class), eq(500), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<JobRunLogExportRow>> loader = invocation.getArgument(9);
            loader.apply(1);
            return artifact;
        });
        JobRunLogExportProcessor processor = new JobRunLogExportProcessor(
                sourceClient, writer, resolver, properties);

        ExportArtifact result = processor.process(task(new JobRunLogQuery()), storage, listener, LocalDateTime.now());

        ArgumentCaptor<JobRunLogPageRequest> captor = ArgumentCaptor.forClass(JobRunLogPageRequest.class);
        verify(sourceClient).loadJobRunLogPage(captor.capture());
        assertThat(captor.getValue().getPageNo()).isEqualTo(1);
        assertThat(captor.getValue().getPageSize()).isEqualTo(500);
        assertThat(result).isSameAs(artifact);
    }

    /**
     * 渠道勾兑异常导出必须透传登录账号、冻结商户范围和受控业务分页容量。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldPropagateFrozenScopeForChannelMatchAbnormalExport() {
        AdminTransactionExportSourceClient sourceClient = mock(AdminTransactionExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = properties();
        ChannelMatchAbnormalRow sourceRow = new ChannelMatchAbnormalRow();
        sourceRow.setAbnormalEventId("ABN-001");
        sourceRow.setPlatformAmount(new BigDecimal("120.35"));
        sourceRow.setPlatformCurrency("USD");
        ChannelMatchAbnormalPageResponse response = new ChannelMatchAbnormalPageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(sourceRow));
        when(sourceClient.loadChannelMatchAbnormalPage(any())).thenReturn(response);
        ExportArtifact artifact = artifact();
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(ChannelMatchAbnormalExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<ChannelMatchAbnormalExportRow>> loader = invocation.getArgument(9);
            ExportPage<ChannelMatchAbnormalExportRow> page = loader.apply(2);
            assertThat(page.total()).isEqualTo(1L);
            assertThat(page.records()).singleElement().satisfies(row -> {
                assertThat(row.getAbnormalEventId()).isEqualTo("ABN-001");
                assertThat(row.getPlatformAmount()).isEqualByComparingTo("120.35");
                assertThat(row.getPlatformCurrency()).isEqualTo("USD");
            });
            return artifact;
        });
        ChannelMatchAbnormalQuery query = new ChannelMatchAbnormalQuery();
        DataExportTaskDO task = task(query, false, Set.of("M1001"), "zh-CN");
        ChannelMatchAbnormalExportProcessor processor = new ChannelMatchAbnormalExportProcessor(
                sourceClient, writer, properties);

        ExportArtifact result = processor.process(task, storage, listener, LocalDateTime.now());

        ArgumentCaptor<ChannelMatchAbnormalPageRequest> captor =
                ArgumentCaptor.forClass(ChannelMatchAbnormalPageRequest.class);
        verify(sourceClient).loadChannelMatchAbnormalPage(captor.capture());
        assertThat(captor.getValue().getCreatorAccountId()).isEqualTo(88L);
        assertThat(captor.getValue().isAllMerchants()).isFalse();
        assertThat(captor.getValue().getMerchantIds()).containsExactly("M1001");
        assertThat(captor.getValue().getPageNo()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
        assertThat(result).isSameAs(artifact);
    }

    /**
     * 结算审核单导出必须按任务冻结语言本地化枚举，同时保持冻结商户范围不变。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldLocalizeSettlementReviewUsingFrozenLocale() {
        AdminSettlementExportSourceClient sourceClient = mock(AdminSettlementExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = mock(ExcelI18nMessageResolver.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = properties();
        SettlementReviewRow sourceRow = new SettlementReviewRow();
        sourceRow.setReviewOrderNo("REV-001");
        sourceRow.setReviewType("REGULAR");
        sourceRow.setCreateMode("AUTO");
        sourceRow.setNetDirection("CREDIT");
        sourceRow.setReviewStatus("APPROVED");
        sourceRow.setDecisionAction("APPROVE");
        SettlementReviewPageResponse response = new SettlementReviewPageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(sourceRow));
        when(sourceClient.loadSettlementReviewPage(any())).thenReturn(response);
        when(resolver.resolve(anyString(), eq(Locale.forLanguageTag("en-US"))))
                .thenAnswer(invocation -> "translated:" + invocation.getArgument(0));
        ExportArtifact artifact = artifact();
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(SettlementReviewExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<SettlementReviewExportRow>> loader = invocation.getArgument(9);
            SettlementReviewExportRow row = loader.apply(3).records().get(0);
            assertThat(row.getReviewType())
                    .isEqualTo("translated:excel.settlement.enum.reviewType.REGULAR");
            assertThat(row.getReviewStatus())
                    .isEqualTo("translated:excel.settlement.enum.reviewStatus.APPROVED");
            return artifact;
        });
        SettlementReviewQuery query = new SettlementReviewQuery();
        DataExportTaskDO task = task(query, false, Set.of("M2001", "M2002"), "en-US");
        SettlementReviewExportProcessor processor = new SettlementReviewExportProcessor(
                sourceClient, writer, resolver, properties);

        ExportArtifact result = processor.process(task, storage, listener, LocalDateTime.now());

        ArgumentCaptor<SettlementReviewPageRequest> captor =
                ArgumentCaptor.forClass(SettlementReviewPageRequest.class);
        verify(sourceClient).loadSettlementReviewPage(captor.capture());
        assertThat(captor.getValue().getCreatorAccountId()).isEqualTo(88L);
        assertThat(captor.getValue().isAllMerchants()).isFalse();
        assertThat(captor.getValue().getMerchantIds()).containsExactlyInAnyOrder("M2001", "M2002");
        assertThat(captor.getValue().getPageNo()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
        assertThat(result).isSameAs(artifact);
    }

    /**
     * 结算入账流水导出必须保持资金事实并按冻结语言转换业务枚举。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldPreserveSettlementPostingFactsAndLocalizeEnums() {
        AdminSettlementExportSourceClient sourceClient = mock(AdminSettlementExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = mock(ExcelI18nMessageResolver.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = properties();
        SettlementPostingRow sourceRow = new SettlementPostingRow();
        sourceRow.setLedgerNo("LEDGER-001");
        sourceRow.setBusinessType("TRANSACTION_SETTLEMENT");
        sourceRow.setDirection("CREDIT");
        sourceRow.setOperationMode("AUTO");
        sourceRow.setAmount(new BigDecimal("88.16"));
        sourceRow.setCurrency("EUR");
        SettlementPostingPageResponse response = new SettlementPostingPageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(sourceRow));
        when(sourceClient.loadSettlementPostingPage(any())).thenReturn(response);
        when(resolver.resolve(anyString(), eq(Locale.SIMPLIFIED_CHINESE)))
                .thenAnswer(invocation -> "中文:" + invocation.getArgument(0));
        ExportArtifact artifact = artifact();
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(SettlementPostingExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<SettlementPostingExportRow>> loader = invocation.getArgument(9);
            SettlementPostingExportRow row = loader.apply(4).records().get(0);
            assertThat(row.getBusinessType())
                    .isEqualTo("中文:excel.settlement.enum.businessType.TRANSACTION_SETTLEMENT");
            assertThat(row.getAmount()).isEqualByComparingTo("88.16");
            assertThat(row.getCurrency()).isEqualTo("EUR");
            return artifact;
        });
        SettlementPostingQuery query = new SettlementPostingQuery();
        DataExportTaskDO task = task(query, true, Set.of(), "zh-CN");
        SettlementPostingExportProcessor processor = new SettlementPostingExportProcessor(
                sourceClient, writer, resolver, properties);

        ExportArtifact result = processor.process(task, storage, listener, LocalDateTime.now());

        ArgumentCaptor<SettlementPostingPageRequest> captor =
                ArgumentCaptor.forClass(SettlementPostingPageRequest.class);
        verify(sourceClient).loadSettlementPostingPage(captor.capture());
        assertThat(captor.getValue().isAllMerchants()).isTrue();
        assertThat(captor.getValue().getMerchantIds()).isEmpty();
        assertThat(captor.getValue().getPageNo()).isEqualTo(4);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
        assertThat(result).isSameAs(artifact);
    }

    /**
     * 安全拦截事件导出必须使用高量分页，并保持有限空商户范围而不退化为全量。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldUsePlatformPageSizeAndFiniteEmptyScopeForSecurityInterceptExport() {
        AdminPlatformExportSourceClient sourceClient = mock(AdminPlatformExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = mock(ExcelI18nMessageResolver.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = properties();
        SecurityInterceptEventRow sourceRow = new SecurityInterceptEventRow();
        sourceRow.setEventNo("SEC-001");
        sourceRow.setProcessStatus(1);
        SecurityInterceptEventPageResponse response = new SecurityInterceptEventPageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(sourceRow));
        when(sourceClient.loadSecurityInterceptEventPage(any())).thenReturn(response);
        when(resolver.resolve("excel.securityIntercept.processed", Locale.forLanguageTag("en-US")))
                .thenReturn("Processed");
        ExportArtifact artifact = artifact();
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(SecurityInterceptEventExportRow.class), eq(321), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<SecurityInterceptEventExportRow>> loader = invocation.getArgument(9);
            SecurityInterceptEventExportRow row = loader.apply(5).records().get(0);
            assertThat(row.getEventNo()).isEqualTo("SEC-001");
            assertThat(row.getProcessStatus()).isEqualTo("Processed");
            return artifact;
        });
        SecurityInterceptEventQuery query = new SecurityInterceptEventQuery();
        DataExportTaskDO task = task(query, false, Set.of(), "en-US");
        SecurityInterceptEventExportProcessor processor = new SecurityInterceptEventExportProcessor(
                sourceClient, writer, resolver, properties);

        ExportArtifact result = processor.process(task, storage, listener, LocalDateTime.now());

        ArgumentCaptor<SecurityInterceptEventPageRequest> captor =
                ArgumentCaptor.forClass(SecurityInterceptEventPageRequest.class);
        verify(sourceClient).loadSecurityInterceptEventPage(captor.capture());
        assertThat(captor.getValue().getCreatorAccountId()).isEqualTo(88L);
        assertThat(captor.getValue().isAllMerchants()).isFalse();
        assertThat(captor.getValue().getMerchantIds()).isEmpty();
        assertThat(captor.getValue().getPageNo()).isEqualTo(5);
        assertThat(captor.getValue().getPageSize()).isEqualTo(321);
        assertThat(result).isSameAs(artifact);
    }

    /**
     * 构造包含查询快照和平台范围快照的导出任务。
     *
     * @param query 任务强类型查询
     * @return 可由处理器解析的任务
     */
    private DataExportTaskDO task(Object query) {
        return task(query, true, Set.of(), "zh-CN");
    }

    /**
     * 构造包含指定语言和冻结商户范围的导出任务。
     *
     * @param query 强类型查询快照
     * @param allMerchants 是否允许全部商户
     * @param merchantIds 冻结商户号集合
     * @param locale 冻结语言
     * @return 可由处理器解析的任务
     */
    private DataExportTaskDO task(Object query,
                                  boolean allMerchants,
                                  Set<String> merchantIds,
                                  String locale) {
        AdminExportScopeSnapshot scope = new AdminExportScopeSnapshot();
        scope.setAllMerchants(allMerchants);
        scope.setMerchantIds(merchantIds);
        DataExportTaskDO task = new DataExportTaskDO();
        task.setTaskNo("EXP-PLATFORM-001");
        task.setTaskName("平台数据导出");
        task.setCreatorAccountId(88L);
        task.setCreatorAccountName("Export Operator");
        task.setLocale(locale);
        task.setQuerySnapshot(JsonUtils.toJsonString(query));
        task.setDataScopeSnapshot(JsonUtils.toJsonString(scope));
        return task;
    }

    /**
     * 构造可区分普通分页和高量分页的测试配置。
     *
     * @return 导出容量配置
     */
    private DataExportProperties properties() {
        DataExportProperties properties = new DataExportProperties();
        properties.setPageSize(73);
        properties.setPlatformPageSize(321);
        return properties;
    }

    /**
     * 构造处理器测试使用的最终文件元数据。
     *
     * @return 不依赖真实对象存储的文件元数据
     */
    private ExportArtifact artifact() {
        return new ExportArtifact("platform.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "exports/admin/platform.xlsx", 1024L, "a".repeat(64), 0L, 0L,
                LocalDateTime.of(2026, 11, 17, 10, 0));
    }
}
