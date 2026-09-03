package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistConfigRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistApprovalRequest;
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
 * @description : admin商户ipwhitelist应用服务，位于 运营后台服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@Service
public class AdminMerchantIpWhitelistApplicationService {

    /**
     * {@code EXPORT_TIME_FORMATTER}常量，统一 {@code AdminMerchantIpWhitelistApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminMerchantIpWhitelistService whitelistService;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
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
     * 审批商户提交的 IP 白名单记录。
     *
     * @param id      白名单记录 ID
     * @param request 审批请求
     * @return 审批后的记录
     */
    public MerchantIpWhitelistResponse approveWhitelist(Long id, MerchantIpWhitelistApprovalRequest request) {
        return whitelistService.approveWhitelist(id, request);
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

    private String formatIpWhitelists(List<MerchantIpWhitelistItem> items, Locale locale) {
        if (items == null || items.isEmpty()) {
            return excelI18nMessageResolver.resolve("excel.common.noData", locale);
        }
        return items.stream()
                .map(item -> item.getIpType() + " " + item.getIpValue() + " (" + resolveStatusText(item.getStatus(), locale) + ")")
                .collect(Collectors.joining("; "));
    }

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

    private void addCondition(StringBuilder builder, String labelKey, String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(locale.getLanguage().equals(Locale.CHINESE.getLanguage()) ? "，" : ", ");
        }
        builder.append(excelI18nMessageResolver.resolve(labelKey, locale)).append("=").append(value.trim());
    }

    private String resolveNullableStatusText(Integer status, Locale locale) {
        return status == null ? null : resolveStatusText(status, locale);
    }

    private String resolveStatusText(Integer status, Locale locale) {
        return excelI18nMessageResolver.resolve(status != null && status == 1 ? "excel.common.enabled" : "excel.common.disabled", locale);
    }

    private String blankToPlaceholder(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }
}
