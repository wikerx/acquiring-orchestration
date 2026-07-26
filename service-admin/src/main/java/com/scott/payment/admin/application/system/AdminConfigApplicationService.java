package com.scott.payment.admin.application.system;

import com.scott.payment.admin.converter.ConfigConverter;
import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.dto.SysConfigQueryRequest;
import com.scott.payment.admin.dto.SysConfigSaveRequest;
import com.scott.payment.admin.dto.export.SysConfigExportRow;
import com.scott.payment.admin.service.AdminConfigService;
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
 * @classname : AdminConfigApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台系统参数配置应用服务
 * @status : create
 */
@Service
public class AdminConfigApplicationService {

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * admin Config Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminConfigService adminConfigService;
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
     * 系统参数配置对象转换器。
     */
    private final ConfigConverter configConverter;

    /**
     * 创建后台系统配置应用服务。
     *
     * @param adminConfigService       系统配置领域服务
     * @param excelExportService       Excel 导出服务
     * @param excelI18nMessageResolver Excel 文案解析器
     * @param excelLocaleResolver      Excel 语言解析器
     * @param configConverter          系统参数配置对象转换器
     */
    public AdminConfigApplicationService(AdminConfigService adminConfigService,
                                         ExcelExportService excelExportService,
                                         ExcelI18nMessageResolver excelI18nMessageResolver,
                                         ExcelLocaleResolver excelLocaleResolver,
                                         ConfigConverter configConverter) {
        this.adminConfigService = adminConfigService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
        this.configConverter = configConverter;
    }

    /**
     * 保存系统配置。
     *
     * @param request 保存请求
     * @return 配置详情
     */
    public SysConfigDTO saveConfig(SysConfigSaveRequest request) {
        return adminConfigService.saveConfig(request);
    }

    /**
     * 按配置键查询系统配置。
     *
     * @param configKey 配置键
     * @return 配置详情
     */
    public SysConfigDTO getConfigByKey(String configKey) {
        return adminConfigService.getConfigByKey(configKey);
    }

    /**
     * 分页查询系统配置。
     *
     * @param request 查询条件
     * @return 配置分页结果
     */
    public PageResult<SysConfigDTO> pageConfigs(SysConfigQueryRequest request) {
        return adminConfigService.pageConfigs(request);
    }

    /**
     * 导出系统参数配置列表。
     *
     * @param request 查询条件
     * @param operator 导出人
     * @param response HTTP 响应
     */
    public void exportConfigs(SysConfigQueryRequest request,
                              String operator,
                              HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<SysConfigExportRow> rows = adminConfigService.listConfigs(request).stream()
                .map(configConverter::toExportRow)
                .peek(row -> fillConfigDisplayValue(row, locale))
                .toList();
        excelExportService.export(
                ExcelExportRequest.<SysConfigExportRow>builder()
                        .fileName(excelI18nMessageResolver.resolve("excel.config.title", locale) + "_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()))
                        .sheetName(excelI18nMessageResolver.resolve("excel.config.title", locale))
                        .titleKey("excel.config.title")
                        .operator(operator)
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(buildQuerySummary(request, locale))
                        .rowClass(SysConfigExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 删除系统配置。
     *
     * @param configKey 配置键
     */
    public void deleteConfig(String configKey) {
        adminConfigService.deleteConfig(configKey);
    }

    /**
     * 填充导出展示文案，避免 Excel 中出现难以理解的原始枚举值。
     *
     * @param row 导出行对象
     * @param locale 当前语言
     */
    private void fillConfigDisplayValue(SysConfigExportRow row, Locale locale) {
        row.setStatus(excelI18nMessageResolver.resolve(
                "1".equals(row.getStatus()) ? "excel.common.enabled" : "excel.common.disabled",
                locale
        ));
    }

    /**
     * 构建导出查询摘要。
     *
     * @param request 查询条件
     * @param locale 当前语言
     * @return 查询摘要
     */
    private String buildQuerySummary(SysConfigQueryRequest request, Locale locale) {
        if (request == null) {
            return excelI18nMessageResolver.resolve("excel.common.noCondition", locale);
        }
        StringBuilder builder = new StringBuilder();
        if (request.getConfigName() != null && !request.getConfigName().isBlank()) {
            builder.append("参数名称=").append(request.getConfigName().trim());
        }
        if (request.getConfigGroup() != null && !request.getConfigGroup().isBlank()) {
            appendSeparator(builder);
            builder.append("参数分组=").append(request.getConfigGroup().trim());
        }
        if (request.getStatus() != null) {
            appendSeparator(builder);
            builder.append("状态=").append(excelI18nMessageResolver.resolve(
                    request.getStatus() == 1 ? "excel.common.enabled" : "excel.common.disabled",
                    locale
            ));
        }
        return builder.isEmpty() ? excelI18nMessageResolver.resolve("excel.common.noCondition", locale) : builder.toString();
    }

    /**
     * 为查询摘要补充分隔符，避免字符串拼接逻辑散落在业务代码里。
     *
     * @param builder 摘要构造器
     */
    private void appendSeparator(StringBuilder builder) {
        if (!builder.isEmpty()) {
            builder.append("，");
        }
    }
}
