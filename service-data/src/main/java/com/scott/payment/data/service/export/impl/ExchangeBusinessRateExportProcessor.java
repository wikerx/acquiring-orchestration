package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeBusinessRateQuery;
import com.scott.payment.data.client.admin.AdminExchangeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.BusinessRatePageRequest;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.BusinessRatePageResponse;
import com.scott.payment.data.client.admin.dto.AdminExchangeExportSourceDTOs.BusinessRateRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExchangeBusinessRateExportRow;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_EXCHANGE_BUSINESS_RATE;

/**
 * 管理端业务汇率事实异步导出处理器。
 *
 * <p>处理器直接输出已生成事实，禁止重新套用规则、舍入或改变报价方向。</p>
 */
@Service
public class ExchangeBusinessRateExportProcessor implements ExportTaskProcessor {
    private final AdminExchangeExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建业务汇率事实异步导出处理器。
     *
     * @param sourceClient 管理端汇率事实来源客户端
     * @param artifactWriter 分页 Excel 文件生成器
     * @param messageResolver Excel 国际化解析器
     * @param properties 导出分页和容量配置
     */
    public ExchangeBusinessRateExportProcessor(AdminExchangeExportSourceClient sourceClient,
                                               ExcelExportArtifactWriter artifactWriter,
                                               ExcelI18nMessageResolver messageResolver,
                                               DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 返回业务汇率事实的稳定任务类型。
     *
     * @return 任务类型常量
     */
    @Override
    public String taskType() {
        return ADMIN_EXCHANGE_BUSINESS_RATE;
    }

    /**
     * 解析任务创建时冻结的查询并分页生成业务汇率文件。
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
        ExchangeBusinessRateQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), ExchangeBusinessRateQuery.class);
        if (query == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.exchange.business.title", "业务汇率", "Business Exchange Rates",
                ExchangeBusinessRateExportRow.class, properties.getPageSize(),
                pageNo -> loadPage(task, query, pageNo, locale));
    }

    /**
     * 使用任务创建人和服务端分页读取一页业务汇率事实。
     *
     * @param task 已领取任务
     * @param query 冻结查询
     * @param pageNo 服务端页码
     * @param locale 冻结语言
     * @return Excel 行分页
     */
    private ExportPage<ExchangeBusinessRateExportRow> loadPage(
            DataExportTaskDO task, ExchangeBusinessRateQuery query, int pageNo, Locale locale) {
        BusinessRatePageRequest request = new BusinessRatePageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        BusinessRatePageResponse response = sourceClient.loadBusinessRatePage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<ExchangeBusinessRateExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(source -> toExportRow(source, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 复制业务汇率事实，仅本地化固定枚举。
     *
     * @param source 业务汇率来源行
     * @param locale 冻结语言
     * @return Excel 行
     */
    private ExchangeBusinessRateExportRow toExportRow(BusinessRateRow source, Locale locale) {
        ExchangeBusinessRateExportRow row = new ExchangeBusinessRateExportRow();
        row.setRateType(label("rateType", source.getRateType(), locale));
        row.setSourceCode(source.getSourceCode());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setOriginalRate(source.getOriginalRate());
        row.setFinalRate(source.getFinalRate());
        row.setEffectiveTime(source.getEffectiveTime());
        row.setExpireTime(source.getExpireTime());
        row.setGenerateMethod(label("generateMethod", source.getGenerateMethod(), locale));
        row.setRateStatus(label("businessRateStatus", source.getRateStatus(), locale));
        row.setRawRateId(source.getRawRateId());
        row.setRuleId(source.getRuleId());
        row.setAdjustDescription(source.getAdjustDescription());
        row.setRemark(source.getRemark());
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
