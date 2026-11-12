package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantIpWhitelistQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MerchantIpWhitelistItemRow;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MerchantIpWhitelistPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MerchantIpWhitelistPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MerchantIpWhitelistRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.MerchantIpWhitelistExportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_MERCHANT_IP_WHITELIST;

/**
 * 管理端商户 IP 白名单异步导出处理器。
 *
 * <p>任务执行时使用创建阶段冻结的商户数据范围，Data 不根据请求参数扩大范围；
 * Admin 继续负责权威查询和按商户聚合。</p>
 */
@Service
public class MerchantIpWhitelistExportProcessor extends
        AbstractBaseReferenceExportProcessor<MerchantIpWhitelistQuery, MerchantIpWhitelistExportRow> {
    private final AdminPlatformExportSourceClient sourceClient;

    /**
     * 创建商户 IP 白名单异步导出处理器。
     *
     * @param sourceClient    Admin 权威导出数据源客户端
     * @param artifactWriter  Excel 文件写入器
     * @param messageResolver Excel 国际化文案解析器
     * @param properties      异步导出分页与文件配置
     */
    public MerchantIpWhitelistExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                              ExcelExportArtifactWriter artifactWriter,
                                              ExcelI18nMessageResolver messageResolver,
                                              DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回处理器唯一支持的商户 IP 白名单任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_MERCHANT_IP_WHITELIST;
    }

    /**
     * 返回冻结查询快照的强类型模型。
     */
    @Override
    protected Class<MerchantIpWhitelistQuery> queryClass() {
        return MerchantIpWhitelistQuery.class;
    }

    /**
     * 返回商户 IP 白名单工作簿标题的国际化键。
     */
    @Override
    protected String titleKey() {
        return "excel.merchantIpWhitelist.title";
    }

    /**
     * 返回缺少中文资源时使用的安全标题。
     */
    @Override
    protected String chineseTitle() {
        return "商户 IP 白名单";
    }

    /**
     * 返回缺少英文资源时使用的安全标题。
     */
    @Override
    protected String englishTitle() {
        return "Merchant IP Whitelist";
    }

    /**
     * 返回商户 IP 白名单 Excel 行模型。
     */
    @Override
    protected Class<MerchantIpWhitelistExportRow> rowClass() {
        return MerchantIpWhitelistExportRow.class;
    }

    /**
     * 使用持久化的数据范围快照向 Admin 分页读取白名单，并转换为 Excel 行。
     *
     * @param task   当前持久化导出任务，包含可信商户范围快照
     * @param query  任务创建时冻结的筛选条件
     * @param pageNo 当前导出页码
     * @param locale 任务创建时冻结的语言环境
     * @return 当前页行数据和相同条件下的总行数
     */
    @Override
    protected ExportPage<MerchantIpWhitelistExportRow> loadPage(
            DataExportTaskDO task, MerchantIpWhitelistQuery query, int pageNo, Locale locale) {
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        MerchantIpWhitelistPageRequest request = new MerchantIpWhitelistPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(toSourceQuery(query));
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        MerchantIpWhitelistPageResponse response = sourceClient.loadMerchantIpWhitelistPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<MerchantIpWhitelistExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 在服务契约边界逐字段复制查询，商户范围只从任务快照传递而不从查询恢复。
     *
     * @param source Data 任务中的冻结查询
     * @return Admin 权威数据源查询
     */
    private com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MerchantIpWhitelistQuery
    toSourceQuery(MerchantIpWhitelistQuery source) {
        var target = new com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.MerchantIpWhitelistQuery();
        target.setMerchantId(source.getMerchantId());
        target.setMerchantKeyword(source.getMerchantKeyword());
        target.setIpValue(source.getIpValue());
        target.setIpType(source.getIpType());
        target.setStatus(source.getStatus());
        target.setApprovalStatus(source.getApprovalStatus());
        target.setSubmitSource(source.getSubmitSource());
        return target;
    }

    /**
     * 将 Admin 返回的按商户聚合投影转换为 Excel 行。
     *
     * @param source Admin 返回的商户白名单安全投影
     * @param locale 任务语言环境
     * @return 可写入 Excel 的商户白名单行
     */
    private MerchantIpWhitelistExportRow toExportRow(MerchantIpWhitelistRow source, Locale locale) {
        MerchantIpWhitelistExportRow row = new MerchantIpWhitelistExportRow();
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setMerchantShortName(source.getMerchantShortName());
        row.setIpWhitelists(formatItems(source.getIpWhitelists(), locale));
        row.setUpdateBy(source.getUpdateBy());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /**
     * 按既有同步导出格式拼接当前查询命中的 IP 集合，并本地化启停状态。
     *
     * @param items  当前商户命中的白名单明细
     * @param locale 任务语言环境
     * @return 可直接写入单元格的白名单摘要
     */
    private String formatItems(List<MerchantIpWhitelistItemRow> items, Locale locale) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(item -> item.getIpType() + " " + item.getIpValue()
                        + " (" + statusLabel(item.getStatus(), locale) + ")")
                .collect(Collectors.joining("; "));
    }
}
