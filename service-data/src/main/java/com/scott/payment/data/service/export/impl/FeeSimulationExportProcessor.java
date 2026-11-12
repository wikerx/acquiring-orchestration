package com.scott.payment.data.service.export.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.FeeSimulationQuery;
import com.scott.payment.data.client.admin.AdminFeeExportSourceClient;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeSimulationPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeSimulationPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeSimulationRow;
import com.scott.payment.data.config.export.DataExportProperties;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.service.export.AdminExportScopeSnapshot;
import com.scott.payment.data.service.export.ExcelExportArtifactWriter;
import com.scott.payment.data.service.export.ExportPage;
import com.scott.payment.data.service.export.FeeSimulationExportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

import static com.scott.payment.data.service.export.DataExportTaskService.ADMIN_FEE_SIMULATION;

/** 管理端费用试算逐项异步导出处理器。 */
@Service
public class FeeSimulationExportProcessor
        extends AbstractBaseReferenceExportProcessor<FeeSimulationQuery, FeeSimulationExportRow> {
    private final AdminFeeExportSourceClient sourceClient;

    /** 创建费用试算导出处理器。 */
    public FeeSimulationExportProcessor(AdminFeeExportSourceClient sourceClient,
                                        ExcelExportArtifactWriter artifactWriter,
                                        ExcelI18nMessageResolver messageResolver,
                                        DataExportProperties properties) {
        super(artifactWriter, messageResolver, properties);
        this.sourceClient = sourceClient;
    }

    @Override
    public String taskType() {
        return ADMIN_FEE_SIMULATION;
    }

    @Override
    protected Class<FeeSimulationQuery> queryClass() {
        return FeeSimulationQuery.class;
    }

    @Override
    protected String titleKey() {
        return "excel.fee.simulationTitle";
    }

    @Override
    protected String chineseTitle() {
        return "费用试算记录";
    }

    @Override
    protected String englishTitle() {
        return "Fee Simulations";
    }

    @Override
    protected Class<FeeSimulationExportRow> rowClass() {
        return FeeSimulationExportRow.class;
    }

    @Override
    protected ExportPage<FeeSimulationExportRow> loadPage(
            DataExportTaskDO task, FeeSimulationQuery query, int pageNo, Locale locale) {
        AdminExportScopeSnapshot scope = JsonUtils.parseObject(
                task.getDataScopeSnapshot(), AdminExportScopeSnapshot.class);
        FeeSimulationPageRequest request = new FeeSimulationPageRequest();
        request.setCreatorAccountId(task.getCreatorAccountId());
        request.setAllMerchants(scope.isAllMerchants());
        request.setMerchantIds(scope.getMerchantIds());
        request.setLocale(locale.toLanguageTag());
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize());
        FeeSimulationPageResponse response = sourceClient.loadFeeSimulationPage(request);
        if (response == null) {
            throw new IllegalStateException("export source response is missing");
        }
        List<FeeSimulationExportRow> rows = response.getRecords() == null ? List.of()
                : response.getRecords().stream().map(this::toExportRow).toList();
        return new ExportPage<>(response.getSourceTotal(), rows);
    }

    /** 转换费用试算逐项导出行。 */
    private FeeSimulationExportRow toExportRow(FeeSimulationRow source) {
        FeeSimulationExportRow row = new FeeSimulationExportRow();
        row.setSimulationNo(source.getSimulationNo());
        row.setLineNo(source.getLineNo());
        row.setMerchantId(source.getMerchantId());
        row.setPlanVersionId(source.getPlanVersionId());
        row.setTransactionType(source.getTransactionType());
        row.setPaymentType(source.getPaymentType());
        row.setPaymentMethod(source.getPaymentMethod());
        row.setSelectedRiskServices(source.getSelectedRiskServices());
        row.setLabelAmount(source.getLabelAmount());
        row.setLabelCurrency(source.getLabelCurrency());
        row.setLabelToUsdRate(source.getLabelToUsdRate());
        row.setItemType(source.getItemType());
        row.setFeeCategory(source.getFeeCategory());
        row.setCalculationStatus(source.getCalculationStatus());
        row.setIncludedInFeeTotal(source.getIncludedInFeeTotal());
        row.setRiskServiceType(source.getRiskServiceType());
        row.setRuleName(source.getRuleName());
        row.setFeeMode(source.getFeeMode());
        row.setChargeTrigger(source.getChargeTrigger());
        row.setAppliedLimit(source.getAppliedLimit());
        row.setRawFeeUsd(source.getRawFeeUsd());
        row.setDetailFeeUsd(source.getDetailFeeUsd());
        row.setDetailFormula(source.getDetailFormula());
        row.setFinalFeeUsd(source.getFinalFeeUsd());
        row.setReserveAmountUsd(source.getReserveAmountUsd());
        row.setNetSettlementUsd(source.getNetSettlementUsd());
        row.setFeeTotalFormula(source.getFeeTotalFormula());
        row.setNetSettlementFormula(source.getNetSettlementFormula());
        row.setRateSource(source.getRateSource());
        row.setRateEffectiveTime(source.getRateEffectiveTime());
        row.setRateValuationTime(source.getRateValuationTime());
        row.setDetailSnapshotStatus(source.getDetailSnapshotStatus());
        row.setOperatorName(source.getOperatorName());
        row.setCreateTime(source.getCreateTime());
        return row;
    }
}
