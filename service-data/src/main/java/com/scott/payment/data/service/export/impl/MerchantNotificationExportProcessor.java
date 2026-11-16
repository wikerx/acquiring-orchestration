package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantNotificationQuery;
import com.scott.payment.data.client.admin.AdminTransactionExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.MerchantNotificationPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.MerchantNotificationPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.MerchantNotificationRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.MerchantNotificationExportRow;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_MERCHANT_NOTIFICATION;

/**
 * 管理端商户通知记录异步导出处理器，仅导出运营排查所需的安全摘要字段。
 */
@Service
public class MerchantNotificationExportProcessor implements ExportTaskProcessor {
    private final AdminTransactionExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final DataExportProperties properties;

    /**
     * 创建商户通知记录异步导出处理器。
     */
    public MerchantNotificationExportProcessor(AdminTransactionExportSourceClient sourceClient,
                                               ExcelExportArtifactWriter artifactWriter,
                                               DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.properties = properties;
    }

    /**
     * 返回管理端商户通知记录任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_MERCHANT_NOTIFICATION;
    }

    /**
     * 解析冻结快照并分页生成商户通知记录文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        MerchantNotificationQuery query = JsonUtils.parseObject(
                task.getQuerySnapshot(), MerchantNotificationQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.transaction.notification.title", "商户通知记录", "Merchant Notifications",
                MerchantNotificationExportRow.class,
                pageNo -> loadPage(task, query, scope, pageNo));
    }

    /**
     * 读取一页 Admin 权威商户通知摘要并转换为 Excel 行。
     */
    private ExportPage<MerchantNotificationExportRow> loadPage(
            DataExportTaskDO task,
            MerchantNotificationQuery query,
            AdminExportScopeSnapshot scope,
            int pageNo) {
        MerchantNotificationPageRequest request = new MerchantNotificationPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        MerchantNotificationPageResponse response = sourceClient.loadMerchantNotificationPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<MerchantNotificationExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(this::toExportRow).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将安全来源投影转换为商户通知 Excel 行，不扩展地址、载荷或响应信息。
     */
    private MerchantNotificationExportRow toExportRow(MerchantNotificationRow source) {
        MerchantNotificationExportRow row = new MerchantNotificationExportRow();
        row.setNotifyId(source.getNotifyId());
        row.setTransactionId(source.getTransactionId());
        row.setOperationId(source.getOperationId());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setNotifyType(source.getNotifyType());
        row.setEventType(source.getEventType());
        row.setNotifyStatus(source.getNotifyStatus());
        row.setLastAttemptNo(source.getLastAttemptNo());
        row.setMaxRetryCount(source.getMaxRetryCount());
        row.setNextRetryTime(source.getNextRetryTime());
        row.setLastNotifyTime(source.getSuccessTime());
        row.setLastFailReason(source.getFailReason());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setCreateTime(source.getCreateTime());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }
}
