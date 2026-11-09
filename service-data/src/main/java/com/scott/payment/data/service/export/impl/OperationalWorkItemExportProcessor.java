package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.WorkItemQuery;
import com.scott.payment.data.client.admin.AdminWorkItemExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminWorkItemExportSourceDTOs.PageRequest;
import com.scott.payment.data.client.admin.dto.AdminWorkItemExportSourceDTOs.PageResponse;
import com.scott.payment.data.client.admin.dto.AdminWorkItemExportSourceDTOs.Row;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.OperationalWorkItemExportRow;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_OPERATIONAL_WORK_ITEM;

/**
 * 统一运营待办异步导出处理器，分页回调 Admin 权威查询并交由共享写入器生成文件。
 */
@Service
public class OperationalWorkItemExportProcessor implements ExportTaskProcessor {
    private final AdminWorkItemExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final DataExportProperties properties;

    /**
     * 创建统一待办分页导出处理器。
     *
     * @param sourceClient Admin 权威导出来源客户端
     * @param artifactWriter 共享 Excel 文件生成器
     * @param properties 导出分页配置
     */
    public OperationalWorkItemExportProcessor(AdminWorkItemExportSourceClient sourceClient,
                                              ExcelExportArtifactWriter artifactWriter,
                                              DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.properties = properties;
    }

    /**
     * 返回统一运营待办任务类型。
     *
     * @return 稳定任务类型
     */
    @Override
    public String taskType() {
        return ADMIN_OPERATIONAL_WORK_ITEM;
    }

    /**
     * 解析冻结快照并分页生成统一运营待办文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        WorkItemQuery query = JsonUtils.parseObject(task.getQuerySnapshot(), WorkItemQuery.class);
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        if (query == null || scope == null) {
            throw new IllegalStateException("export task snapshot is invalid");
        }
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.operations.workItem.title", "统一运营待办", "Operational Work Items",
                OperationalWorkItemExportRow.class,
                pageNo -> loadPage(task, query, scope, pageNo));
    }

    /**
     * 使用创建账号和冻结商户范围读取一页权威待办摘要。
     */
    private ExportPage<OperationalWorkItemExportRow> loadPage(DataExportTaskDO task,
                                                               WorkItemQuery query,
                                                               AdminExportScopeSnapshot scope,
                                                               int pageNo) {
        PageRequest request = new PageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        PageResponse response = sourceClient.loadPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<OperationalWorkItemExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(this::toExportRow).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 转换内部来源行，不重新解释金额、币种和来源状态。
     */
    private OperationalWorkItemExportRow toExportRow(Row source) {
        OperationalWorkItemExportRow row = new OperationalWorkItemExportRow();
        row.setWorkItemNo(source.getWorkItemNo());
        row.setSourceType(source.getSourceType());
        row.setBusinessNo(source.getBusinessNo());
        row.setMerchantId(source.getMerchantId());
        row.setWorkStatus(source.getWorkStatus());
        row.setSourceStatus(source.getSourceStatus());
        row.setPriority(source.getPriority());
        row.setOwnerAccountName(source.getOwnerAccountName());
        row.setCurrency(source.getCurrency());
        row.setAmount(source.getAmount());
        row.setFirstDetectedAt(source.getFirstDetectedAt());
        row.setSlaDueAt(source.getSlaDueAt());
        row.setOverdue(Boolean.TRUE.equals(source.getOverdue()) ? "Y" : "N");
        row.setLatestRemark(source.getLatestRemark());
        return row;
    }
}
