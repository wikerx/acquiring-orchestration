package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CurrencyQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CurrencyPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CurrencyPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CurrencyRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.CurrencyExportRow;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_CURRENCY;

/**
 * 管理端币种异步导出处理器，保持 ISO 数字代码的文本语义。
 */
@Service
public class CurrencyExportProcessor
        extends AbstractBaseReferenceExportProcessor<CurrencyQuery, CurrencyExportRow> {
    private final AdminPlatformExportSourceClient sourceClient;

    /**
     * 创建币种异步导出处理器。
     *
     * @param sourceClient Admin 平台级导出源客户端
     * @param artifactWriter 统一分页 Excel 写入器
     * @param messageResolver Excel 国际化解析器
     * @param properties 异步导出容量配置
     */
    public CurrencyExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                   ExcelExportArtifactWriter artifactWriter,
                                   ExcelI18nMessageResolver messageResolver,
                                   DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回币种稳定任务类型。
     *
     * @return 任务类型
     */
    @Override
    public String taskType() {
        return ADMIN_CURRENCY;
    }

    /**
     * 返回币种查询快照类型。
     *
     * @return 查询类型
     */
    @Override
    protected Class<CurrencyQuery> queryClass() {
        return CurrencyQuery.class;
    }

    /**
     * 返回币种 Excel 标题键。
     *
     * @return 标题键
     */
    @Override
    protected String titleKey() {
        return "excel.currency.title";
    }

    /**
     * 返回币种中文标题。
     *
     * @return 中文标题
     */
    @Override
    protected String chineseTitle() {
        return "币种列表";
    }

    /**
     * 返回币种英文标题。
     *
     * @return 英文标题
     */
    @Override
    protected String englishTitle() {
        return "Currencies";
    }

    /**
     * 返回币种 Excel 行类型。
     *
     * @return 行类型
     */
    @Override
    protected Class<CurrencyExportRow> rowClass() {
        return CurrencyExportRow.class;
    }

    /**
     * 按冻结筛选读取一页币种并本地化状态。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 页码
     * @param locale 冻结语言
     * @return 导出分页
     */
    @Override
    protected ExportPage<CurrencyExportRow> loadPage(
            DataExportTaskDO task, CurrencyQuery query, int pageNo, Locale locale) {
        CurrencyPageRequest request = new CurrencyPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        CurrencyPageResponse response = sourceClient.loadCurrencyPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<CurrencyExportRow> rows = response.getRecords() == null
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
    private CurrencyExportRow toExportRow(CurrencyRow source, Locale locale) {
        CurrencyExportRow row = new CurrencyExportRow();
        row.setAlpha3Code(source.getAlpha3Code());
        row.setNumericCode(source.getNumericCode());
        row.setChineseName(source.getChineseName());
        row.setEnglishName(source.getEnglishName());
        row.setCurrencySymbol(source.getCurrencySymbol());
        row.setFractionDigits(source.getFractionDigits());
        row.setStatus(statusLabel(source.getStatus(), locale));
        row.setCreatedAt(source.getCreatedAt());
        return row;
    }
}
