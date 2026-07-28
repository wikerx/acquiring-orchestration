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
     * admin Dict Service 依赖，用于 Admin Dict Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminDictService adminDictService;
    /**
     * excel Export Service 依赖，用于 Admin Dict Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelExportService excelExportService;
    /**
     * excel I 18 n Message Resolver，用于保存 Admin Dict Application Service 中与 exceli18nmessageresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    /**
     * excel Locale Resolver，用于保存 Admin Dict Application Service 中与 excellocaleresolver 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 构造查询条件对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param builder builder 输入值，参与 builder 的查询、校验、转换、写入或日志摘要
     * @param labelKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     */
    private void appendCondition(StringBuilder builder, String labelKey, String value, Locale locale) {
        appendSeparator(builder);
        builder.append(excelI18nMessageResolver.resolve(labelKey, locale)).append("=").append(value);
    }

    /**
     * 解析resolve状态文本，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param enabled enabled 输入值，参与 enabled 的查询、校验、转换、写入或日志摘要
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolveStatusText(boolean enabled, Locale locale) {
        return excelI18nMessageResolver.resolve(enabled ? "excel.common.enabled" : "excel.common.disabled", locale);
    }

    /**
     * 解析resolveboolean文本，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param yes yes 输入值，参与 yes 的查询、校验、转换、写入或日志摘要
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
