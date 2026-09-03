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
import com.scott.payment.admin.service.AdminRefundQueryService;
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
 * @description : 管理端退款应用服务，本地编排只读查询和导出，并仅将审批命令及可信操作人身份提交给 service-payment。
 * @status : create
 */
@Service
public class AdminRefundApplicationService {

    /**
     * {@code EXPORT_PAGE_SIZE}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int EXPORT_PAGE_SIZE = 500;
    /**
     * {@code EXPORT_LEASE_TIME}常量，统一 {@code AdminRefundApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final Duration EXPORT_LEASE_TIME = Duration.ofMinutes(5);
    /**
     * {@code EXPORT_TIME_FORMATTER}常量，统一 {@code AdminRefundApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AdminRefundQueryService refundQueryService;
    private final PaymentInternalClient paymentInternalClient;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;
    private final TransactionShardingProperties shardingProperties;
    private final RedisConcurrencyLimiter exportConcurrencyLimiter;

    /**
     * 创建管理端退款应用服务。
     *
     * @param refundQueryService service-admin 本地退款查询服务
     * @param paymentInternalClient service-payment 退款审批命令客户端
     * @param excelExportService Excel 分页导出服务
     * @param excelI18nMessageResolver Excel 国际化消息解析器
     * @param excelLocaleResolver Excel 语言环境解析器
     * @param shardingProperties 交易查询预算配置
     * @param exportConcurrencyLimiter 导出并发限制器
     */
    public AdminRefundApplicationService(AdminRefundQueryService refundQueryService,
                                         PaymentInternalClient paymentInternalClient,
                                         ExcelExportService excelExportService,
                                         ExcelI18nMessageResolver excelI18nMessageResolver,
                                         ExcelLocaleResolver excelLocaleResolver,
                                         TransactionShardingProperties shardingProperties,
                                         RedisConcurrencyLimiter exportConcurrencyLimiter) {
        this.refundQueryService = refundQueryService;
        this.paymentInternalClient = paymentInternalClient;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
        this.shardingProperties = shardingProperties;
        this.exportConcurrencyLimiter = exportConcurrencyLimiter;
    }

    /**
     * 查询管理端退款分页及统计。
     *
     * @param query 退款筛选、时间范围和分页条件
     * @return 退款分页和统计结果
     */
    public RefundSearchResponse search(RefundQuery query) {
        return refundQueryService.search(query);
    }

    /**
     * 查询管理端退款详情。
     *
     * @param transactionId 退款或撤销交易号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 退款记录和交易生命周期详情
     */
    public RefundDetailResponse detail(String transactionId, LocalDateTime transactionDateTime) {
        return refundQueryService.detail(transactionId, transactionDateTime);
    }

    /**
     * 按查询条件分页流式导出退款和撤销记录。
     *
     * @param query 退款筛选条件
     * @param operator 页面展示的导出操作人
     * @param response 文件下载响应
     */
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

    /**
     * 提交退款审批通过命令。
     *
     * @param approvalId 退款审批任务号
     * @param request 页面审批决策及期望版本
     * @return 支付核心返回的审批结果
     */
    public ApprovalResult approve(String approvalId, ApprovalDecisionRequest request) {
        return paymentInternalClient.approveRefund(approvalId, clientRequest(request));
    }

    /**
     * 提交退款审批拒绝命令。
     *
     * @param approvalId 退款审批任务号
     * @param request 页面审批决策及期望版本
     * @return 支付核心返回的审批结果
     */
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
        RefundSearchResponse searchResponse = refundQueryService.search(query);
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
