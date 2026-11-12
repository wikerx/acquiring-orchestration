package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantTransactionQuery;
import com.scott.payment.data.client.merchant.MerchantExportSourceClient;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.TransactionPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.TransactionPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.TransactionRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.MerchantExportScopeSnapshot;
import com.scott.payment.data.service.export.MerchantTransactionExportRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.MERCHANT_TRANSACTION_OPERATION;

/**
 * 商户交易异步导出处理器，使用冻结商户号读取标签口径的安全来源投影。
 */
@Service
public class MerchantTransactionExportProcessor implements ExportTaskProcessor {
    private final MerchantExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建商户交易异步导出处理器。
     */
    public MerchantTransactionExportProcessor(MerchantExportSourceClient sourceClient,
                                              ExcelExportArtifactWriter artifactWriter,
                                              ExcelI18nMessageResolver messageResolver,
                                              DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 返回商户交易任务类型。
     */
    @Override
    public String taskType() {
        return MERCHANT_TRANSACTION_OPERATION;
    }

    /**
     * 解析冻结快照并分页生成商户交易文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        MerchantTransactionQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), MerchantTransactionQuery.class);
        MerchantExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), MerchantExportScopeSnapshot.class);
        requireScope(task, query, scope);
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.transaction.operation.title", "交易查询", "Transaction Records",
                MerchantTransactionExportRow.class,
                pageNo -> loadPage(task, query, pageNo, locale));
    }

    /**
     * 读取一页 Merchant 权威安全投影并转换为 Excel 行。
     */
    private ExportPage<MerchantTransactionExportRow> loadPage(DataExportTaskDO task,
                                                               MerchantTransactionQuery query,
                                                               int pageNo,
                                                               Locale locale) {
        TransactionPageRequest request = new TransactionPageRequest();
        request.setMerchantId(task.getMerchantId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        TransactionPageResponse response = sourceClient.loadTransactionPage(request);
        if (response == null) {
            throw new IllegalStateException("merchant export source response is missing");
        }
        List<MerchantTransactionExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将商户安全来源投影转换为文件行，不重新计算金额或汇率。
     */
    private MerchantTransactionExportRow toExportRow(TransactionRow source, Locale locale) {
        MerchantTransactionExportRow row = new MerchantTransactionExportRow();
        row.setTransactionId(source.getTransactionId());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setMerchantOrderId(source.getMerchantOrderId());
        row.setTransactionType(source.getTransactionType());
        row.setTransactionStatus(source.getTransactionStatus());
        row.setTransactionAmount(source.getTransactionAmount());
        row.setTransactionCurrency(source.getTransactionCurrency());
        row.setMerchantResponseCode(source.getMerchantResponseCode());
        row.setMerchantResponseMessage(source.getMerchantResponseMessage());
        row.setMerchantNotificationStatus(source.getMerchantNotificationStatus());
        row.setPaymentMethod(source.getPaymentMethod());
        row.setPaymentBrand(source.getPaymentBrand());
        row.setCardBin(source.getCardBin());
        row.setCardNumberMasked(source.getCardNumberMasked());
        row.setThreeDs(messageResolver.resolve(Integer.valueOf(1).equals(source.getThreeDsEnabled())
                ? "excel.common.yes" : "excel.common.no", locale));
        row.setAuthCode(source.getAuthCode());
        row.setSettlementStatus(source.getSettlementStatus());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setOperationTime(source.getOperationTime());
        return row;
    }

    /**
     * 交叉校验任务归属字段和快照商户号，防止损坏快照跨商户读取。
     */
    private void requireScope(DataExportTaskDO task,
                              MerchantTransactionQuery query,
                              MerchantExportScopeSnapshot scope) {
        if (query == null || scope == null || !StringUtils.hasText(task.getMerchantId())
                || !task.getMerchantId().equals(scope.getMerchantId())) {
            throw new IllegalStateException("merchant export task scope is invalid");
        }
    }

    /**
     * 解析任务冻结语言，空值回退简体中文。
     */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
