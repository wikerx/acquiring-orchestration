package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ChannelMatchAbnormalQuery;
import com.scott.payment.data.client.admin.AdminTransactionExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.ChannelMatchAbnormalPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.ChannelMatchAbnormalPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.ChannelMatchAbnormalRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ChannelMatchAbnormalExportRow;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_CHANNEL_MATCH_ABNORMAL;

/**
 * 管理端渠道勾兑异常异步导出处理器，按冻结商户范围分页读取安全案件投影。
 */
@Service
public class ChannelMatchAbnormalExportProcessor implements ExportTaskProcessor {
    private final AdminTransactionExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final DataExportProperties properties;

    /**
     * 创建渠道勾兑异常异步导出处理器。
     */
    public ChannelMatchAbnormalExportProcessor(AdminTransactionExportSourceClient sourceClient,
                                               ExcelExportArtifactWriter artifactWriter,
                                               DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.properties = properties;
    }

    /** 返回渠道勾兑异常稳定任务类型。 */
    @Override
    public String taskType() {
        return ADMIN_CHANNEL_MATCH_ABNORMAL;
    }

    /**
     * 解析冻结查询和数据范围，逐页生成最终 Excel 文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        ChannelMatchAbnormalQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), ChannelMatchAbnormalQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.abnormal.title", "渠道勾兑异常", "Channel Match Abnormalities",
                ChannelMatchAbnormalExportRow.class, properties.getPageSize(),
                pageNo -> loadPage(task, query, scope, pageNo));
    }

    /**
     * 读取一页安全案件投影并转换为 Data 独立行模型。
     */
    private ExportPage<ChannelMatchAbnormalExportRow> loadPage(
            DataExportTaskDO task,
            ChannelMatchAbnormalQuery query,
            AdminExportScopeSnapshot scope,
            int pageNo) {
        ChannelMatchAbnormalPageRequest request = new ChannelMatchAbnormalPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        ChannelMatchAbnormalPageResponse response = sourceClient.loadChannelMatchAbnormalPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<ChannelMatchAbnormalExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(this::toExportRow).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将 Admin 安全投影复制为 Excel 行，不重新解释案件事实或金额。
     */
    private ChannelMatchAbnormalExportRow toExportRow(ChannelMatchAbnormalRow source) {
        ChannelMatchAbnormalExportRow row = new ChannelMatchAbnormalExportRow();
        row.setAbnormalEventId(source.getAbnormalEventId());
        row.setTransactionId(source.getTransactionId());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setAbnormalType(source.getAbnormalType());
        row.setAbnormalLevel(source.getAbnormalLevel());
        row.setEventStatus(source.getEventStatus());
        row.setPlatformStatus(source.getPlatformStatus());
        row.setChannelCode(source.getChannelCode());
        row.setChannelStatus(source.getChannelStatus());
        row.setPlatformAmount(source.getPlatformAmount());
        row.setPlatformCurrency(source.getPlatformCurrency());
        row.setOccurrenceCount(source.getOccurrenceCount());
        row.setAssignedToName(source.getAssignedToName());
        row.setFirstSeenTime(source.getFirstSeenTime());
        row.setLastSeenTime(source.getLastSeenTime());
        return row;
    }
}
