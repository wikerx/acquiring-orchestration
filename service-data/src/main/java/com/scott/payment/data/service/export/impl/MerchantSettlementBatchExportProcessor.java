package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantSettlementBatchQuery;
import com.scott.payment.data.client.merchant.MerchantExportSourceClient;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementBatchPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementBatchPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementBatchRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.MerchantExportScopeSnapshot;
import com.scott.payment.data.service.export.MerchantSettlementBatchExportRow;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.MERCHANT_SETTLEMENT_BATCH;

/**
 * 商户结算批次异步导出处理器，按冻结商户号读取商户可见净入账结果。
 */
@Service
public class MerchantSettlementBatchExportProcessor implements ExportTaskProcessor {
    private final MerchantExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建商户结算批次异步导出处理器。
     */
    public MerchantSettlementBatchExportProcessor(MerchantExportSourceClient sourceClient,
                                                  ExcelExportArtifactWriter artifactWriter,
                                                  ExcelI18nMessageResolver messageResolver,
                                                  DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 返回商户结算批次任务类型。
     */
    @Override
    public String taskType() {
        return MERCHANT_SETTLEMENT_BATCH;
    }

    /**
     * 解析冻结快照并分页生成商户结算批次文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        MerchantSettlementBatchQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), MerchantSettlementBatchQuery.class);
        MerchantExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), MerchantExportScopeSnapshot.class);
        MerchantSettlementExportSupport.requireScope(task, query, scope);
        Locale locale = MerchantSettlementExportSupport.resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.merchantSettlement.batchTitle", "结算批次", "Settlement Batches",
                MerchantSettlementBatchExportRow.class,
                pageNo -> loadPage(task, query, pageNo, locale));
    }

    /**
     * 读取一页商户结算批次安全投影并转换为文件行。
     */
    private ExportPage<MerchantSettlementBatchExportRow> loadPage(
            DataExportTaskDO task, MerchantSettlementBatchQuery query, int pageNo, Locale locale) {
        SettlementBatchPageRequest request = new SettlementBatchPageRequest();
        request.setMerchantId(task.getMerchantId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        SettlementBatchPageResponse response = sourceClient.loadSettlementBatchPage(request);
        if (response == null) {
            throw new IllegalStateException("merchant settlement batch source response is missing");
        }
        List<MerchantSettlementBatchExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将结算批次安全投影转换为本地化文件行，不重新计算净额。
     */
    private MerchantSettlementBatchExportRow toExportRow(SettlementBatchRow source, Locale locale) {
        MerchantSettlementBatchExportRow row = new MerchantSettlementBatchExportRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setBatchType(label("batchType", source.getBatchType(), locale));
        row.setBatchStatus(label("batchStatus", source.getBatchStatus(), locale));
        row.setTargetCurrency(source.getTargetCurrency());
        row.setTransactionCount(source.getTransactionCount());
        row.setSettlementItemCount(source.getSettlementItemCount());
        row.setNetDirection(label("direction", source.getNetDirection(), locale));
        row.setNetAmount(source.getNetAmount());
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
