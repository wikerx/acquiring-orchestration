package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FundLedgerQuery;
import com.scott.payment.data.client.admin.AdminFundExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundLedgerPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundLedgerPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundLedgerRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.FundLedgerExportRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_FUND_LEDGER;

/**
 * 管理端余额流水异步导出处理器，统一承载指定账户和全局流水导出。
 */
@Service
public class FundLedgerExportProcessor implements ExportTaskProcessor {
    private final AdminFundExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /** 创建余额流水异步导出处理器。 */
    public FundLedgerExportProcessor(AdminFundExportSourceClient sourceClient,
                                     ExcelExportArtifactWriter artifactWriter,
                                     ExcelI18nMessageResolver messageResolver,
                                     DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /** 返回余额流水稳定任务类型。 */
    @Override
    public String taskType() {
        return ADMIN_FUND_LEDGER;
    }

    /** 解析冻结条件并生成余额流水文件。 */
    @Override
    public ExportArtifact process(DataExportTaskDO task, ExportObjectStorageService storage,
                                  ExportProgressListener progressListener, LocalDateTime now) {
        FundLedgerQuery query = JsonUtils.parseObject(task.getQuerySnapshot(), FundLedgerQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.fund.ledgerTitle", "余额流水", "Balance Ledger",
                FundLedgerExportRow.class, properties.getPageSize(),
                pageNo -> loadPage(task, query, scope, pageNo, locale));
    }

    /** 请求一页冻结范围内的流水并转换为 Excel 行。 */
    private ExportPage<FundLedgerExportRow> loadPage(
            DataExportTaskDO task, FundLedgerQuery query, AdminExportScopeSnapshot scope,
            int pageNo, Locale locale) {
        FundLedgerPageRequest request = new FundLedgerPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        FundLedgerPageResponse response = sourceClient.loadFundLedgerPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<FundLedgerExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(source -> toExportRow(source, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /** 复制不可变资金事实，仅本地化业务类型、余额类型和方向。 */
    private FundLedgerExportRow toExportRow(FundLedgerRow source, Locale locale) {
        FundLedgerExportRow row = new FundLedgerExportRow();
        row.setLedgerNo(source.getLedgerNo());
        row.setAccountNo(source.getAccountNo());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setBusinessType(label("businessType", source.getBusinessType(), locale));
        row.setSummary(source.getSummary());
        row.setBusinessNo(source.getBusinessNo());
        row.setBalanceType(label("balanceType", source.getBalanceType(), locale));
        row.setDirection(label("direction", source.getDirection(), locale));
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setBalanceBefore(source.getBalanceBefore());
        row.setBalanceAfter(source.getBalanceAfter());
        row.setAccountSequence(source.getAccountSequence());
        row.setOperatorName(source.getOperatorName());
        row.setReviewerName(source.getReviewerName());
        row.setOperationReason(source.getOperationReason());
        row.setReviewComment(source.getReviewComment());
        row.setBusinessTime(source.getBusinessTime());
        row.setPostedTime(source.getPostedTime());
        row.setRequestId(source.getRequestId());
        row.setIdempotencyKey(source.getIdempotencyKey());
        return row;
    }

    /** 解析资金枚举展示值，缺少翻译时保留协议原值。 */
    private String label(String category, String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        String key = "excel.fund.enum." + category + "." + normalized;
        String resolved = messageResolver.resolve(key, locale);
        return key.equals(resolved) ? normalized : resolved;
    }

    /** 解析任务冻结语言，未知语言回退简体中文。 */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }
}
