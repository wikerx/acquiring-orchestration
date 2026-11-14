package com.scott.payment.admin.application.risk;

import com.scott.payment.admin.mapper.AdminTradeBlackWorkflowMapper;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.excel.model.ExcelDynamicExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 系统交易加黑 Excel 模板编排测试。 */
class AdminTradeBlackWorkbookApplicationServiceTest {

    /** 清理线程认证上下文。 */
    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    /**
     * 中文模板必须使用统一 Excel 布局和国际化表头，并保留两种订单号示例。
     */
    @Test
    void shouldBuildLocalizedXlsxTemplateRequest() {
        ExcelExportService excelExportService = mock(ExcelExportService.class);
        ExcelI18nMessageResolver messageResolver = mock(ExcelI18nMessageResolver.class);
        ExcelLocaleResolver localeResolver = mock(ExcelLocaleResolver.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Locale locale = Locale.SIMPLIFIED_CHINESE;
        when(localeResolver.resolveCurrentLocale()).thenReturn(locale);
        Map<String, String> messages = Map.of(
                "excel.tradeBlack.template.title", "系统交易加黑导入模板",
                "excel.tradeBlack.template.hint", "请删除示例行后再导入",
                "excel.tradeBlack.identifierType", "订单号类型",
                "excel.tradeBlack.lookupMerchantId", "查询来源商户号（商户订单号时必填）",
                "excel.tradeBlack.orderNo", "订单号",
                "excel.tradeBlack.identifier.system", "系统订单号",
                "excel.tradeBlack.identifier.merchant", "商户订单号"
        );
        when(messageResolver.resolve(any(), eq(locale)))
                .thenAnswer(invocation -> messages.get(invocation.getArgument(0)));
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAccountId(88L);
        account.setLoginAccount("scott");
        InternalAuthContextHolder.set(account);
        AdminTradeBlackWorkbookApplicationService service =
                new AdminTradeBlackWorkbookApplicationService(
                        mock(AdminTradeBlackWorkflowMapper.class), excelExportService,
                        messageResolver, localeResolver);

        service.downloadTemplate(response);

        ArgumentCaptor<ExcelDynamicExportRequest> captor =
                ArgumentCaptor.forClass(ExcelDynamicExportRequest.class);
        verify(excelExportService).exportDynamic(captor.capture(), eq(response));
        ExcelDynamicExportRequest request = captor.getValue();
        assertThat(request.getFileName()).startsWith("系统交易加黑导入模板_");
        assertThat(request.getSheetName()).isEqualTo("系统交易加黑导入模板");
        assertThat(request.getTitle()).isEqualTo("系统交易加黑导入模板");
        assertThat(request.getQuerySummary()).isEqualTo("请删除示例行后再导入");
        assertThat(request.getColumns())
                .extracting(column -> column.header())
                .containsExactly("订单号类型", "查询来源商户号（商户订单号时必填）", "订单号");
        assertThat(request.getColumns()).allSatisfy(column -> assertThat(column.forceText()).isTrue());
        assertThat(request.getDataList()).hasSize(52);
        assertThat(request.getDataList().get(0))
                .containsEntry("identifierType", "系统订单号")
                .containsEntry("lookupMerchantId", "")
                .containsEntry("orderNo", "SYSTEM_ORDER_EXAMPLE");
        assertThat(request.getDataList().get(1))
                .containsEntry("identifierType", "商户订单号")
                .containsEntry("lookupMerchantId", "M20260001")
                .containsEntry("orderNo", "MERCHANT_ORDER_EXAMPLE");
        assertThat(request.getDataList().subList(2, request.getDataList().size()))
                .allSatisfy(row -> assertThat(row.values()).containsOnly(""));
    }
}
