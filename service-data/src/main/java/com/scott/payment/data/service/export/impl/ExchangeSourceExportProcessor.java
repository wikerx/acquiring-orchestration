package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeSourceQuery;
import com.scott.payment.data.client.admin.AdminExchangeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.SourcePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.SourcePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.SourceRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExchangeSourceExportRow;
import com.scott.payment.data.service.export.ExportPage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_SOURCE;

/**
 * 管理端汇率源配置异步导出处理器。
 */
@Service
public class ExchangeSourceExportProcessor
        extends AbstractExchangeExportProcessor<ExchangeSourceQuery, ExchangeSourceExportRow> {
    private final AdminExchangeExportSourceClient sourceClient;

    /**
     * 创建汇率源配置异步导出处理器。
     *
     * @param sourceClient 管理端汇率导出源客户端
     * @param artifactWriter 分页 Excel 文件生成器
     * @param messageResolver Excel 国际化解析器
     * @param properties 导出分页和容量配置
     */
    public ExchangeSourceExportProcessor(AdminExchangeExportSourceClient sourceClient,
                                         ExcelExportArtifactWriter artifactWriter,
                                         ExcelI18nMessageResolver messageResolver,
                                         DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回汇率源配置稳定任务类型。
     *
     * @return 任务类型
     */
    @Override
    public String taskType() {
        return ADMIN_EXCHANGE_SOURCE;
    }

    /**
     * 返回冻结查询类型。
     *
     * @return 查询类型
     */
    @Override
    protected Class<ExchangeSourceQuery> queryClass() {
        return ExchangeSourceQuery.class;
    }

    /**
     * 返回 Excel 行类型。
     *
     * @return 行类型
     */
    @Override
    protected Class<ExchangeSourceExportRow> rowClass() {
        return ExchangeSourceExportRow.class;
    }

    /**
     * 返回汇率源标题国际化键。
     */
    @Override
    protected String titleKey() {
        return "excel.exchange.source.title";
    }

    /**
     * 返回汇率源中文标题。
     */
    @Override
    protected String defaultChineseTitle() {
        return "汇率源";
    }

    /**
     * 返回汇率源英文标题。
     */
    @Override
    protected String defaultEnglishTitle() {
        return "Exchange Rate Sources";
    }

    /**
     * 分页读取汇率源配置并转换为 Excel 行。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 服务端页码
     * @param locale 冻结语言
     * @return Excel 行分页
     */
    @Override
    protected ExportPage<ExchangeSourceExportRow> loadPage(
            DataExportTaskDO task, ExchangeSourceQuery query, int pageNo, Locale locale) {
        SourcePageRequest request = new SourcePageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        SourcePageResponse response = sourceClient.loadSourcePage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<ExchangeSourceExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 复制汇率源字段并本地化状态标识。
     *
     * @param source 权威来源行
     * @param locale 冻结语言
     * @return Excel 行
     */
    private ExchangeSourceExportRow toExportRow(SourceRow source, Locale locale) {
        ExchangeSourceExportRow row = new ExchangeSourceExportRow();
        row.setSourceCode(source.getSourceCode());
        row.setSourceName(source.getSourceName());
        row.setSourceType(label("sourceType", source.getSourceType(), locale));
        row.setRequestUrl(source.getRequestUrl());
        row.setDefaultSource(yesNo(source.getDefaultSource(), locale));
        row.setPriority(source.getPriority());
        row.setTimeoutSeconds(source.getTimeoutSeconds());
        row.setSourceStatus(label("binaryStatus", source.getSourceStatus(), locale));
        row.setLastFetchTime(source.getLastFetchTime());
        row.setLastFetchStatus(label("fetchStatus", source.getLastFetchStatus(), locale));
        row.setRemark(source.getRemark());
        row.setCreateTime(source.getCreateTime());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }
}
