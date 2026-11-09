package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.SettlementReserveQuery;
import com.scott.payment.data.client.admin.AdminSettlementExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReservePageRequest;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReservePageResponse;
import com.scott.payment.data.client.admin.dto.AdminSettlementExportSourceDTOs.SettlementReserveRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.SettlementReserveExportRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_SETTLEMENT_RESERVE;

/**
 * 管理端保证金结算明细异步导出处理器，保持原标签币种和不可变动作事实。
 */
@Service
public class SettlementReserveExportProcessor implements ExportTaskProcessor {
    private final AdminSettlementExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建保证金结算明细异步导出处理器。
     */
    public SettlementReserveExportProcessor(AdminSettlementExportSourceClient sourceClient,
                                            ExcelExportArtifactWriter artifactWriter,
                                            ExcelI18nMessageResolver messageResolver,
                                            DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 返回管理端保证金结算明细任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_SETTLEMENT_RESERVE;
    }

    /**
     * 解析冻结快照并分页生成保证金结算明细文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        SettlementReserveQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), SettlementReserveQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        Locale locale = resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.settlement.reserveTitle", "保证金结算明细", "Settlement Reserves",
                SettlementReserveExportRow.class,
                pageNo -> loadPage(task, query, scope, pageNo, locale));
    }

    /**
     * 读取一页保证金结算安全投影并转换为任务冻结语言下的 Excel 行。
     */
    private ExportPage<SettlementReserveExportRow> loadPage(
            DataExportTaskDO task,
            SettlementReserveQuery query,
            AdminExportScopeSnapshot scope,
            int pageNo,
            Locale locale) {
        SettlementReservePageRequest request = new SettlementReservePageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        SettlementReservePageResponse response = sourceClient.loadSettlementReservePage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<SettlementReserveExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream()
                .map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将安全来源投影转换为保证金结算 Excel 行，不转换标签币种或金额。
     */
    private SettlementReserveExportRow toExportRow(SettlementReserveRow source, Locale locale) {
        SettlementReserveExportRow row = new SettlementReserveExportRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setMerchantId(source.getMerchantId());
        row.setReserveNo(source.getReserveNo());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setSourceTransactionDateTime(source.getSourceTransactionDateTime());
        row.setActionType(settlementLabel("reserveAction", source.getActionType(), locale));
        row.setDirection(settlementLabel("direction", source.getDirection(), locale));
        row.setCurrency(source.getCurrency());
        row.setAmount(source.getAmount());
        row.setRemainingAmount(source.getRemainingAmount());
        row.setReserveStatus(settlementLabel("reserveStatus", source.getReserveStatus(), locale));
        row.setExpectedReleaseDate(source.getExpectedReleaseDate());
        row.setActionTime(source.getActionTime());
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
