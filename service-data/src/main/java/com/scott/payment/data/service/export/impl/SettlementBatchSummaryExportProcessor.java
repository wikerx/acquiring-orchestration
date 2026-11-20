package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SettlementBatchSummaryQuery;
import com.scott.payment.data.client.admin.AdminSettlementExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementBatchSummaryPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementBatchSummaryPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementBatchSummaryRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.SettlementBatchSummaryExportRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SETTLEMENT_BATCH_SUMMARY;

/**
 * 管理端单个结算批次结果摘要异步导出处理器。
 */
@Service
public class SettlementBatchSummaryExportProcessor implements ExportTaskProcessor {
    private final AdminSettlementExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    public SettlementBatchSummaryExportProcessor(AdminSettlementExportSourceClient sourceClient,
                                                 ExcelExportArtifactWriter artifactWriter,
                                                 ExcelI18nMessageResolver messageResolver,
                                                 DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 管理端结算批次摘要专属任务类型，防止调度到不匹配的导出处理器。
     */
    @Override
    public String taskType() {
        return ADMIN_SETTLEMENT_BATCH_SUMMARY;
    }

    /**
     * 校验查询及数据范围快照后分页读取结算批次摘要，按任务语言写入私有文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        SettlementBatchSummaryQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), SettlementBatchSummaryQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.settlement.batchSummaryTitle", "结算批次摘要", "Settlement Batch Summary",
                SettlementBatchSummaryExportRow.class,
                pageNo -> loadPage(task, query, scope, pageNo, locale));
    }

    private ExportPage<SettlementBatchSummaryExportRow> loadPage(
            DataExportTaskDO task,
            SettlementBatchSummaryQuery query,
            AdminExportScopeSnapshot scope,
            int pageNo,
            Locale locale) {
        SettlementBatchSummaryPageRequest request = new SettlementBatchSummaryPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setSummaryProjection(true);
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        SettlementBatchSummaryPageResponse response = sourceClient.loadSettlementBatchSummaryPage(request);
        if (response == null) {
            throw new IllegalStateException("settlement batch summary source response is missing");
        }
        List<SettlementBatchSummaryExportRow> rows = response.getSummaryRecords() == null
                ? List.of() : response.getSummaryRecords().stream()
                .map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    private SettlementBatchSummaryExportRow toExportRow(
            SettlementBatchSummaryRow source, Locale locale) {
        SettlementBatchSummaryExportRow row = new SettlementBatchSummaryExportRow();
        row.setPaymentType(label("paymentType", source.getPaymentType(), locale));
        row.setPaymentMethod(label("paymentMethod", source.getPaymentMethod(), locale));
        row.setTransactionType(label("transactionType", source.getTransactionType(), locale));
        row.setResultItemType(label("resultItemType", source.getResultItemType(), locale));
        row.setFeeCategory(label("feeCategory", source.getFeeCategory(), locale));
        row.setDirection(label("direction", source.getDirection(), locale));
        row.setSourceCurrency(source.getSourceCurrency());
        row.setSourceAmount(source.getSourceAmount());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setTargetAmount(source.getTargetAmount());
        row.setTransactionCount(source.getTransactionCount());
        return row;
    }

    private String label(String category, String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        String key = "excel.settlement.enum." + category + "." + normalized;
        String resolved = messageResolver.resolve(key, locale);
        return key.equals(resolved) ? normalized : resolved;
    }

    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null
                ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag.replace('_', '-'));
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
