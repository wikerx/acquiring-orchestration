package com.scott.payment.admin.application.system;

import com.scott.payment.admin.converter.DictConverter;
import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.SysDictDataSaveRequest;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.dto.SysDictTypeQueryRequest;
import com.scott.payment.admin.dto.SysDictTypeSaveRequest;
import com.scott.payment.admin.dto.export.SysDictDataExportRow;
import com.scott.payment.admin.dto.export.SysDictTypeExportRow;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDictApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台数据字典应用服务
 * @status : create
 */
@Service
public class AdminDictApplicationService {

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * admin Dict Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminDictService adminDictService;
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
     * 数据字典对象转换器。
     */
    private final DictConverter dictConverter;

    /**
     * 创建后台数据字典应用服务。
     *
     * @param adminDictService         数据字典领域服务
     * @param excelExportService       Excel 导出服务
     * @param excelI18nMessageResolver Excel 文案解析器
     * @param excelLocaleResolver      Excel 语言解析器
     * @param dictConverter            数据字典对象转换器
     */
    public AdminDictApplicationService(AdminDictService adminDictService,
                                       ExcelExportService excelExportService,
                                       ExcelI18nMessageResolver excelI18nMessageResolver,
                                       ExcelLocaleResolver excelLocaleResolver,
                                       DictConverter dictConverter) {
        this.adminDictService = adminDictService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
        this.dictConverter = dictConverter;
    }

    /**
     * 保存字典类型。
     *
     * @param request 保存请求
     * @return 字典类型详情
     */
    public SysDictTypeDTO saveDictType(SysDictTypeSaveRequest request) {
        return adminDictService.saveDictType(request);
    }

    /**
     * 分页查询字典类型。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResult<SysDictTypeDTO> pageDictTypes(SysDictTypeQueryRequest request) {
        return adminDictService.pageDictTypes(request);
    }

    /**
     * 导出字典类型列表。
     *
     * @param request 查询条件
     * @param operator 导出人
     * @param response HTTP 响应
     */
    public void exportDictTypes(SysDictTypeQueryRequest request,
                                String operator,
                                HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<SysDictTypeExportRow> rows = adminDictService.listDictTypes(request).stream()
                .map(dictConverter::toTypeExportRow)
                .peek(row -> fillDictTypeDisplayValue(row, locale))
                .toList();
        String exportTitle = excelI18nMessageResolver.resolve("excel.dict.title", locale);
        excelExportService.export(
                ExcelExportRequest.<SysDictTypeExportRow>builder()
                        .fileName(exportTitle + "_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName(exportTitle)
                        .titleKey("excel.dict.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(buildDictTypeQuerySummary(request, locale))
                        .rowClass(SysDictTypeExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 删除字典类型。
     *
     * @param dictType 字典类型编码
     */
    public void deleteDictType(String dictType) {
        adminDictService.deleteDictType(dictType);
    }

    /**
     * 保存字典数据。
     *
     * @param request 保存请求
     * @return 字典数据详情
     */
    public SysDictDataDTO saveDictData(SysDictDataSaveRequest request) {
        return adminDictService.saveDictData(request);
    }

    /**
     * 分页查询字典数据。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResult<SysDictDataDTO> pageDictData(SysDictDataQueryRequest request) {
        return adminDictService.pageDictData(request);
    }

    /**
     * 导出字典数据列表。
     *
     * @param request 查询条件
     * @param operator 导出人
     * @param response HTTP 响应
     */
    public void exportDictData(SysDictDataQueryRequest request,
                               String operator,
                               HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<SysDictDataExportRow> rows = adminDictService.listDictData(request).stream()
                .map(dictConverter::toDataExportRow)
                .peek(row -> fillDictDataDisplayValue(row, locale))
                .toList();
        String exportTitle = excelI18nMessageResolver.resolve("excel.dictData.title", locale);
        excelExportService.export(
                ExcelExportRequest.<SysDictDataExportRow>builder()
                        .fileName(exportTitle + "_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName(exportTitle)
                        .titleKey("excel.dictData.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(buildDictDataQuerySummary(request, locale))
                        .rowClass(SysDictDataExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 按主键查询字典数据详情。
     *
     * @param id 字典数据主键
     * @return 字典数据详情
     */
    public SysDictDataDTO getDictDataById(Long id) {
        return adminDictService.getDictDataById(id);
    }

    /**
     * 按主键更新字典数据。
     *
     * @param id      字典数据主键
     * @param request 保存请求
     * @return 更新后的字典数据
     */
    public SysDictDataDTO updateDictDataById(Long id, SysDictDataSaveRequest request) {
        return adminDictService.updateDictDataById(id, request);
    }

    /**
     * 删除字典数据。
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典值
     * @param locale    语言区域
     */
    public void deleteDictData(String dictType, String dictValue, String locale) {
        adminDictService.deleteDictData(dictType, dictValue, locale);
    }

    /**
     * 按主键删除字典数据。
     *
     * @param id 字典数据主键
     */
    public void deleteDictDataById(Long id) {
        adminDictService.deleteDictDataById(id);
    }

    /**
     * 填充字典类型导出展示文案。
     *
     * @param row    导出行对象
     * @param locale 当前语言
     */
    private void fillDictTypeDisplayValue(SysDictTypeExportRow row, Locale locale) {
        row.setStatus(resolveStatusText("1".equals(row.getStatus()), locale));
        row.setSystemBuiltin(resolveBooleanText("1".equals(row.getSystemBuiltin()), locale));
    }

    /**
     * 填充字典数据导出展示文案。
     *
     * @param row    导出行对象
     * @param locale 当前语言
     */
    private void fillDictDataDisplayValue(SysDictDataExportRow row, Locale locale) {
        row.setStatus(resolveStatusText("1".equals(row.getStatus()), locale));
        row.setDefaultFlag(resolveBooleanText("1".equals(row.getDefaultFlag()), locale));
    }

    /**
     * 构建字典类型导出摘要。
     *
     * @param request 查询条件
     * @param locale  当前语言
     * @return 摘要
     */
    private String buildDictTypeQuerySummary(SysDictTypeQueryRequest request, Locale locale) {
        if (request == null) {
            return excelI18nMessageResolver.resolve("excel.common.noCondition", locale);
        }
        StringBuilder builder = new StringBuilder();
        if (request.getDictName() != null && !request.getDictName().isBlank()) {
            appendCondition(builder, "excel.dict.dictName", request.getDictName().trim(), locale);
        }
        if (request.getDictType() != null && !request.getDictType().isBlank()) {
            appendCondition(builder, "excel.dict.dictType", request.getDictType().trim(), locale);
        }
        if (request.getBizDomain() != null && !request.getBizDomain().isBlank()) {
            appendCondition(builder, "excel.dict.bizDomain", request.getBizDomain().trim(), locale);
        }
        if (request.getStatus() != null) {
            appendCondition(builder, "excel.dict.status", resolveStatusText(request.getStatus() == 1, locale), locale);
        }
        return builder.isEmpty() ? excelI18nMessageResolver.resolve("excel.common.noCondition", locale) : builder.toString();
    }

    /**
     * 构建字典数据导出摘要。
     *
     * @param request 查询条件
     * @param locale  当前语言
     * @return 摘要
     */
    private String buildDictDataQuerySummary(SysDictDataQueryRequest request, Locale locale) {
        if (request == null) {
            return excelI18nMessageResolver.resolve("excel.common.noCondition", locale);
        }
        StringBuilder builder = new StringBuilder();
        if (request.getDictType() != null && !request.getDictType().isBlank()) {
            appendCondition(builder, "excel.dictData.dictType", request.getDictType().trim(), locale);
        }
        if (request.getDictLabel() != null && !request.getDictLabel().isBlank()) {
            appendCondition(builder, "excel.dictData.dictLabel", request.getDictLabel().trim(), locale);
        }
        if (request.getDictValue() != null && !request.getDictValue().isBlank()) {
            appendCondition(builder, "excel.dictData.dictValue", request.getDictValue().trim(), locale);
        }
        if (request.getLocale() != null && !request.getLocale().isBlank()) {
            appendCondition(builder, "excel.dictData.locale", request.getLocale().trim(), locale);
        }
        if (request.getStatus() != null) {
            appendCondition(builder, "excel.dictData.status", resolveStatusText(request.getStatus() == 1, locale), locale);
        }
        return builder.isEmpty() ? excelI18nMessageResolver.resolve("excel.common.noCondition", locale) : builder.toString();
    }

    /**
     * 编排 append Condition 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminDictApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param builder builder 输入值，含义由调用方法名称和所属业务对象限定
     * @param labelKey label Key 输入值，含义由调用方法名称和所属业务对象限定
     * @param value 待校验或转换的原始值
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void appendCondition(StringBuilder builder, String labelKey, String value, Locale locale) {
        appendSeparator(builder);
        builder.append(excelI18nMessageResolver.resolve(labelKey, locale)).append("=").append(value);
    }

    /**
     * 编排 resolve Status Text 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminDictApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param enabled enabled 输入值，含义由调用方法名称和所属业务对象限定
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolveStatusText(boolean enabled, Locale locale) {
        return excelI18nMessageResolver.resolve(enabled ? "excel.common.enabled" : "excel.common.disabled", locale);
    }

    /**
     * 编排 resolve Boolean Text 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminDictApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param yes yes 输入值，含义由调用方法名称和所属业务对象限定
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolveBooleanText(boolean yes, Locale locale) {
        return excelI18nMessageResolver.resolve(yes ? "excel.common.yes" : "excel.common.no", locale);
    }

    /**
     * 为查询摘要补充分隔符。
     *
     * @param builder 摘要构造器
     */
    private void appendSeparator(StringBuilder builder) {
        if (!builder.isEmpty()) {
            builder.append("，");
        }
    }
}
