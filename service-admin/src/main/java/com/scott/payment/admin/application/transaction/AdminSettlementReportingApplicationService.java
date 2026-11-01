package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.dto.export.SettlementBatchSummaryExportRow;
import com.scott.payment.admin.dto.export.SettlementPostingExportRow;
import com.scott.payment.admin.dto.export.SettlementTransactionExportRow;
import com.scott.payment.admin.dto.export.SettlementReviewExportRow;
import com.scott.payment.admin.dto.export.SettlementReserveItemExportRow;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReconciliationRecord;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.TransactionSettlementSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultSummaryLine;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSummary;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminSettlementQueryService;
import com.scott.payment.admin.service.AdminSettlementReportingQueryService;
import com.scott.payment.admin.service.AdminSettlementReviewQueryService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Admin 结算预审、逐笔结果、保证金动作和资金流水的本地查询与分页导出编排；每页重新应用当前账号数据范围。
 */
@Service
public class AdminSettlementReportingApplicationService {

    /**
     * {@code EXPORT_PAGE_SIZE}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int EXPORT_PAGE_SIZE = 200;
    /**
     * {@code EXPORT_TIME_FORMATTER}常量，统一 {@code AdminSettlementReportingApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminSettlementReviewQueryService reviewQueryService;
    private final AdminSettlementReportingQueryService reportingQueryService;
    private final AdminSettlementQueryService batchQueryService;
    private final AdminMerchantDataScopeResolver dataScopeResolver;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver messageResolver;
    private final ExcelLocaleResolver localeResolver;

    /**
     * 创建管理端结算报表应用服务。
     */
    @Autowired
    public AdminSettlementReportingApplicationService(AdminSettlementReviewQueryService reviewQueryService,
                                                      AdminSettlementReportingQueryService reportingQueryService,
                                                      AdminSettlementQueryService batchQueryService,
                                                      AdminMerchantDataScopeResolver dataScopeResolver,
                                                      ExcelExportService excelExportService,
                                                      ExcelI18nMessageResolver messageResolver,
                                                      ExcelLocaleResolver localeResolver) {
        this.reviewQueryService = reviewQueryService;
        this.reportingQueryService = reportingQueryService;
        this.batchQueryService = batchQueryService;
        this.dataScopeResolver = dataScopeResolver;
        this.excelExportService = excelExportService;
        this.messageResolver = messageResolver;
        this.localeResolver = localeResolver;
    }

    /**
     * 保留旧构造器，供现有聚焦测试按原依赖范围实例化。
     */
    public AdminSettlementReportingApplicationService(AdminSettlementReviewQueryService reviewQueryService,
                                                      AdminSettlementReportingQueryService reportingQueryService,
                                                      AdminMerchantDataScopeResolver dataScopeResolver,
                                                      ExcelExportService excelExportService,
                                                      ExcelI18nMessageResolver messageResolver,
                                                      ExcelLocaleResolver localeResolver) {
        this(reviewQueryService, reportingQueryService, null, dataScopeResolver,
                excelExportService, messageResolver, localeResolver);
    }

    /**
     * 查询当前 Admin 数据范围内指定正式批次的结算汇总分页。
     */
    public PageResult<ResultSummaryLine> searchBatchResultSummaries(String settlementBatchNo,
                                                                    Integer pageNo,
                                                                    Integer pageSize) {
        InternalAuthAccount account = currentAdminAccount();
        return requiredBatchQueryService().searchResultSummaries(
                settlementBatchNo, pageNo, pageSize, dataScopeResolver.resolve(account));
    }

    /**
     * 查询当前 Admin 数据范围内的结算结果明细。
     *
     * @param request 结算结果过滤和分页条件
     * @return 结算结果明细分页
     */
    public PageResult<TransactionSettlementSummary> searchResultItems(ResultItemSearchRequest request) {
        InternalAuthAccount account = currentAdminAccount();
        return reportingQueryService.searchResultItems(request, dataScopeResolver.resolve(account));
    }

    /**
     * 分页查询正式批次内一笔交易的结算财务组件。
     */
    public PageResult<ResultItemSummary> searchResultItemComponents(String settlementBatchNo,
                                                                     String transactionId,
                                                                     Integer pageNo,
                                                                     Integer pageSize) {
        InternalAuthAccount account = currentAdminAccount();
        return reportingQueryService.searchResultItemComponents(
                settlementBatchNo, transactionId, pageNo, pageSize,
                dataScopeResolver.resolve(account));
    }

    /**
     * 按真实交易身份查询当前 Admin 数据范围内的对账状态快照。
     */
    public List<ReconciliationRecord> findReconciliationRecordsByTransaction(
            String transactionId, LocalDateTime transactionDateTime) {
        InternalAuthAccount account = currentAdminAccount();
        return reportingQueryService.findReconciliationRecordsByTransaction(
                transactionId, transactionDateTime, dataScopeResolver.resolve(account));
    }

    /**
     * 按真实交易身份查询当前 Admin 数据范围内的结算结果。
     *
     * @param transactionId 真实平台交易号
     * @param transactionDateTime 交易季度精确路由时间
     * @param pageNo 页码
     * @param pageSize 页大小
     * @return 当前交易结算结果分页
     */
    public PageResult<ResultItemSummary> searchResultItemsByTransaction(String transactionId,
                                                                        LocalDateTime transactionDateTime,
                                                                        Integer pageNo,
                                                                        Integer pageSize) {
        InternalAuthAccount account = currentAdminAccount();
        return reportingQueryService.searchResultItemsByTransaction(
                transactionId, transactionDateTime, pageNo, pageSize,
                dataScopeResolver.resolve(account));
    }

    /**
     * 查询当前 Admin 数据范围内的净额入账流水。
     *
     * @param request 资金流水过滤和分页条件
     * @return 结算入账流水分页
     */
    public PageResult<PostingSummary> searchPostings(PostingSearchRequest request) {
        InternalAuthAccount account = currentAdminAccount();
        return reportingQueryService.searchPostings(request, dataScopeResolver.resolve(account));
    }

    /**
     * 查询当前 Admin 数据范围内的保证金动作明细。
     *
     * @param request 保证金动作过滤和分页条件
     * @return 保证金结算明细分页
     */
    public PageResult<ReserveItemSummary> searchReserveItems(ReserveItemSearchRequest request) {
        InternalAuthAccount account = currentAdminAccount();
        return reportingQueryService.searchReserveItems(request, dataScopeResolver.resolve(account));
    }

    /**
     * 按原支付身份查询当前 Admin 数据范围内的完整保证金结算动作。
     *
     * @param transactionId 原支付真实平台交易号
     * @param transactionDateTime 原支付季度精确路由时间
     * @param pageNo 页码
     * @param pageSize 页大小
     * @return 当前原交易保证金动作分页
     */
    public PageResult<ReserveItemSummary> searchReserveItemsByTransaction(String transactionId,
                                                                          LocalDateTime transactionDateTime,
                                                                          Integer pageNo,
                                                                          Integer pageSize) {
        InternalAuthAccount account = currentAdminAccount();
        return reportingQueryService.searchReserveItemsByTransaction(
                transactionId, transactionDateTime, pageNo, pageSize,
                dataScopeResolver.resolve(account));
    }

    /**
     * 分页导出当前数据范围内的预审单。
     *
     * @param request 预审单查询条件
     * @param response Excel 响应流
     */
    public void exportReviews(ReviewSearchRequest request, HttpServletResponse response) {
        ReviewSearchRequest query = request == null ? new ReviewSearchRequest() : request;
        Locale locale = localeResolver.resolveCurrentLocale();
        exportPaged("excel.settlement.reviewTitle", SettlementReviewExportRow.class,
                pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    InternalAuthAccount account = currentAdminAccount();
                    return reviewQueryService.searchReviews(query, dataScopeResolver.resolve(account))
                            .getRecords().stream().map(item -> toReviewExportRow(item, locale)).toList();
                }, reviewQuerySummary(query), response);
    }

    /**
     * 分页导出结算结果，金额、币种和汇率保持数据库不可变事实。
     *
     * @param request 结算结果查询条件
     * @param response Excel 响应流
     */
    public void exportResultItems(ResultItemSearchRequest request, HttpServletResponse response) {
        ResultItemSearchRequest query = request == null ? new ResultItemSearchRequest() : request;
        Locale locale = localeResolver.resolveCurrentLocale();
        exportPaged("excel.settlement.resultTitle", SettlementTransactionExportRow.class,
                pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    return searchResultItems(query).getRecords().stream()
                            .map(item -> toResultExportRow(item, locale)).toList();
                }, resultQuerySummary(query), response);
    }

    /**
     * 分页导出净额入账流水及人工结算审计字段。
     *
     * @param request 入账流水查询条件
     * @param response Excel 响应流
     */
    public void exportPostings(PostingSearchRequest request, HttpServletResponse response) {
        PostingSearchRequest query = request == null ? new PostingSearchRequest() : request;
        Locale locale = localeResolver.resolveCurrentLocale();
        exportPaged("excel.settlement.postingTitle", SettlementPostingExportRow.class,
                pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    return searchPostings(query).getRecords().stream()
                            .map(item -> toPostingExportRow(item, locale)).toList();
                }, postingQuerySummary(query), response);
    }

    /**
     * 分页导出原标签币种保证金动作和剩余责任。
     *
     * @param request 保证金动作查询条件
     * @param response Excel 响应流
     */
    public void exportReserveItems(ReserveItemSearchRequest request, HttpServletResponse response) {
        ReserveItemSearchRequest query = request == null ? new ReserveItemSearchRequest() : request;
        Locale locale = localeResolver.resolveCurrentLocale();
        exportPaged("excel.settlement.reserveTitle", SettlementReserveItemExportRow.class,
                pageNo -> {
                    query.setPageNo(pageNo);
                    query.setPageSize(EXPORT_PAGE_SIZE);
                    return searchReserveItems(query).getRecords().stream()
                            .map(item -> toReserveExportRow(item, locale)).toList();
                }, reserveQuerySummary(query), response);
    }

    /**
     * 按固定页大小导出指定正式批次的全部结算汇总。
     */
    public void exportBatchResultSummaries(String settlementBatchNo, HttpServletResponse response) {
        String batchNo = StringUtils.hasText(settlementBatchNo) ? settlementBatchNo.trim() : settlementBatchNo;
        Locale locale = localeResolver.resolveCurrentLocale();
        exportPaged("excel.settlement.batchSummaryTitle", SettlementBatchSummaryExportRow.class,
                pageNo -> searchBatchResultSummaries(batchNo, pageNo, EXPORT_PAGE_SIZE)
                        .getRecords().stream()
                        .map(item -> toBatchSummaryExportRow(item, locale)).toList(),
                "settlementBatchNo=" + batchNo, response);
    }

    /**
     * 使用统一 Excel 组件按固定页大小导出，分页加载器负责逐页执行 Admin 数据范围过滤。
     */
    private <T> void exportPaged(String titleKey,
                                 Class<T> rowClass,
                                 java.util.function.IntFunction<List<T>> pageLoader,
                                 String querySummary,
                                 HttpServletResponse response) {
        InternalAuthAccount account = currentAdminAccount();
        Locale locale = localeResolver.resolveCurrentLocale();
        String title = messageResolver.resolve(titleKey, locale);
        LocalDateTime now = LocalDateTime.now();
        excelExportService.exportPaged(ExcelPagedExportRequest.<T>builder()
                .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                .sheetName(title)
                .titleKey(titleKey)
                .operator(operatorName(account))
                .exportTime(now)
                .locale(locale)
                .querySummary(querySummary)
                .rowClass(rowClass)
                .pageSize(EXPORT_PAGE_SIZE)
                .pageLoader(pageLoader)
                .build(), response);
    }

    /**
     * 转换为复核记录导出行数据。
     */
    private SettlementReviewExportRow toReviewExportRow(ReviewSummary source, Locale locale) {
        SettlementReviewExportRow row = new SettlementReviewExportRow();
        row.setReviewOrderNo(source.getReviewOrderNo());
        row.setReviewType(settlementLabel("reviewType", source.getReviewType(), locale));
        row.setCreateMode(settlementLabel("createMode", source.getCreateMode(), locale));
        row.setMerchantId(source.getMerchantId());
        row.setBusinessDate(source.getBusinessDate());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setCandidateCount(source.getCandidateCount());
        row.setNetDirection(settlementLabel("direction", source.getNetDirection(), locale));
        row.setNetAmount(source.getNetAmount());
        row.setReviewStatus(settlementLabel("reviewStatus", source.getReviewStatus(), locale));
        row.setSubmittedByAccountName(source.getSubmittedByAccountName());
        row.setSubmittedTime(source.getSubmittedTime());
        row.setDecidedByAccountName(source.getDecidedByAccountName());
        row.setDecisionAction(settlementLabel("decisionAction", source.getDecisionAction(), locale));
        row.setDecisionTime(source.getDecisionTime());
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        return row;
    }

    /**
     * 转换为结算批次摘要导出行数据。
     */
    private SettlementBatchSummaryExportRow toBatchSummaryExportRow(ResultSummaryLine source,
                                                                     Locale locale) {
        SettlementBatchSummaryExportRow row = new SettlementBatchSummaryExportRow();
        row.setPaymentType(settlementLabel("paymentType", source.getPaymentType(), locale));
        row.setPaymentMethod(settlementLabel("paymentMethod", source.getPaymentMethod(), locale));
        row.setTransactionType(settlementLabel("transactionType", source.getTransactionType(), locale));
        row.setResultItemType(settlementLabel("resultItemType", source.getResultItemType(), locale));
        row.setFeeCategory(settlementLabel("feeCategory", source.getFeeCategory(), locale));
        row.setDirection(settlementLabel("direction", source.getDirection(), locale));
        row.setSourceCurrency(source.getSourceCurrency());
        row.setSourceAmount(source.getSourceAmount());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setTargetAmount(source.getTargetAmount());
        row.setTransactionCount(source.getTransactionCount());
        return row;
    }

    /**
     * 按业务日期解析结算批次查询服务。
     */
    private AdminSettlementQueryService requiredBatchQueryService() {
        if (batchQueryService == null) {
            throw new IllegalStateException("Admin settlement batch query service is unavailable");
        }
        return batchQueryService;
    }

    /**
     * 转换为结算结果导出行数据。
     */
    private SettlementTransactionExportRow toResultExportRow(TransactionSettlementSummary source, Locale locale) {
        SettlementTransactionExportRow row = new SettlementTransactionExportRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setBatchStatus(settlementLabel("batchStatus", source.getBatchStatus(), locale));
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setSourceTransactionDateTime(source.getSourceTransactionDateTime());
        row.setPaymentType(settlementLabel("paymentType", source.getPaymentType(), locale));
        row.setPaymentMethod(settlementLabel("paymentMethod", source.getPaymentMethod(), locale));
        row.setTransactionType(settlementLabel("transactionType", source.getTransactionType(), locale));
        row.setSourceAmount(source.getSourceAmount());
        row.setSourceCurrency(source.getSourceCurrency());
        row.setComponentCount(source.getComponentCount());
        row.setNetDirection(settlementLabel("direction", source.getNetDirection(), locale));
        row.setNetTargetAmount(source.getNetTargetAmount());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /**
     * 转换为结算入账导出行数据。
     */
    private SettlementPostingExportRow toPostingExportRow(PostingSummary source, Locale locale) {
        SettlementPostingExportRow row = new SettlementPostingExportRow();
        row.setLedgerNo(source.getLedgerNo());
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setMerchantId(source.getMerchantId());
        row.setAccountId(source.getAccountId());
        row.setBusinessType(settlementLabel("businessType", source.getBusinessType(), locale));
        row.setDirection(settlementLabel("direction", source.getDirection(), locale));
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setBalanceBefore(source.getBalanceBefore());
        row.setBalanceAfter(source.getBalanceAfter());
        row.setAccountSequence(source.getAccountSequence());
        row.setOperationMode(settlementLabel("operationMode", source.getOperationMode(), locale));
        row.setOperatorName(source.getOperatorName());
        row.setReviewerName(source.getReviewerName());
        row.setOperationReason(source.getOperationReason());
        row.setReviewComment(source.getReviewComment());
        row.setPostedTime(source.getPostedTime());
        row.setIdempotencyKey(source.getIdempotencyKey());
        row.setReversalOfLedgerId(source.getReversalOfLedgerId());
        return row;
    }

    /**
     * 转换为保证金结算导出行数据。
     */
    private SettlementReserveItemExportRow toReserveExportRow(ReserveItemSummary source, Locale locale) {
        SettlementReserveItemExportRow row = new SettlementReserveItemExportRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setMerchantId(source.getMerchantId());
        row.setReserveNo(source.getReserveNo());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setSourceTransactionDateTime(source.getSourceTransactionDateTime());
        row.setActionType(settlementLabel("reserveAction", source.getActionType(), locale));
        row.setDirection(settlementLabel("direction", source.getDirection(), locale));
        row.setCurrency(source.getCurrency());
        row.setAmount(source.getAmount());
        row.setRemainingAmount(source.getRemainingAmount());
        row.setReserveStatus(settlementLabel("reserveStatus", source.getReserveStatus(), locale));
        row.setExpectedReleaseDate(source.getExpectedReleaseDate());
        row.setActionTime(source.getActionTime());
        return row;
    }

    /**
     * 解析结算业务编码的展示标签。
     */
    private String settlementLabel(String category, String value, Locale locale) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        String key = "excel.settlement.enum." + category + "." + normalized;
        String resolved = messageResolver.resolve(key, locale);
        return key.equals(resolved) ? normalized : resolved;
    }

    /**
     * 构建结算预审查询审计摘要。
     */
    private String reviewQuerySummary(ReviewSearchRequest query) {
        return "beginBusinessDate=" + query.getBeginBusinessDate() + ", endBusinessDate="
                + query.getEndBusinessDate() + ", merchantId=" + query.getMerchantId()
                + ", reviewType=" + query.getReviewType() + ", reviewStatus=" + query.getReviewStatus();
    }

    /**
     * 构建结算结果查询审计摘要。
     */
    private String resultQuerySummary(ResultItemSearchRequest query) {
        return "beginBusinessDate=" + query.getBeginBusinessDate() + ", endBusinessDate="
                + query.getEndBusinessDate() + ", batchNo=" + query.getSettlementBatchNo()
                + ", merchantId=" + query.getMerchantId() + ", resultItemType=" + query.getResultItemType();
    }

    /**
     * 构建结算入账查询审计摘要。
     */
    private String postingQuerySummary(PostingSearchRequest query) {
        return "beginPostedTime=" + query.getBeginPostedTime() + ", endPostedTime="
                + query.getEndPostedTime() + ", batchNo=" + query.getSettlementBatchNo()
                + ", merchantId=" + query.getMerchantId() + ", operationMode=" + query.getOperationMode();
    }

    /**
     * 构建保证金结算查询审计摘要。
     */
    private String reserveQuerySummary(ReserveItemSearchRequest query) {
        return "beginBusinessDate=" + query.getBeginBusinessDate() + ", endBusinessDate="
                + query.getEndBusinessDate() + ", batchNo=" + query.getSettlementBatchNo()
                + ", merchantId=" + query.getMerchantId() + ", actionType=" + query.getActionType();
    }

    /**
     * 从可信内部认证上下文解析 Admin 账户，不允许客户端传入操作人。
     */
    private InternalAuthAccount currentAdminAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null || !"ADMIN".equalsIgnoreCase(account.getAppCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return account;
    }

    /**
     * 解析当前操作人的审计名称。
     */
    private String operatorName(InternalAuthAccount account) {
        String value = StringUtils.hasText(account.getRealName())
                ? account.getRealName().trim() : account.getLoginAccount();
        return StringUtils.hasText(value) ? value.trim() : "unknown";
    }
}
