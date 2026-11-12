package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FeePlanQuery;
import com.scott.payment.data.client.admin.AdminFeeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeePlanPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeTemplatePageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeTemplateRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.FeeTemplateExportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_FEE_TEMPLATE;

/** 管理端费用模板异步导出处理器。 */
@Service
public class FeeTemplateExportProcessor
        extends AbstractBaseReferenceExportProcessor<FeePlanQuery, FeeTemplateExportRow> {
    private final AdminFeeExportSourceClient sourceClient;

    /** 创建费用模板导出处理器。 */
    public FeeTemplateExportProcessor(AdminFeeExportSourceClient sourceClient,
                                      ExcelExportArtifactWriter artifactWriter,
                                      ExcelI18nMessageResolver messageResolver,
                                      DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    @Override
    public String taskType() {
        return ADMIN_FEE_TEMPLATE;
    }

    @Override
    protected Class<FeePlanQuery> queryClass() {
        return FeePlanQuery.class;
    }

    @Override
    protected String titleKey() {
        return "excel.fee.templateTitle";
    }

    @Override
    protected String chineseTitle() {
        return "费用模板";
    }

    @Override
    protected String englishTitle() {
        return "Fee Templates";
    }

    @Override
    protected Class<FeeTemplateExportRow> rowClass() {
        return FeeTemplateExportRow.class;
    }

    @Override
    protected ExportPage<FeeTemplateExportRow> loadPage(
            DataExportTaskDO task, FeePlanQuery query, int pageNo, Locale locale) {
        FeePlanPageRequest request = request(task, query, pageNo);
        FeeTemplatePageResponse response = sourceClient.loadFeeTemplatePage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<FeeTemplateExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(this::toExportRow).toList();
        return new ExportPage<>(response.getTotal(), rows);
    }

    /** 构造绑定任务身份、商户范围和分页参数的内部请求。 */
    private FeePlanPageRequest request(DataExportTaskDO task, FeePlanQuery query, int pageNo) {
        com.scott.payment.data.service.export.AdminExportScopeSnapshot scope =
                com.scott.payment.component.core.json.JsonUtils.parseObject(
                        task.getDataScopeSnapshot(),
                        com.scott.payment.data.service.export.AdminExportScopeSnapshot.class);
        FeePlanPageRequest request = new FeePlanPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        return request;
    }

    /** 转换费用模板导出行。 */
    private FeeTemplateExportRow toExportRow(FeeTemplateRow source) {
        FeeTemplateExportRow row = new FeeTemplateExportRow();
        row.setPlanCode(source.getPlanCode());
        row.setPlanName(source.getPlanName());
        row.setCurrentVersionNo(source.getCurrentVersionNo());
        row.setStatus(source.getStatus());
        row.setRemark(source.getRemark());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }
}
