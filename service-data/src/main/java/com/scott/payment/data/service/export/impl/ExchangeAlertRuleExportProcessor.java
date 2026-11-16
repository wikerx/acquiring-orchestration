package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeAlertRuleQuery;
import com.scott.payment.data.client.admin.AdminExchangeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertRulePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertRulePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.AlertRuleRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExchangeAlertRuleExportRow;
import com.scott.payment.data.service.export.ExportPage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_ALERT_RULE;

/**
 * 管理端汇率预警规则异步导出处理器。
 */
@Service
public class ExchangeAlertRuleExportProcessor
        extends AbstractExchangeExportProcessor<ExchangeAlertRuleQuery, ExchangeAlertRuleExportRow> {
    private final AdminExchangeExportSourceClient sourceClient;

    /**
     * 创建汇率预警规则异步导出处理器。
     *
     * @param sourceClient 管理端汇率导出源客户端
     * @param artifactWriter 分页 Excel 文件生成器
     * @param messageResolver Excel 国际化解析器
     * @param properties 导出分页和容量配置
     */
    public ExchangeAlertRuleExportProcessor(AdminExchangeExportSourceClient sourceClient,
                                            ExcelExportArtifactWriter artifactWriter,
                                            ExcelI18nMessageResolver messageResolver,
                                            DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回汇率预警规则稳定任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_EXCHANGE_ALERT_RULE;
    }

    /**
     * 返回冻结查询类型。
     */
    @Override
    protected Class<ExchangeAlertRuleQuery> queryClass() {
        return ExchangeAlertRuleQuery.class;
    }

    /**
     * 返回 Excel 行类型。
     */
    @Override
    protected Class<ExchangeAlertRuleExportRow> rowClass() {
        return ExchangeAlertRuleExportRow.class;
    }

    /**
     * 返回汇率预警规则标题国际化键。
     */
    @Override
    protected String titleKey() {
        return "excel.exchange.alertRule.title";
    }

    /**
     * 返回汇率预警规则中文标题。
     */
    @Override
    protected String defaultChineseTitle() {
        return "汇率预警规则";
    }

    /**
     * 返回汇率预警规则英文标题。
     */
    @Override
    protected String defaultEnglishTitle() {
        return "Exchange Rate Alert Rules";
    }

    /**
     * 分页读取预警规则并转换为 Excel 行。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 服务端页码
     * @param locale 冻结语言
     * @return Excel 行分页
     */
    @Override
    protected ExportPage<ExchangeAlertRuleExportRow> loadPage(
            DataExportTaskDO task, ExchangeAlertRuleQuery query, int pageNo, Locale locale) {
        AlertRulePageRequest request = new AlertRulePageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        AlertRulePageResponse response = sourceClient.loadAlertRulePage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<ExchangeAlertRuleExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 复制预警规则字段并本地化状态，百分比字符串保持原值。
     *
     * @param source 权威来源行
     * @param locale 冻结语言
     * @return Excel 行
     */
    private ExchangeAlertRuleExportRow toExportRow(AlertRuleRow source, Locale locale) {
        ExchangeAlertRuleExportRow row = new ExchangeAlertRuleExportRow();
        row.setRuleCode(source.getRuleCode());
        row.setRuleName(source.getRuleName());
        row.setRateType(label("rateType", source.getRateType(), locale));
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setWindowMinutes(source.getWindowMinutes());
        row.setTriggerThreshold(source.getTriggerThreshold());
        row.setRecoveryThreshold(source.getRecoveryThreshold());
        row.setRecoveryConsecutiveCount(source.getRecoveryConsecutiveCount());
        row.setMinimumSampleCount(source.getMinimumSampleCount());
        row.setAlertLevel(label("alertLevel", source.getAlertLevel(), locale));
        row.setSystemMandatory(yesNo(source.getSystemMandatory(), locale));
        row.setRuleStatus(label("binaryStatus", source.getRuleStatus(), locale));
        row.setRemark(source.getRemark());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }
}
