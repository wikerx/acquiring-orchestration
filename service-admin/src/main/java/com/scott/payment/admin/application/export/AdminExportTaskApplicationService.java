package com.scott.payment.admin.application.export;

import com.scott.payment.admin.client.data.DataExportTaskInternalClient;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.DownloadRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.TaskRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.WorkItemQuery;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.TransactionOrderQuery;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RefundSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RetryRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SearchRequestDTO;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TransactionOrderSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.WorkItemSubmitRequest;
import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.util.Locale;

/**
 * 管理端异步导出应用服务，负责可信身份、数据范围冻结和 Data 内部调用编排。
 */
@Service
public class AdminExportTaskApplicationService {
    private static final String OPERATIONAL_WORK_ITEM = "ADMIN_OPERATIONAL_WORK_ITEM";
    private static final String TRANSACTION_ORDER = "ADMIN_TRANSACTION_ORDER";
    private static final String REFUND = "ADMIN_REFUND";
    private final DataExportTaskInternalClient dataClient;
    private final AdminMerchantDataScopeResolver dataScopeResolver;

    /**
     * 创建管理端异步导出用例编排服务。
     */
    public AdminExportTaskApplicationService(DataExportTaskInternalClient dataClient,
                                             AdminMerchantDataScopeResolver dataScopeResolver) {
        this.dataClient = dataClient;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 冻结当前账号、商户范围和待办查询后幂等创建异步任务。
     */
    public TaskSummary submitOperationalWorkItems(WorkItemSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), OPERATIONAL_WORK_ITEM, account, scope);
        command.setWorkItemQuery(toWorkItemQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前账号、商户范围和交易主单查询后幂等创建异步任务。
     */
    public TaskSummary submitTransactionOrders(TransactionOrderSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), TRANSACTION_ORDER, account, scope);
        command.setTransactionOrderQuery(toTransactionOrderQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 冻结当前账号、商户范围和退款查询后幂等创建异步任务。
     */
    public TaskSummary submitRefunds(RefundSubmitRequest request) {
        InternalAuthAccount account = currentAccount();
        AdminMerchantDataScope scope = dataScopeResolver.resolve(account);
        CreateRequest command = createRequest(request.getRequestId(), REFUND, account, scope);
        command.setRefundQuery(toRefundQuery(request.getQuery()));
        return dataClient.submit(command);
    }

    /**
     * 查询当前账号自己的导出任务，服务端覆盖任何调用方账号字段。
     */
    public PageResult<TaskSummary> search(SearchRequestDTO request) {
        InternalAuthAccount account = currentAccount();
        SearchRequestDTO safeRequest = request == null ? new SearchRequestDTO() : request;
        com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SearchRequestDTO command =
                new com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SearchRequestDTO();
        command.setCreatorAccountId(account.getAccountId());
        command.setTaskType(safeRequest.getTaskType());
        command.setStatus(safeRequest.getStatus());
        command.setCreatedStartTime(safeRequest.getCreatedStartTime());
        command.setCreatedEndTime(safeRequest.getCreatedEndTime());
        command.setPageNo(safeRequest.getPageNo());
        command.setPageSize(safeRequest.getPageSize());
        return dataClient.search(command);
    }

    /**
     * 查询当前账号拥有的单任务详情。
     */
    public TaskSummary detail(String taskNo) {
        return dataClient.detail(taskRequest(taskNo, null));
    }

    /**
     * 使用当前账号和预期版本人工重试 FAILED 任务。
     */
    public TaskSummary retry(String taskNo, RetryRequest request) {
        return dataClient.retry(taskRequest(taskNo, request.getExpectedVersion()));
    }

    /**
     * 通过 Data 代理私有对象流并把当前操作人与网络来源写入下载审计。
     */
    public void download(String taskNo,
                         HttpServletRequest servletRequest,
                         HttpServletResponse servletResponse) {
        InternalAuthAccount account = currentAccount();
        DownloadRequest command = new DownloadRequest();
        command.setTaskNo(requireTaskNo(taskNo));
        command.setCreatorAccountId(account.getAccountId());
        command.setOperatorName(operatorName(account));
        command.setClientIp(servletRequest == null ? null : servletRequest.getRemoteAddr());
        dataClient.download(command, servletResponse);
    }

    /**
     * 构造归属当前账号的内部任务请求。
     */
    private TaskRequest taskRequest(String taskNo, Long expectedVersion) {
        TaskRequest command = new TaskRequest();
        command.setTaskNo(requireTaskNo(taskNo));
        command.setCreatorAccountId(currentAccount().getAccountId());
        command.setExpectedVersion(expectedVersion);
        return command;
    }

    /**
     * 复制允许冻结的待办筛选字段，不保存列表当前页码和每页条数。
     */
    private WorkItemQuery toWorkItemQuery(SearchRequest source) {
        WorkItemQuery target = new WorkItemQuery();
        target.setWorkItemNo(source.getWorkItemNo());
        target.setKeyword(source.getKeyword());
        target.setSourceType(source.getSourceType());
        target.setWorkStatus(source.getWorkStatus());
        target.setPriority(source.getPriority());
        target.setOwnerAccountId(source.getOwnerAccountId());
        target.setMineOnly(source.getMineOnly());
        target.setUnassignedOnly(source.getUnassignedOnly());
        target.setOverdueOnly(source.getOverdueOnly());
        target.setDetectedStartTime(source.getDetectedStartTime());
        target.setDetectedEndTime(source.getDetectedEndTime());
        return target;
    }

    /**
     * 创建包含可信操作人、语言、时区和商户范围的 Data 内部命令。
     */
    private CreateRequest createRequest(String requestId,
                                        String taskType,
                                        InternalAuthAccount account,
                                        AdminMerchantDataScope scope) {
        CreateRequest command = new CreateRequest();
        command.setRequestId(requestId.trim());
        command.setTaskType(taskType);
        command.setTaskName(taskName(taskType, LocaleContextHolder.getLocale()));
        command.setCreatorAccountId(account.getAccountId());
        command.setCreatorAccountName(operatorName(account));
        command.setLocale(LocaleContextHolder.getLocale().toLanguageTag());
        command.setTimezone(ZoneId.systemDefault().getId());
        command.setAllMerchants(scope.allMerchants());
        command.setMerchantIds(scope.merchantIds());
        return command;
    }

    /**
     * 复制交易主单允许冻结的筛选字段并剔除分页参数。
     */
    private TransactionOrderQuery toTransactionOrderQuery(TransactionPageQuery source) {
        TransactionOrderQuery target = new TransactionOrderQuery();
        target.setMerchantId(source.getMerchantId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setTransactionId(source.getTransactionId());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setTransactionType(source.getTransactionType());
        target.setTransactionStatus(source.getTransactionStatus());
        target.setChannelCode(source.getChannelCode());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setPaymentBrand(source.getPaymentBrand());
        target.setCardBin(source.getCardBin());
        target.setChannelOrderNo(source.getChannelOrderNo());
        target.setMerchantResponseCode(source.getMerchantResponseCode());
        target.setChannelResponseCode(source.getChannelResponseCode());
        target.setAuthCode(source.getAuthCode());
        target.setAcquirerReferenceNo(source.getAcquirerReferenceNo());
        target.setChannelMatchStatus(source.getChannelMatchStatus());
        target.setReconciliationStatus(source.getReconciliationStatus());
        target.setSettlementStatus(source.getSettlementStatus());
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        target.setQueryTimeZone(source.getQueryTimeZone());
        return target;
    }

    /**
     * 复制退款允许冻结的筛选字段并剔除分页参数。
     */
    private com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.RefundQuery toRefundQuery(
            RefundQuery source) {
        com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.RefundQuery target =
                new com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.RefundQuery();
        target.setMerchantId(source.getMerchantId());
        target.setRefundTransactionId(source.getRefundTransactionId());
        target.setSourceTransactionId(source.getSourceTransactionId());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setMerchantOperationNo(source.getMerchantOperationNo());
        target.setTransactionType(source.getTransactionType());
        target.setRefundScope(source.getRefundScope());
        target.setApprovalStatus(source.getApprovalStatus());
        target.setTransactionStatus(source.getTransactionStatus());
        target.setRequestSource(source.getRequestSource());
        target.setChannelCode(source.getChannelCode());
        target.setChannelOrderNo(source.getChannelOrderNo());
        target.setAcquirerReferenceNo(source.getAcquirerReferenceNo());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setPaymentBrand(source.getPaymentBrand());
        target.setLabelCurrency(source.getLabelCurrency());
        target.setTransactionCurrency(source.getTransactionCurrency());
        target.setMinimumTransactionAmount(source.getMinimumTransactionAmount());
        target.setMaximumTransactionAmount(source.getMaximumTransactionAmount());
        target.setApplicantId(source.getApplicantId());
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        target.setCompleteBeginTime(source.getCompleteBeginTime());
        target.setCompleteEndTime(source.getCompleteEndTime());
        target.setQueryTimeZone(source.getQueryTimeZone());
        return target;
    }

    /**
     * 从线程认证上下文获取 ADMIN 账号，内部调用不能伪造浏览器身份。
     */
    private InternalAuthAccount currentAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null || account.getAppId() == null
                || !"ADMIN".equalsIgnoreCase(account.getAppCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return account;
    }

    /**
     * 选择可审计的操作人名称，真实姓名为空时回退登录账号。
     */
    private String operatorName(InternalAuthAccount account) {
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName().trim();
        }
        return StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount().trim() : "admin";
    }

    /**
     * 校验任务号后返回去除首尾空白的稳定值。
     */
    private String requireTaskNo(String taskNo) {
        if (!StringUtils.hasText(taskNo) || taskNo.length() > 64) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return taskNo.trim();
    }

    /**
     * 根据当前管理端语言生成用户可识别的任务名称。
     */
    private String taskName(String taskType, Locale locale) {
        boolean chinese = Locale.CHINESE.getLanguage().equals(locale.getLanguage());
        return switch (taskType) {
            case TRANSACTION_ORDER -> chinese ? "交易主单导出" : "Transaction Order Export";
            case REFUND -> chinese ? "退款管理导出" : "Refund Management Export";
            default -> chinese ? "统一运营待办导出" : "Operational Work Item Export";
        };
    }
}
