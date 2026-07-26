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
     * EXPORT TIME FORMATTER，用于保存 Admin Security Intercept Event Application Service 中与 exporttimeformatter 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * event Service 依赖，用于 Admin Security Intercept Event Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminSecurityInterceptEventService eventService;
    /**
     * excel Export Service 依赖，用于 Admin Security Intercept Event Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelExportService excelExportService;
    /**
     * excel I 18 n Message Resolver，用于保存 Admin Security Intercept Event Application Service 中与 exceli18nmessageresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    /**
     * excel Locale Resolver，用于保存 Admin Security Intercept Event Application Service 中与 excellocaleresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 构造exportrow对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 构造query汇总对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 创建查询条件，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param conditions conditions 输入值，参与 conditions 的查询、校验、转换、写入或日志摘要
     * @param labelKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
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
     * 解析resolve可空process状态文本，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolveNullableProcessStatusText(Integer status, Locale locale) {
        return status == null ? null : resolveProcessStatusText(status, locale);
    }

    /**
     * 解析resolveprocess状态文本，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 规范化blanktoplaceholder，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String blankToPlaceholder(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
