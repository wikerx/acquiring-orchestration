package com.scott.payment.admin.application.export;

import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReservePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReservePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReserveRow;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementTransactionPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementTransactionPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementTransactionRow;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReviewPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReviewPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementReviewRow;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementPostingPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementPostingPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminSettlementExportInternalDTOs.SettlementPostingRow;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.TransactionSettlementSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReviewSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.PostingSummary;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminSettlementReportingQueryService;
import com.scott.payment.admin.service.AdminSettlementReviewQueryService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * 管理端结算异步导出来源服务，恢复冻结查询并在 SQL 层应用冻结商户范围。
 */
@Service
public class AdminSettlementExportSourceApplicationService {
    private final AdminSettlementReportingQueryService reportingQueryService;
    private final AdminSettlementReviewQueryService reviewQueryService;

    /**
     * 创建结算异步导出来源服务。
     */
    public AdminSettlementExportSourceApplicationService(
            AdminSettlementReportingQueryService reportingQueryService,
            AdminSettlementReviewQueryService reviewQueryService) {
        this.reportingQueryService = reportingQueryService;
        this.reviewQueryService = reviewQueryService;
    }

    /**
     * 按冻结条件和商户范围读取一页结算交易安全投影。
     */
    public SettlementTransactionPageResponse pageTransactions(
            SettlementTransactionPageRequest request) {
        PageResult<TransactionSettlementSummary> page = reportingQueryService.searchResultItems(
                toTransactionQuery(request), dataScope(request.isAllMerchants(), request.getMerchantIds()));
        SettlementTransactionPageResponse response = new SettlementTransactionPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toTransactionRow).toList());
        return response;
    }

    /**
     * 按冻结条件和商户范围读取一页保证金结算安全投影。
     */
    public SettlementReservePageResponse pageReserves(SettlementReservePageRequest request) {
        PageResult<ReserveItemSummary> page = reportingQueryService.searchReserveItems(
                toReserveQuery(request), dataScope(request.isAllMerchants(), request.getMerchantIds()));
        SettlementReservePageResponse response = new SettlementReservePageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toReserveRow).toList());
        return response;
    }

    /**
     * 按冻结条件和商户范围读取一页结算审核单安全投影。
     */
    public SettlementReviewPageResponse pageReviews(SettlementReviewPageRequest request) {
        PageResult<ReviewSummary> page = reviewQueryService.searchReviews(
                toReviewQuery(request), dataScope(request.isAllMerchants(), request.getMerchantIds()));
        SettlementReviewPageResponse response = new SettlementReviewPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toReviewRow).toList());
        return response;
    }

    /**
     * 按冻结条件和商户范围读取一页结算入账流水安全投影。
     */
    public SettlementPostingPageResponse pagePostings(SettlementPostingPageRequest request) {
        PageResult<PostingSummary> page = reportingQueryService.searchPostings(
                toPostingQuery(request), dataScope(request.isAllMerchants(), request.getMerchantIds()));
        SettlementPostingPageResponse response = new SettlementPostingPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toPostingRow).toList());
        return response;
    }

    /**
     * 恢复结算交易筛选并由内部请求覆盖分页参数。
     */
    private ResultItemSearchRequest toTransactionQuery(SettlementTransactionPageRequest request) {
        var source = request.getQuery();
        ResultItemSearchRequest target = new ResultItemSearchRequest();
        target.setSettlementBatchNo(source.getSettlementBatchNo());
        target.setMerchantId(source.getMerchantId());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setBeginTransactionTime(source.getBeginTransactionTime());
        target.setEndTransactionTime(source.getEndTransactionTime());
        target.setResultItemType(source.getResultItemType());
        target.setResultRole(source.getResultRole());
        target.setDirection(source.getDirection());
        target.setTargetCurrency(source.getTargetCurrency());
        target.setSourceDetailType(source.getSourceDetailType());
        target.setBeginBusinessDate(source.getBeginBusinessDate());
        target.setEndBusinessDate(source.getEndBusinessDate());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /**
     * 恢复保证金结算筛选并由内部请求覆盖分页参数。
     */
    private ReserveItemSearchRequest toReserveQuery(SettlementReservePageRequest request) {
        var source = request.getQuery();
        ReserveItemSearchRequest target = new ReserveItemSearchRequest();
        target.setSettlementBatchNo(source.getSettlementBatchNo());
        target.setMerchantId(source.getMerchantId());
        target.setReserveNo(source.getReserveNo());
        target.setReserveActionNo(source.getReserveActionNo());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setReserveStatus(source.getReserveStatus());
        target.setActionType(source.getActionType());
        target.setCurrency(source.getCurrency());
        target.setBeginTransactionTime(source.getBeginTransactionTime());
        target.setEndTransactionTime(source.getEndTransactionTime());
        target.setBeginExpectedReleaseDate(source.getBeginExpectedReleaseDate());
        target.setEndExpectedReleaseDate(source.getEndExpectedReleaseDate());
        target.setBeginBusinessDate(source.getBeginBusinessDate());
        target.setEndBusinessDate(source.getEndBusinessDate());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /**
     * 恢复结算审核单冻结筛选并覆盖内部分页参数。
     */
    private ReviewSearchRequest toReviewQuery(SettlementReviewPageRequest request) {
        var source = request.getQuery();
        ReviewSearchRequest target = new ReviewSearchRequest();
        target.setReviewOrderNo(source.getReviewOrderNo());
        target.setMerchantId(source.getMerchantId());
        target.setReviewType(source.getReviewType());
        target.setReviewStatus(source.getReviewStatus());
        target.setBeginBusinessDate(source.getBeginBusinessDate());
        target.setEndBusinessDate(source.getEndBusinessDate());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /**
     * 恢复结算入账流水冻结筛选并覆盖内部分页参数。
     */
    private PostingSearchRequest toPostingQuery(SettlementPostingPageRequest request) {
        var source = request.getQuery();
        PostingSearchRequest target = new PostingSearchRequest();
        target.setSettlementBatchNo(source.getSettlementBatchNo());
        target.setMerchantId(source.getMerchantId());
        target.setLedgerNo(source.getLedgerNo());
        target.setDirection(source.getDirection());
        target.setOperationMode(source.getOperationMode());
        target.setCurrency(source.getCurrency());
        target.setBeginPostedTime(source.getBeginPostedTime());
        target.setEndPostedTime(source.getEndPostedTime());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /**
     * 将冻结范围恢复为显式全量或有限集合，空有限集合保持无权限语义。
     */
    private AdminMerchantDataScope dataScope(boolean allMerchants, java.util.Set<String> merchantIds) {
        return allMerchants ? AdminMerchantDataScope.all() : AdminMerchantDataScope.limited(merchantIds);
    }

    /**
     * 将结算交易查询结果投影为内部安全导出行，不重新计算任何金额。
     */
    private SettlementTransactionRow toTransactionRow(TransactionSettlementSummary source) {
        SettlementTransactionRow row = new SettlementTransactionRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setBatchStatus(source.getBatchStatus());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setSourceTransactionDateTime(source.getSourceTransactionDateTime());
        row.setPaymentType(source.getPaymentType());
        row.setPaymentMethod(source.getPaymentMethod());
        row.setTransactionType(source.getTransactionType());
        row.setSourceAmount(source.getSourceAmount());
        row.setSourceCurrency(source.getSourceCurrency());
        row.setComponentCount(source.getComponentCount());
        row.setNetDirection(source.getNetDirection());
        row.setNetTargetAmount(source.getNetTargetAmount());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /**
     * 将保证金动作查询结果投影为内部安全导出行，不转换标签币种。
     */
    private SettlementReserveRow toReserveRow(ReserveItemSummary source) {
        SettlementReserveRow row = new SettlementReserveRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setMerchantId(source.getMerchantId());
        row.setReserveNo(source.getReserveNo());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setSourceTransactionDateTime(source.getSourceTransactionDateTime());
        row.setActionType(source.getActionType());
        row.setDirection(source.getDirection());
        row.setCurrency(source.getCurrency());
        row.setAmount(source.getAmount());
        row.setRemainingAmount(source.getRemainingAmount());
        row.setReserveStatus(source.getReserveStatus());
        row.setExpectedReleaseDate(source.getExpectedReleaseDate());
        row.setActionTime(source.getActionTime());
        return row;
    }

    /**
     * 将结算审核单查询结果投影为导出安全行。
     */
    private SettlementReviewRow toReviewRow(ReviewSummary source) {
        SettlementReviewRow row = new SettlementReviewRow();
        row.setReviewOrderNo(source.getReviewOrderNo());
        row.setReviewType(source.getReviewType());
        row.setCreateMode(source.getCreateMode());
        row.setMerchantId(source.getMerchantId());
        row.setBusinessDate(source.getBusinessDate());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setCandidateCount(source.getCandidateCount());
        row.setNetDirection(source.getNetDirection());
        row.setNetAmount(source.getNetAmount());
        row.setReviewStatus(source.getReviewStatus());
        row.setSubmittedByAccountName(source.getSubmittedByAccountName());
        row.setSubmittedTime(source.getSubmittedTime());
        row.setDecidedByAccountName(source.getDecidedByAccountName());
        row.setDecisionAction(source.getDecisionAction());
        row.setDecisionTime(source.getDecisionTime());
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        return row;
    }

    /**
     * 将结算入账流水查询结果投影为导出安全行。
     */
    private SettlementPostingRow toPostingRow(PostingSummary source) {
        SettlementPostingRow row = new SettlementPostingRow();
        row.setLedgerNo(source.getLedgerNo());
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setMerchantId(source.getMerchantId());
        row.setAccountId(source.getAccountId());
        row.setBusinessType(source.getBusinessType());
        row.setDirection(source.getDirection());
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setBalanceBefore(source.getBalanceBefore());
        row.setBalanceAfter(source.getBalanceAfter());
        row.setAccountSequence(source.getAccountSequence());
        row.setOperationMode(source.getOperationMode());
        row.setOperatorName(source.getOperatorName());
        row.setReviewerName(source.getReviewerName());
        row.setOperationReason(source.getOperationReason());
        row.setReviewComment(source.getReviewComment());
        row.setPostedTime(source.getPostedTime());
        row.setIdempotencyKey(source.getIdempotencyKey());
        row.setReversalOfLedgerId(source.getReversalOfLedgerId());
        return row;
    }
}
