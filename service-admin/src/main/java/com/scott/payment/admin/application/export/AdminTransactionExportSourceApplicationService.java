package com.scott.payment.admin.application.export;

import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.OrderPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.OrderPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.OrderRow;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.OperationPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.OperationPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.OperationRow;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.MerchantNotificationPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.MerchantNotificationPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.MerchantNotificationRow;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.RefundPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.RefundPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.RefundRow;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundRecord;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationResponse;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminRefundQueryService;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理端交易域异步导出来源服务，恢复冻结查询并在 SQL 层收紧冻结商户范围。
 */
@Service
public class AdminTransactionExportSourceApplicationService {
    private final AdminTransactionQueryService transactionQueryService;
    private final AdminRefundQueryService refundQueryService;

    /**
     * 创建交易主单与退款异步导出来源服务。
     */
    public AdminTransactionExportSourceApplicationService(
            AdminTransactionQueryService transactionQueryService,
            AdminRefundQueryService refundQueryService) {
        this.transactionQueryService = transactionQueryService;
        this.refundQueryService = refundQueryService;
    }

    /**
     * 按冻结条件和商户范围读取一页交易主单安全投影。
     */
    public OrderPageResponse pageOrders(OrderPageRequest request) {
        PageResult<TransactionOrderResponse> page = transactionQueryService.pageOrdersForExport(
                toOrderQuery(request), dataScope(request.isAllMerchants(), request.getMerchantIds()));
        OrderPageResponse response = new OrderPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toOrderRow).toList());
        return response;
    }

    /**
     * 按冻结条件和商户范围读取一页退款安全投影。
     */
    public RefundPageResponse pageRefunds(RefundPageRequest request) {
        PageResult<RefundRecord> page = refundQueryService.pageForExport(
                toRefundQuery(request), dataScope(request.isAllMerchants(), request.getMerchantIds()));
        RefundPageResponse response = new RefundPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toRefundRow).toList());
        return response;
    }

    /**
     * 按冻结条件和商户范围读取一页交易操作流水安全投影。
     */
    public OperationPageResponse pageOperations(OperationPageRequest request) {
        PageResult<TransactionOperationResponse> page = transactionQueryService.pageOperationsForExport(
                toOperationQuery(request), dataScope(request.isAllMerchants(), request.getMerchantIds()));
        OperationPageResponse response = new OperationPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toOperationRow).toList());
        return response;
    }

    /**
     * 按冻结条件和商户范围读取一页商户通知记录安全投影。
     */
    public MerchantNotificationPageResponse pageMerchantNotifications(
            MerchantNotificationPageRequest request) {
        PageResult<MerchantNotificationResponse> page =
                transactionQueryService.pageMerchantNotificationsForExport(
                        toMerchantNotificationQuery(request),
                        dataScope(request.isAllMerchants(), request.getMerchantIds()));
        MerchantNotificationPageResponse response = new MerchantNotificationPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toMerchantNotificationRow).toList());
        return response;
    }

    /**
     * 将内部交易主单冻结条件恢复为现有查询对象并覆盖内部页码。
     */
    private com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery toOrderQuery(
            OrderPageRequest request) {
        return toTransactionQuery(request.getQuery(), request.getPageNo(), request.getPageSize());
    }

    /**
     * 将内部交易操作冻结条件恢复为现有查询对象并覆盖内部页码。
     */
    private com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery toOperationQuery(
            OperationPageRequest request) {
        return toTransactionQuery(request.getQuery(), request.getPageNo(), request.getPageSize());
    }

    /**
     * 将共享交易筛选快照恢复为现有查询对象。
     */
    private com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery toTransactionQuery(
            com.scott.payment.admin.dto.exporttask.AdminTransactionExportInternalDTOs.TransactionOrderQuery source,
            Integer pageNo,
            Integer pageSize) {
        var target = new com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery();
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
        target.setPageNo(pageNo);
        target.setPageSize(pageSize);
        return target;
    }

    /**
     * 将内部商户通知冻结条件恢复为现有查询对象并覆盖内部页码。
     */
    private com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery
            toMerchantNotificationQuery(MerchantNotificationPageRequest request) {
        var source = request.getQuery();
        var target = new com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery();
        target.setMerchantId(source.getMerchantId());
        target.setTransactionId(source.getTransactionId());
        target.setNotifyStatus(source.getNotifyStatus());
        target.setBeginTime(source.getBeginTime());
        target.setEndTime(source.getEndTime());
        target.setQueryTimeZone(source.getQueryTimeZone());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /**
     * 将内部退款冻结条件恢复为现有查询对象并覆盖内部页码。
     */
    private com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery toRefundQuery(
            RefundPageRequest request) {
        var source = request.getQuery();
        var target = new com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery();
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
     * 将现有交易主单查询结果投影为内部安全导出行。
     */
    private OrderRow toOrderRow(TransactionOrderResponse source) {
        OrderRow row = new OrderRow();
        row.setRootTransactionId(source.getRootTransactionId());
        row.setLatestTransactionId(source.getLatestTransactionId());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setMerchantOrderId(source.getMerchantOrderId());
        row.setTransactionType(source.getTransactionType());
        row.setTransactionStatus(source.getTransactionStatus());
        row.setLifecycleStatus(source.getLifecycleStatus());
        row.setCurrentAmount(source.getCurrentAmount() == null
                ? source.getTransactionAmount() : source.getCurrentAmount());
        row.setCurrentCurrency(StringUtils.hasText(source.getCurrentCurrency())
                ? source.getCurrentCurrency() : source.getTransactionCurrency());
        row.setAuthorizedAmount(source.getAuthorizedAmount());
        row.setCapturedAmount(source.getCapturedAmount());
        row.setRefundedAmount(source.getRefundedAmount());
        row.setTransactionRate(source.getTransactionRate());
        row.setChannelCode(source.getChannelCode());
        row.setChannelOrderNo(source.getChannelOrderNo());
        row.setMerchantResponseCode(source.getMerchantResponseCode());
        row.setMerchantResponseMessage(source.getMerchantResponseMessage());
        row.setChannelMatchStatus(source.getChannelMatchStatus());
        row.setThreeDsEnabled(source.getThreeDsEnabled());
        row.setDccEnabled(source.getDccEnabled());
        row.setEdcEnabled(source.getEdcEnabled());
        row.setReconciliationStatus(source.getReconciliationStatus());
        row.setSettlementStatus(source.getSettlementStatus());
        row.setTransactionDateTime(source.getTransactionDateTime());
        return row;
    }

    /**
     * 将现有退款查询结果投影为内部安全导出行。
     */
    private RefundRow toRefundRow(RefundRecord source) {
        RefundRow row = new RefundRow();
        row.setRefundTransactionId(source.getRefundTransactionId());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setTransactionType(source.getTransactionType());
        row.setRefundScope(source.getRefundScope());
        row.setRequestSource(source.getRequestSource());
        row.setTransactionAmount(source.getTransactionAmount());
        row.setTransactionCurrency(source.getTransactionCurrency());
        row.setTransactionStatus(source.getTransactionStatus());
        row.setApprovalStatus(source.getApprovalStatus());
        row.setApplicantName(source.getApplicantName());
        row.setChannelCode(source.getChannelCode());
        row.setChannelOrderNo(source.getChannelOrderNo());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setCompleteTime(source.getCompleteTime());
        return row;
    }

    /**
     * 将现有交易操作查询结果投影为内部安全导出行。
     */
    private OperationRow toOperationRow(TransactionOperationResponse source) {
        OperationRow row = new OperationRow();
        row.setTransactionId(source.getTransactionId());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setMerchantOrderId(source.getMerchantOrderId());
        row.setTransactionType(source.getTransactionType());
        row.setTransactionStatus(source.getTransactionStatus());
        row.setTransactionAmount(source.getTransactionAmount());
        row.setTransactionCurrency(source.getTransactionCurrency());
        row.setTransactionRate(source.getTransactionRate());
        row.setMerchantResponseCode(source.getMerchantResponseCode());
        row.setMerchantResponseMessage(source.getMerchantResponseMessage());
        row.setMerchantNotificationStatus(source.getMerchantNotificationStatus());
        row.setPaymentMethod(source.getPaymentMethod());
        row.setPaymentBrand(source.getPaymentBrand());
        row.setCardBin(source.getCardBin());
        row.setCardNumberMasked(source.getCardNumberMasked());
        row.setChannelCode(source.getChannelCode());
        row.setChannelOrderNo(source.getChannelOrderNo());
        row.setChannelTransactionId(source.getChannelTransactionId());
        row.setChannelResponseCode(source.getChannelResponseCode());
        row.setChannelResponseMessage(source.getChannelResponseMessage());
        row.setAuthCode(source.getAuthCode());
        row.setAcquirerReferenceNo(source.getAcquirerReferenceNo());
        row.setChannelMatchStatus(source.getChannelMatchStatus());
        row.setThreeDsEnabled(source.getThreeDsEnabled());
        row.setDccEnabled(source.getDccEnabled());
        row.setEdcEnabled(source.getEdcEnabled());
        row.setReconciliationStatus(source.getReconciliationStatus());
        row.setSettlementStatus(source.getSettlementStatus());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setOperationTime(source.getOperationTime());
        return row;
    }

    /**
     * 将现有商户通知查询结果投影为不含地址和载荷的内部安全导出行。
     */
    private MerchantNotificationRow toMerchantNotificationRow(MerchantNotificationResponse source) {
        MerchantNotificationRow row = new MerchantNotificationRow();
        row.setNotifyId(source.getNotifyId());
        row.setTransactionId(source.getTransactionId());
        row.setOperationId(source.getOperationId());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setNotifyType(source.getNotifyType());
        row.setEventType(source.getEventType());
        row.setNotifyStatus(source.getNotifyStatus());
        row.setLastAttemptNo(source.getLastAttemptNo());
        row.setMaxRetryCount(source.getMaxRetryCount());
        row.setNextRetryTime(source.getNextRetryTime());
        row.setSuccessTime(source.getSuccessTime());
        row.setFailReason(source.getFailReason());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setCreateTime(source.getCreateTime());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }
}
