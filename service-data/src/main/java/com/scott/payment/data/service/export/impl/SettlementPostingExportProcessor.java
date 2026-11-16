package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SettlementPostingQuery;
import com.scott.payment.data.client.admin.AdminSettlementExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementPostingPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementPostingPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementPostingRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.SettlementPostingExportRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SETTLEMENT_POSTING;

/**
 * 管理端结算入账流水异步导出处理器，保持资金事实不变并本地化枚举展示。
 */
@Service
public class SettlementPostingExportProcessor implements ExportTaskProcessor {
    private final AdminSettlementExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /** 创建结算入账流水异步导出处理器。 */
    public SettlementPostingExportProcessor(AdminSettlementExportSourceClient sourceClient,
                                            ExcelExportArtifactWriter artifactWriter,
                                            ExcelI18nMessageResolver messageResolver,
                                            DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /** 返回结算入账流水稳定任务类型。 */
    @Override
    public String taskType() {
        return ADMIN_SETTLEMENT_POSTING;
    }

    /** 解析冻结快照并分页生成结算入账流水文件。 */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        SettlementPostingQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), SettlementPostingQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.settlement.postingTitle", "结算入账流水", "Settlement Postings",
                SettlementPostingExportRow.class, properties.getPageSize(),
                pageNo -> loadPage(task, query, scope, pageNo, locale));
    }

    /** 读取一页资金流水并转换为冻结语言下的 Excel 行。 */
    private ExportPage<SettlementPostingExportRow> loadPage(
            DataExportTaskDO task,
            SettlementPostingQuery query,
            AdminExportScopeSnapshot scope,
            int pageNo,
            Locale locale) {
        SettlementPostingPageRequest request = new SettlementPostingPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        SettlementPostingPageResponse response = sourceClient.loadSettlementPostingPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<SettlementPostingExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /** 将资金事实复制到 Excel 行，仅本地化业务枚举。 */
    private SettlementPostingExportRow toExportRow(SettlementPostingRow source, Locale locale) {
        SettlementPostingExportRow row = new SettlementPostingExportRow();
        row.setLedgerNo(source.getLedgerNo());
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setMerchantId(source.getMerchantId());
        row.setAccountId(source.getAccountId());
        row.setBusinessType(label("businessType", source.getBusinessType(), locale));
        row.setDirection(label("direction", source.getDirection(), locale));
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setBalanceBefore(source.getBalanceBefore());
        row.setBalanceAfter(source.getBalanceAfter());
        row.setAccountSequence(source.getAccountSequence());
        row.setOperationMode(label("operationMode", source.getOperationMode(), locale));
        row.setOperatorName(source.getOperatorName());
        row.setReviewerName(source.getReviewerName());
        row.setOperationReason(source.getOperationReason());
        row.setReviewComment(source.getReviewComment());
        row.setPostedTime(source.getPostedTime());
        row.setIdempotencyKey(source.getIdempotencyKey());
        row.setReversalOfLedgerId(source.getReversalOfLedgerId());
        return row;
    }

    /** 解析结算枚举展示值，缺少翻译时保留协议原值。 */
    private String label(String category, String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        String key = "excel.settlement.enum." + category + "." + normalized;
        String resolved = messageResolver.resolve(key, locale);
        return key.equals(resolved) ? normalized : resolved;
    }

    /** 解析任务冻结语言，未知语言回退简体中文。 */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null
                ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
