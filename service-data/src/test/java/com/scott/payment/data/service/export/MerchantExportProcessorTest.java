package com.scott.payment.data.service.export;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantRefundQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantFundLedgerQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantSettlementReserveQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantSettlementTransactionQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantTransactionQuery;
import com.scott.payment.data.client.merchant.MerchantExportSourceClient;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.RefundPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.RefundPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.RefundRow;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.FundLedgerPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.FundLedgerPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.FundLedgerRow;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementReservePageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementReservePageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementReserveRow;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementTransactionPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementTransactionPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementTransactionRow;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.TransactionPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.TransactionPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.TransactionRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.impl.MerchantRefundExportProcessor;
import com.scott.payment.data.service.export.impl.MerchantFundLedgerExportProcessor;
import com.scott.payment.data.service.export.impl.MerchantSettlementReserveExportProcessor;
import com.scott.payment.data.service.export.impl.MerchantSettlementTransactionExportProcessor;
import com.scott.payment.data.service.export.impl.MerchantTransactionExportProcessor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户异步导出处理器测试。
 *
 * <p>验证冻结商户范围、来源分页参数和标签金额在 Data 生成文件前保持不变。</p>
 */
class MerchantExportProcessorTest {

    /**
     * 交易导出必须使用冻结商户号和配置页大小，并把标签金额原样写入 Excel 行。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepMerchantTransactionScopePageAndLabelAmount() {
        MerchantExportSourceClient sourceClient = mock(MerchantExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = mock(ExcelI18nMessageResolver.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = properties(137);
        TransactionRow source = new TransactionRow();
        source.setTransactionId("TXN-MERCHANT-001");
        source.setTransactionAmount(new BigDecimal("100.00"));
        source.setTransactionCurrency("EUR");
        source.setThreeDsEnabled(1);
        TransactionPageResponse response = new TransactionPageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(source));
        when(sourceClient.loadTransactionPage(any())).thenReturn(response);
        when(resolver.resolve(anyString(), any())).thenReturn("是");
        ExportArtifact artifact = artifact("merchant-transaction.xlsx");
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(MerchantTransactionExportRow.class), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<MerchantTransactionExportRow>> loader = invocation.getArgument(8);
            ExportPage<MerchantTransactionExportRow> page = loader.apply(3);
            assertThat(page.total()).isEqualTo(1L);
            assertThat(page.records()).singleElement().satisfies(row -> {
                assertThat(row.getTransactionAmount()).isEqualByComparingTo("100.00");
                assertThat(row.getTransactionCurrency()).isEqualTo("EUR");
            });
            return artifact;
        });
        MerchantTransactionExportProcessor processor = new MerchantTransactionExportProcessor(
                sourceClient, writer, resolver, properties);

        ExportArtifact result = processor.process(transactionTask("M100001"), storage, listener,
                LocalDateTime.of(2026, 11, 10, 16, 0));

        ArgumentCaptor<TransactionPageRequest> captor = ArgumentCaptor.forClass(TransactionPageRequest.class);
        verify(sourceClient).loadTransactionPage(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo("M100001");
        assertThat(captor.getValue().getPageNo()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(137);
        assertThat(result).isSameAs(artifact);
    }

    /**
     * 退款导出必须使用冻结商户号和配置页大小，并把标签退款金额原样写入 Excel 行。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepMerchantRefundScopePageAndLabelAmount() {
        MerchantExportSourceClient sourceClient = mock(MerchantExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = properties(89);
        RefundRow source = new RefundRow();
        source.setRefundTransactionId("RFD-MERCHANT-001");
        source.setTransactionAmount(new BigDecimal("20.00"));
        source.setTransactionCurrency("EUR");
        RefundPageResponse response = new RefundPageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(source));
        when(sourceClient.loadRefundPage(any())).thenReturn(response);
        ExportArtifact artifact = artifact("merchant-refund.xlsx");
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(MerchantRefundExportRow.class), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<MerchantRefundExportRow>> loader = invocation.getArgument(8);
            ExportPage<MerchantRefundExportRow> page = loader.apply(2);
            assertThat(page.total()).isEqualTo(1L);
            assertThat(page.records()).singleElement().satisfies(row -> {
                assertThat(row.getTransactionAmount()).isEqualByComparingTo("20.00");
                assertThat(row.getTransactionCurrency()).isEqualTo("EUR");
            });
            return artifact;
        });
        MerchantRefundExportProcessor processor = new MerchantRefundExportProcessor(
                sourceClient, writer, properties);

        ExportArtifact result = processor.process(refundTask("M100001"), storage, listener,
                LocalDateTime.of(2026, 11, 10, 16, 0));

        ArgumentCaptor<RefundPageRequest> captor = ArgumentCaptor.forClass(RefundPageRequest.class);
        verify(sourceClient).loadRefundPage(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo("M100001");
        assertThat(captor.getValue().getPageNo()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(89);
        assertThat(result).isSameAs(artifact);
    }

    /**
     * 交易结算导出必须按冻结商户分页，并原样保留清分来源金额与商户结算金额。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepSettlementTransactionAmountsWithoutRounding() {
        MerchantExportSourceClient sourceClient = mock(MerchantExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = mock(ExcelI18nMessageResolver.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = properties(113);
        SettlementTransactionRow source = new SettlementTransactionRow();
        source.setSourceAmount(new BigDecimal("80.12340000"));
        source.setSourceCurrency("EUR");
        source.setNetTargetAmount(new BigDecimal("88.13574000"));
        source.setTargetCurrency("USD");
        SettlementTransactionPageResponse response = new SettlementTransactionPageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(source));
        when(sourceClient.loadSettlementTransactionPage(any())).thenReturn(response);
        when(resolver.resolve(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        ExportArtifact artifact = artifact("merchant-settlement-transaction.xlsx");
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(MerchantSettlementTransactionExportRow.class), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<MerchantSettlementTransactionExportRow>> loader = invocation.getArgument(8);
            ExportPage<MerchantSettlementTransactionExportRow> page = loader.apply(4);
            assertThat(page.records()).singleElement().satisfies(row -> {
                assertThat(row.getSourceAmount()).isEqualByComparingTo("80.12340000");
                assertThat(row.getSourceCurrency()).isEqualTo("EUR");
                assertThat(row.getNetTargetAmount()).isEqualByComparingTo("88.13574000");
                assertThat(row.getTargetCurrency()).isEqualTo("USD");
            });
            return artifact;
        });
        MerchantSettlementTransactionExportProcessor processor =
                new MerchantSettlementTransactionExportProcessor(sourceClient, writer, resolver, properties);

        ExportArtifact result = processor.process(
                task("M100001", new MerchantSettlementTransactionQuery()), storage, listener,
                LocalDateTime.of(2026, 11, 10, 16, 0));

        ArgumentCaptor<SettlementTransactionPageRequest> captor =
                ArgumentCaptor.forClass(SettlementTransactionPageRequest.class);
        verify(sourceClient).loadSettlementTransactionPage(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo("M100001");
        assertThat(captor.getValue().getPageNo()).isEqualTo(4);
        assertThat(captor.getValue().getPageSize()).isEqualTo(113);
        assertThat(result).isSameAs(artifact);
    }

    /**
     * 保证金导出必须保持标签币种和责任余额，不得按结算币种重新换汇或舍入。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepReserveLabelCurrencyAndRemainingAmount() {
        MerchantExportSourceClient sourceClient = mock(MerchantExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExcelI18nMessageResolver resolver = mock(ExcelI18nMessageResolver.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = properties(97);
        SettlementReserveRow source = new SettlementReserveRow();
        source.setCurrency("EUR");
        source.setAmount(new BigDecimal("7.12340000"));
        source.setRemainingAmount(new BigDecimal("5.00340000"));
        SettlementReservePageResponse response = new SettlementReservePageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(source));
        when(sourceClient.loadSettlementReservePage(any())).thenReturn(response);
        when(resolver.resolve(anyString(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        ExportArtifact artifact = artifact("merchant-settlement-reserve.xlsx");
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(MerchantSettlementReserveExportRow.class), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<MerchantSettlementReserveExportRow>> loader = invocation.getArgument(8);
            ExportPage<MerchantSettlementReserveExportRow> page = loader.apply(2);
            assertThat(page.records()).singleElement().satisfies(row -> {
                assertThat(row.getCurrency()).isEqualTo("EUR");
                assertThat(row.getAmount()).isEqualByComparingTo("7.12340000");
                assertThat(row.getRemainingAmount()).isEqualByComparingTo("5.00340000");
            });
            return artifact;
        });
        MerchantSettlementReserveExportProcessor processor =
                new MerchantSettlementReserveExportProcessor(sourceClient, writer, resolver, properties);

        ExportArtifact result = processor.process(
                task("M100001", new MerchantSettlementReserveQuery()), storage, listener,
                LocalDateTime.of(2026, 11, 10, 16, 0));

        ArgumentCaptor<SettlementReservePageRequest> captor =
                ArgumentCaptor.forClass(SettlementReservePageRequest.class);
        verify(sourceClient).loadSettlementReservePage(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo("M100001");
        assertThat(captor.getValue().getPageNo()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(97);
        assertThat(result).isSameAs(artifact);
    }

    /**
     * 余额流水导出必须携带冻结账户，并保持金额及前后余额的十进制原值。
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepFundLedgerAccountScopeAndExactBalances() {
        MerchantExportSourceClient sourceClient = mock(MerchantExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        ExportObjectStorageService storage = mock(ExportObjectStorageService.class);
        ExportProgressListener listener = mock(ExportProgressListener.class);
        DataExportProperties properties = properties(83);
        FundLedgerRow source = new FundLedgerRow();
        source.setLedgerNo("LEDGER-1001");
        source.setCurrency("EUR");
        source.setAmount(new BigDecimal("0.00000001"));
        source.setBalanceBefore(new BigDecimal("100.12000000"));
        source.setBalanceAfter(new BigDecimal("100.12000001"));
        FundLedgerPageResponse response = new FundLedgerPageResponse();
        response.setTotal(1L);
        response.setRecords(List.of(source));
        when(sourceClient.loadFundLedgerPage(any())).thenReturn(response);
        ExportArtifact artifact = artifact("merchant-fund-ledger.xlsx");
        when(writer.write(any(), same(storage), same(listener), any(), anyString(), anyString(), anyString(),
                eq(MerchantFundLedgerExportRow.class), any())).thenAnswer(invocation -> {
            IntFunction<ExportPage<MerchantFundLedgerExportRow>> loader = invocation.getArgument(8);
            ExportPage<MerchantFundLedgerExportRow> page = loader.apply(5);
            assertThat(page.total()).isEqualTo(1L);
            assertThat(page.records()).singleElement().satisfies(row -> {
                assertThat(row.getAmount().toPlainString()).isEqualTo("0.00000001");
                assertThat(row.getBalanceBefore().toPlainString()).isEqualTo("100.12000000");
                assertThat(row.getBalanceAfter().toPlainString()).isEqualTo("100.12000001");
                assertThat(row.getCurrency()).isEqualTo("EUR");
            });
            return artifact;
        });
        MerchantFundLedgerQuery query = new MerchantFundLedgerQuery();
        query.setAccountId(7001L);
        MerchantFundLedgerExportProcessor processor = new MerchantFundLedgerExportProcessor(
                sourceClient, writer, properties);

        ExportArtifact result = processor.process(task("M100001", query), storage, listener,
                LocalDateTime.of(2026, 11, 10, 16, 0));

        ArgumentCaptor<FundLedgerPageRequest> captor = ArgumentCaptor.forClass(FundLedgerPageRequest.class);
        verify(sourceClient).loadFundLedgerPage(captor.capture());
        assertThat(captor.getValue().getMerchantId()).isEqualTo("M100001");
        assertThat(captor.getValue().getQuery().getAccountId()).isEqualTo(7001L);
        assertThat(captor.getValue().getPageNo()).isEqualTo(5);
        assertThat(captor.getValue().getPageSize()).isEqualTo(83);
        assertThat(result).isSameAs(artifact);
    }

    /**
     * 缺少冻结账户主键的余额流水任务必须在调用来源服务前失败关闭。
     */
    @Test
    void shouldRejectFundLedgerTaskWithoutFrozenAccount() {
        MerchantExportSourceClient sourceClient = mock(MerchantExportSourceClient.class);
        MerchantFundLedgerExportProcessor processor = new MerchantFundLedgerExportProcessor(
                sourceClient, mock(ExcelExportArtifactWriter.class), properties(100));

        assertThatThrownBy(() -> processor.process(
                task("M100001", new MerchantFundLedgerQuery()),
                mock(ExportObjectStorageService.class), mock(ExportProgressListener.class), LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scope is invalid");
        verify(sourceClient, never()).loadFundLedgerPage(any());
    }

    /**
     * 结算任务字段与冻结快照商户号不一致时必须在读取来源数据前失败关闭。
     */
    @Test
    void shouldRejectSettlementTaskWhenFrozenMerchantScopeDiffers() {
        MerchantExportSourceClient sourceClient = mock(MerchantExportSourceClient.class);
        DataExportTaskDO task = task("M100001", new MerchantSettlementReserveQuery());
        task.setDataScopeSnapshot(scopeJson("M200002"));
        MerchantSettlementReserveExportProcessor processor = new MerchantSettlementReserveExportProcessor(
                sourceClient, mock(ExcelExportArtifactWriter.class),
                mock(ExcelI18nMessageResolver.class), properties(100));

        assertThatThrownBy(() -> processor.process(task, mock(ExportObjectStorageService.class),
                mock(ExportProgressListener.class), LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scope is invalid");
        verify(sourceClient, never()).loadSettlementReservePage(any());
    }

    /**
     * 交易任务字段与冻结快照商户号不一致时必须在读取来源数据前失败关闭。
     */
    @Test
    void shouldRejectTransactionTaskWhenFrozenMerchantScopeDiffers() {
        MerchantExportSourceClient sourceClient = mock(MerchantExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        DataExportTaskDO task = transactionTask("M100001");
        task.setDataScopeSnapshot(scopeJson("M200002"));
        MerchantTransactionExportProcessor processor = new MerchantTransactionExportProcessor(
                sourceClient, writer, mock(ExcelI18nMessageResolver.class), properties(100));

        assertThatThrownBy(() -> processor.process(task, mock(ExportObjectStorageService.class),
                mock(ExportProgressListener.class), LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scope is invalid");
        verify(sourceClient, never()).loadTransactionPage(any());
    }

    /**
     * 退款任务字段与冻结快照商户号不一致时必须在读取来源数据前失败关闭。
     */
    @Test
    void shouldRejectRefundTaskWhenFrozenMerchantScopeDiffers() {
        MerchantExportSourceClient sourceClient = mock(MerchantExportSourceClient.class);
        ExcelExportArtifactWriter writer = mock(ExcelExportArtifactWriter.class);
        DataExportTaskDO task = refundTask("M100001");
        task.setDataScopeSnapshot(scopeJson("M200002"));
        MerchantRefundExportProcessor processor = new MerchantRefundExportProcessor(
                sourceClient, writer, properties(100));

        assertThatThrownBy(() -> processor.process(task, mock(ExportObjectStorageService.class),
                mock(ExportProgressListener.class), LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scope is invalid");
        verify(sourceClient, never()).loadRefundPage(any());
    }

    /**
     * 构造带商户标签查询和冻结范围的交易导出任务。
     */
    private DataExportTaskDO transactionTask(String merchantId) {
        return task(merchantId, new MerchantTransactionQuery());
    }

    /**
     * 构造带商户标签查询和冻结范围的退款导出任务。
     */
    private DataExportTaskDO refundTask(String merchantId) {
        return task(merchantId, new MerchantRefundQuery());
    }

    /**
     * 构造处理器可解析且明确归属商户的导出任务。
     */
    private DataExportTaskDO task(String merchantId, Object query) {
        DataExportTaskDO task = new DataExportTaskDO();
        task.setTaskNo("EXP-MERCHANT-001");
        task.setTaskName("商户导出");
        task.setCreatorAccountId(88L);
        task.setCreatorAccountName("Merchant Operator");
        task.setMerchantId(merchantId);
        task.setLocale("zh-CN");
        task.setQuerySnapshot(JsonUtils.toJsonString(query));
        task.setDataScopeSnapshot(scopeJson(merchantId));
        return task;
    }

    /**
     * 序列化任务冻结时的商户范围。
     */
    private String scopeJson(String merchantId) {
        MerchantExportScopeSnapshot scope = new MerchantExportScopeSnapshot();
        scope.setMerchantId(merchantId);
        return JsonUtils.toJsonString(scope);
    }

    /**
     * 构造带指定来源分页容量的导出配置。
     */
    private DataExportProperties properties(int pageSize) {
        DataExportProperties properties = new DataExportProperties();
        properties.setPageSize(pageSize);
        return properties;
    }

    /**
     * 构造不依赖真实对象存储的最终文件元数据。
     */
    private ExportArtifact artifact(String fileName) {
        return new ExportArtifact(fileName,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "exports/merchant/2026/11/EXP-MERCHANT-001/" + fileName,
                1024L, "a".repeat(64), 1L, 1L,
                LocalDateTime.of(2026, 11, 17, 16, 0));
    }
}
