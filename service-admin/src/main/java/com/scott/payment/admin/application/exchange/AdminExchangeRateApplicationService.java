package com.scott.payment.admin.application.exchange;

import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateBatchSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.GenerateBusinessRateRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotResponse;
import com.scott.payment.admin.dto.export.ExchangeBusinessRateExportRow;
import com.scott.payment.admin.dto.export.ExchangeRateRuleExportRow;
import com.scott.payment.admin.dto.export.ExchangeRateSourceExportRow;
import com.scott.payment.admin.dto.export.ExchangeRateUsageSnapshotExportRow;
import com.scott.payment.admin.dto.export.ExchangeRawRateExportRow;
import com.scott.payment.admin.service.AdminExchangeRateService;
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
import java.util.List;
import java.util.Locale;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminExchangeRateApplicationService
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : Admin Exchange Rate Application Service 应用服务，位于 运营后台服务，编排控制器入参、登录或商户上下文、领域服务调用和响应模型组装。
 * @status : create
 */
public class AdminExchangeRateApplicationService {

    /**
     * EXPORT TIME FORMATTER，用于保存 Admin Exchange Rate Application Service 中与 exporttimeformatter 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * admin Exchange Rate Service 依赖，用于 Admin Exchange Rate Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminExchangeRateService adminExchangeRateService;

    /**
     * excel Export Service 依赖，用于 Admin Exchange Rate Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelExportService excelExportService;

    /**
     * excel I 18 n Message Resolver，用于保存 Admin Exchange Rate Application Service 中与 exceli18nmessageresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;

    /**
     * excel Locale Resolver，用于保存 Admin Exchange Rate Application Service 中与 excellocaleresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelLocaleResolver excelLocaleResolver;

/**
 * 整理adminexchange汇率applicationservice，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param adminExchangeRateService admin Exchange Rate Service 输入值，参与 adminexchange汇率service 的查询、校验、转换、写入或日志摘要
 * @param excelExportService excel Export Service 输入值，参与 excelexportservice 的查询、校验、转换、写入或日志摘要
 * @param excelI18nMessageResolver excel I 18 n Message Resolver 输入值，参与 exceli18n说明resolver 的查询、校验、转换、写入或日志摘要
 * @param excelLocaleResolver excel Locale Resolver 输入值，参与 excellocaleresolver 的查询、校验、转换、写入或日志摘要
 */
    public AdminExchangeRateApplicationService(AdminExchangeRateService adminExchangeRateService,
                                               ExcelExportService excelExportService,
                                               ExcelI18nMessageResolver excelI18nMessageResolver,
                                               ExcelLocaleResolver excelLocaleResolver) {
        this.adminExchangeRateService = adminExchangeRateService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 分页查询汇率源配置。
     *
     * @param query 查询条件，允许为空
     * @return 汇率源分页结果
     */
    public PageResult<SourceResponse> pageSources(SourceQuery query) {
        return adminExchangeRateService.pageSources(query);
    }

    /**
     * 导出汇率源配置。
     *
     * @param query    查询条件，允许为空
     * @param operator 导出人
     * @param response HTTP 响应
     */
    public void exportSources(SourceQuery query, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<ExchangeRateSourceExportRow> rows = adminExchangeRateService.listSources(query).stream()
                .map(row -> toSourceExportRow(row, locale))
                .toList();
        export("excel.exchange.source.title", querySummary(locale), rows, ExchangeRateSourceExportRow.class, operator, locale, response);
    }

    /**
     * 查询汇率源详情。
     *
     * @param id 汇率源主键
     * @return 汇率源详情
     */
    public SourceResponse getSource(Long id) {
        return adminExchangeRateService.getSource(id);
    }

    /**
     * 新增汇率源配置。
     *
     * @param request 保存请求
     * @return 新增后的汇率源详情
     */
    public SourceResponse createSource(SourceSaveRequest request) {
        return adminExchangeRateService.createSource(request);
    }

    /**
     * 修改汇率源配置。
     *
     * @param id      汇率源主键
     * @param request 保存请求
     * @return 修改后的汇率源详情
     */
    public SourceResponse updateSource(Long id, SourceSaveRequest request) {
        return adminExchangeRateService.updateSource(id, request);
    }

    /**
     * 启用或停用汇率源。
     *
     * @param id     汇率源主键
     * @param status 状态值，1 表示启用，0 表示停用
     * @return 切换状态后的汇率源详情
     */
    public SourceResponse updateSourceStatus(Long id, Integer status) {
        return adminExchangeRateService.updateSourceStatus(id, status);
    }

    /**
     * 删除未被引用的汇率源。
     *
     * @param id 汇率源主键
     */
    public void deleteSource(Long id) {
        adminExchangeRateService.deleteSource(id);
    }

    /**
     * 分页查询原始汇率记录。
     *
     * @param query 查询条件，允许为空
     * @return 原始汇率分页结果
     */
    public PageResult<RawRateResponse> pageRawRates(RawRateQuery query) {
        return adminExchangeRateService.pageRawRates(query);
    }

    /**
     * 导出原始汇率记录。
     *
     * @param query    查询条件，允许为空
     * @param operator 导出人
     * @param response HTTP 响应
     */
    public void exportRawRates(RawRateQuery query, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<ExchangeRawRateExportRow> rows = adminExchangeRateService.listRawRates(query).stream()
                .map(row -> toRawRateExportRow(row, locale))
                .toList();
        export("excel.exchange.raw.title", querySummary(locale), rows, ExchangeRawRateExportRow.class, operator, locale, response);
    }

    /**
     * 查询原始汇率详情。
     *
     * @param id 原始汇率主键
     * @return 原始汇率详情
     */
    public RawRateResponse getRawRate(Long id) {
        return adminExchangeRateService.getRawRate(id);
    }

    /**
     * 手工新增原始汇率记录。
     *
     * @param request 原始汇率保存请求
     * @return 新增后的原始汇率详情
     */
    public RawRateResponse createManualRawRate(RawRateSaveRequest request) {
        return adminExchangeRateService.createManualRawRate(request);
    }

    /**
     * 作废未被业务汇率引用的原始汇率。
     *
     * @param id         原始汇率主键
     * @param voidReason 作废原因
     * @return 作废后的原始汇率详情
     */
    public RawRateResponse voidRawRate(Long id, String voidReason) {
        return adminExchangeRateService.voidRawRate(id, voidReason);
    }

    /**
     * 分页查询汇率规则。
     *
     * @param query 查询条件，允许为空
     * @return 汇率规则分页结果
     */
    public PageResult<RuleResponse> pageRules(RuleQuery query) {
        return adminExchangeRateService.pageRules(query);
    }

    /**
     * 导出汇率规则。
     *
     * @param query    查询条件，允许为空
     * @param operator 导出人
     * @param response HTTP 响应
     */
    public void exportRules(RuleQuery query, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<ExchangeRateRuleExportRow> rows = adminExchangeRateService.listRules(query).stream()
                .map(row -> toRuleExportRow(row, locale))
                .toList();
        export("excel.exchange.rule.title", querySummary(locale), rows, ExchangeRateRuleExportRow.class, operator, locale, response);
    }

    /**
     * 查询汇率规则详情。
     *
     * @param id 规则主键
     * @return 汇率规则详情
     */
    public RuleResponse getRule(Long id) {
        return adminExchangeRateService.getRule(id);
    }

    /**
     * 新增汇率规则。
     *
     * @param request 规则保存请求
     * @return 新增后的规则详情
     */
    public RuleResponse createRule(RuleSaveRequest request) {
        return adminExchangeRateService.createRule(request);
    }

    /**
     * 修改汇率规则。
     *
     * @param id      规则主键
     * @param request 规则保存请求
     * @return 修改后的规则详情
     */
    public RuleResponse updateRule(Long id, RuleSaveRequest request) {
        return adminExchangeRateService.updateRule(id, request);
    }

    /**
     * 启用或停用汇率规则。
     *
     * @param id     规则主键
     * @param status 状态值，1 表示启用，0 表示停用
     * @return 切换状态后的规则详情
     */
    public RuleResponse updateRuleStatus(Long id, Integer status) {
        return adminExchangeRateService.updateRuleStatus(id, status);
    }

    /**
     * 分页查询业务汇率。
     *
     * @param query 查询条件，允许为空
     * @return 业务汇率分页结果
     */
    public PageResult<BusinessRateResponse> pageBusinessRates(BusinessRateQuery query) {
        return adminExchangeRateService.pageBusinessRates(query);
    }

    /**
     * 导出业务汇率。
     *
     * @param query    查询条件，允许为空
     * @param operator 导出人
     * @param response HTTP 响应
     */
    public void exportBusinessRates(BusinessRateQuery query, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<ExchangeBusinessRateExportRow> rows = adminExchangeRateService.listBusinessRates(query).stream()
                .map(row -> toBusinessRateExportRow(row, locale))
                .toList();
        export("excel.exchange.business.title", querySummary(locale), rows, ExchangeBusinessRateExportRow.class, operator, locale, response);
    }

    /**
     * 查询业务汇率详情。
     *
     * @param id 业务汇率主键
     * @return 业务汇率详情
     */
    public BusinessRateResponse getBusinessRate(Long id) {
        return adminExchangeRateService.getBusinessRate(id);
    }

    /**
     * 手工新增可直接使用的业务汇率。
     *
     * @param request 业务汇率保存请求
     * @return 新增后的业务汇率
     */
    public BusinessRateResponse createManualBusinessRate(BusinessRateSaveRequest request) {
        return adminExchangeRateService.createManualBusinessRate(request);
    }

    /**
     * 批量手工新增可直接使用的业务汇率。
     *
     * @param request 批量保存请求
     * @return 新增后的业务汇率列表
     */
    public List<BusinessRateResponse> createManualBusinessRates(BusinessRateBatchSaveRequest request) {
        return adminExchangeRateService.createManualBusinessRates(request);
    }

    /**
     * 根据原始汇率和规则生成最终业务汇率，并使同范围旧业务汇率失效。
     *
     * @param request 业务汇率生成请求
     * @return 生成后的业务汇率详情
     */
    public BusinessRateResponse generateBusinessRate(GenerateBusinessRateRequest request) {
        return adminExchangeRateService.generateBusinessRate(request);
    }

    /**
     * 启用或停用业务汇率。
     *
     * @param id     业务汇率主键
     * @param status 状态值，1 表示启用，0 表示停用
     * @return 切换状态后的业务汇率详情
     */
    public BusinessRateResponse updateBusinessRateStatus(Long id, Integer status) {
        return adminExchangeRateService.updateBusinessRateStatus(id, status);
    }

    /**
     * 分页查询交易、清分或结算链路写入的汇率使用快照。
     *
     * @param query 查询条件，允许为空
     * @return 汇率使用快照分页结果
     */
    public PageResult<UsageSnapshotResponse> pageUsageSnapshots(UsageSnapshotQuery query) {
        return adminExchangeRateService.pageUsageSnapshots(query);
    }

    /**
     * 导出汇率使用快照。
     *
     * @param query    查询条件，允许为空
     * @param operator 导出人
     * @param response HTTP 响应
     */
    public void exportUsageSnapshots(UsageSnapshotQuery query, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<ExchangeRateUsageSnapshotExportRow> rows = adminExchangeRateService.listUsageSnapshots(query).stream()
                .map(row -> toUsageSnapshotExportRow(row, locale))
                .toList();
        export("excel.exchange.snapshot.title", querySummary(locale), rows, ExchangeRateUsageSnapshotExportRow.class, operator, locale, response);
    }

    /**
     * 查询汇率使用快照详情。
     *
     * @param id 快照主键
     * @return 汇率使用快照详情
     */
    public UsageSnapshotResponse getUsageSnapshot(Long id) {
        return adminExchangeRateService.getUsageSnapshot(id);
    }

    private <T> void export(String titleKey, String querySummary, List<T> rows, Class<T> rowClass,
                            String operator, Locale locale, HttpServletResponse response) {
        String exportTitle = excelI18nMessageResolver.resolve(titleKey, locale);
        excelExportService.export(
                ExcelExportRequest.<T>builder()
                        .fileName(exportTitle + "_" + timestampSuffix())
                        .sheetName(exportTitle)
                        .titleKey(titleKey)
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(querySummary)
                        .rowClass(rowClass)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 构造来源exportrow对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private ExchangeRateSourceExportRow toSourceExportRow(SourceResponse source, Locale locale) {
        ExchangeRateSourceExportRow row = new ExchangeRateSourceExportRow();
        row.setSourceCode(source.getSourceCode());
        row.setSourceName(source.getSourceName());
        row.setSourceType(resolveEnum("sourceType", source.getSourceType(), locale));
        row.setRequestUrl(source.getRequestUrl());
        row.setDefaultSource(resolveBoolean(source.getDefaultSource(), locale));
        row.setPriority(source.getPriority());
        row.setTimeoutSeconds(source.getTimeoutSeconds());
        row.setSourceStatus(resolveStatus(source.getSourceStatus(), locale));
        row.setLastFetchTime(source.getLastFetchTime());
        row.setLastFetchStatus(resolveEnum("fetchStatus", source.getLastFetchStatus(), locale));
        row.setRemark(source.getRemark());
        row.setCreateTime(source.getCreateTime());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /**
     * 构造raw汇率exportrow对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private ExchangeRawRateExportRow toRawRateExportRow(RawRateResponse source, Locale locale) {
        ExchangeRawRateExportRow row = new ExchangeRawRateExportRow();
        row.setSourceCode(source.getSourceCode());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setSpotBuyRate(source.getSpotBuyRate());
        row.setSpotSellRate(source.getSpotSellRate());
        row.setCashBuyRate(source.getCashBuyRate());
        row.setCashSellRate(source.getCashSellRate());
        row.setMiddleRate(source.getMiddleRate());
        row.setPublishTime(source.getPublishTime());
        row.setFetchTime(source.getFetchTime());
        row.setEffectiveTime(source.getEffectiveTime());
        row.setCreateMethod(resolveEnum("createMethod", source.getCreateMethod(), locale));
        row.setBatchNo(source.getBatchNo());
        row.setRateStatus(resolveEnum("rawRateStatus", source.getRateStatus(), locale));
        row.setVoidReason(source.getVoidReason());
        row.setCreateTime(source.getCreateTime());
        return row;
    }

    /**
     * 构造规则exportrow对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private ExchangeRateRuleExportRow toRuleExportRow(RuleResponse source, Locale locale) {
        ExchangeRateRuleExportRow row = new ExchangeRateRuleExportRow();
        row.setRateType(resolveEnum("rateType", source.getRateType(), locale));
        row.setSourceCode(source.getSourceCode());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setRateField(resolveEnum("rateField", source.getRateField(), locale));
        row.setAdjustDirection(resolveEnum("adjustDirection", source.getAdjustDirection(), locale));
        row.setAdjustMethod(resolveEnum("adjustMethod", source.getAdjustMethod(), locale));
        row.setAdjustValue(source.getAdjustValue());
        row.setDecimalScale(source.getDecimalScale());
        row.setRoundingMode(resolveEnum("roundingMode", source.getRoundingMode(), locale));
        row.setPriority(source.getPriority());
        row.setEffectiveStartTime(source.getEffectiveStartTime());
        row.setEffectiveEndTime(source.getEffectiveEndTime());
        row.setRuleStatus(resolveStatus(source.getRuleStatus(), locale));
        row.setRemark(source.getRemark());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /**
     * 构造business汇率exportrow对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private ExchangeBusinessRateExportRow toBusinessRateExportRow(BusinessRateResponse source, Locale locale) {
        ExchangeBusinessRateExportRow row = new ExchangeBusinessRateExportRow();
        row.setRateType(resolveEnum("rateType", source.getRateType(), locale));
        row.setSourceCode(source.getSourceCode());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setOriginalRate(source.getOriginalRate());
        row.setFinalRate(source.getFinalRate());
        row.setEffectiveTime(source.getEffectiveTime());
        row.setExpireTime(source.getExpireTime());
        row.setGenerateMethod(resolveEnum("generateMethod", source.getGenerateMethod(), locale));
        row.setRateStatus(resolveEnum("businessRateStatus", source.getRateStatus(), locale));
        row.setRawRateId(source.getRawRateId());
        row.setRuleId(source.getRuleId());
        row.setAdjustDescription(source.getAdjustDescription());
        row.setRemark(source.getRemark());
        row.setCreateTime(source.getCreateTime());
        return row;
    }

    /**
     * 构造usagesnapshotexportrow对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private ExchangeRateUsageSnapshotExportRow toUsageSnapshotExportRow(UsageSnapshotResponse source, Locale locale) {
        ExchangeRateUsageSnapshotExportRow row = new ExchangeRateUsageSnapshotExportRow();
        row.setRateType(resolveEnum("rateType", source.getRateType(), locale));
        row.setUsageScene(resolveEnum("usageScene", source.getUsageScene(), locale));
        row.setBusinessType(source.getBusinessType());
        row.setBusinessNo(source.getBusinessNo());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setUsedRate(source.getUsedRate());
        row.setRawRateId(source.getRawRateId());
        row.setRuleId(source.getRuleId());
        row.setBusinessRateId(source.getBusinessRateId());
        row.setAppliedTime(source.getAppliedTime());
        row.setCalculationDescription(source.getCalculationDescription());
        row.setCreateTime(source.getCreateTime());
        return row;
    }

    /**
     * 解析resolveenum，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param group group 输入值，参与 group 的查询、校验、转换、写入或日志摘要
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolveEnum(String group, String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return excelI18nMessageResolver.resolve("excel.exchange.enum." + group + "." + value, locale);
    }

    /**
     * 解析resolve状态，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolveStatus(Integer status, Locale locale) {
        return excelI18nMessageResolver.resolve(status != null && status == 1 ? "excel.common.enabled" : "excel.common.disabled", locale);
    }

    /**
     * 解析resolveboolean，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolveBoolean(Integer value, Locale locale) {
        return excelI18nMessageResolver.resolve(value != null && value == 1 ? "excel.common.yes" : "excel.common.no", locale);
    }

    /**
     * 查询汇总数据，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private String querySummary(Locale locale) {
        return excelI18nMessageResolver.resolve("excel.common.noCondition", locale);
    }

    /**
     * 规范化timestampsuffix，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String timestampSuffix() {
        return EXPORT_TIME_FORMATTER.format(LocalDateTime.now());
    }
}
