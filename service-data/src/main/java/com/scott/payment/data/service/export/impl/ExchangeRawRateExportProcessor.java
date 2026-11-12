package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeRawRateQuery;
import com.scott.payment.data.client.admin.AdminExchangeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RawRatePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RawRatePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.RawRateRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExchangeRawRateExportRow;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_RAW_RATE;

/**
 * 管理端原始汇率事实异步导出处理器。
 *
 * <p>处理器只分页复制来源报价，不执行汇率取倒数、方向转换或精度调整。</p>
 */
@Service
public class ExchangeRawRateExportProcessor implements ExportTaskProcessor {
    private final AdminExchangeExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建原始汇率事实异步导出处理器。
     *
     * @param sourceClient 管理端汇率事实来源客户端
     * @param artifactWriter 分页 Excel 文件生成器
     * @param messageResolver Excel 国际化解析器
     * @param properties 导出分页和容量配置
     */
    public ExchangeRawRateExportProcessor(AdminExchangeExportSourceClient sourceClient,
                                          ExcelExportArtifactWriter artifactWriter,
                                          ExcelI18nMessageResolver messageResolver,
                                          DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 返回原始汇率事实的稳定任务类型。
     *
     * @return 任务类型常量
     */
    @Override
    public String taskType() {
        return ADMIN_EXCHANGE_RAW_RATE;
    }

    /**
     * 解析任务创建时冻结的查询并分页生成原始汇率文件。
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
        ExchangeRawRateQuery query = JsonUtils.parseObject(task.getQuerySnapshot(), ExchangeRawRateQuery.class);
        if (query == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.exchange.raw.title", "原始汇率", "Raw Exchange Rates",
                ExchangeRawRateExportRow.class, properties.getPageSize(),
                pageNo -> loadPage(task, query, pageNo, locale));
    }

    /**
     * 使用任务创建人和服务端分页读取一页原始汇率事实。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 服务端页码
     * @param locale 冻结语言
     * @return Excel 行分页
     */
    private ExportPage<ExchangeRawRateExportRow> loadPage(
            DataExportTaskDO task, ExchangeRawRateQuery query, int pageNo, Locale locale) {
        RawRatePageRequest request = new RawRatePageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        RawRatePageResponse response = sourceClient.loadRawRatePage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<ExchangeRawRateExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(source -> toExportRow(source, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 复制原始报价字段，仅本地化创建方式和状态。
     *
     * @param source 原始汇率来源行
     * @param locale 冻结语言
     * @return Excel 行
     */
    private ExchangeRawRateExportRow toExportRow(RawRateRow source, Locale locale) {
        ExchangeRawRateExportRow row = new ExchangeRawRateExportRow();
        row.setSourceCode(source.getSourceCode());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setSpotBuyRate(source.getSpotBuyRate());
        row.setSpotSellRate(source.getSpotSellRate());
        row.setCashBuyRate(source.getCashBuyRate());
        row.setCashSellRate(source.getCashSellRate());
        row.setMiddleRate(source.getMiddleRate());
        row.setPublishTime(source.getPublishTime());
        row.setFetchTime(source.getFetchTime());
        row.setEffectiveTime(source.getEffectiveTime());
        row.setCreateMethod(label("createMethod", source.getCreateMethod(), locale));
        row.setBatchNo(source.getBatchNo());
        row.setRateStatus(label("rawRateStatus", source.getRateStatus(), locale));
        row.setVoidReason(source.getVoidReason());
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
