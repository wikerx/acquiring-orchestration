package com.scott.payment.merchant.application.export;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.application.transaction.MerchantRefundApplicationService;
import com.scott.payment.merchant.application.transaction.MerchantTransactionApplicationService;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantRefundQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantFundLedgerQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementBatchQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementReserveQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementSummaryQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementTransactionQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantTransactionQuery;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.RefundPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.RefundPageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.RefundRow;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.FundLedgerPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.FundLedgerPageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.FundLedgerRow;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementBatchPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementBatchPageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementBatchRow;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementReservePageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementReservePageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementReserveRow;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementSummaryPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementSummaryPageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementSummaryRow;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementTransactionPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementTransactionPageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.SettlementTransactionRow;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.TransactionPageRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.TransactionPageResponse;
import com.scott.payment.merchant.dto.exporttask.MerchantExportSourceDTOs.TransactionRow;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundRecord;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundSearchResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchSummary;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItem;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.SummaryLine;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionSettlement;
import com.scott.payment.merchant.service.MerchantSettlementQueryService;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.DetailQuery;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundLedgerResponse;
import com.scott.payment.merchant.service.MerchantFinanceService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Merchant 权威导出来源应用服务，按冻结商户号分页生成不含渠道字段的安全投影。
 */
@Service
public class MerchantExportSourceApplicationService {
    private final MerchantTransactionApplicationService transactionApplicationService;
    private final MerchantRefundApplicationService refundApplicationService;
    private final MerchantSettlementQueryService settlementQueryService;
    private final MerchantFinanceService financeService;

    /**
     * 创建商户导出来源服务。
     */
    public MerchantExportSourceApplicationService(
            MerchantTransactionApplicationService transactionApplicationService,
            MerchantRefundApplicationService refundApplicationService,
            MerchantSettlementQueryService settlementQueryService,
            MerchantFinanceService financeService) {
        this.transactionApplicationService = transactionApplicationService;
        this.refundApplicationService = refundApplicationService;
        this.settlementQueryService = settlementQueryService;
        this.financeService = financeService;
    }

    /**
     * 按冻结查询读取一页商户交易动作安全投影。
     */
    public TransactionPageResponse pageTransactions(TransactionPageRequest request) {
        requirePageRequest(request == null ? null : request.getMerchantId(),
                request == null ? null : request.getPageNo(), request == null ? null : request.getPageSize());
        com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery query =
                toTransactionQuery(request.getQuery());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        TransactionOperationSearchResponse source = transactionApplicationService.searchOperations(
                request.getMerchantId(), query);
        PageResult<TransactionOperationResponse> page = source == null ? null : source.getPage();
        TransactionPageResponse response = new TransactionPageResponse();
        response.setTotal(page == null ? 0L : page.getTotal());
        response.setRecords(page == null || page.getRecords() == null
                ? List.of() : page.getRecords().stream().map(this::toTransactionRow).toList());
        return response;
    }

    /**
     * 按冻结查询读取一页商户退款安全投影。
     */
    public RefundPageResponse pageRefunds(RefundPageRequest request) {
        requirePageRequest(request == null ? null : request.getMerchantId(),
                request == null ? null : request.getPageNo(), request == null ? null : request.getPageSize());
        com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery query =
                toRefundQuery(request.getQuery());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        RefundSearchResponse source = refundApplicationService.search(request.getMerchantId(), query);
        PageResult<RefundRecord> page = source == null ? null : source.getPage();
        RefundPageResponse response = new RefundPageResponse();
        response.setTotal(page == null ? 0L : page.getTotal());
        response.setRecords(page == null || page.getRecords() == null
                ? List.of() : page.getRecords().stream().map(this::toRefundRow).toList());
        return response;
    }

    /**
     * 按冻结查询读取一页商户结算批次安全投影。
     */
    public SettlementBatchPageResponse pageSettlementBatches(SettlementBatchPageRequest request) {
        requirePageRequest(request == null ? null : request.getMerchantId(),
                request == null ? null : request.getPageNo(), request == null ? null : request.getPageSize());
        com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery query =
                toSettlementBatchQuery(request.getQuery());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        PageResult<BatchSummary> page = settlementQueryService.searchBatches(request.getMerchantId(), query);
        SettlementBatchPageResponse response = new SettlementBatchPageResponse();
        response.setTotal(page == null ? 0L : page.getTotal());
        response.setRecords(page == null || page.getRecords() == null
                ? List.of() : page.getRecords().stream().map(this::toSettlementBatchRow).toList());
        return response;
    }

    /**
     * 按冻结商户号读取指定批次的一页结算汇总，批次归属由查询服务复核。
     */
    public SettlementSummaryPageResponse pageSettlementSummaries(SettlementSummaryPageRequest request) {
        requirePageRequest(request == null ? null : request.getMerchantId(),
                request == null ? null : request.getPageNo(), request == null ? null : request.getPageSize());
        MerchantSettlementSummaryQuery query = request.getQuery();
        if (query == null || !StringUtils.hasText(query.getSettlementBatchNo())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        PageResult<SummaryLine> page = settlementQueryService.searchResultSummaries(
                request.getMerchantId(), query.getSettlementBatchNo().trim(),
                request.getPageNo(), request.getPageSize());
        SettlementSummaryPageResponse response = new SettlementSummaryPageResponse();
        response.setTotal(page == null ? 0L : page.getTotal());
        response.setRecords(page == null || page.getRecords() == null
                ? List.of() : page.getRecords().stream().map(this::toSettlementSummaryRow).toList());
        return response;
    }

    /**
     * 按冻结查询读取一页商户交易结算安全投影。
     */
    public SettlementTransactionPageResponse pageSettlementTransactions(
            SettlementTransactionPageRequest request) {
        requirePageRequest(request == null ? null : request.getMerchantId(),
                request == null ? null : request.getPageNo(), request == null ? null : request.getPageSize());
        com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery query =
                toSettlementTransactionQuery(request.getQuery());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        PageResult<TransactionSettlement> page = settlementQueryService.searchTransactionItems(
                request.getMerchantId(), query);
        SettlementTransactionPageResponse response = new SettlementTransactionPageResponse();
        response.setTotal(page == null ? 0L : page.getTotal());
        response.setRecords(page == null || page.getRecords() == null
                ? List.of() : page.getRecords().stream().map(this::toSettlementTransactionRow).toList());
        return response;
    }

    /**
     * 按冻结查询读取一页商户保证金结算安全投影。
     */
    public SettlementReservePageResponse pageSettlementReserves(SettlementReservePageRequest request) {
        requirePageRequest(request == null ? null : request.getMerchantId(),
                request == null ? null : request.getPageNo(), request == null ? null : request.getPageSize());
        com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery query =
                toSettlementReserveQuery(request.getQuery());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        PageResult<ReserveItem> page = settlementQueryService.searchReserveItems(request.getMerchantId(), query);
        SettlementReservePageResponse response = new SettlementReservePageResponse();
        response.setTotal(page == null ? 0L : page.getTotal());
        response.setRecords(page == null || page.getRecords() == null
                ? List.of() : page.getRecords().stream().map(this::toSettlementReserveRow).toList());
        return response;
    }

    /**
     * 按冻结商户、资金账户和筛选条件读取一页余额流水安全投影。
     */
    public FundLedgerPageResponse pageFundLedgers(FundLedgerPageRequest request) {
        requirePageRequest(request == null ? null : request.getMerchantId(),
                request == null ? null : request.getPageNo(), request == null ? null : request.getPageSize());
        MerchantFundLedgerQuery sourceQuery = request.getQuery();
        if (sourceQuery == null || sourceQuery.getAccountId() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        DetailQuery query = toFundLedgerQuery(sourceQuery);
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        PageResult<FundLedgerResponse> page = financeService.pageLedgers(
                request.getMerchantId(), sourceQuery.getAccountId(), query);
        FundLedgerPageResponse response = new FundLedgerPageResponse();
        response.setTotal(page == null ? 0L : page.getTotal());
        response.setRecords(page == null || page.getRecords() == null
                ? List.of() : page.getRecords().stream().map(this::toFundLedgerRow).toList());
        return response;
    }

    /**
     * 将冻结条件恢复为商户交易查询，不接收外部商户号和分页值。
     */
    private com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery toTransactionQuery(
            MerchantTransactionQuery source) {
        if (source == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery target =
                new com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery();
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setTransactionId(source.getTransactionId());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setTransactionType(source.getTransactionType());
        target.setTransactionStatus(source.getTransactionStatus());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setPaymentBrand(source.getPaymentBrand());
        target.setMerchantResponseCode(source.getMerchantResponseCode());
        target.setSettlementStatus(source.getSettlementStatus());
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        target.setQueryTimeZone(source.getQueryTimeZone());
        return target;
    }

    /**
     * 将冻结条件恢复为商户退款查询，不接收渠道条件和平台交易金额。
     */
    private com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery toRefundQuery(
            MerchantRefundQuery source) {
        if (source == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery target =
                new com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery();
        target.setRefundTransactionId(source.getRefundTransactionId());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setMerchantOperationNo(source.getMerchantOperationNo());
        target.setTransactionType(source.getTransactionType());
        target.setRefundScope(source.getRefundScope());
        target.setApprovalStatus(source.getApprovalStatus());
        target.setTransactionStatus(source.getTransactionStatus());
        target.setRequestSource(source.getRequestSource());
        target.setLabelCurrency(source.getLabelCurrency());
        target.setMinimumTransactionAmount(source.getMinimumTransactionAmount());
        target.setMaximumTransactionAmount(source.getMaximumTransactionAmount());
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        target.setCompleteBeginTime(source.getCompleteBeginTime());
        target.setCompleteEndTime(source.getCompleteEndTime());
        target.setQueryTimeZone(source.getQueryTimeZone());
        return target;
    }

    /**
     * 将冻结条件恢复为商户结算批次查询，不接收商户号和分页值。
     */
    private com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery toSettlementBatchQuery(
            MerchantSettlementBatchQuery source) {
        if (source == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery target =
                new com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery();
        target.setSettlementBatchNo(source.getSettlementBatchNo());
        target.setBatchType(source.getBatchType());
        target.setBatchStatus(source.getBatchStatus());
        target.setBeginBusinessDate(source.getBeginBusinessDate());
        target.setEndBusinessDate(source.getEndBusinessDate());
        return target;
    }

    /**
     * 将冻结条件恢复为商户交易结算查询，不引入平台或渠道成本条件。
     */
    private com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery
            toSettlementTransactionQuery(MerchantSettlementTransactionQuery source) {
        if (source == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery target =
                new com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery();
        target.setSettlementBatchNo(source.getSettlementBatchNo());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setBeginTransactionTime(source.getBeginTransactionTime());
        target.setEndTransactionTime(source.getEndTransactionTime());
        target.setPaymentType(source.getPaymentType());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setTransactionType(source.getTransactionType());
        target.setFeeCategory(source.getFeeCategory());
        target.setBeginBusinessDate(source.getBeginBusinessDate());
        target.setEndBusinessDate(source.getEndBusinessDate());
        return target;
    }

    /**
     * 将冻结条件恢复为商户保证金结算查询，币种保持原标签责任口径。
     */
    private com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery
            toSettlementReserveQuery(MerchantSettlementReserveQuery source) {
        if (source == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery target =
                new com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery();
        target.setSettlementBatchNo(source.getSettlementBatchNo());
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
        return target;
    }

    /**
     * 将冻结余额流水条件恢复为服务查询，不接受浏览器分页或账户覆盖值。
     */
    private DetailQuery toFundLedgerQuery(MerchantFundLedgerQuery source) {
        DetailQuery target = new DetailQuery();
        target.setKeyword(source.getKeyword());
        target.setBalanceType(source.getBalanceType());
        target.setBusinessType(source.getBusinessType());
        target.setPostedStartTime(source.getPostedStartTime());
        target.setPostedEndTime(source.getPostedEndTime());
        return target;
    }

    /**
     * 将交易动作转换为商户安全行，金额始终取标签口径并排除交易汇率、渠道和对账字段。
     */
    private TransactionRow toTransactionRow(TransactionOperationResponse source) {
        TransactionRow row = new TransactionRow();
        row.setTransactionId(source.getTransactionId());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setMerchantOrderId(source.getMerchantOrderId());
        row.setTransactionType(source.getTransactionType());
        row.setTransactionStatus(source.getTransactionStatus());
        row.setTransactionAmount(source.getLabelAmount());
        row.setTransactionCurrency(source.getLabelCurrency());
        row.setMerchantResponseCode(source.getMerchantResponseCode());
        row.setMerchantResponseMessage(source.getMerchantResponseMessage());
        row.setMerchantNotificationStatus(source.getMerchantNotificationStatus());
        row.setPaymentMethod(source.getPaymentMethod());
        row.setPaymentBrand(source.getPaymentBrand());
        row.setCardBin(source.getCardBin());
        row.setCardNumberMasked(source.getCardNumberMasked());
        row.setThreeDsEnabled(source.getThreeDsEnabled());
        row.setAuthCode(source.getAuthCode());
        row.setSettlementStatus(source.getSettlementStatus());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setOperationTime(source.getOperationTime());
        return row;
    }

    /**
     * 将退款记录转换为商户安全行，金额始终取标签口径且不包含审批内部原因。
     */
    private RefundRow toRefundRow(RefundRecord source) {
        RefundRow row = new RefundRow();
        row.setRefundTransactionId(source.getRefundTransactionId());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setTransactionType(source.getTransactionType());
        row.setRefundScope(source.getRefundScope());
        row.setTransactionAmount(source.getLabelAmount());
        row.setTransactionCurrency(source.getLabelCurrency());
        row.setTransactionStatus(source.getTransactionStatus());
        row.setApprovalStatus(source.getApprovalStatus());
        row.setMerchantVisibleMessage(source.getMerchantVisibleMessage());
        row.setPaymentMethod(source.getPaymentMethod());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setCompleteTime(source.getCompleteTime());
        return row;
    }

    /**
     * 将结算批次转换为商户安全行，不返回内部账户主键或汇率来源。
     */
    private SettlementBatchRow toSettlementBatchRow(BatchSummary source) {
        SettlementBatchRow row = new SettlementBatchRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
        row.setBatchType(source.getBatchType());
        row.setBatchStatus(source.getBatchStatus());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setTransactionCount(source.getTransactionCount());
        row.setSettlementItemCount(source.getCandidateCount());
        row.setNetDirection(source.getNetDirection());
        row.setNetAmount(source.getNetAmount());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /**
     * 将结算汇总转换为商户安全行，金额直接复制不可变结算聚合结果。
     */
    private SettlementSummaryRow toSettlementSummaryRow(SummaryLine source) {
        SettlementSummaryRow row = new SettlementSummaryRow();
        row.setPaymentType(source.getPaymentType());
        row.setPaymentMethod(source.getPaymentMethod());
        row.setTransactionType(source.getTransactionType());
        row.setResultItemType(source.getResultItemType());
        row.setFeeCategory(source.getFeeCategory());
        row.setDirection(source.getDirection());
        row.setSourceCurrency(source.getSourceCurrency());
        row.setSourceAmount(source.getSourceAmount());
        row.setTargetCurrency(source.getTargetCurrency());
        row.setTargetAmount(source.getTargetAmount());
        row.setTransactionCount(source.getTransactionCount());
        return row;
    }

    /**
     * 将交易结算记录转换为商户安全行，不返回渠道金额、渠道币种或渠道汇率。
     */
    private SettlementTransactionRow toSettlementTransactionRow(TransactionSettlement source) {
        SettlementTransactionRow row = new SettlementTransactionRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
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
     * 将保证金结算记录转换为商户安全行，金额保持原标签币种且不含内部原因。
     */
    private SettlementReserveRow toSettlementReserveRow(ReserveItem source) {
        SettlementReserveRow row = new SettlementReserveRow();
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setBusinessDate(source.getBusinessDate());
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
     * 将余额流水转换为商户安全行，金额和前后余额保持账务事实原值。
     */
    private FundLedgerRow toFundLedgerRow(FundLedgerResponse source) {
        FundLedgerRow row = new FundLedgerRow();
        row.setLedgerNo(source.getLedgerNo());
        row.setBusinessType(source.getBusinessType());
        row.setSummary(source.getSummary());
        row.setBusinessNo(source.getBusinessNo());
        row.setBalanceType(source.getBalanceType());
        row.setDirection(source.getDirection());
        row.setAmount(source.getAmount());
        row.setCurrency(source.getCurrency());
        row.setBalanceBefore(source.getBalanceBefore());
        row.setBalanceAfter(source.getBalanceAfter());
        row.setOperatorName(source.getOperatorName());
        row.setReviewerName(source.getReviewerName());
        row.setPostedTime(source.getPostedTime());
        return row;
    }

    /**
     * 校验内部分页边界，防止失控页大小拖垮交易分片查询。
     */
    private void requirePageRequest(String merchantId, Integer pageNo, Integer pageSize) {
        if (!StringUtils.hasText(merchantId) || pageNo == null || pageNo < 1
                || pageSize == null || pageSize < 1 || pageSize > 500) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
    }
}
