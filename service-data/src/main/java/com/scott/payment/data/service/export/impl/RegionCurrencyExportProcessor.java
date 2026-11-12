package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.RegionCurrencyQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.RegionCurrencyPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.RegionCurrencyPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.RegionCurrencyRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.RegionCurrencyExportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_REGION_CURRENCY;

/**
 * 管理端国家默认币种关系异步导出处理器，复用 Admin 权威分页语义。
 */
@Service
public class RegionCurrencyExportProcessor
        extends AbstractBaseReferenceExportProcessor<RegionCurrencyQuery, RegionCurrencyExportRow> {
    private final AdminPlatformExportSourceClient sourceClient;

    /**
     * 创建国家默认币种关系异步导出处理器。
     *
     * @param sourceClient Admin 平台级导出源客户端
     * @param artifactWriter 统一分页 Excel 写入器
     * @param messageResolver Excel 国际化解析器
     * @param properties 异步导出容量配置
     */
    public RegionCurrencyExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                         ExcelExportArtifactWriter artifactWriter,
                                         ExcelI18nMessageResolver messageResolver,
                                         DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回国家默认币种关系稳定任务类型。
     *
     * @return 任务类型
     */
    @Override
    public String taskType() {
        return ADMIN_REGION_CURRENCY;
    }

    /**
     * 返回国家默认币种关系查询快照类型。
     *
     * @return 查询类型
     */
    @Override
    protected Class<RegionCurrencyQuery> queryClass() {
        return RegionCurrencyQuery.class;
    }

    /**
     * 返回国家默认币种关系 Excel 标题键。
     *
     * @return 标题键
     */
    @Override
    protected String titleKey() {
        return "excel.regionCurrency.title";
    }

    /**
     * 返回国家默认币种关系中文标题。
     *
     * @return 中文标题
     */
    @Override
    protected String chineseTitle() {
        return "地区币种配置";
    }

    /**
     * 返回国家默认币种关系英文标题。
     *
     * @return 英文标题
     */
    @Override
    protected String englishTitle() {
        return "Region Currencies";
    }

    /**
     * 返回国家默认币种关系 Excel 行类型。
     *
     * @return 行类型
     */
    @Override
    protected Class<RegionCurrencyExportRow> rowClass() {
        return RegionCurrencyExportRow.class;
    }

    /**
     * 按冻结筛选读取一页国家默认币种关系并本地化状态。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 页码
     * @param locale 冻结语言
     * @return 导出分页
     */
    @Override
    protected ExportPage<RegionCurrencyExportRow> loadPage(
            DataExportTaskDO task, RegionCurrencyQuery query, int pageNo, Locale locale) {
        RegionCurrencyPageRequest request = new RegionCurrencyPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        RegionCurrencyPageResponse response = sourceClient.loadRegionCurrencyPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<RegionCurrencyExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将 Admin 安全投影转换为任务冻结语言下的 Excel 行。
     *
     * @param source Admin 安全投影
     * @param locale 冻结语言
     * @return Excel 行
     */
    private RegionCurrencyExportRow toExportRow(RegionCurrencyRow source, Locale locale) {
        RegionCurrencyExportRow row = new RegionCurrencyExportRow();
        row.setAlpha2Code(source.getAlpha2Code());
        row.setCountryName(source.getCountryName());
        row.setContinentName(source.getContinentName());
        row.setCurrencyAlpha3Code(source.getCurrencyAlpha3Code());
        row.setCurrencyName(source.getCurrencyName());
        row.setCurrencySymbol(source.getCurrencySymbol());
        row.setStatus(statusLabel(source.getStatus(), locale));
        return row;
    }
}
