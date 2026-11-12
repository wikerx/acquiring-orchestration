package com.scott.payment.data.service.export;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FundAccountQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FundDeductionQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FundLedgerQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FundRechargeQuery;
import com.scott.payment.data.client.admin.AdminFundExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundAccountPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundAccountPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundAccountRow;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundDeductionPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundDeductionPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundDeductionRow;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundLedgerPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundLedgerPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundLedgerRow;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundRechargePageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundRechargePageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundRechargeRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.impl.FundAccountExportProcessor;
import com.scott.payment.data.service.export.impl.FundDeductionExportProcessor;
import com.scott.payment.data.service.export.impl.FundLedgerExportProcessor;
import com.scott.payment.data.service.export.impl.FundRechargeExportProcessor;
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
 * 管理端资金账户域异步导出处理器测试，验证冻结范围、分页和资金事实不变。
 */
class FundExportProcessorTest {

    /** 资金账户导出必须使用普通分页并保持余额和结算币种。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportFundAccountsWithFrozenScope() {
        AdminFundExportSourceClient client = mock(AdminFundExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = resolver();
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        FundAccountRow source = new FundAccountRow();
        source.setAccountNo("FA-1001");
        source.setAvailableBalance(new BigDecimal("123.45000000"));
        source.setSettlementCurrency("EUR");
        source.setAccountStatus("NORMAL");
        source.setReverseRestricted(0);
        FundAccountPageResponse response = new FundAccountPageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(source));
        when(client.loadFundAccountPage(any())).thenReturn(response);
        ExportArtifact artifact = artifact();
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(FundAccountExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<FundAccountExportRow>> loader = invocation.getArgument(9);
            FundAccountExportRow row = loader.apply(2).records().get(0);
            assertThat(row.getAvailableBalance()).isEqualByComparingTo("123.45000000");
            assertThat(row.getSettlementCurrency()).isEqualTo("EUR");
            assertThat(row.getAccountStatus()).isEqualTo("中文:excel.fund.enum.accountStatus.NORMAL");
            return artifact;
        });
        FundAccountExportProcessor processor = new FundAccountExportProcessor(
                client, writer, resolver, properties());

        ExportArtifact result = processor.process(
                task(new FundAccountQuery(), false, Set.of("M1001")), storage, listener, LocalDateTime.now());

        ArgumentCaptor<FundAccountPageRequest> captor = ArgumentCaptor.forClass(FundAccountPageRequest.class);
        verify(client).loadFundAccountPage(captor.capture());
        assertThat(captor.getValue().getMerchantIds()).containsExactly("M1001");
        assertThat(captor.getValue().getPageNo()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
        assertThat(result).isSameAs(artifact);
    }

    /** 指定账户流水导出必须冻结账户主键并保持金额、余额和币种。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportFundLedgersWithoutChangingMoneyFacts() {
        AdminFundExportSourceClient client = mock(AdminFundExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = resolver();
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        FundLedgerRow source = new FundLedgerRow();
        source.setLedgerNo("LEDGER-1001");
        source.setBusinessType("TRANSACTION_SETTLEMENT");
        source.setBalanceType("AVAILABLE");
        source.setDirection("CREDIT");
        source.setAmount(new BigDecimal("88.16000000"));
        source.setBalanceBefore(new BigDecimal("100.00000000"));
        source.setBalanceAfter(new BigDecimal("188.16000000"));
        source.setCurrency("USD");
        FundLedgerPageResponse response = new FundLedgerPageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(source));
        when(client.loadFundLedgerPage(any())).thenReturn(response);
        ExportArtifact artifact = artifact();
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(FundLedgerExportRow.class), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<FundLedgerExportRow>> loader = invocation.getArgument(9);
            FundLedgerExportRow row = loader.apply(4).records().get(0);
            assertThat(row.getAmount()).isEqualByComparingTo("88.16000000");
            assertThat(row.getBalanceAfter()).isEqualByComparingTo("188.16000000");
            assertThat(row.getCurrency()).isEqualTo("USD");
            assertThat(row.getDirection()).isEqualTo("中文:excel.fund.enum.direction.CREDIT");
            return artifact;
        });
        FundLedgerQuery query = new FundLedgerQuery();
        query.setAccountId(7001L);
        FundLedgerExportProcessor processor = new FundLedgerExportProcessor(
                client, writer, resolver, properties());

        processor.process(task(query, true, Set.of()), storage, listener, LocalDateTime.now());

        ArgumentCaptor<FundLedgerPageRequest> captor = ArgumentCaptor.forClass(FundLedgerPageRequest.class);
        verify(client).loadFundLedgerPage(captor.capture());
        assertThat(captor.getValue().getQuery().getAccountId()).isEqualTo(7001L);
        assertThat(captor.getValue().isAllMerchants()).isTrue();
        assertThat(captor.getValue().getPageNo()).isEqualTo(4);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
    }

    /** 充值申请导出必须保留金额币种并转换冻结语言下的状态。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportFundRechargesWithLocalizedStatus() {
        AdminFundExportSourceClient client = mock(AdminFundExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = resolver();
        FundRechargeRow source = new FundRechargeRow();
        source.setRechargeNo("RCH-1001");
        source.setAmount(new BigDecimal("20.50000000"));
        source.setCurrency("GBP");
        source.setRechargeStatus("PENDING_RECHECK");
        FundRechargePageResponse response = new FundRechargePageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(source));
        when(client.loadFundRechargePage(any())).thenReturn(response);
        ExcelExportArtifactWriter configuredWriter = writerFor(
                writer, FundRechargeExportRow.class, row -> {
                    assertThat(row.getAmount()).isEqualByComparingTo("20.50000000");
                    assertThat(row.getCurrency()).isEqualTo("GBP");
                    assertThat(row.getRechargeStatus())
                            .isEqualTo("中文:excel.fund.enum.rechargeStatus.PENDING_RECHECK");
                });
        FundRechargeExportProcessor processor = new FundRechargeExportProcessor(
                client, configuredWriter, resolver, properties());

        processor.process(task(new FundRechargeQuery(), false, Set.of("M2001")),
                mock(ExportObjectStorageService.class), mock(ExportProgressListener.class), LocalDateTime.now());

        ArgumentCaptor<FundRechargePageRequest> captor = ArgumentCaptor.forClass(FundRechargePageRequest.class);
        verify(client).loadFundRechargePage(captor.capture());
        assertThat(captor.getValue().getMerchantIds()).containsExactly("M2001");
    }

    /** 扣减申请导出必须保留金额币种并本地化扣减类型和状态。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldExportFundDeductionsWithLocalizedCategory() {
        AdminFundExportSourceClient client = mock(AdminFundExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = resolver();
        FundDeductionRow source = new FundDeductionRow();
        source.setDeductionNo("DED-1001");
        source.setDeductionCategory("EXTRA_FEE");
        source.setDeductionStatus("POSTED");
        source.setAmount(new BigDecimal("9.99000000"));
        source.setCurrency("CAD");
        FundDeductionPageResponse response = new FundDeductionPageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(source));
        when(client.loadFundDeductionPage(any())).thenReturn(response);
        ExcelExportArtifactWriter configuredWriter = writerFor(
                writer, FundDeductionExportRow.class, row -> {
                    assertThat(row.getAmount()).isEqualByComparingTo("9.99000000");
                    assertThat(row.getCurrency()).isEqualTo("CAD");
                    assertThat(row.getDeductionCategory())
                            .isEqualTo("中文:excel.fund.enum.deductionCategory.EXTRA_FEE");
                });
        FundDeductionExportProcessor processor = new FundDeductionExportProcessor(
                client, configuredWriter, resolver, properties());

        processor.process(task(new FundDeductionQuery(), false, Set.of("M3001")),
                mock(ExportObjectStorageService.class), mock(ExportProgressListener.class), LocalDateTime.now());

        ArgumentCaptor<FundDeductionPageRequest> captor = ArgumentCaptor.forClass(FundDeductionPageRequest.class);
        verify(client).loadFundDeductionPage(captor.capture());
        assertThat(captor.getValue().getMerchantIds()).containsExactly("M3001");
    }

    /** 配置能执行给定行类型分页加载器的模拟写入器。 */
    @SuppressWarnings("unchecked")
    private <T> ExcelExportArtifactWriter writerFor(
            ExcelExportArtifactWriter writer, Class<T> rowType,
            java.util.function.Consumer<T> assertion) {
        when(writer.write(any(), any(), any(), any(), anyString(), anyString(), anyString(),
                eq(rowType), eq(73), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<T>> loader = invocation.getArgument(9);
            assertion.accept(loader.apply(3).records().get(0));
            return artifact();
        });
        return writer;
    }

    /** 创建返回中文标记的枚举解析器，便于验证键和冻结语言。 */
    private ExcelI18nMessageResolver resolver() {
        ExcelI18nMessageResolver resolver = mock(ExcelI18nMessageResolver.class);
        when(resolver.resolve(anyString(), eq(Locale.SIMPLIFIED_CHINESE)))
                .thenAnswer(invocation -> "中文:" + invocation.getArgument(0));
        return resolver;
    }

    /** 创建含强类型查询和冻结商户范围的任务。 */
    private DataExportTaskDO task(Object query, boolean allMerchants, Set<String> merchantIds) {
        AdminExportScopeSnapshot scope = new AdminExportScopeSnapshot();
        scope.setAllMerchants(allMerchants);
        scope.setMerchantIds(merchantIds);
        DataExportTaskDO task = new DataExportTaskDO();
        task.setTaskNo("EXP-FUND-001");
        task.setTaskName("资金导出");
        task.setCreatorAccountId(88L);
        task.setCreatorAccountName("Fund Export Operator");
        task.setLocale("zh-CN");
        task.setQuerySnapshot(JsonUtils.toJsonString(query));
        task.setDataScopeSnapshot(JsonUtils.toJsonString(scope));
        return task;
    }

    /** 创建资金导出使用的普通分页配置。 */
    private DataExportProperties properties() {
        DataExportProperties properties = new DataExportProperties();
        properties.setPageSize(73);
        properties.setPlatformPageSize(321);
        return properties;
    }

    /** 创建不依赖真实对象存储的文件元数据。 */
    private ExportArtifact artifact() {
        return new ExportArtifact("fund.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "exports/admin/fund.xlsx", 1024L, "a".repeat(64), 1L, 1L,
                LocalDateTime.of(2026, 11, 18, 10, 0));
    }
}
