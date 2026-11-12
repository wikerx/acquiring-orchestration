package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantSettlementSummaryQuery;
import com.scott.payment.data.client.merchant.MerchantExportSourceClient;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementSummaryPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementSummaryPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementSummaryRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.MerchantExportScopeSnapshot;
import com.scott.payment.data.service.export.MerchantSettlementSummaryExportRow;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.MERCHANT_SETTLEMENT_SUMMARY;

/**
 * 商户结算汇总异步导出处理器，按冻结商户号复核批次归属后分页生成文件。
 */
@Service
public class MerchantSettlementSummaryExportProcessor implements ExportTaskProcessor {
    private final MerchantExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建商户结算汇总异步导出处理器。
     */
    public MerchantSettlementSummaryExportProcessor(MerchantExportSourceClient sourceClient,
                                                    ExcelExportArtifactWriter artifactWriter,
                                                    ExcelI18nMessageResolver messageResolver,
                                                    DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 返回商户结算汇总任务类型。
     */
    @Override
    public String taskType() {
        return MERCHANT_SETTLEMENT_SUMMARY;
    }

    /**
     * 解析冻结快照并分页生成商户结算汇总文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        MerchantSettlementSummaryQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), MerchantSettlementSummaryQuery.class);
        MerchantExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), MerchantExportScopeSnapshot.class);
        MerchantSettlementExportSupport.requireScope(task, query, scope);
        Locale locale = MerchantSettlementExportSupport.resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.merchantSettlement.summaryTitle", "结算汇总", "Settlement Summary",
                MerchantSettlementSummaryExportRow.class,
                pageNo -> loadPage(task, query, pageNo, locale));
    }

    /**
     * 读取一页商户结算汇总安全投影并转换为文件行。
     */
    private ExportPage<MerchantSettlementSummaryExportRow> loadPage(
            DataExportTaskDO task, MerchantSettlementSummaryQuery query, int pageNo, Locale locale) {
        SettlementSummaryPageRequest request = new SettlementSummaryPageRequest();
        request.setMerchantId(task.getMerchantId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        SettlementSummaryPageResponse response = sourceClient.loadSettlementSummaryPage(request);
        if (response == null) {
            throw new IllegalStateException("merchant settlement summary source response is missing");
        }
        List<MerchantSettlementSummaryExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将结算汇总安全投影转换为本地化文件行，不重新聚合金额。
     */
    private MerchantSettlementSummaryExportRow toExportRow(SettlementSummaryRow source, Locale locale) {
        MerchantSettlementSummaryExportRow row = new MerchantSettlementSummaryExportRow();
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

    /**
     * 本地化结算协议值，缺少翻译时保留原值。
     */
    private String label(String category, String value, Locale locale) {
        return MerchantSettlementExportSupport.settlementLabel(messageResolver, category, value, locale);
    }
}
