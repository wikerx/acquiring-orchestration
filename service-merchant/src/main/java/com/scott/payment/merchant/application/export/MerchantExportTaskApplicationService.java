package com.scott.payment.merchant.application.export;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.client.data.MerchantDataExportTaskClient;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.DownloadRequest;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantRefundQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantFundLedgerQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementBatchQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementReserveQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementSummaryQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementTransactionQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantTransactionQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.SearchRequest;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.TaskRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.RefundSubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.FundLedgerSubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.RetryRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SettlementBatchSubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SettlementReserveSubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SettlementSummarySubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SettlementTransactionSubmitRequest;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.TaskSummary;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.TransactionSubmitRequest;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.DetailQuery;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundAccountResponse;
import com.scott.payment.merchant.service.MerchantFinanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.util.Locale;

/**
 * 商户异步导出应用服务，统一从认证上下文冻结账号、商户号、语言和查询条件。
 */
@Service
public class MerchantExportTaskApplicationService {
    public static final String MERCHANT_TRANSACTION_OPERATION = "MERCHANT_TRANSACTION_OPERATION";
    public static final String MERCHANT_REFUND = "MERCHANT_REFUND";
    public static final String MERCHANT_SETTLEMENT_BATCH = "MERCHANT_SETTLEMENT_BATCH";
    public static final String MERCHANT_SETTLEMENT_SUMMARY = "MERCHANT_SETTLEMENT_SUMMARY";
    public static final String MERCHANT_SETTLEMENT_TRANSACTION = "MERCHANT_SETTLEMENT_TRANSACTION";
    public static final String MERCHANT_SETTLEMENT_RESERVE = "MERCHANT_SETTLEMENT_RESERVE";
    public static final String MERCHANT_FUND_LEDGER = "MERCHANT_FUND_LEDGER";

    private final MerchantDataExportTaskClient dataClient;
    private final MerchantFinanceService financeService;

    /**
     * 创建商户异步导出应用服务。
     *
     * @param dataClient Data 异步导出内部客户端
     * @param financeService 商户资金账户只读服务
     */
    public MerchantExportTaskApplicationService(MerchantDataExportTaskClient dataClient,
                                                MerchantFinanceService financeService) {
        this.dataClient = dataClient;
        this.financeService = financeService;
    }

    /**
     * 冻结当前商户账号和交易查询条件并幂等创建异步任务。
     */
    public TaskSummary submitTransactions(TransactionSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        CreateRequest command = createRequest(request.getRequestId(), MERCHANT_TRANSACTION_OPERATION, account);
        command.setMerchantTransactionQuery(toTransactionQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前商户账号和退款查询条件并幂等创建异步任务。
     */
    public TaskSummary submitRefunds(RefundSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        CreateRequest command = createRequest(request.getRequestId(), MERCHANT_REFUND, account);
        command.setMerchantRefundQuery(toRefundQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前商户账号和结算批次条件并幂等创建异步任务。
     */
    public TaskSummary submitSettlementBatches(SettlementBatchSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        CreateRequest command = createRequest(request.getRequestId(), MERCHANT_SETTLEMENT_BATCH, account);
        command.setMerchantSettlementBatchQuery(toSettlementBatchQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前商户账号和指定结算批次并幂等创建汇总导出任务。
     */
    public TaskSummary submitSettlementSummaries(SettlementSummarySubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        CreateRequest command = createRequest(request.getRequestId(), MERCHANT_SETTLEMENT_SUMMARY, account);
        MerchantSettlementSummaryQuery query = new MerchantSettlementSummaryQuery();
        query.setSettlementBatchNo(request.getSettlementBatchNo().trim());
        command.setMerchantSettlementSummaryQuery(query);
        return dataClient.submit(command);
    }

    /**
     * 冻结当前商户账号和交易结算明细条件并幂等创建异步任务。
     */
    public TaskSummary submitSettlementTransactions(SettlementTransactionSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        CreateRequest command = createRequest(
                request.getRequestId(), MERCHANT_SETTLEMENT_TRANSACTION, account);
        command.setMerchantSettlementTransactionQuery(toSettlementTransactionQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前商户账号和保证金结算明细条件并幂等创建异步任务。
     */
    public TaskSummary submitSettlementReserves(SettlementReserveSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        CreateRequest command = createRequest(request.getRequestId(), MERCHANT_SETTLEMENT_RESERVE, account);
        command.setMerchantSettlementReserveQuery(toSettlementReserveQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前认证商户资金账户、余额流水筛选和语言后创建异步任务。
     */
    public TaskSummary submitFundLedgers(FundLedgerSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        FundAccountResponse fundAccount = financeService.getFundAccount(account.getMerchantId());
        if (fundAccount == null || fundAccount.getId() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        CreateRequest command = createRequest(request.getRequestId(), MERCHANT_FUND_LEDGER, account);
        command.setMerchantFundLedgerQuery(toFundLedgerQuery(request.getQuery(), fundAccount.getId()));
        return dataClient.submit(command);
    }

    /**
     * 查询当前认证账号在当前商户下创建的任务。
     */
    public PageResult<TaskSummary> search(com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SearchRequest request) {
        InternalAuthAccount account = currentAccount();
        com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SearchRequest safeRequest =
                request == null ? new com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.SearchRequest() : request;
        SearchRequest command = new SearchRequest();
        command.setCreatorAccountId(account.getAccountId());
        command.setMerchantId(account.getMerchantId());
        command.setTaskType(safeRequest.getTaskType());
        command.setStatus(safeRequest.getStatus());
        command.setCreatedStartTime(safeRequest.getCreatedStartTime());
        command.setCreatedEndTime(safeRequest.getCreatedEndTime());
        command.setPageNo(safeRequest.getPageNo());
        command.setPageSize(safeRequest.getPageSize());
        return dataClient.search(command);
    }

    /**
     * 查询当前商户账号拥有的任务详情。
     */
    public TaskSummary detail(String taskNo) {
        return dataClient.detail(taskRequest(taskNo, null));
    }

    /**
     * 使用版本条件重新排队当前商户账号的失败任务。
     */
    public TaskSummary retry(String taskNo, RetryRequest request) {
        return dataClient.retry(taskRequest(taskNo, request.getExpectedVersion()));
    }

    /**
     * 代理下载当前商户账号拥有的私有文件，并传递受控审计信息。
     */
    public void download(String taskNo,
                         HttpServletRequest servletRequest,
                         HttpServletResponse servletResponse) {
        InternalAuthAccount account = currentAccount();
        DownloadRequest command = new DownloadRequest();
        command.setTaskNo(requireTaskNo(taskNo));
        command.setCreatorAccountId(account.getAccountId());
        command.setMerchantId(account.getMerchantId());
        command.setOperatorName(operatorName(account));
        command.setClientIp(servletRequest == null ? null : servletRequest.getRemoteAddr());
        dataClient.download(command, servletResponse);
    }

    /**
     * 创建包含可信账号、商户号、语言和时区的 Data 内部命令。
     */
    private CreateRequest createRequest(String requestId, String taskType, InternalAuthAccount account) {
        CreateRequest command = new CreateRequest();
        command.setRequestId(requestId.trim());
        command.setTaskType(taskType);
        command.setTaskName(taskName(taskType, LocaleContextHolder.getLocale()));
        command.setCreatorAccountId(account.getAccountId());
        command.setMerchantId(account.getMerchantId());
        command.setCreatorAccountName(operatorName(account));
        command.setLocale(LocaleContextHolder.getLocale().toLanguageTag());
        command.setTimezone(ZoneId.systemDefault().getId());
        return command;
    }

    /**
     * 复制商户交易页面允许冻结的筛选字段并剔除分页和商户号。
     */
    private MerchantTransactionQuery toTransactionQuery(
            com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery source) {
        MerchantTransactionQuery target = new MerchantTransactionQuery();
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
     * 复制商户退款页面允许冻结的标签口径筛选字段并剔除分页和商户号。
     */
    private MerchantRefundQuery toRefundQuery(
            com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery source) {
        MerchantRefundQuery target = new MerchantRefundQuery();
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
     * 复制结算批次页面允许冻结的筛选字段并剔除分页参数。
     */
    private MerchantSettlementBatchQuery toSettlementBatchQuery(
            com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery source) {
        MerchantSettlementBatchQuery target = new MerchantSettlementBatchQuery();
        target.setSettlementBatchNo(source.getSettlementBatchNo());
        target.setBatchType(source.getBatchType());
        target.setBatchStatus(source.getBatchStatus());
        target.setBeginBusinessDate(source.getBeginBusinessDate());
        target.setEndBusinessDate(source.getEndBusinessDate());
        return target;
    }

    /**
     * 复制交易结算页面允许冻结的筛选字段并剔除分页参数。
     */
    private MerchantSettlementTransactionQuery toSettlementTransactionQuery(
            com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery source) {
        MerchantSettlementTransactionQuery target = new MerchantSettlementTransactionQuery();
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
     * 复制保证金结算页面允许冻结的筛选字段并剔除分页参数。
     */
    private MerchantSettlementReserveQuery toSettlementReserveQuery(
            com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery source) {
        MerchantSettlementReserveQuery target = new MerchantSettlementReserveQuery();
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
     * 复制余额流水筛选并冻结认证商户当前资金账户，分页参数不进入任务快照。
     */
    private MerchantFundLedgerQuery toFundLedgerQuery(DetailQuery source, Long accountId) {
        MerchantFundLedgerQuery target = new MerchantFundLedgerQuery();
        target.setAccountId(accountId);
        target.setKeyword(source.getKeyword());
        target.setBalanceType(source.getBalanceType());
        target.setBusinessType(source.getBusinessType());
        target.setPostedStartTime(source.getPostedStartTime());
        target.setPostedEndTime(source.getPostedEndTime());
        return target;
    }

    /**
     * 构造归属当前账号和商户的单任务请求。
     */
    private TaskRequest taskRequest(String taskNo, Long expectedVersion) {
        InternalAuthAccount account = currentAccount();
        TaskRequest command = new TaskRequest();
        command.setTaskNo(requireTaskNo(taskNo));
        command.setCreatorAccountId(account.getAccountId());
        command.setMerchantId(account.getMerchantId());
        command.setExpectedVersion(expectedVersion);
        return command;
    }

    /**
     * 读取绑定商户号的可信认证账号，缺失任一身份字段时拒绝请求。
     */
    private InternalAuthAccount currentAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return account;
    }

    /**
     * 返回操作人展示名称，避免将空值写入任务审计。
     */
    private String operatorName(InternalAuthAccount account) {
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        return StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount() : "merchant";
    }

    /**
     * 根据任务类型和冻结语言生成稳定任务名称。
     */
    private String taskName(String taskType, Locale locale) {
        boolean chinese = Locale.CHINESE.getLanguage().equals(locale.getLanguage());
        return switch (taskType) {
            case MERCHANT_TRANSACTION_OPERATION -> chinese ? "交易查询" : "Transaction Records";
            case MERCHANT_REFUND -> chinese ? "退款记录" : "Refund Records";
            case MERCHANT_SETTLEMENT_BATCH -> chinese ? "结算批次" : "Settlement Batches";
            case MERCHANT_SETTLEMENT_SUMMARY -> chinese ? "结算汇总" : "Settlement Summary";
            case MERCHANT_SETTLEMENT_TRANSACTION -> chinese ? "交易结算明细" : "Settlement Transactions";
            case MERCHANT_SETTLEMENT_RESERVE -> chinese ? "保证金结算明细" : "Settlement Reserves";
            case MERCHANT_FUND_LEDGER -> chinese ? "余额流水" : "Balance Ledger";
            default -> chinese ? "数据导出" : "Data Export";
        };
    }

    /**
     * 校验并规整任务号，拒绝空任务号枚举内部接口。
     */
    private String requireTaskNo(String taskNo) {
        if (!StringUtils.hasText(taskNo)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return taskNo.trim();
    }
}
