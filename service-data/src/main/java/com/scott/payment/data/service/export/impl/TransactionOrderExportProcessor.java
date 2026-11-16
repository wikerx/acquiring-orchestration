package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.TransactionOrderQuery;
import com.scott.payment.data.client.admin.AdminTransactionExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OrderPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OrderPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.OrderRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.TransactionOrderExportRow;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_TRANSACTION_ORDER;

/**
 * 管理端交易主单异步导出处理器，严格使用任务创建时冻结的查询和商户范围。
 */
@Service
public class TransactionOrderExportProcessor implements ExportTaskProcessor {
    private final AdminTransactionExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建交易主单异步导出处理器。
     */
    public TransactionOrderExportProcessor(AdminTransactionExportSourceClient sourceClient,
                                           ExcelExportArtifactWriter artifactWriter,
                                           ExcelI18nMessageResolver messageResolver,
                                           DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 返回管理端交易主单任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_TRANSACTION_ORDER;
    }

    /**
     * 解析冻结快照并分页生成交易主单文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        TransactionOrderQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), TransactionOrderQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.transaction.order.title", "交易主单", "Transaction Orders",
                TransactionOrderExportRow.class,
                pageNo -> loadPage(task, query, scope, pageNo, locale));
    }

    /**
     * 读取一页 Admin 权威交易主单并转换为 Excel 行。
     */
    private ExportPage<TransactionOrderExportRow> loadPage(DataExportTaskDO task,
                                                            TransactionOrderQuery query,
                                                            AdminExportScopeSnapshot scope,
                                                            int pageNo,
                                                            Locale locale) {
        OrderPageRequest request = new OrderPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        OrderPageResponse response = sourceClient.loadOrderPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<TransactionOrderExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将安全来源投影转换为交易主单 Excel 行，不改变金额和汇率语义。
     */
    private TransactionOrderExportRow toExportRow(OrderRow source, Locale locale) {
        TransactionOrderExportRow row = new TransactionOrderExportRow();
        row.setRootTransactionId(source.getRootTransactionId());
        row.setLatestTransactionId(source.getLatestTransactionId());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setMerchantOrderId(source.getMerchantOrderId());
        row.setTransactionType(source.getTransactionType());
        row.setTransactionStatus(source.getTransactionStatus());
        row.setLifecycleStatus(source.getLifecycleStatus());
        row.setCurrentAmount(source.getCurrentAmount());
        row.setCurrentCurrency(source.getCurrentCurrency());
        row.setAuthorizedAmount(source.getAuthorizedAmount());
        row.setCapturedAmount(source.getCapturedAmount());
        row.setRefundedAmount(source.getRefundedAmount());
        row.setTransactionRate(source.getTransactionRate());
        row.setChannelCode(source.getChannelCode());
        row.setChannelOrderNo(source.getChannelOrderNo());
        row.setMerchantResponseCode(source.getMerchantResponseCode());
        row.setMerchantResponseMessage(source.getMerchantResponseMessage());
        row.setChannelMatchStatus(source.getChannelMatchStatus());
        row.setThreeDs(binaryLabel(source.getThreeDsEnabled(), "excel.common.yes", "excel.common.no", locale));
        row.setDcc(binaryLabel(source.getDccEnabled(), "excel.transaction.common.capabilityEnabled",
                "excel.transaction.common.capabilityDisabled", locale));
        row.setEdc(binaryLabel(source.getEdcEnabled(), "excel.transaction.common.capabilityEnabled",
                "excel.transaction.common.capabilityDisabled", locale));
        row.setReconciliationStatus(source.getReconciliationStatus());
        row.setSettlementStatus(source.getSettlementStatus());
        row.setTransactionDateTime(source.getTransactionDateTime());
        return row;
    }

    /**
     * 将数据库标志转换为任务冻结语言下的可读值。
     */
    private String binaryLabel(Integer value, String enabledKey, String disabledKey, Locale locale) {
        return messageResolver.resolve(Integer.valueOf(1).equals(value) ? enabledKey : disabledKey, locale);
    }

    /**
     * 解析任务冻结语言，空值回退简体中文。
     */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
