package com.scott.payment.admin.application.fee;

import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanDetailResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanSummaryResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeReviewResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeTemplateCreateRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeVersionSaveRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.MerchantFeeVersionSaveRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.MerchantTemplateAssignRequest;
import com.scott.payment.admin.dto.export.FeeReviewExportRow;
import com.scott.payment.admin.dto.export.FeeSimulationExportRow;
import com.scott.payment.admin.dto.export.FeeTemplateExportRow;
import com.scott.payment.admin.dto.export.MerchantFeeExportRow;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.admin.service.AdminFeeService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFeeApplicationService
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端费用应用服务，编排登录操作人快照与费用领域服务调用。
 * @status : create
 */
@Service
public class AdminFeeApplicationService {

    private static final int EXPORT_PAGE_SIZE = 200;
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminFeeService feeService;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;

    /** 构造费用应用服务。 */
    public AdminFeeApplicationService(AdminFeeService feeService,
                                      ExcelExportService excelExportService,
                                      ExcelI18nMessageResolver excelI18nMessageResolver,
                                      ExcelLocaleResolver excelLocaleResolver) {
        this.feeService = feeService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /** 分页查询模板。 */
    public PageResult<FeePlanSummaryResponse> pageTemplates(FeePlanQuery query) {
        return feeService.pageTemplates(query);
    }

    /** 按当前筛选条件分页导出费用模板。 */
    public void exportTemplates(FeePlanQuery request, HttpServletResponse response) {
        FeePlanQuery query = request == null ? new FeePlanQuery() : request;
        exportPaged("excel.fee.templateTitle", FeeTemplateExportRow.class, pageNo -> {
            query.setPageNo(pageNo);
            query.setPageSize(EXPORT_PAGE_SIZE);
            return feeService.pageTemplates(query).getRecords().stream().map(this::toTemplateExportRow).toList();
        }, querySummary(query), response);
    }

    /** 查询模板详情。 */
    public FeePlanDetailResponse getTemplate(Long id) {
        return feeService.getTemplate(id);
    }

    /** 新建模板待审核版本。 */
    public FeePlanDetailResponse createTemplate(FeeTemplateCreateRequest request) {
        Operator operator = currentOperator();
        return feeService.createTemplate(request, operator.id(), operator.name());
    }

    /** 创建模板新版本。 */
    public FeePlanDetailResponse createTemplateVersion(Long id, FeeVersionSaveRequest request) {
        Operator operator = currentOperator();
        return feeService.createTemplateVersion(id, request, operator.id(), operator.name());
    }

    /** 启用或禁用模板。 */
    public void updateTemplateStatus(Long id, boolean enabled) {
        feeService.updateTemplateStatus(id, enabled, currentOperator().name());
    }

    /** 归档模板。 */
    public void archiveTemplate(Long id) {
        feeService.archiveTemplate(id, currentOperator().name());
    }

    /** 分页查询商户费用配置状态。 */
    public PageResult<FeePlanSummaryResponse> pageMerchantFees(FeePlanQuery query) {
        return feeService.pageMerchantFees(query);
    }

    /** 按当前筛选条件分页导出商户费率。 */
    public void exportMerchantFees(FeePlanQuery request, HttpServletResponse response) {
        FeePlanQuery query = request == null ? new FeePlanQuery() : request;
        exportPaged("excel.fee.merchantTitle", MerchantFeeExportRow.class, pageNo -> {
            query.setPageNo(pageNo);
            query.setPageSize(EXPORT_PAGE_SIZE);
            return feeService.pageMerchantFees(query).getRecords().stream().map(this::toMerchantExportRow).toList();
        }, querySummary(query), response);
    }

    /** 查询商户费用配置详情。 */
    public FeePlanDetailResponse getMerchantFee(String merchantId) {
        return feeService.getMerchantFee(merchantId);
    }

    /** 原样复制模板当前生效版本，创建商户费用待审核版本。 */
    public FeePlanDetailResponse assignMerchantTemplate(String merchantId, MerchantTemplateAssignRequest request) {
        MerchantFeeVersionSaveRequest versionRequest = new MerchantFeeVersionSaveRequest();
        versionRequest.setTemplateId(request.getTemplateId());
        versionRequest.setChangeReason(request.getChangeReason());
        versionRequest.setPlanName(request.getPlanName());
        versionRequest.setRemark(request.getRemark());
        Operator operator = currentOperator();
        return feeService.createMerchantVersion(merchantId, versionRequest, operator.id(), operator.name());
    }

    /** 提交商户独立配置或基于模板调整后的待审核版本。 */
    public FeePlanDetailResponse createMerchantCustomVersion(String merchantId,
                                                             MerchantFeeVersionSaveRequest request) {
        if (request.getRules() == null || request.getRules().isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "独立或调整费率至少需要一条费用规则");
        }
        Operator operator = currentOperator();
        return feeService.createMerchantVersion(merchantId, request, operator.id(), operator.name());
    }

    /** 查询待审核列表。 */
    public PageResult<FeeReviewResponse> pageReviews(FeePlanQuery query) {
        return feeService.pageReviews(query);
    }

    /** 按当前筛选条件分页导出费率复核记录。 */
    public void exportReviews(FeePlanQuery request, HttpServletResponse response) {
        FeePlanQuery query = request == null ? new FeePlanQuery() : request;
        exportPaged("excel.fee.reviewTitle", FeeReviewExportRow.class, pageNo -> {
            query.setPageNo(pageNo);
            query.setPageSize(EXPORT_PAGE_SIZE);
            return feeService.pageReviews(query).getRecords().stream().map(this::toReviewExportRow).toList();
        }, querySummary(query), response);
    }

    /** 审核通过并即时生效。 */
    public FeePlanDetailResponse approveVersion(Long versionId, String comment) {
        Operator operator = currentOperator();
        return feeService.approveVersion(versionId, comment, operator.id(), operator.name());
    }

    /** 审核拒绝。 */
    public FeePlanDetailResponse rejectVersion(Long versionId, String comment) {
        Operator operator = currentOperator();
        return feeService.rejectVersion(versionId, comment, operator.id(), operator.name());
    }

    /** 执行费用试算。 */
    public FeeSimulationResponse simulate(FeeSimulationRequest request) {
        Operator operator = currentOperator();
        return feeService.simulate(request, operator.id(), operator.name());
    }

    /** 分页查询费用试算记录。 */
    public PageResult<FeeSimulationRecordResponse> pageSimulationRecords(FeeSimulationRecordQuery query) {
        return feeService.pageSimulationRecords(query);
    }

    /** 按当前筛选条件分页导出费用试算记录。 */
    public void exportSimulationRecords(FeeSimulationRecordQuery request, HttpServletResponse response) {
        FeeSimulationRecordQuery query = request == null ? new FeeSimulationRecordQuery() : request;
        exportPaged("excel.fee.simulationTitle", FeeSimulationExportRow.class, pageNo -> {
            query.setPageNo(pageNo);
            query.setPageSize(EXPORT_PAGE_SIZE);
            return feeService.pageSimulationRecords(query).getRecords().stream().map(this::toSimulationExportRow).toList();
        }, simulationQuerySummary(query), response);
    }

    private <T> void exportPaged(String titleKey,
                                 Class<T> rowClass,
                                 IntFunction<List<T>> pageLoader,
                                 String querySummary,
                                 HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        String title = excelI18nMessageResolver.resolve(titleKey, locale);
        LocalDateTime now = LocalDateTime.now();
        excelExportService.exportPaged(ExcelPagedExportRequest.<T>builder()
                .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                .sheetName(title)
                .titleKey(titleKey)
                .operator(currentOperator().name())
                .exportTime(now)
                .locale(locale)
                .querySummary(querySummary)
                .rowClass(rowClass)
                .pageSize(EXPORT_PAGE_SIZE)
                .pageLoader(pageLoader::apply)
                .build(), response);
    }

    private FeeTemplateExportRow toTemplateExportRow(FeePlanSummaryResponse source) {
        FeeTemplateExportRow row = new FeeTemplateExportRow();
        row.setPlanCode(source.getPlanCode());
        row.setPlanName(source.getPlanName());
        row.setCurrentVersionNo(source.getCurrentVersionNo());
        row.setStatus(source.getStatus());
        row.setRemark(source.getRemark());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    private MerchantFeeExportRow toMerchantExportRow(FeePlanSummaryResponse source) {
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

    private FeeReviewExportRow toReviewExportRow(FeeReviewResponse source) {
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

    private FeeSimulationExportRow toSimulationExportRow(FeeSimulationRecordResponse source) {
        FeeSimulationExportRow row = new FeeSimulationExportRow();
        row.setSimulationNo(source.getSimulationNo());
        row.setMerchantId(source.getMerchantId());
        row.setTransactionType(source.getTransactionType());
        row.setPaymentType(source.getPaymentType());
        row.setPaymentMethod(source.getPaymentMethod());
        row.setLabelAmount(source.getLabelAmount());
        row.setLabelCurrency(source.getLabelCurrency());
        row.setFinalFeeUsd(source.getFinalFeeUsd());
        row.setOperatorName(source.getOperatorName());
        row.setCreateTime(source.getCreateTime());
        return row;
    }

    private String querySummary(FeePlanQuery query) {
        return "keyword=" + query.getKeyword() + ", status=" + query.getStatus()
                + ", versionStatus=" + query.getVersionStatus();
    }

    private String simulationQuerySummary(FeeSimulationRecordQuery query) {
        return "simulationNo=" + query.getKeyword() + ", merchantId=" + query.getMerchantId()
                + ", transactionType=" + query.getTransactionType();
    }

    private Operator currentOperator() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null
                || !StringUtils.hasText(account.getLoginAccount())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "登录账号上下文缺失");
        }
        String name = StringUtils.hasText(account.getRealName()) ? account.getRealName() : account.getLoginAccount();
        return new Operator(account.getAccountId(), name);
    }

    private record Operator(Long id, String name) {
    }
}
