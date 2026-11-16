package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SettlementTransactionQuery;
import com.scott.payment.data.client.admin.AdminSettlementExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementTransactionPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementTransactionPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementTransactionRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.SettlementTransactionExportRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SETTLEMENT_TRANSACTION;

/**
 * 管理端结算交易明细异步导出处理器，使用冻结商户范围读取权威结算投影。
 */
@Service
public class SettlementTransactionExportProcessor implements ExportTaskProcessor {
    private final AdminSettlementExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建结算交易明细异步导出处理器。
     */
    public SettlementTransactionExportProcessor(AdminSettlementExportSourceClient sourceClient,
                                                ExcelExportArtifactWriter artifactWriter,
                                                ExcelI18nMessageResolver messageResolver,
                                                DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 返回管理端结算交易明细任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_SETTLEMENT_TRANSACTION;
    }

    /**
     * 解析冻结快照并分页生成结算交易明细文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        SettlementTransactionQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), SettlementTransactionQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.settlement.resultTitle", "结算交易明细", "Settlement Transactions",
                SettlementTransactionExportRow.class,
                pageNo -> loadPage(task, query, scope, pageNo, locale));
    }

    /**
     * 读取一页结算交易安全投影并转换为任务冻结语言下的 Excel 行。
     */
    private ExportPage<SettlementTransactionExportRow> loadPage(
            DataExportTaskDO task,
            SettlementTransactionQuery query,
            AdminExportScopeSnapshot scope,
            int pageNo,
            Locale locale) {
        SettlementTransactionPageRequest request = new SettlementTransactionPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        SettlementTransactionPageResponse response = sourceClient.loadSettlementTransactionPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<SettlementTransactionExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将安全来源投影转换为结算交易 Excel 行，不重新计算金额或汇率。
     */
    private SettlementTransactionExportRow toExportRow(
            SettlementTransactionRow source, Locale locale) {
        SettlementTransactionExportRow row = new SettlementTransactionExportRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setBatchStatus(settlementLabel("batchStatus", source.getBatchStatus(), locale));
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setSourceTransactionDateTime(source.getSourceTransactionDateTime());
        row.setPaymentType(settlementLabel("paymentType", source.getPaymentType(), locale));
        row.setPaymentMethod(settlementLabel("paymentMethod", source.getPaymentMethod(), locale));
        row.setTransactionType(settlementLabel("transactionType", source.getTransactionType(), locale));
        row.setSourceAmount(source.getSourceAmount());
        row.setSourceCurrency(source.getSourceCurrency());
        row.setComponentCount(source.getComponentCount());
        row.setNetDirection(settlementLabel("direction", source.getNetDirection(), locale));
        row.setNetTargetAmount(source.getNetTargetAmount());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /**
     * 解析结算业务编码的展示标签，缺少翻译时保留协议原值。
     */
    private String settlementLabel(String category, String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        String key = "excel.settlement.enum." + category + "." + normalized;
        String resolved = messageResolver.resolve(key, locale);
        return key.equals(resolved) ? normalized : resolved;
    }

    /**
     * 解析任务冻结语言，未知语言回退简体中文。
     */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
