package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistConfigRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistCreateRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistItem;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistQuery;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistUpdateRequest;
import com.scott.payment.admin.dto.export.MerchantIpWhitelistExportRow;
import com.scott.payment.admin.service.AdminMerchantIpWhitelistService;
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
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantIpWhitelistApplicationService
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 商户 IP 白名单管理应用服务，位于 service-admin 应用层，承接后台接口并委托领域服务处理白名单配置。
 * @status : create
 */
@Service
public class AdminMerchantIpWhitelistApplicationService {

    /**
     * EXPORT TIME FORMATTER 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * whitelist Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminMerchantIpWhitelistService whitelistService;
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
     * 创建商户 IP 白名单应用服务。
     *
     * @param whitelistService          白名单领域服务
     * @param excelExportService        Excel 导出服务
     * @param excelI18nMessageResolver  Excel 文案解析器
     * @param excelLocaleResolver       Excel 语言解析器
     */
    public AdminMerchantIpWhitelistApplicationService(AdminMerchantIpWhitelistService whitelistService,
                                                      ExcelExportService excelExportService,
                                                      ExcelI18nMessageResolver excelI18nMessageResolver,
                                                      ExcelLocaleResolver excelLocaleResolver) {
        this.whitelistService = whitelistService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 分页查询商户 IP 白名单记录。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    public PageResult<MerchantIpWhitelistResponse> pageWhitelists(MerchantIpWhitelistQuery query) {
        return whitelistService.pageWhitelists(query);
    }

    /**
     * 按当前查询条件导出商户 IP 白名单。
     *
     * @param query 查询条件
     * @param operator 导出操作人
     * @param response HTTP 响应
     */
    public void exportWhitelists(MerchantIpWhitelistQuery query, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<MerchantIpWhitelistExportRow> rows = whitelistService.listWhitelists(query).stream()
                .map(row -> toExportRow(row, locale))
                .toList();
        LocalDateTime now = LocalDateTime.now();
        String titleKey = "excel.merchantIpWhitelist.title";
        String title = excelI18nMessageResolver.resolve(titleKey, locale);
        excelExportService.export(
                ExcelExportRequest.<MerchantIpWhitelistExportRow>builder()
                        .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                        .sheetName(title)
                        .titleKey(titleKey)
                        .operator(operator)
                        .exportTime(now)
                        .locale(locale)
                        .querySummary(buildQuerySummary(query, locale))
                        .rowClass(MerchantIpWhitelistExportRow.class)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 查询单条白名单记录详情。
     *
     * @param id 白名单记录 ID
     * @return 白名单详情
     */
    public MerchantIpWhitelistResponse getWhitelist(Long id) {
        return whitelistService.getWhitelist(id);
    }

    /**
     * 批量新增商户 IP 白名单。
     *
     * @param request 新增请求
     * @return 新增后的记录集合
     */
    public List<MerchantIpWhitelistResponse> createWhitelists(MerchantIpWhitelistCreateRequest request) {
        return whitelistService.createWhitelists(request);
    }

    /**
     * 更新单条 IP 白名单。
     *
     * @param id      白名单记录 ID
     * @param request 更新请求
     * @return 更新后的记录
     */
    public MerchantIpWhitelistResponse updateWhitelist(Long id, MerchantIpWhitelistUpdateRequest request) {
        return whitelistService.updateWhitelist(id, request);
    }

    /**
     * 更新 IP 白名单记录状态。
     *
     * @param id     白名单记录 ID
     * @param status 状态，1 启用，0 停用
     * @return 更新后的记录
     */
    public MerchantIpWhitelistResponse updateWhitelistStatus(Long id, Integer status) {
        return whitelistService.updateWhitelistStatus(id, status);
    }

    /**
     * 删除单条 IP 白名单记录。
     *
     * @param id 白名单记录 ID
     */
    public void deleteWhitelist(Long id) {
        whitelistService.deleteWhitelist(id);
    }

    /**
     * 更新商户维度白名单校验开关。
     *
     * @param request 开关请求
     * @return 当前配置视图
     */
    public MerchantIpWhitelistResponse updateConfig(MerchantIpWhitelistConfigRequest request) {
        return whitelistService.updateConfig(request);
    }

    /**
     * 转换生成 to Export Row 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private MerchantIpWhitelistExportRow toExportRow(MerchantIpWhitelistResponse source, Locale locale) {
        MerchantIpWhitelistExportRow row = new MerchantIpWhitelistExportRow();
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(blankToPlaceholder(source.getMerchantName()));
        row.setMerchantShortName(blankToPlaceholder(source.getMerchantShortName()));
        row.setAccessControl(resolveStatusText(source.getIpWhitelistEnabled(), locale));
        row.setIpWhitelists(formatIpWhitelists(source.getIpWhitelists(), locale));
        row.setUpdateBy(blankToPlaceholder(source.getUpdateBy()));
        row.setUpdateTime(source.getGmtModified());
        row.setConfigRemark(blankToPlaceholder(source.getConfigRemark()));
        return row;
    }

    /**
     * 完成 format Ip Whitelists 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param items items 输入值，含义由调用方法名称和所属业务对象限定
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String formatIpWhitelists(List<MerchantIpWhitelistItem> items, Locale locale) {
        if (items == null || items.isEmpty()) {
            return excelI18nMessageResolver.resolve("excel.common.noData", locale);
        }
        return items.stream()
                .map(item -> item.getIpType() + " " + item.getIpValue() + " (" + resolveStatusText(item.getStatus(), locale) + ")")
                .collect(Collectors.joining("; "));
    }

    /**
     * 构建 build Query Summary 对应的领域对象、请求对象或日志对象。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private String buildQuerySummary(MerchantIpWhitelistQuery query, Locale locale) {
        MerchantIpWhitelistQuery condition = query == null ? new MerchantIpWhitelistQuery() : query;
        StringBuilder builder = new StringBuilder();
        addCondition(builder, "excel.merchantIpWhitelist.merchantId", condition.getMerchantId(), locale);
        addCondition(builder, "excel.merchantIpWhitelist.ipType", condition.getIpType(), locale);
        addCondition(builder, "excel.merchantIpWhitelist.ipValue", condition.getIpValue(), locale);
        addCondition(builder, "excel.merchantIpWhitelist.status", resolveNullableStatusText(condition.getStatus(), locale), locale);
        addCondition(builder, "excel.merchantIpWhitelist.accessControl", resolveNullableStatusText(condition.getIpWhitelistEnabled(), locale), locale);
        return builder.isEmpty() ? excelI18nMessageResolver.resolve("excel.common.noCondition", locale) : builder.toString();
    }

    /**
     * 计算 add Condition 对应的数值结果，调用方负责保证金额和币种上下文一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param builder builder 输入值，含义由调用方法名称和所属业务对象限定
     * @param labelKey label Key 输入值，含义由调用方法名称和所属业务对象限定
     * @param value 待校验或转换的原始值
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void addCondition(StringBuilder builder, String labelKey, String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(locale.getLanguage().equals(Locale.CHINESE.getLanguage()) ? "，" : ", ");
        }
        builder.append(excelI18nMessageResolver.resolve(labelKey, locale)).append("=").append(value.trim());
    }

    /**
     * 解析 resolve Nullable Status Text 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolveNullableStatusText(Integer status, Locale locale) {
        return status == null ? null : resolveStatusText(status, locale);
    }

    /**
     * 解析 resolve Status Text 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private String resolveStatusText(Integer status, Locale locale) {
        return excelI18nMessageResolver.resolve(status != null && status == 1 ? "excel.common.enabled" : "excel.common.disabled", locale);
    }

    /**
     * 完成 blank To Placeholder 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private String blankToPlaceholder(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
