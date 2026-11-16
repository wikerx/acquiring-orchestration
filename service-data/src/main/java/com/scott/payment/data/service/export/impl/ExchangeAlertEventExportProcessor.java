package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeAlertEventQuery;
import com.scott.payment.data.client.admin.AdminExchangeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertEventPageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertEventPageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertEventRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExchangeAlertEventExportRow;
import com.scott.payment.data.service.export.ExportPage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_ALERT_EVENT;

/**
 * 管理端汇率预警事件异步导出处理器，只读取冻结事实而不改变事件状态。
 */
@Service
public class ExchangeAlertEventExportProcessor
        extends AbstractExchangeExportProcessor<ExchangeAlertEventQuery, ExchangeAlertEventExportRow> {
    private final AdminExchangeExportSourceClient sourceClient;

    /**
     * 创建汇率预警事件异步导出处理器。
     *
     * @param sourceClient 管理端汇率导出源客户端
     * @param artifactWriter 分页 Excel 文件生成器
     * @param messageResolver Excel 国际化解析器
     * @param properties 导出分页和容量配置
     */
    public ExchangeAlertEventExportProcessor(AdminExchangeExportSourceClient sourceClient,
                                             ExcelExportArtifactWriter artifactWriter,
                                             ExcelI18nMessageResolver messageResolver,
                                             DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回汇率预警事件稳定任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_EXCHANGE_ALERT_EVENT;
    }

    /**
     * 返回冻结查询类型。
     */
    @Override
    protected Class<ExchangeAlertEventQuery> queryClass() {
        return ExchangeAlertEventQuery.class;
    }

    /**
     * 返回 Excel 行类型。
     */
    @Override
    protected Class<ExchangeAlertEventExportRow> rowClass() {
        return ExchangeAlertEventExportRow.class;
    }

    /**
     * 返回汇率预警事件标题国际化键。
     */
    @Override
    protected String titleKey() {
        return "excel.exchange.alertEvent.title";
    }

    /**
     * 返回汇率预警事件中文标题。
     */
    @Override
    protected String defaultChineseTitle() {
        return "汇率预警事件";
    }

    /**
     * 返回汇率预警事件英文标题。
     */
    @Override
    protected String defaultEnglishTitle() {
        return "Exchange Rate Alert Events";
    }

    /**
     * 分页读取预警事件事实并转换为 Excel 行。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 服务端页码
     * @param locale 冻结语言
     * @return Excel 行分页
     */
    @Override
    protected ExportPage<ExchangeAlertEventExportRow> loadPage(
            DataExportTaskDO task, ExchangeAlertEventQuery query, int pageNo, Locale locale) {
        AlertEventPageRequest request = new AlertEventPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        AlertEventPageResponse response = sourceClient.loadAlertEventPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<ExchangeAlertEventExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 复制预警事件冻结证据并本地化展示状态。
     *
     * @param source 权威来源行
     * @param locale 冻结语言
     * @return Excel 行
     */
    private ExchangeAlertEventExportRow toExportRow(AlertEventRow source, Locale locale) {
        ExchangeAlertEventExportRow row = new ExchangeAlertEventExportRow();
        row.setEventNo(source.getEventNo());
        row.setRuleCode(source.getRuleCode());
        row.setRateType(label("rateType", source.getRateType(), locale));
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setAlertLevel(label("alertLevel", source.getAlertLevel(), locale));
        row.setEventStatus(label("eventStatus", source.getEventStatus(), locale));
        row.setVolatilityRate(source.getVolatilityRate());
        row.setMaximumVolatilityRate(source.getMaximumVolatilityRate());
        row.setTriggerThreshold(source.getTriggerThreshold());
        row.setMinimumRate(source.getMinimumRate());
        row.setMinimumRateSource(source.getMinimumRateSource());
        row.setMaximumRate(source.getMaximumRate());
        row.setMaximumRateSource(source.getMaximumRateSource());
        row.setLatestRate(source.getLatestRate());
        row.setLatestRateSource(source.getLatestRateSource());
        row.setSampleCount(source.getSampleCount());
        row.setFirstTriggerTime(source.getFirstTriggerTime());
        row.setLastDetectedTime(source.getLastDetectedTime());
        row.setAcknowledgedBy(source.getAcknowledgedBy());
        row.setResolvedTime(source.getResolvedTime());
        return row;
    }
}
