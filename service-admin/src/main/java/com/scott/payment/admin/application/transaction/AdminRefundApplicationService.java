package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalClientRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalDecisionRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.ApprovalResult;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundRecord;
import com.scott.payment.admin.dto.export.RefundManagementExportRow;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.redis.concurrency.RedisConcurrencyLimiter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRefundApplicationService
 * @date : 2026-08-06 16:00
 * @email : scott_x@163.com
 * @description : 管理端退款应用服务，编排只读查询并从认证上下文构造不可由浏览器伪造的审批操作人。
 * @status : create
 */
@Service
public class AdminRefundApplicationService {

    private static final int EXPORT_PAGE_SIZE = 500;
    private static final Duration EXPORT_LEASE_TIME = Duration.ofMinutes(5);
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentInternalClient paymentInternalClient;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;
    private final TransactionShardingProperties shardingProperties;
    private final RedisConcurrencyLimiter exportConcurrencyLimiter;

    /** 创建管理端退款应用服务。 */
    public AdminRefundApplicationService(PaymentInternalClient paymentInternalClient,
                                         ExcelExportService excelExportService,
                                         ExcelI18nMessageResolver excelI18nMessageResolver,
                                         ExcelLocaleResolver excelLocaleResolver,
                                         TransactionShardingProperties shardingProperties,
                                         RedisConcurrencyLimiter exportConcurrencyLimiter) {
        this.paymentInternalClient = paymentInternalClient;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
        this.shardingProperties = shardingProperties;
        this.exportConcurrencyLimiter = exportConcurrencyLimiter;
    }

    /** @param query 查询条件 @return 退款分页和统计 */
    public RefundSearchResponse search(RefundQuery query) {
        return paymentInternalClient.searchRefunds(query);
    }

    /** @return 退款详情 */
    public RefundDetailResponse detail(String transactionId, LocalDateTime transactionDateTime) {
        return paymentInternalClient.refundDetail(transactionId, transactionDateTime);
    }

    /** 按查询条件分页流式导出退款和撤销记录。 */
    public void export(RefundQuery query, String operator, HttpServletResponse response) {
        String identity = currentAdminIdentity(operator);
        boolean acquired = exportConcurrencyLimiter.execute(
                "transaction", "admin-refund-export", identity,
                shardingProperties.getQueryBudget().getMaxConcurrentExportsPerUser(),
                EXPORT_LEASE_TIME,
                () -> exportExcel(query == null ? new RefundQuery() : query, operator, response));
        if (!acquired) {
            throw new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS);
        }
    }

    /** @return 审批通过结果 */
    public ApprovalResult approve(String approvalId, ApprovalDecisionRequest request) {
        return paymentInternalClient.approveRefund(approvalId, clientRequest(request));
    }

    /** @return 审批拒绝结果 */
    public ApprovalResult reject(String approvalId, ApprovalDecisionRequest request) {
        return paymentInternalClient.rejectRefund(approvalId, clientRequest(request));
    }

    private ApprovalClientRequest clientRequest(ApprovalDecisionRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getDecisionRequestId())
                || request.getExpectedVersion() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        ApprovalClientRequest clientRequest = new ApprovalClientRequest();
        clientRequest.setDecisionRequestId(request.getDecisionRequestId());
        clientRequest.setExpectedVersion(request.getExpectedVersion());
        clientRequest.setApprovalReason(request.getApprovalReason());
        clientRequest.setOperatorId("admin-account:" + account.getAccountId());
        clientRequest.setOperatorName(StringUtils.hasText(account.getRealName())
                ? account.getRealName() : account.getLoginAccount());
        return clientRequest;
    }

    private void exportExcel(RefundQuery query, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        String titleKey = "excel.refund.adminTitle";
        String title = excelI18nMessageResolver.resolve(titleKey, locale);
        LocalDateTime now = LocalDateTime.now();
        excelExportService.exportPaged(
                ExcelPagedExportRequest.<RefundManagementExportRow>builder()
                        .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                        .sheetName(title)
                        .titleKey(titleKey)
                        .operator(operator)
                        .exportTime(now)
                        .locale(locale)
                        .querySummary(querySummary(query))
                        .rowClass(RefundManagementExportRow.class)
                        .pageSize(EXPORT_PAGE_SIZE)
                        .pageLoader(pageNo -> loadExportPage(query, pageNo))
                        .build(), response);
    }

    private List<RefundManagementExportRow> loadExportPage(RefundQuery query, int pageNo) {
        query.setPageNo(pageNo);
        query.setPageSize(EXPORT_PAGE_SIZE);
        RefundSearchResponse searchResponse = paymentInternalClient.searchRefunds(query);
        if (searchResponse == null || searchResponse.getPage() == null) {
            return List.of();
        }
        return searchResponse.getPage().getRecords().stream().map(this::toExportRow).toList();
    }

    private RefundManagementExportRow toExportRow(RefundRecord source) {
        RefundManagementExportRow row = new RefundManagementExportRow();
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

    private String querySummary(RefundQuery query) {
        return "beginTime=" + query.getBeginTime() + ", endTime=" + query.getEndTime()
                + ", merchantId=" + query.getMerchantId() + ", status=" + query.getTransactionStatus();
    }

    private String currentAdminIdentity(String operator) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account != null && account.getAccountId() != null) {
            return "admin-account:" + account.getAccountId();
        }
        return StringUtils.hasText(operator) ? "admin-operator:" + operator : "admin-operator:unknown";
    }
}
