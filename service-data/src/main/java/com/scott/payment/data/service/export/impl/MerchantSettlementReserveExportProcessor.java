package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantSettlementReserveQuery;
import com.scott.payment.data.client.merchant.MerchantExportSourceClient;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementReservePageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementReservePageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.SettlementReserveRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.MerchantExportScopeSnapshot;
import com.scott.payment.data.service.export.MerchantSettlementReserveExportRow;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.MERCHANT_SETTLEMENT_RESERVE;

/**
 * 商户保证金结算异步导出处理器，保持原标签币种责任和不可变动作事实。
 */
@Service
public class MerchantSettlementReserveExportProcessor implements ExportTaskProcessor {
    private final MerchantExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final ExcelI18nMessageResolver messageResolver;
    private final DataExportProperties properties;

    /**
     * 创建商户保证金结算异步导出处理器。
     */
    public MerchantSettlementReserveExportProcessor(MerchantExportSourceClient sourceClient,
                                                    ExcelExportArtifactWriter artifactWriter,
                                                    ExcelI18nMessageResolver messageResolver,
                                                    DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.messageResolver = messageResolver;
        this.properties = properties;
    }

    /**
     * 返回商户保证金结算任务类型。
     */
    @Override
    public String taskType() {
        return MERCHANT_SETTLEMENT_RESERVE;
    }

    /**
     * 解析冻结快照并分页生成商户保证金结算文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        MerchantSettlementReserveQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), MerchantSettlementReserveQuery.class);
        MerchantExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), MerchantExportScopeSnapshot.class);
        MerchantSettlementExportSupport.requireScope(task, query, scope);
        Locale locale = MerchantSettlementExportSupport.resolveLocale(task.getLocale());
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.merchantSettlement.reserveTitle", "保证金结算明细", "Settlement Reserves",
                MerchantSettlementReserveExportRow.class,
                pageNo -> loadPage(task, query, pageNo, locale));
    }

    /**
     * 读取一页商户保证金结算安全投影并转换为文件行。
     */
    private ExportPage<MerchantSettlementReserveExportRow> loadPage(
            DataExportTaskDO task,
            MerchantSettlementReserveQuery query,
            int pageNo,
            Locale locale) {
        SettlementReservePageRequest request = new SettlementReservePageRequest();
        request.setMerchantId(task.getMerchantId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        SettlementReservePageResponse response = sourceClient.loadSettlementReservePage(request);
        if (response == null) {
            throw new IllegalStateException("merchant settlement reserve source response is missing");
        }
        List<MerchantSettlementReserveExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(row -> toExportRow(row, locale)).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将保证金结算安全投影转换为本地化文件行，不重新计算责任余额。
     */
    private MerchantSettlementReserveExportRow toExportRow(
            SettlementReserveRow source, Locale locale) {
        MerchantSettlementReserveExportRow row = new MerchantSettlementReserveExportRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setReserveNo(source.getReserveNo());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setSourceTransactionDateTime(source.getSourceTransactionDateTime());
        row.setActionType(label("reserveAction", source.getActionType(), locale));
        row.setDirection(label("direction", source.getDirection(), locale));
        row.setCurrency(source.getCurrency());
        row.setAmount(source.getAmount());
        row.setRemainingAmount(source.getRemainingAmount());
        row.setReserveStatus(label("reserveStatus", source.getReserveStatus(), locale));
        row.setExpectedReleaseDate(source.getExpectedReleaseDate());
        row.setActionTime(source.getActionTime());
        return row;
    }

    /**
     * 本地化结算协议值，缺少翻译时保留原值。
     */
    private String label(String category, String value, Locale locale) {
        return MerchantSettlementExportSupport.settlementLabel(messageResolver, category, value, locale);
    }
}
