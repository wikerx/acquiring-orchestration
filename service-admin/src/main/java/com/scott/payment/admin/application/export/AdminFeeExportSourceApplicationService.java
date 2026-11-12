package com.scott.payment.admin.application.export;

import com.scott.payment.admin.application.fee.AdminFeeApplicationService;
import com.scott.payment.admin.dto.export.FeeSimulationExportRow;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeePlanPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeReviewPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeReviewRow;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeSimulationPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeSimulationPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeSimulationRow;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeTemplatePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeTemplateRow;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.MerchantFeePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.MerchantFeeRow;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanSummaryResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeReviewResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordQuery;
import com.scott.payment.admin.service.AdminFeeService;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 管理端费用域异步导出来源服务，复用权威分页查询并输出现有 Excel 最小字段集合。
 */
@Service
public class AdminFeeExportSourceApplicationService {
    private final AdminFeeService feeService;
    private final AdminFeeApplicationService feeApplicationService;

    /**
     * 创建费用导出来源服务。
     *
     * @param feeService 费用配置权威查询服务
     * @param feeApplicationService 费用试算导出本地化转换服务
     */
    public AdminFeeExportSourceApplicationService(
            AdminFeeService feeService,
            AdminFeeApplicationService feeApplicationService) {
        this.feeService = feeService;
        this.feeApplicationService = feeApplicationService;
    }

    /** 按冻结筛选分页读取费用模板。 */
    public FeeTemplatePageResponse pageTemplates(FeePlanPageRequest request) {
        PageResult<FeePlanSummaryResponse> page = feeService.pageTemplates(toPlanQuery(request));
        FeeTemplatePageResponse response = new FeeTemplatePageResponse();
        response.setTotal(page.getTotal());
        response.setRecords(page.getRecords().stream().map(this::toTemplateRow).toList());
        return response;
    }

    /** 按冻结筛选和商户范围分页读取商户费率。 */
    public MerchantFeePageResponse pageMerchantFees(FeePlanPageRequest request) {
        PageResult<FeePlanSummaryResponse> page = feeService.pageMerchantFeesForExport(
                toPlanQuery(request), scope(request.isAllMerchants(), request.getMerchantIds()));
        MerchantFeePageResponse response = new MerchantFeePageResponse();
        response.setTotal(page.getTotal());
        response.setRecords(page.getRecords().stream().map(this::toMerchantFeeRow).toList());
        return response;
    }

    /** 按冻结筛选和商户范围分页读取费用复核记录。 */
    public FeeReviewPageResponse pageReviews(FeePlanPageRequest request) {
        PageResult<FeeReviewResponse> page = feeService.pageReviewsForExport(
                toPlanQuery(request), scope(request.isAllMerchants(), request.getMerchantIds()));
        FeeReviewPageResponse response = new FeeReviewPageResponse();
        response.setTotal(page.getTotal());
        response.setRecords(page.getRecords().stream().map(this::toReviewRow).toList());
        return response;
    }

    /**
     * 按冻结筛选、商户范围和语言分页读取费用试算逐项明细。
     *
     * <p>sourceTotal 保持试算主记录分页语义；最终 Excel 行数由 Data 写入器按展开结果统计。</p>
     */
    public FeeSimulationPageResponse pageSimulations(FeeSimulationPageRequest request) {
        FeeSimulationRecordQuery query = new FeeSimulationRecordQuery();
        query.setKeyword(request.getQuery().getKeyword());
        query.setMerchantId(request.getQuery().getMerchantId());
        query.setTransactionType(request.getQuery().getTransactionType());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        PageResult<com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordResponse> page =
                feeService.pageSimulationRecordsForExport(
                        query, scope(request.isAllMerchants(), request.getMerchantIds()));
        Locale locale = resolveLocale(request.getLocale());
        FeeSimulationPageResponse response = new FeeSimulationPageResponse();
        response.setSourceTotal(page.getTotal());
        response.setRecords(feeApplicationService.toSimulationExportRows(page.getRecords(), locale)
                .stream().map(this::toSimulationRow).toList());
        return response;
    }

    /** 将内部费用方案筛选转换为领域分页查询，并由服务端覆盖分页。 */
    private FeePlanQuery toPlanQuery(FeePlanPageRequest request) {
        FeePlanQuery query = new FeePlanQuery();
        query.setKeyword(request.getQuery().getKeyword());
        query.setStatus(request.getQuery().getStatus());
        query.setVersionStatus(request.getQuery().getVersionStatus());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        return query;
    }

    /** 从可信任务快照恢复商户数据范围。 */
    private AdminMerchantDataScope scope(boolean allMerchants, java.util.Set<String> merchantIds) {
        return allMerchants ? AdminMerchantDataScope.all() : AdminMerchantDataScope.limited(merchantIds);
    }

    /** 解析任务冻结语言；缺失或非法语言回退简体中文。 */
    private Locale resolveLocale(String languageTag) {
        Locale locale = languageTag == null ? Locale.SIMPLIFIED_CHINESE : Locale.forLanguageTag(languageTag);
        return locale.getLanguage().isBlank() ? Locale.SIMPLIFIED_CHINESE : locale;
    }

    /** 转换费用模板最小导出投影。 */
    private FeeTemplateRow toTemplateRow(FeePlanSummaryResponse source) {
        FeeTemplateRow row = new FeeTemplateRow();
        row.setPlanCode(source.getPlanCode());
        row.setPlanName(source.getPlanName());
        row.setCurrentVersionNo(source.getCurrentVersionNo());
        row.setStatus(source.getStatus());
        row.setRemark(source.getRemark());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /** 转换商户费率最小导出投影。 */
    private MerchantFeeRow toMerchantFeeRow(FeePlanSummaryResponse source) {
        MerchantFeeRow row = new MerchantFeeRow();
        row.setMerchantId(source.getMerchantId());
        row.setMerchantName(source.getMerchantName());
        row.setPlanCode(source.getPlanCode());
        row.setOriginType(source.getOriginType());
        row.setCurrentVersionNo(source.getCurrentVersionNo());
        row.setStatus(source.getStatus());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /** 转换费率复核最小导出投影。 */
    private FeeReviewRow toReviewRow(FeeReviewResponse source) {
        FeeReviewRow row = new FeeReviewRow();
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

    /** 转换与同步导出字段完全一致的费用试算逐项投影。 */
    private FeeSimulationRow toSimulationRow(FeeSimulationExportRow source) {
        FeeSimulationRow row = new FeeSimulationRow();
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
