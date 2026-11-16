package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.CardBinQuery;
import com.scott.payment.data.client.admin.AdminPlatformExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CardBinPageRequest;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CardBinPageResponse;
import com.scott.payment.data.client.admin.dto.AdminPlatformExportSourceDTOs.CardBinRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.CardBinExportRow;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_CARD_BIN;

/**
 * 管理端卡 BIN 异步导出处理器，按冻结条件分页读取安全投影并写入私有对象存储。
 */
@Service
public class CardBinExportProcessor implements ExportTaskProcessor {
    private final AdminPlatformExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final DataExportProperties properties;

    /**
     * 创建卡 BIN 异步导出处理器。
     *
     * @param sourceClient Admin 平台级导出源客户端
     * @param artifactWriter 统一分页 Excel 写入器
     * @param properties 异步导出分页和容量配置
     */
    public CardBinExportProcessor(AdminPlatformExportSourceClient sourceClient,
                                  ExcelExportArtifactWriter artifactWriter,
                                  DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.properties = properties;
    }

    /**
     * 返回管理端卡 BIN 稳定任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_CARD_BIN;
    }

    /**
     * 解析冻结快照，逐页读取卡 BIN 并生成最终 Excel 文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        CardBinQuery query = JsonUtils.parseObject(task.getQuerySnapshot(), CardBinQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.cardBin.title", "卡 BIN 库", "Card BIN",
                CardBinExportRow.class, properties.getPlatformPageSize(),
                pageNo -> loadPage(task, query, pageNo));
    }

    /**
     * 请求一页卡 BIN 安全投影并转换为 Data 独立的 Excel 行模型。
     */
    private ExportPage<CardBinExportRow> loadPage(
            DataExportTaskDO task, CardBinQuery query, int pageNo) {
        CardBinPageRequest request = new CardBinPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPlatformPageSize());
        CardBinPageResponse response = sourceClient.loadCardBinPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<CardBinExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(this::toExportRow).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将 Admin 安全投影转换为 Excel 行，BIN 和国家数字码始终按文本输出。
     */
    private CardBinExportRow toExportRow(CardBinRow source) {
        CardBinExportRow row = new CardBinExportRow();
        row.setCardBinStart(source.getCardBinStart());
        row.setCardBinEnd(source.getCardBinEnd());
        row.setBinLength(source.getBinLength());
        row.setCardBrand(source.getCardBrand());
        row.setCardSubBrand(source.getCardSubBrand());
        row.setCardType(source.getCardType());
        row.setCardLevel(source.getCardLevel());
        row.setIssuerCountryName(source.getIssuerCountryName());
        row.setIssuerCountryAlpha2(source.getIssuerCountryAlpha2());
        row.setIssuerCountryAlpha3(source.getIssuerCountryAlpha3());
        row.setIssuerCountryNumeric(source.getIssuerCountryNumeric());
        row.setIssuerBank(source.getIssuerBank());
        row.setIssuerWebUrl(source.getIssuerWebUrl());
        row.setIssuerTelephone(source.getIssuerTelephone());
        row.setDataSource(source.getDataSource());
        row.setStatus(source.getStatus());
        row.setEffectiveTime(source.getEffectiveTime());
        row.setExpireTime(source.getExpireTime());
        row.setRemark(source.getRemark());
        row.setCreateBy(source.getCreateBy());
        row.setUpdateBy(source.getUpdateBy());
        row.setCreateTime(source.getCreateTime());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }
}
