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
 * @description : 安全拦截事件后台应用服务，位于 service-admin 应用层，承接接口并编排分页、详情、人工处理和导出。
 * @status : create
 */
@Service
public class AdminSecurityInterceptEventApplicationService {

    /**
     * EXPORT TIME FORMATTER 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * event Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminSecurityInterceptEventService eventService;
    /**
     * excel Export Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExcelExportService excelExportService;
    /**
     * excel I18n Message Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    /**
     * excel Locale Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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

    /**
     * 编排 to Export Row 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminSecurityInterceptEventApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
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

    /**
     * 编排 build Query Summary 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminSecurityInterceptEventApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
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

    /**
     * 编排 add Condition 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminSecurityInterceptEventApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param conditions conditions 输入值，含义由调用方法名称和所属业务对象限定
     * @param labelKey label Key 输入值，含义由调用方法名称和所属业务对象限定
     * @param value 待校验或转换的原始值
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     */
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

    /**
     * 编排 resolve Nullable Process Status Text 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminSecurityInterceptEventApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolveNullableProcessStatusText(Integer status, Locale locale) {
        return status == null ? null : resolveProcessStatusText(status, locale);
    }

    /**
     * 编排 resolve Process Status Text 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminSecurityInterceptEventApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolveProcessStatusText(Integer status, Locale locale) {
        String key = switch (status == null ? 0 : status) {
            case 1 -> "excel.securityIntercept.processed";
            case 2 -> "excel.securityIntercept.ignored";
            default -> "excel.securityIntercept.unhandled";
        };
        return excelI18nMessageResolver.resolve(key, locale);
    }

    /**
     * 编排 blank To Placeholder 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminSecurityInterceptEventApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String blankToPlaceholder(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
