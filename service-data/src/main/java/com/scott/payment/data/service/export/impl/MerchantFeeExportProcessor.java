package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FeePlanQuery;
import com.scott.payment.data.client.admin.AdminFeeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeePlanPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.MerchantFeePageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.MerchantFeeRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.MerchantFeeExportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_MERCHANT_FEE;

/** 管理端商户费率异步导出处理器。 */
@Service
public class MerchantFeeExportProcessor
        extends AbstractBaseReferenceExportProcessor<FeePlanQuery, MerchantFeeExportRow> {
    private final AdminFeeExportSourceClient sourceClient;

    /** 创建商户费率导出处理器。 */
    public MerchantFeeExportProcessor(AdminFeeExportSourceClient sourceClient,
                                      ExcelExportArtifactWriter artifactWriter,
                                      ExcelI18nMessageResolver messageResolver,
                                      DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    @Override
    public String taskType() {
        return ADMIN_MERCHANT_FEE;
    }

    @Override
    protected Class<FeePlanQuery> queryClass() {
        return FeePlanQuery.class;
    }

    @Override
    protected String titleKey() {
        return "excel.fee.merchantTitle";
    }

    @Override
    protected String chineseTitle() {
        return "商户费率";
    }

    @Override
    protected String englishTitle() {
        return "Merchant Fees";
    }

    @Override
    protected Class<MerchantFeeExportRow> rowClass() {
        return MerchantFeeExportRow.class;
    }

    @Override
    protected ExportPage<MerchantFeeExportRow> loadPage(
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
        MerchantFeePageResponse response = sourceClient.loadMerchantFeePage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<MerchantFeeExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(this::toExportRow).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /** 转换商户费率导出行。 */
    private MerchantFeeExportRow toExportRow(MerchantFeeRow source) {
        MerchantFeeExportRow row = new MerchantFeeExportRow();
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setPlanCode(source.getPlanCode());
        row.setOriginType(source.getOriginType());
        row.setCurrentVersionNo(source.getCurrentVersionNo());
        row.setStatus(source.getStatus());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }
}
