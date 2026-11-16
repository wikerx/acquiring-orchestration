package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeUsageSnapshotQuery;
import com.scott.payment.data.client.admin.AdminExchangeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.UsageSnapshotPageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.UsageSnapshotPageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.UsageSnapshotRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExchangeUsageSnapshotExportRow;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_USAGE_SNAPSHOT;

/**
 * 管理端汇率使用快照异步导出处理器。
 *
 * <p>处理器将业务固化的 usedRate 原值写入 Excel，不根据来源方向重新换算。</p>
 */
@Service
public class ExchangeUsageSnapshotExportProcessor implements ExportTaskProcessor {
    private final AdminExchangeExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建汇率使用快照异步导出处理器。
     *
     * @param sourceClient 管理端汇率事实来源客户端
     * @param artifactWriter 分页 Excel 文件生成器
     * @param messageResolver Excel 国际化解析器
     * @param properties 导出分页和容量配置
     */
    public ExchangeUsageSnapshotExportProcessor(AdminExchangeExportSourceClient sourceClient,
                                                ExcelExportArtifactWriter artifactWriter,
                                                ExcelI18nMessageResolver messageResolver,
                                                DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 返回汇率使用快照的稳定任务类型。
     *
     * @return 任务类型常量
     */
    @Override
    public String taskType() {
        return ADMIN_EXCHANGE_USAGE_SNAPSHOT;
    }

    /**
     * 解析任务创建时冻结的查询并分页生成汇率使用快照文件。
     *
     * @param task 已领取的导出任务
     * @param storage 私有对象存储
     * @param progressListener 分页进度监听器
     * @param now 本轮执行时间
     * @return 已上传文件的不可变元数据
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task, ExportObjectStorageService storage,
                                  ExportProgressListener progressListener, LocalDateTime now) {
        ExchangeUsageSnapshotQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), ExchangeUsageSnapshotQuery.class);
        if (query == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.exchange.snapshot.title", "汇率使用快照", "Exchange Rate Usage Snapshots",
                ExchangeUsageSnapshotExportRow.class, properties.getPageSize(),
                pageNo -> loadPage(task, query, pageNo, locale));
    }

    /**
     * 使用任务创建人和服务端分页读取一页汇率使用快照事实。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 服务端页码
     * @param locale 冻结语言
     * @return Excel 行分页
     */
    private ExportPage<ExchangeUsageSnapshotExportRow> loadPage(
            DataExportTaskDO task, ExchangeUsageSnapshotQuery query, int pageNo, Locale locale) {
        UsageSnapshotPageRequest request = new UsageSnapshotPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        UsageSnapshotPageResponse response = sourceClient.loadUsageSnapshotPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<ExchangeUsageSnapshotExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(source -> toExportRow(source, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 复制已使用汇率及溯源字段，仅本地化汇率类型和使用场景。
     *
     * @param source 汇率使用快照来源行
     * @param locale 冻结语言
     * @return Excel 行
     */
    private ExchangeUsageSnapshotExportRow toExportRow(UsageSnapshotRow source, Locale locale) {
        ExchangeUsageSnapshotExportRow row = new ExchangeUsageSnapshotExportRow();
        row.setRateType(label("rateType", source.getRateType(), locale));
        row.setUsageEventId(source.getUsageEventId());
        row.setProducerService(source.getProducerService());
        row.setUsageScene(label("usageScene", source.getUsageScene(), locale));
        row.setBusinessType(source.getBusinessType());
        row.setBusinessNo(source.getBusinessNo());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setUsedRate(source.getUsedRate());
        row.setRawRateId(source.getRawRateId());
        row.setRuleId(source.getRuleId());
        row.setBusinessRateId(source.getBusinessRateId());
        row.setRateSource(source.getRateSource());
        row.setSourceQuoteDirection(source.getSourceQuoteDirection());
        row.setRateEffectiveTime(source.getRateEffectiveTime());
        row.setAppliedTime(source.getAppliedTime());
        row.setCalculationDescription(source.getCalculationDescription());
        row.setCreateTime(source.getCreateTime());
        return row;
    }

    /**
     * 解析汇率枚举展示值，缺少翻译时保留协议原值。
     *
     * @param category 汇率枚举分类
     * @param value 协议原值
     * @param locale 冻结语言
     * @return 国际化展示值或协议原值
     */
    private String label(String category, String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        String key = "excel.exchange.enum." + category + "." + normalized;
        String resolved = messageResolver.resolve(key, locale);
        return key.equals(resolved) ? normalized : resolved;
    }

    /**
     * 解析任务冻结语言，未知语言回退简体中文。
     *
     * @param languageTag IETF 语言标签
     * @return 可用于 Excel 的语言环境
     */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
