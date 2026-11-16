package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CountryQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CountryPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CountryPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CountryRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.CountryExportRow;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_COUNTRY;

/**
 * 管理端国家和地区异步导出处理器，按冻结条件分页读取 Admin 安全投影。
 */
@Service
public class CountryExportProcessor
        extends AbstractBaseReferenceExportProcessor<CountryQuery, CountryExportRow> {
    private final AdminPlatformExportSourceClient sourceClient;

    /**
     * 创建国家和地区异步导出处理器。
     *
     * @param sourceClient Admin 平台级导出源客户端
     * @param artifactWriter 统一分页 Excel 写入器
     * @param messageResolver Excel 国际化解析器
     * @param properties 异步导出容量配置
     */
    public CountryExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                  ExcelExportArtifactWriter artifactWriter,
                                  ExcelI18nMessageResolver messageResolver,
                                  DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回国家和地区稳定任务类型。
     *
     * @return 任务类型
     */
    @Override
    public String taskType() {
        return ADMIN_COUNTRY;
    }

    /**
     * 返回国家和地区查询快照类型。
     *
     * @return 查询类型
     */
    @Override
    protected Class<CountryQuery> queryClass() {
        return CountryQuery.class;
    }

    /**
     * 返回国家和地区 Excel 标题键。
     *
     * @return 标题键
     */
    @Override
    protected String titleKey() {
        return "excel.country.title";
    }

    /**
     * 返回国家和地区中文标题。
     *
     * @return 中文标题
     */
    @Override
    protected String chineseTitle() {
        return "国家和地区";
    }

    /**
     * 返回国家和地区英文标题。
     *
     * @return 英文标题
     */
    @Override
    protected String englishTitle() {
        return "Countries and Regions";
    }

    /**
     * 返回国家和地区 Excel 行类型。
     *
     * @return 行类型
     */
    @Override
    protected Class<CountryExportRow> rowClass() {
        return CountryExportRow.class;
    }

    /**
     * 按冻结筛选读取一页国家和地区并本地化状态。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 页码
     * @param locale 冻结语言
     * @return 导出分页
     */
    @Override
    protected ExportPage<CountryExportRow> loadPage(
            DataExportTaskDO task, CountryQuery query, int pageNo, Locale locale) {
        CountryPageRequest request = new CountryPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        CountryPageResponse response = sourceClient.loadCountryPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<CountryExportRow> rows = response.getRecords() == null
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
    private CountryExportRow toExportRow(CountryRow source, Locale locale) {
        CountryExportRow row = new CountryExportRow();
        row.setAlpha2Code(source.getAlpha2Code());
        row.setAlpha3Code(source.getAlpha3Code());
        row.setChineseName(source.getChineseName());
        row.setEnglishName(source.getEnglishName());
        row.setContinentName(source.getContinentName());
        row.setCurrencyAlpha3Code(source.getCurrencyAlpha3Code());
        row.setStatus(statusLabel(source.getStatus(), locale));
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }
}
