package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FeePlanQuery;
import com.scott.payment.data.client.admin.AdminFeeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeePlanPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeReviewPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeReviewRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.FeeReviewExportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_FEE_REVIEW;

/** 管理端费率复核异步导出处理器。 */
@Service
public class FeeReviewExportProcessor
        extends AbstractBaseReferenceExportProcessor<FeePlanQuery, FeeReviewExportRow> {
    private final AdminFeeExportSourceClient sourceClient;

    /** 创建费率复核导出处理器。 */
    public FeeReviewExportProcessor(AdminFeeExportSourceClient sourceClient,
                                    ExcelExportArtifactWriter artifactWriter,
                                    ExcelI18nMessageResolver messageResolver,
                                    DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    @Override
    public String taskType() {
        return ADMIN_FEE_REVIEW;
    }

    @Override
    protected Class<FeePlanQuery> queryClass() {
        return FeePlanQuery.class;
    }

    @Override
    protected String titleKey() {
        return "excel.fee.reviewTitle";
    }

    @Override
    protected String chineseTitle() {
        return "费率审核";
    }

    @Override
    protected String englishTitle() {
        return "Fee Reviews";
    }

    @Override
    protected Class<FeeReviewExportRow> rowClass() {
        return FeeReviewExportRow.class;
    }

    @Override
    protected ExportPage<FeeReviewExportRow> loadPage(
            DataExportTaskDO task, FeePlanQuery query, int pageNo, Locale locale) {
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        FeePlanPageRequest request = new FeePlanPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        FeeReviewPageResponse response = sourceClient.loadFeeReviewPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<FeeReviewExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(this::toExportRow).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /** 转换费率复核导出行。 */
    private FeeReviewExportRow toExportRow(FeeReviewRow source) {
        FeeReviewExportRow row = new FeeReviewExportRow();
        row.setPlanCode(source.getPlanCode());
        row.setPlanName(source.getPlanName());
        row.setPlanType(source.getPlanType());
        row.setMerchantId(source.getMerchantId());
        row.setVersionNo(source.getVersionNo());
        row.setChangeReason(source.getChangeReason());
        row.setSubmitByName(source.getSubmitByName());
        row.setSubmitTime(source.getSubmitTime());
        return row;
    }
}
