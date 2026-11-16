package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SettlementReviewQuery;
import com.scott.payment.data.client.admin.AdminSettlementExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReviewPageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReviewPageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReviewRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.SettlementReviewExportRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SETTLEMENT_REVIEW;

/**
 * 管理端结算审核单异步导出处理器，按冻结语言本地化审批枚举。
 */
@Service
public class SettlementReviewExportProcessor implements ExportTaskProcessor {
    private final AdminSettlementExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /** 创建结算审核单异步导出处理器。 */
    public SettlementReviewExportProcessor(AdminSettlementExportSourceClient sourceClient,
                                           ExcelExportArtifactWriter artifactWriter,
                                           ExcelI18nMessageResolver messageResolver,
                                           DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /** 返回结算审核单稳定任务类型。 */
    @Override
    public String taskType() {
        return ADMIN_SETTLEMENT_REVIEW;
    }

    /** 解析冻结快照并分页生成结算审核单文件。 */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        SettlementReviewQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), SettlementReviewQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.settlement.reviewTitle", "结算审核单", "Settlement Review Orders",
                SettlementReviewExportRow.class, properties.getPageSize(),
                pageNo -> loadPage(task, query, scope, pageNo, locale));
    }

    /** 读取一页审核单并转换为冻结语言下的 Excel 行。 */
    private ExportPage<SettlementReviewExportRow> loadPage(
            DataExportTaskDO task,
            SettlementReviewQuery query,
            AdminExportScopeSnapshot scope,
            int pageNo,
            Locale locale) {
        SettlementReviewPageRequest request = new SettlementReviewPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        SettlementReviewPageResponse response = sourceClient.loadSettlementReviewPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<SettlementReviewExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /** 将审核单事实复制到 Excel 行，仅本地化枚举展示值。 */
    private SettlementReviewExportRow toExportRow(SettlementReviewRow source, Locale locale) {
        SettlementReviewExportRow row = new SettlementReviewExportRow();
        row.setReviewOrderNo(source.getReviewOrderNo());
        row.setReviewType(label("reviewType", source.getReviewType(), locale));
        row.setCreateMode(label("createMode", source.getCreateMode(), locale));
        row.setMerchantId(source.getMerchantId());
        row.setBusinessDate(source.getBusinessDate());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setCandidateCount(source.getCandidateCount());
        row.setNetDirection(label("direction", source.getNetDirection(), locale));
        row.setNetAmount(source.getNetAmount());
        row.setReviewStatus(label("reviewStatus", source.getReviewStatus(), locale));
        row.setSubmittedByAccountName(source.getSubmittedByAccountName());
        row.setSubmittedTime(source.getSubmittedTime());
        row.setDecidedByAccountName(source.getDecidedByAccountName());
        row.setDecisionAction(label("decisionAction", source.getDecisionAction(), locale));
        row.setDecisionTime(source.getDecisionTime());
        row.setSettlementBatchNo(source.getSettlementBatchNo());
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
