package com.scott.payment.admin.application.fee;

import com.scott.payment.admin.dto.export.FeeSimulationExportRow;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationDetailResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordResponse;
import com.scott.payment.admin.service.AdminFeeService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.dictionary.model.DictionaryOptionSnapshot;
import com.scott.payment.component.db.dictionary.service.DictionaryOptionCacheReader;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 管理端费用应用服务导出编排测试。 */
class AdminFeeApplicationServiceTests {

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    /** 试算记录导出必须按逐项明细展开，并使用数据字典标签及统一空值口径。 */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldExportLocalizedSimulationDetailRows() {
        AdminFeeService feeService = mock(AdminFeeService.class);
        ExcelExportService exportService = mock(ExcelExportService.class);
        ExcelI18nMessageResolver messageResolver = mock(ExcelI18nMessageResolver.class);
        ExcelLocaleResolver localeResolver = mock(ExcelLocaleResolver.class);
        DictionaryOptionCacheReader dictionaryReader = mock(DictionaryOptionCacheReader.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Locale locale = Locale.SIMPLIFIED_CHINESE;

        when(localeResolver.resolveCurrentLocale()).thenReturn(locale);
        when(messageResolver.resolve(anyString(), eq(locale)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dictionaryReader.findEnabled("transaction_type", locale.toLanguageTag()))
                .thenReturn(List.of(option("PAYMENT", "支付")));
        when(dictionaryReader.findEnabled("acquiring_payment_method", locale.toLanguageTag()))
                .thenReturn(List.of(option("BANK_CARD", "银行卡")));
        when(dictionaryReader.findEnabled("card_brand", locale.toLanguageTag()))
                .thenReturn(List.of(option("VISA", "Visa")));

        FeeSimulationRecordResponse source = simulationRecord();
        when(feeService.pageSimulationRecords(any(FeeSimulationRecordQuery.class)))
                .thenReturn(PageResult.of(1, 1, 200, List.of(source)));
        AtomicReference<ExcelPagedExportRequest<FeeSimulationExportRow>> captured = new AtomicReference<>();
        doAnswer(invocation -> {
            captured.set((ExcelPagedExportRequest<FeeSimulationExportRow>) invocation.getArgument(0));
            return null;
        }).when(exportService).exportPaged(any(ExcelPagedExportRequest.class), eq(response));
        setOperator();

        AdminFeeApplicationService service = new AdminFeeApplicationService(
                feeService, exportService, messageResolver, localeResolver, dictionaryReader);
        service.exportSimulationRecords(new FeeSimulationRecordQuery(), response);

        List<FeeSimulationExportRow> rows = captured.get().getPageLoader().apply(1);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getTransactionType()).isEqualTo("支付");
        assertThat(rows.get(0).getPaymentType()).isEqualTo("银行卡");
        assertThat(rows.get(0).getPaymentMethod()).isEqualTo("Visa");
        assertThat(rows.get(0).getSelectedRiskServices())
                .isEqualTo("excel.fee.risk.INTERNAL, excel.fee.risk.THREE_DS");
        assertThat(rows.get(0).getAppliedLimit()).isEqualTo("-");
        assertThat(rows.get(0).getDetailFeeUsd()).isEqualByComparingTo("3.20");
        assertThat(rows.get(1).getItemType()).isEqualTo("excel.fee.itemType.RESERVE");
        assertThat(rows.get(1).getIncludedInFeeTotal()).isEqualTo("excel.common.no");
        assertThat(rows.get(1).getDetailFeeUsd()).isEqualByComparingTo("10.00");
        assertThat(rows.get(1).getNetSettlementFormula())
                .isEqualTo("USD 100 - USD 3.20 - USD 10 = USD 86.80");
    }

    private static FeeSimulationRecordResponse simulationRecord() {
        FeeSimulationRecordResponse source = new FeeSimulationRecordResponse();
        source.setSimulationNo("FS202608240001");
        source.setMerchantId("M10001");
        source.setPlanVersionId(21L);
        source.setTransactionType("PAYMENT");
        source.setPaymentType("BANK_CARD");
        source.setPaymentMethod("VISA");
        source.setRiskServiceTypes(List.of("INTERNAL", "THREE_DS"));
        source.setLabelAmount(new BigDecimal("100"));
        source.setLabelCurrency("USD");
        source.setLabelToUsdRate(BigDecimal.ONE);
        source.setFinalFeeUsd(new BigDecimal("3.20"));
        source.setReserveAmountUsd(new BigDecimal("10.00"));
        source.setEstimatedNetSettlementUsd(new BigDecimal("86.80"));
        source.setFormulaSnapshot("USD 3.20 = USD 3.20");
        source.setNetSettlementFormulaSnapshot("USD 100 - USD 3.20 - USD 10 = USD 86.80");
        source.setSettlementRateSource("SYSTEM_IDENTITY");
        source.setDetailSnapshotStatus("COMPLETE");
        source.setOperatorName("试算人");
        source.setCreateTime(LocalDateTime.of(2026, 8, 24, 10, 30));
        source.setFeeDetails(List.of(feeDetail(), reserveDetail()));
        return source;
    }

    private static FeeSimulationDetailResponse feeDetail() {
        FeeSimulationDetailResponse detail = new FeeSimulationDetailResponse();
        detail.setLineNo(1);
        detail.setItemType("FEE");
        detail.setFeeCategory("TRANSACTION_FEE");
        detail.setRiskServiceType("NONE");
        detail.setCalculationStatus("CALCULATED");
        detail.setIncludedInFeeTotal(true);
        detail.setChargeTrigger("NOT_APPLICABLE");
        detail.setRuleName("银行卡交易手续费");
        detail.setFeeMode("STANDARD");
        detail.setRawFeeUsd(new BigDecimal("3.20"));
        detail.setFinalFeeUsd(new BigDecimal("3.20"));
        detail.setAppliedLimit("NONE");
        detail.setFormulaSnapshot("USD 100 * 3.2% = USD 3.20");
        return detail;
    }

    private static FeeSimulationDetailResponse reserveDetail() {
        FeeSimulationDetailResponse detail = new FeeSimulationDetailResponse();
        detail.setLineNo(2);
        detail.setItemType("RESERVE");
        detail.setFeeCategory("RESERVE");
        detail.setRiskServiceType("NONE");
        detail.setCalculationStatus("CALCULATED");
        detail.setIncludedInFeeTotal(false);
        detail.setChargeTrigger("NOT_APPLICABLE");
        detail.setRuleName("滚动保证金");
        detail.setFeeMode("STANDARD");
        detail.setRawFeeUsd(new BigDecimal("10.00"));
        detail.setFinalFeeUsd(new BigDecimal("10.00"));
        detail.setAppliedLimit("NONE");
        detail.setFormulaSnapshot("USD 100 * 10% = USD 10");
        return detail;
    }

    private static DictionaryOptionSnapshot option(String value, String label) {
        DictionaryOptionSnapshot option = new DictionaryOptionSnapshot();
        option.setDictValue(value);
        option.setDictLabel(label);
        return option;
    }

    private static void setOperator() {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAccountId(8L);
        account.setLoginAccount("fee.operator");
        account.setRealName("试算人");
        InternalAuthContextHolder.set(account);
    }
}
