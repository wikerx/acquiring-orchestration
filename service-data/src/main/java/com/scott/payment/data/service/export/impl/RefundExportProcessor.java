package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.RefundQuery;
import com.scott.payment.data.client.admin.AdminTransactionExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.RefundPageRequest;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.RefundPageResponse;
import com.scott.payment.data.client.admin.dto.AdminTransactionExportSourceDTOs.RefundRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.RefundManagementExportRow;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_REFUND;

/**
 * 管理端退款异步导出处理器，分页读取 Admin 安全投影并生成私有文件。
 */
@Service
public class RefundExportProcessor implements ExportTaskProcessor {
    private final AdminTransactionExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final DataExportProperties properties;

    /**
     * 创建退款异步导出处理器。
     */
    public RefundExportProcessor(AdminTransactionExportSourceClient sourceClient,
                                 ExcelExportArtifactWriter artifactWriter,
                                 DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.properties = properties;
    }

    /**
     * 返回管理端退款任务类型。
     */
    @Override
    public String taskType() {
        return ADMIN_REFUND;
    }

    /**
     * 解析冻结快照并分页生成退款文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        RefundQuery query = JsonUtils.parseObject(task.getQuerySnapshot(), RefundQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.refund.adminTitle", "退款管理", "Refund Management",
                RefundManagementExportRow.class,
                pageNo -> loadPage(task, query, scope, pageNo));
    }

    /**
     * 读取一页 Admin 权威退款摘要并转换为 Excel 行。
     */
    private ExportPage<RefundManagementExportRow> loadPage(DataExportTaskDO task,
                                                            RefundQuery query,
                                                            AdminExportScopeSnapshot scope,
                                                            int pageNo) {
        RefundPageRequest request = new RefundPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        RefundPageResponse response = sourceClient.loadRefundPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<RefundManagementExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(this::toExportRow).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将安全来源投影转换为退款 Excel 行，不重新计算标签或交易金额。
     */
    private RefundManagementExportRow toExportRow(RefundRow source) {
        RefundManagementExportRow row = new RefundManagementExportRow();
        row.setRefundTransactionId(source.getRefundTransactionId());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setTransactionType(source.getTransactionType());
        row.setRefundScope(source.getRefundScope());
        row.setRequestSource(source.getRequestSource());
        row.setTransactionAmount(source.getTransactionAmount());
        row.setTransactionCurrency(source.getTransactionCurrency());
        row.setTransactionStatus(source.getTransactionStatus());
        row.setApprovalStatus(source.getApprovalStatus());
        row.setApplicantName(source.getApplicantName());
        row.setChannelCode(source.getChannelCode());
        row.setChannelOrderNo(source.getChannelOrderNo());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setCompleteTime(source.getCompleteTime());
        return row;
    }
}
