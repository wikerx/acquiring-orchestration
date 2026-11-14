package com.scott.payment.admin.application.risk;

import com.scott.payment.admin.mapper.AdminTradeBlackWorkflowMapper;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.excel.model.ExcelDynamicExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelDynamicColumnDefinition;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 系统交易加黑 Excel 用例服务，统一生成国际化模板和失败明细。
 *
 * <p>模板只包含交易定位字段；失败明细只包含脱敏展示值，禁止导出名单哈希或敏感明文。</p>
 */
@Service
public class AdminTradeBlackWorkbookApplicationService {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 预置文本格式空白行，防止超长纯数字订单号被 Excel 转换为科学计数法。 */
    private static final int TEMPLATE_TEXT_ROWS = 50;

    private final AdminTradeBlackWorkflowMapper workflowMapper;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver messageResolver;
    private final ExcelLocaleResolver localeResolver;

    /** 创建系统交易加黑 Excel 用例服务。 */
    public AdminTradeBlackWorkbookApplicationService(AdminTradeBlackWorkflowMapper workflowMapper,
                                                      ExcelExportService excelExportService,
                                                      ExcelI18nMessageResolver messageResolver,
                                                      ExcelLocaleResolver localeResolver) {
        this.workflowMapper = workflowMapper;
        this.excelExportService = excelExportService;
        this.messageResolver = messageResolver;
        this.localeResolver = localeResolver;
    }

    /** 下载交易定位导入模板。 */
    public void downloadTemplate(HttpServletResponse response) {
        Locale locale = localeResolver.resolveCurrentLocale();
        excelExportService.exportDynamic(templateRequest(locale, operatorName(currentAccount())), response);
    }

    /** 为模板中心生成系统交易加黑的内置模板正文。 */
    public byte[] builtinTemplateContent(Locale locale) {
        Locale targetLocale = locale == null ? Locale.SIMPLIFIED_CHINESE : locale;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        excelExportService.exportDynamic(templateRequest(targetLocale, "SYSTEM"), output);
        return output.toByteArray();
    }

    /** 构建系统交易加黑的统一模板请求。 */
    private ExcelDynamicExportRequest templateRequest(Locale locale, String operator) {
        String title = message("excel.tradeBlack.template.title", locale);
        List<ExcelDynamicColumnDefinition> columns = List.of(
                textColumn("identifierType", "excel.tradeBlack.identifierType", 24, locale),
                textColumn("lookupMerchantId", "excel.tradeBlack.lookupMerchantId", 34, locale),
                textColumn("orderNo", "excel.tradeBlack.orderNo", 34, locale)
        );
        Map<String, Object> systemOrder = new LinkedHashMap<>();
        systemOrder.put("identifierType", message("excel.tradeBlack.identifier.system", locale));
        systemOrder.put("lookupMerchantId", "");
        systemOrder.put("orderNo", "SYSTEM_ORDER_EXAMPLE");
        Map<String, Object> merchantOrder = new LinkedHashMap<>();
        merchantOrder.put("identifierType", message("excel.tradeBlack.identifier.merchant", locale));
        merchantOrder.put("lookupMerchantId", "M20260001");
        merchantOrder.put("orderNo", "MERCHANT_ORDER_EXAMPLE");
        List<Map<String, Object>> rows = new ArrayList<>(TEMPLATE_TEXT_ROWS + 2);
        rows.add(systemOrder);
        rows.add(merchantOrder);
        for (int index = 0; index < TEMPLATE_TEXT_ROWS; index++) {
            rows.add(Map.of("identifierType", "", "lookupMerchantId", "", "orderNo", ""));
        }
        return ExcelDynamicExportRequest.builder()
                .fileName(title + "_" + FILE_TIME.format(LocalDateTime.now()))
                .sheetName(title)
                .title(title)
                .operator(operator)
                .exportTime(LocalDateTime.now())
                .locale(locale)
                .querySummary(message("excel.tradeBlack.template.hint", locale))
                .columns(columns)
                .dataList(rows)
                .build();
    }

    /** 下载当前账号拥有的批次失败明细。 */
    public void downloadFailureReport(String batchNo, HttpServletResponse response) {
        String normalizedBatchNo = StringUtils.hasText(batchNo) ? batchNo.trim() : null;
        if (!StringUtils.hasText(normalizedBatchNo)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "批次号不能为空");
        }
        InternalAuthAccount account = currentAccount();
        if (workflowMapper.countBatchForAccount(normalizedBatchNo, account.getAccountId()) == 0) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND);
        }
        Locale locale = localeResolver.resolveCurrentLocale();
        List<Map<String, Object>> rows = workflowMapper.selectFailedItems(
                        normalizedBatchNo, account.getAccountId()).stream()
                .map(row -> localizeFailureRow(row, locale))
                .toList();
        String title = message("excel.tradeBlack.failure.title", locale);
        List<ExcelDynamicColumnDefinition> columns = List.of(
                column("row_no", "excel.tradeBlack.failure.rowNo", 10, locale),
                column("input_order_no", "excel.tradeBlack.failure.orderNo", 28, locale),
                column("function_name", "excel.tradeBlack.failure.function", 24, locale),
                column("source_field", "excel.tradeBlack.failure.sourceField", 28, locale),
                column("target_scope_label", "excel.tradeBlack.failure.targetScope", 18, locale),
                column("target_merchant_id", "excel.tradeBlack.failure.targetMerchantId", 20, locale),
                column("match_value_masked", "excel.tradeBlack.failure.maskedValue", 28, locale),
                column("result_label", "excel.tradeBlack.failure.result", 16, locale),
                column("result_message", "excel.tradeBlack.failure.message", 36, locale)
        );
        write(title + "_" + normalizedBatchNo,
                message("excel.tradeBlack.failure.summary", locale) + ": " + normalizedBatchNo,
                columns, rows, response, locale);
    }

    /** 将失败行的固定枚举转换为任务语言文案。 */
    private Map<String, Object> localizeFailureRow(Map<String, Object> source, Locale locale) {
        Map<String, Object> target = new LinkedHashMap<>(source);
        String functionCode = stringValue(source.get("function_code"));
        target.put("function_name", message("excel.tradeBlack.function." + functionCode, locale));
        String scope = stringValue(source.get("target_scope"));
        target.put("target_scope_label", message("excel.tradeBlack.scope." + scope, locale));
        String resultCode = stringValue(source.get("result_code"));
        target.put("result_label", message("excel.tradeBlack.result." + resultCode, locale));
        return target;
    }

    /** 使用统一标题、元信息、表头和样式写入 Excel。 */
    private void write(String title,
                       String summary,
                       List<ExcelDynamicColumnDefinition> columns,
                       List<Map<String, Object>> rows,
                       HttpServletResponse response,
                       Locale locale) {
        excelExportService.exportDynamic(
                ExcelDynamicExportRequest.builder()
                        .fileName(title + "_" + FILE_TIME.format(LocalDateTime.now()))
                        .sheetName(title)
                        .title(title)
                        .operator(operatorName(currentAccount()))
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(summary)
                        .columns(columns)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /** 创建左对齐的动态 Excel 列。 */
    private ExcelDynamicColumnDefinition column(String key, String headerKey, int width, Locale locale) {
        return new ExcelDynamicColumnDefinition(
                key, message(headerKey, locale), width, HorizontalAlignment.LEFT);
    }

    /** 创建强制文本格式的模板列，保护超长纯数字订单号和商户号。 */
    private ExcelDynamicColumnDefinition textColumn(String key, String headerKey, int width, Locale locale) {
        return new ExcelDynamicColumnDefinition(
                key, message(headerKey, locale), width, HorizontalAlignment.LEFT, true);
    }

    private String message(String key, Locale locale) {
        return messageResolver.resolve(key, locale);
    }

    private InternalAuthAccount currentAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return account;
    }

    private String operatorName(InternalAuthAccount account) {
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        if (StringUtils.hasText(account.getLoginAccount())) {
            return account.getLoginAccount();
        }
        return String.valueOf(account.getAccountId());
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
