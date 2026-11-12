package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantSettlementTransactionQuery;
import com.scott.payment.data.client.merchant.MerchantExportSourceClient;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementTransactionPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementTransactionPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementTransactionRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.MerchantExportScopeSnapshot;
import com.scott.payment.data.service.export.MerchantSettlementTransactionExportRow;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.MERCHANT_SETTLEMENT_TRANSACTION;

/**
 * 商户交易结算异步导出处理器，导出商户清分来源金额到结算金额的只读结果。
 */
@Service
public class MerchantSettlementTransactionExportProcessor implements ExportTaskProcessor {
    private final MerchantExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建商户交易结算异步导出处理器。
     */
    public MerchantSettlementTransactionExportProcessor(MerchantExportSourceClient sourceClient,
                                                        ExcelExportArtifactWriter artifactWriter,
                                                        ExcelI18nMessageResolver messageResolver,
                                                        DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 返回商户交易结算任务类型。
     */
    @Override
    public String taskType() {
        return MERCHANT_SETTLEMENT_TRANSACTION;
    }

    /**
     * 解析冻结快照并分页生成商户交易结算文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        MerchantSettlementTransactionQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), MerchantSettlementTransactionQuery.class);
        MerchantExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), MerchantExportScopeSnapshot.class);
        MerchantSettlementExportSupport.requireScope(task, query, scope);
        Locale locale = MerchantSettlementExportSupport.resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.merchantSettlement.transactionTitle", "交易结算明细", "Settlement Transactions",
                MerchantSettlementTransactionExportRow.class,
                pageNo -> loadPage(task, query, pageNo, locale));
    }

    /**
     * 读取一页商户交易结算安全投影并转换为文件行。
     */
    private ExportPage<MerchantSettlementTransactionExportRow> loadPage(
            DataExportTaskDO task,
            MerchantSettlementTransactionQuery query,
            int pageNo,
            Locale locale) {
        SettlementTransactionPageRequest request = new SettlementTransactionPageRequest();
        request.setMerchantId(task.getMerchantId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        SettlementTransactionPageResponse response = sourceClient.loadSettlementTransactionPage(request);
        if (response == null) {
            throw new IllegalStateException("merchant settlement transaction source response is missing");
        }
        List<MerchantSettlementTransactionExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将交易结算安全投影转换为本地化文件行，不重新换汇或舍入金额。
     */
    private MerchantSettlementTransactionExportRow toExportRow(
            SettlementTransactionRow source, Locale locale) {
        MerchantSettlementTransactionExportRow row = new MerchantSettlementTransactionExportRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setSourceTransactionDateTime(source.getSourceTransactionDateTime());
        row.setPaymentType(label("paymentType", source.getPaymentType(), locale));
        row.setPaymentMethod(label("paymentMethod", source.getPaymentMethod(), locale));
        row.setTransactionType(label("transactionType", source.getTransactionType(), locale));
        row.setSourceAmount(source.getSourceAmount());
        row.setSourceCurrency(source.getSourceCurrency());
        row.setComponentCount(source.getComponentCount());
        row.setNetDirection(label("direction", source.getNetDirection(), locale));
        row.setNetTargetAmount(source.getNetTargetAmount());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /**
     * 本地化结算协议值，缺少翻译时保留原值。
     */
    private String label(String category, String value, Locale locale) {
        return MerchantSettlementExportSupport.settlementLabel(messageResolver, category, value, locale);
    }
}
