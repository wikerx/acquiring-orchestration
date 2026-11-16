package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantRefundQuery;
import com.scott.payment.data.client.merchant.MerchantExportSourceClient;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.RefundPageRequest;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.RefundPageResponse;
import com.scott.payment.data.client.merchant.dto.MerchantExportSourceDTOs.RefundRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportArtifact;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.ExportProgressListener;
import com.scott.payment.data.service.export.ExportTaskProcessor;
import com.scott.payment.data.service.export.MerchantExportScopeSnapshot;
import com.scott.payment.data.service.export.MerchantRefundExportRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

import static com.scott.payment.data.service.export.DataExportTaskService.MERCHANT_REFUND;

/**
 * 商户退款异步导出处理器，使用冻结商户号读取标签口径和商户可见说明。
 */
@Service
public class MerchantRefundExportProcessor implements ExportTaskProcessor {
    private final MerchantExportSourceClient sourceClient;
    private final ExcelExportArtifactWriter artifactWriter;
    private final DataExportProperties properties;

    /**
     * 创建商户退款异步导出处理器。
     */
    public MerchantRefundExportProcessor(MerchantExportSourceClient sourceClient,
                                         ExcelExportArtifactWriter artifactWriter,
                                         DataExportProperties properties) {
        this.sourceClient = sourceClient;
        this.artifactWriter = artifactWriter;
        this.properties = properties;
    }

    /**
     * 返回商户退款任务类型。
     */
    @Override
    public String taskType() {
        return MERCHANT_REFUND;
    }

    /**
     * 解析冻结快照并分页生成商户退款文件。
     */
    @Override
    public ExportArtifact process(DataExportTaskDO task,
                                  ExportObjectStorageService storage,
                                  ExportProgressListener progressListener,
                                  LocalDateTime now) {
        MerchantRefundQuery query = JsonUtils.parseObject(task.getQuerySnapshot(), MerchantRefundQuery.class);
        MerchantExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), MerchantExportScopeSnapshot.class);
        requireScope(task, query, scope);
        return artifactWriter.write(task, storage, progressListener, now,
                "excel.refund.merchantTitle", "退款记录", "Refund Records",
                MerchantRefundExportRow.class,
                pageNo -> loadPage(task, query, pageNo));
    }

    /**
     * 读取一页 Merchant 退款安全投影并转换为 Excel 行。
     */
    private ExportPage<MerchantRefundExportRow> loadPage(DataExportTaskDO task,
                                                          MerchantRefundQuery query,
                                                          int pageNo) {
        RefundPageRequest request = new RefundPageRequest();
        request.setMerchantId(task.getMerchantId());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(properties.getPageSize());
        RefundPageResponse response = sourceClient.loadRefundPage(request);
        if (response == null) {
            throw new IllegalStateException("merchant refund export source response is missing");
        }
        List<MerchantRefundExportRow> rows = response.getRecords() == null
                ? List.of() : response.getRecords().stream().map(this::toExportRow).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /**
     * 将商户退款安全投影转换为文件行，不引入渠道或内部审批字段。
     */
    private MerchantRefundExportRow toExportRow(RefundRow source) {
        MerchantRefundExportRow row = new MerchantRefundExportRow();
        row.setRefundTransactionId(source.getRefundTransactionId());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setTransactionType(source.getTransactionType());
        row.setRefundScope(source.getRefundScope());
        row.setTransactionAmount(source.getTransactionAmount());
        row.setTransactionCurrency(source.getTransactionCurrency());
        row.setTransactionStatus(source.getTransactionStatus());
        row.setApprovalStatus(source.getApprovalStatus());
        row.setMerchantVisibleMessage(source.getMerchantVisibleMessage());
        row.setPaymentMethod(source.getPaymentMethod());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setCompleteTime(source.getCompleteTime());
        return row;
    }

    /**
     * 交叉校验任务归属字段和快照商户号，防止损坏快照跨商户读取。
     */
    private void requireScope(DataExportTaskDO task,
                              MerchantRefundQuery query,
                              MerchantExportScopeSnapshot scope) {
        if (query == null || scope == null || !StringUtils.hasText(task.getMerchantId())
                || !task.getMerchantId().equals(scope.getMerchantId())) {
            throw new IllegalStateException("merchant refund export task scope is invalid");
        }
    }
}
