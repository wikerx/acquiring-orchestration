package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeVolatilityQuery;
import com.scott.payment.data.client.admin.AdminExchangeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.VolatilityPageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.VolatilityPageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.VolatilityRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExchangeVolatilityExportRow;
import com.scott.payment.data.service.export.ExportPage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_VOLATILITY;

/**
 * 管理端汇率波动摘要异步导出处理器，只复制 Admin 权威算法结果。
 */
@Service
public class ExchangeVolatilityExportProcessor
        extends AbstractExchangeExportProcessor<ExchangeVolatilityQuery, ExchangeVolatilityExportRow> {
    private final AdminExchangeExportSourceClient sourceClient;

    /**
     * 创建汇率波动摘要异步导出处理器。
     *
     * @param sourceClient 管理端汇率导出源客户端
     * @param artifactWriter 分页 Excel 文件生成器
     * @param messageResolver Excel 国际化解析器
     * @param properties 导出分页和容量配置
     */
    public ExchangeVolatilityExportProcessor(AdminExchangeExportSourceClient sourceClient,
                                             ExcelExportArtifactWriter artifactWriter,
                                             ExcelI18nMessageResolver messageResolver,
                                             DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回汇率波动稳定任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_EXCHANGE_VOLATILITY;
    }

    /**
     * 返回冻结查询类型。
     */
    @Override
    protected Class<ExchangeVolatilityQuery> queryClass() {
        return ExchangeVolatilityQuery.class;
    }

    /**
     * 返回 Excel 行类型。
     */
    @Override
    protected Class<ExchangeVolatilityExportRow> rowClass() {
        return ExchangeVolatilityExportRow.class;
    }

    /**
     * 返回汇率波动标题国际化键。
     */
    @Override
    protected String titleKey() {
        return "excel.exchange.volatility.title";
    }

    /**
     * 返回汇率波动中文标题。
     */
    @Override
    protected String defaultChineseTitle() {
        return "汇率波动";
    }

    /**
     * 返回汇率波动英文标题。
     */
    @Override
    protected String defaultEnglishTitle() {
        return "Exchange Rate Volatility";
    }

    /**
     * 分页读取权威波动摘要并转换为 Excel 行。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 服务端页码
     * @param locale 冻结语言
     * @return Excel 行分页
     */
    @Override
    protected ExportPage<ExchangeVolatilityExportRow> loadPage(
            DataExportTaskDO task, ExchangeVolatilityQuery query, int pageNo, Locale locale) {
        VolatilityPageRequest request = new VolatilityPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        VolatilityPageResponse response = sourceClient.loadVolatilityPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<ExchangeVolatilityExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 复制权威波动摘要，不重新计算汇率或波动百分比。
     *
     * @param source 权威来源行
     * @param locale 冻结语言
     * @return Excel 行
     */
    private ExchangeVolatilityExportRow toExportRow(VolatilityRow source, Locale locale) {
        ExchangeVolatilityExportRow row = new ExchangeVolatilityExportRow();
        row.setRateType(label("rateType", source.getRateType(), locale));
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setLatestRate(source.getLatestRate());
        row.setMinimumRate(source.getMinimumRate());
        row.setMaximumRate(source.getMaximumRate());
        row.setVolatilityRate(source.getVolatilityRate());
        row.setSampleCount(source.getSampleCount());
        row.setLatestEffectiveTime(source.getLatestEffectiveTime());
        row.setAlertStatus(label("alertStatus", source.getAlertStatus(), locale));
        return row;
    }
}
