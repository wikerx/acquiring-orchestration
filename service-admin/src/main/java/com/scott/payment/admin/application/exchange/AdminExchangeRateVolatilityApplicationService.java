package com.scott.payment.admin.application.exchange;

import com.alibaba.excel.EasyExcel;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEvaluationRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEvaluationResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventActionRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleImportError;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleImportResult;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.TrendSeries;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityOverview;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityQuery;
import com.scott.payment.admin.dto.export.ExchangeRateAlertEventExportRow;
import com.scott.payment.admin.dto.export.ExchangeRateAlertRuleExportRow;
import com.scott.payment.admin.dto.export.ExchangeRateVolatilityExportRow;
import com.scott.payment.admin.service.AdminExchangeRateVolatilityService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 管理端汇率波动和预警应用服务，负责导入导出及领域服务编排。 */
@Service
public class AdminExchangeRateVolatilityApplicationService {

    private static final long MAX_IMPORT_BYTES = 2L * 1024L * 1024L;
    private static final int IMPORT_COLUMN_COUNT = 11;
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminExchangeRateVolatilityService volatilityService;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /** 创建汇率波动预警应用服务。 */
    public AdminExchangeRateVolatilityApplicationService(AdminExchangeRateVolatilityService volatilityService,
                                                         ExcelExportService excelExportService,
                                                         ExcelI18nMessageResolver excelI18nMessageResolver,
                                                         ExcelLocaleResolver excelLocaleResolver) {
        this.volatilityService = volatilityService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /** 查询跨来源聚合后的币种对波动摘要。 */
    public List<VolatilityOverview> overview(VolatilityQuery request) {
        return volatilityService.overview(request);
    }

    /** 查询保留来源证据的趋势序列，前端展示值不反向参与业务判断。 */
    public List<TrendSeries> trend(VolatilityQuery request) {
        return volatilityService.trend(request);
    }

    /** 分页编排规则查询，规则约束仍由领域服务统一执行。 */
    public PageResult<AlertRuleResponse> pageRules(AlertRuleQuery request) {
        return volatilityService.pageRules(request);
    }

    /** 获取规则详情并保留系统强制属性。 */
    public AlertRuleResponse getRule(Long id) {
        return volatilityService.getRule(id);
    }

    /** 创建自定义规则，系统基线只能通过受控迁移提供。 */
    public AlertRuleResponse createRule(AlertRuleSaveRequest request) {
        return volatilityService.createRule(request);
    }

    /** 更新规则并由领域服务保护系统基线不被放宽。 */
    public AlertRuleResponse updateRule(Long id, AlertRuleSaveRequest request) {
        return volatilityService.updateRule(id, request);
    }

    /** 删除自定义规则；系统强制规则由领域服务拒绝。 */
    public void deleteRule(Long id) {
        volatilityService.deleteRule(id);
    }

    /** 切换规则启停状态并保护系统强制规则持续生效。 */
    public AlertRuleResponse changeRuleStatus(Long id, boolean enabled) {
        return volatilityService.changeRuleStatus(id, enabled);
    }

    /** 分页查询预警事件及其冻结证据。 */
    public PageResult<AlertEventResponse> pageEvents(AlertEventQuery request) {
        return volatilityService.pageEvents(request);
    }

    /** 获取单个事件的完整生命周期和版本信息。 */
    public AlertEventResponse getEvent(Long id) {
        return volatilityService.getEvent(id);
    }

    /** 以 CAS 版本确认 OPEN 事件并记录操作人备注。 */
    public AlertEventResponse acknowledgeEvent(Long id, AlertEventActionRequest request) {
        return volatilityService.acknowledgeEvent(id, request);
    }

    /** 以 CAS 版本关闭已恢复事件，禁止关闭仍活动的异常。 */
    public AlertEventResponse closeEvent(Long id, AlertEventActionRequest request) {
        return volatilityService.closeEvent(id, request);
    }

    /** 将内部调度命令交给领域服务执行幂等评估。 */
    public AlertEvaluationResponse evaluate(AlertEvaluationRequest request) {
        return volatilityService.evaluate(request);
    }

    /** 导出当前趋势筛选下的波动摘要。 */
    public void exportVolatility(VolatilityQuery request, String operator, HttpServletResponse response) {
        List<ExchangeRateVolatilityExportRow> rows = volatilityService.overview(request).stream()
                .map(this::toVolatilityExportRow).toList();
        export("excel.exchange.volatility.title", rows, ExchangeRateVolatilityExportRow.class, operator, response);
    }

    /** 导出当前筛选下的规则配置。 */
    public void exportRules(AlertRuleQuery request, String operator, HttpServletResponse response) {
        List<ExchangeRateAlertRuleExportRow> rows = volatilityService.listRules(request).stream()
                .map(this::toRuleExportRow).toList();
        export("excel.exchange.alertRule.title", rows, ExchangeRateAlertRuleExportRow.class, operator, response);
    }

    /** 导出当前筛选下的不可变预警事件事实。 */
    public void exportEvents(AlertEventQuery request, String operator, HttpServletResponse response) {
        List<ExchangeRateAlertEventExportRow> rows = volatilityService.listEvents(request).stream()
                .map(this::toEventExportRow).toList();
        export("excel.exchange.alertEvent.title", rows, ExchangeRateAlertEventExportRow.class, operator, response);
    }

    /**
     * 从固定列顺序的 Excel 文件读取自定义规则；任一行失败时领域服务不会写入任何行。
     */
    public AlertRuleImportResult importRules(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw invalid("import file is required");
        }
        if (file.getSize() > MAX_IMPORT_BYTES) {
            throw invalid("import file size must not exceed 2 MB");
        }
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)
                || !(fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || fileName.toLowerCase(Locale.ROOT).endsWith(".xls"))) {
            throw invalid("import file must be an xlsx or xls workbook");
        }
        List<Map<Integer, String>> rows;
        try {
            rows = EasyExcel.read(file.getInputStream()).headRowNumber(1).sheet().doReadSync();
        } catch (IOException | RuntimeException exception) {
            throw invalid("import workbook cannot be parsed");
        }

        List<AlertRuleSaveRequest> requests = new ArrayList<>();
        List<AlertRuleImportError> parseErrors = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Map<Integer, String> row = rows.get(index);
            if (row == null || row.values().stream().allMatch(value -> !StringUtils.hasText(value))) {
                continue;
            }
            if (row.size() < IMPORT_COLUMN_COUNT) {
                AlertRuleImportError error = new AlertRuleImportError();
                error.setRowNumber(index + 2);
                error.setMessage("import row must contain 11 columns");
                parseErrors.add(error);
                continue;
            }
            try {
                requests.add(toImportRequest(row));
            } catch (RuntimeException exception) {
                AlertRuleImportError error = new AlertRuleImportError();
                error.setRowNumber(index + 2);
                error.setMessage("window, recovery count and sample count must be integers");
                parseErrors.add(error);
            }
        }
        if (!parseErrors.isEmpty()) {
            AlertRuleImportResult result = new AlertRuleImportResult();
            result.setTotalCount(rows.size());
            result.setSuccessCount(0);
            result.setFailureCount(parseErrors.size());
            result.setErrors(parseErrors);
            return result;
        }
        return volatilityService.importRules(requests);
    }

    /** Excel 导入列顺序：名称、类型、原币、目标币、窗口、触发、恢复、恢复次数、样本数、等级、备注。 */
    private AlertRuleSaveRequest toImportRequest(Map<Integer, String> row) {
        AlertRuleSaveRequest request = new AlertRuleSaveRequest();
        request.setRuleName(value(row, 0));
        request.setRateType(value(row, 1));
        request.setBaseCurrency(value(row, 2));
        request.setQuoteCurrency(value(row, 3));
        request.setWindowMinutes(Integer.valueOf(value(row, 4)));
        request.setTriggerThreshold(value(row, 5));
        request.setRecoveryThreshold(value(row, 6));
        request.setRecoveryConsecutiveCount(Integer.valueOf(value(row, 7)));
        request.setMinimumSampleCount(Integer.valueOf(value(row, 8)));
        request.setAlertLevel(value(row, 9));
        request.setRemark(value(row, 10));
        return request;
    }

    /** 统一输出国际化 Excel。 */
    private <T> void export(String titleKey, List<T> rows, Class<T> rowClass,
                            String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        String title = excelI18nMessageResolver.resolve(titleKey, locale);
        excelExportService.export(ExcelExportRequest.<T>builder()
                .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                .sheetName(title)
                .titleKey(titleKey)
                .operator(operator)
                .exportTime(LocalDateTime.now())
                .locale(locale)
                .querySummary(excelI18nMessageResolver.resolve("excel.common.noCondition", locale))
                .rowClass(rowClass)
                .dataList(rows)
                .build(), response);
    }

    private ExchangeRateVolatilityExportRow toVolatilityExportRow(VolatilityOverview source) {
        ExchangeRateVolatilityExportRow row = new ExchangeRateVolatilityExportRow();
        row.setRateType(source.getRateType());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setLatestRate(source.getLatestRate());
        row.setMinimumRate(source.getMinimumRate());
        row.setMaximumRate(source.getMaximumRate());
        row.setVolatilityRate(source.getVolatilityRate());
        row.setSampleCount(source.getSampleCount());
        row.setLatestEffectiveTime(source.getLatestEffectiveTime());
        row.setAlertStatus(source.getAlertStatus());
        return row;
    }

    private ExchangeRateAlertRuleExportRow toRuleExportRow(AlertRuleResponse source) {
        ExchangeRateAlertRuleExportRow row = new ExchangeRateAlertRuleExportRow();
        row.setRuleCode(source.getRuleCode());
        row.setRuleName(source.getRuleName());
        row.setRateType(source.getRateType());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setWindowMinutes(source.getWindowMinutes());
        row.setTriggerThreshold(source.getTriggerThreshold());
        row.setRecoveryThreshold(source.getRecoveryThreshold());
        row.setRecoveryConsecutiveCount(source.getRecoveryConsecutiveCount());
        row.setMinimumSampleCount(source.getMinimumSampleCount());
        row.setAlertLevel(source.getAlertLevel());
        row.setSystemMandatory(Integer.valueOf(1).equals(source.getSystemMandatory()) ? "YES" : "NO");
        row.setRuleStatus(Integer.valueOf(1).equals(source.getRuleStatus()) ? "ENABLED" : "DISABLED");
        row.setRemark(source.getRemark());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    private ExchangeRateAlertEventExportRow toEventExportRow(AlertEventResponse source) {
        ExchangeRateAlertEventExportRow row = new ExchangeRateAlertEventExportRow();
        row.setEventNo(source.getEventNo());
        row.setRuleCode(source.getRuleCode());
        row.setRateType(source.getRateType());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setAlertLevel(source.getAlertLevel());
        row.setEventStatus(source.getEventStatus());
        row.setVolatilityRate(source.getVolatilityRate());
        row.setMaximumVolatilityRate(source.getMaximumVolatilityRate());
        row.setTriggerThreshold(source.getTriggerThreshold());
        row.setMinimumRate(source.getMinimumRate());
        row.setMinimumRateSource(source.getMinimumRateSource());
        row.setMaximumRate(source.getMaximumRate());
        row.setMaximumRateSource(source.getMaximumRateSource());
        row.setLatestRate(source.getLatestRate());
        row.setLatestRateSource(source.getLatestRateSource());
        row.setSampleCount(source.getSampleCount());
        row.setFirstTriggerTime(source.getFirstTriggerTime());
        row.setLastDetectedTime(source.getLastDetectedTime());
        row.setAcknowledgedBy(source.getAcknowledgedBy());
        row.setResolvedTime(source.getResolvedTime());
        return row;
    }

    private String value(Map<Integer, String> row, int index) {
        String value = row.get(index);
        return value == null ? "" : value.trim();
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
