package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeRuleQuery;
import com.scott.payment.data.client.admin.AdminExchangeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RulePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RulePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RuleRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExchangeRuleExportRow;
import com.scott.payment.data.service.export.ExportPage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_RULE;

/**
 * 管理端汇率规则配置异步导出处理器。
 */
@Service
public class ExchangeRuleExportProcessor
        extends AbstractExchangeExportProcessor<ExchangeRuleQuery, ExchangeRuleExportRow> {
    private final AdminExchangeExportSourceClient sourceClient;

    /**
     * 创建汇率规则配置异步导出处理器。
     *
     * @param sourceClient 管理端汇率导出源客户端
     * @param artifactWriter 分页 Excel 文件生成器
     * @param messageResolver Excel 国际化解析器
     * @param properties 导出分页和容量配置
     */
    public ExchangeRuleExportProcessor(AdminExchangeExportSourceClient sourceClient,
                                       ExcelExportArtifactWriter artifactWriter,
                                       ExcelI18nMessageResolver messageResolver,
                                       DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    /**
     * 返回汇率规则稳定任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_EXCHANGE_RULE;
    }

    /**
     * 返回冻结查询类型。
     */
    @Override
    protected Class<ExchangeRuleQuery> queryClass() {
        return ExchangeRuleQuery.class;
    }

    /**
     * 返回 Excel 行类型。
     */
    @Override
    protected Class<ExchangeRuleExportRow> rowClass() {
        return ExchangeRuleExportRow.class;
    }

    /**
     * 返回汇率规则标题国际化键。
     */
    @Override
    protected String titleKey() {
        return "excel.exchange.rule.title";
    }

    /**
     * 返回汇率规则中文标题。
     */
    @Override
    protected String defaultChineseTitle() {
        return "汇率规则";
    }

    /**
     * 返回汇率规则英文标题。
     */
    @Override
    protected String defaultEnglishTitle() {
        return "Exchange Rate Rules";
    }

    /**
     * 分页读取汇率规则并转换为 Excel 行。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 服务端页码
     * @param locale 冻结语言
     * @return Excel 行分页
     */
    @Override
    protected ExportPage<ExchangeRuleExportRow> loadPage(
            DataExportTaskDO task, ExchangeRuleQuery query, int pageNo, Locale locale) {
        RulePageRequest request = new RulePageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        RulePageResponse response = sourceClient.loadRulePage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<ExchangeRuleExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 复制汇率规则字段并本地化枚举值，调整值保持 BigDecimal 原值。
     *
     * @param source 权威来源行
     * @param locale 冻结语言
     * @return Excel 行
     */
    private ExchangeRuleExportRow toExportRow(RuleRow source, Locale locale) {
        ExchangeRuleExportRow row = new ExchangeRuleExportRow();
        row.setRateType(label("rateType", source.getRateType(), locale));
        row.setSourceCode(source.getSourceCode());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setRateField(label("rateField", source.getRateField(), locale));
        row.setAdjustDirection(label("adjustDirection", source.getAdjustDirection(), locale));
        row.setAdjustMethod(label("adjustMethod", source.getAdjustMethod(), locale));
        row.setAdjustValue(source.getAdjustValue());
        row.setDecimalScale(source.getDecimalScale());
        row.setRoundingMode(label("roundingMode", source.getRoundingMode(), locale));
        row.setPriority(source.getPriority());
        row.setEffectiveStartTime(source.getEffectiveStartTime());
        row.setEffectiveEndTime(source.getEffectiveEndTime());
        row.setRuleStatus(label("binaryStatus", source.getRuleStatus(), locale));
        row.setRemark(source.getRemark());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }
}
