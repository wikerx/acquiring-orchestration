package com.scott.payment.admin.application.security;

import com.scott.payment.admin.dto.export.SecurityInterceptEventExportRow;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventMarkRequest;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventQuery;
import com.scott.payment.admin.dto.security.SecurityInterceptEventDTOs.SecurityInterceptEventResponse;
import com.scott.payment.admin.service.AdminSecurityInterceptEventService;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSecurityInterceptEventApplicationService
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : admin安全interceptevent应用服务，位于 运营后台服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@Service
public class AdminSecurityInterceptEventApplicationService {

    /**
     * {@code EXPORT_TIME_FORMATTER}常量，统一 {@code AdminSecurityInterceptEventApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminSecurityInterceptEventService eventService;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建安全拦截事件后台应用服务。
     *
     * @param eventService             安全事件服务
     * @param excelExportService       Excel 导出服务
     * @param excelI18nMessageResolver Excel 文案解析器
     * @param excelLocaleResolver      Excel 语言解析器
     */
    public AdminSecurityInterceptEventApplicationService(AdminSecurityInterceptEventService eventService,
                                                         ExcelExportService excelExportService,
                                                         ExcelI18nMessageResolver excelI18nMessageResolver,
                                                         ExcelLocaleResolver excelLocaleResolver) {
        this.eventService = eventService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 分页查询安全拦截事件。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public PageResult<SecurityInterceptEventResponse> pageEvents(SecurityInterceptEventQuery query) {
        return eventService.pageEvents(query);
    }

    /**
     * 查询安全拦截事件详情。
     *
     * @param id 事件主键
     * @return 事件详情
     */
    public SecurityInterceptEventResponse getEvent(Long id) {
        return eventService.getEvent(id);
    }

    /**
     * 标记安全拦截事件处理状态。
     *
     * @param id      事件主键
     * @param request 处理请求
     * @return 更新后的事件详情
     */
    public SecurityInterceptEventResponse markEvent(Long id, SecurityInterceptEventMarkRequest request) {
        return eventService.markEvent(id, request);
    }

    /**
     * 按当前查询条件导出安全拦截事件。
     *
     * @param query    查询条件
     * @param operator 操作人
     * @param response HTTP 响应
     */
    public void exportEvents(SecurityInterceptEventQuery query, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<SecurityInterceptEventExportRow> rows = eventService.listEvents(query).stream()
                .map(row -> toExportRow(row, locale))
                .toList();
        LocalDateTime now = LocalDateTime.now();
        String titleKey = "excel.securityIntercept.title";
        String title = excelI18nMessageResolver.resolve(titleKey, locale);
        excelExportService.export(
                ExcelExportRequest.<SecurityInterceptEventExportRow>builder()
                        .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                        .sheetName(title)
                        .titleKey(titleKey)
                        .operator(operator)
                        .exportTime(now)
                        .locale(locale)
                        .querySummary(buildQuerySummary(query, locale))
                        .rowClass(SecurityInterceptEventExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    private SecurityInterceptEventExportRow toExportRow(SecurityInterceptEventResponse source, Locale locale) {
        SecurityInterceptEventExportRow row = new SecurityInterceptEventExportRow();
        row.setEventNo(blankToPlaceholder(source.getEventNo()));
        row.setEventTime(source.getEventTime());
        row.setSourceLayer(blankToPlaceholder(source.getSourceLayer()));
        row.setEventType(blankToPlaceholder(source.getEventType()));
        row.setRiskLevel(blankToPlaceholder(source.getRiskLevel()));
        row.setAction(blankToPlaceholder(source.getAction()));
        row.setMerchantId(blankToPlaceholder(source.getMerchantId()));
        row.setClientIp(blankToPlaceholder(source.getClientIp()));
        row.setRequestPath(blankToPlaceholder(source.getRequestPath()));
        row.setTraceId(blankToPlaceholder(source.getTraceId()));
        row.setHitRuleCode(blankToPlaceholder(source.getHitRuleCode()));
        row.setReasonCode(blankToPlaceholder(source.getReasonCode()));
        row.setReasonMessage(blankToPlaceholder(source.getReasonMessage()));
        row.setProcessStatus(resolveProcessStatusText(source.getProcessStatus(), locale));
        row.setProcessedBy(blankToPlaceholder(source.getProcessedBy()));
        row.setProcessedTime(source.getProcessedTime());
        return row;
    }

    private String buildQuerySummary(SecurityInterceptEventQuery query, Locale locale) {
        SecurityInterceptEventQuery condition = query == null ? new SecurityInterceptEventQuery() : query;
        List<String> conditions = new ArrayList<>();
        addCondition(conditions, "excel.securityIntercept.eventNo", condition.getEventNo(), locale);
        addCondition(conditions, "excel.securityIntercept.sourceLayer", condition.getSourceLayer(), locale);
        addCondition(conditions, "excel.securityIntercept.eventType", condition.getEventType(), locale);
        addCondition(conditions, "excel.securityIntercept.riskLevel", condition.getRiskLevel(), locale);
        addCondition(conditions, "excel.securityIntercept.merchantId", condition.getMerchantId(), locale);
        addCondition(conditions, "excel.securityIntercept.clientIp", condition.getClientIp(), locale);
        addCondition(conditions, "excel.securityIntercept.traceId", condition.getTraceId(), locale);
        addCondition(conditions, "excel.securityIntercept.processStatus", resolveNullableProcessStatusText(condition.getProcessStatus(), locale), locale);
        addCondition(conditions, "excel.securityIntercept.queryTimeZone", condition.getQueryTimeZone(), locale);
        addCondition(conditions, "excel.securityIntercept.beginTime", condition.getBeginTime(), locale);
        addCondition(conditions, "excel.securityIntercept.endTime", condition.getEndTime(), locale);
        if (conditions.isEmpty()) {
            return excelI18nMessageResolver.resolve("excel.common.noCondition", locale);
        }
        return String.join(locale.getLanguage().equals(Locale.CHINESE.getLanguage()) ? "，" : ", ", conditions);
    }

    private void addCondition(List<String> conditions, String labelKey, Object value, Locale locale) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (!StringUtils.hasText(text)) {
            return;
        }
        conditions.add(excelI18nMessageResolver.resolve(labelKey, locale) + "=" + text.trim());
    }

    private String resolveNullableProcessStatusText(Integer status, Locale locale) {
        return status == null ? null : resolveProcessStatusText(status, locale);
    }

    private String resolveProcessStatusText(Integer status, Locale locale) {
        String key = switch (status == null ? 0 : status) {
            case 1 -> "excel.securityIntercept.processed";
            case 2 -> "excel.securityIntercept.ignored";
            default -> "excel.securityIntercept.unhandled";
        };
        return excelI18nMessageResolver.resolve(key, locale);
    }

    private String blankToPlaceholder(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
