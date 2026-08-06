package com.scott.payment.merchant.application.transaction;

import com.scott.payment.merchant.client.payment.PaymentInternalClient;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundRecord;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundSearchResponse;
import com.scott.payment.merchant.dto.export.MerchantRefundExportRow;
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
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRefundApplicationService
 * @date : 2026-08-06 16:10
 * @email : scott_x@163.com
 * @description : 商户退款应用服务，强制认证商户数据边界并生成统一商户可见处理说明。
 * @status : create
 */
@Service
public class MerchantRefundApplicationService {

    private static final int EXPORT_PAGE_SIZE = 500;
    private static final Duration EXPORT_LEASE_TIME = Duration.ofMinutes(5);
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentInternalClient paymentInternalClient;
    private final ExcelExportService excelExportService;
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    private final ExcelLocaleResolver excelLocaleResolver;
    private final TransactionShardingProperties shardingProperties;
    private final RedisConcurrencyLimiter exportConcurrencyLimiter;

    /** 创建商户退款应用服务。 */
    public MerchantRefundApplicationService(PaymentInternalClient paymentInternalClient,
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

    /** 查询当前商户退款分页和统计，忽略浏览器传入的商户号。 */
    public RefundSearchResponse search(String merchantId, RefundQuery query) {
        RefundQuery safeQuery = query == null ? new RefundQuery() : query;
        safeQuery.setMerchantId(merchantId);
        RefundSearchResponse response = paymentInternalClient.searchRefunds(safeQuery);
        if (response != null && response.getPage() != null) {
            response.getPage().getRecords().forEach(this::applyMerchantVisibleMessage);
        }
        return response;
    }

    /** 查询当前商户退款详情。 */
    public RefundDetailResponse detail(String merchantId,
                                       String transactionId,
                                       LocalDateTime transactionDateTime) {
        RefundDetailResponse response = paymentInternalClient.refundDetail(
                transactionId, transactionDateTime, merchantId);
        if (response != null && response.getRefund() != null) {
            applyMerchantVisibleMessage(response.getRefund());
        }
        return response;
    }

    /** 分页流式导出当前认证商户的退款记录。 */
    public void export(String merchantId, RefundQuery query, String operator, HttpServletResponse response) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        InternalAuthAccount account = InternalAuthContextHolder.get();
        String identity = account != null && account.getAccountId() != null
                ? "merchant-account:" + account.getAccountId()
                : "merchant:" + merchantId;
        boolean acquired = exportConcurrencyLimiter.execute(
                "transaction", "merchant-refund-export", identity,
                shardingProperties.getQueryBudget().getMaxConcurrentExportsPerUser(),
                EXPORT_LEASE_TIME,
                () -> exportExcel(merchantId, query == null ? new RefundQuery() : query, operator, response));
        if (!acquired) {
            throw new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS);
        }
    }

    private void applyMerchantVisibleMessage(RefundRecord refund) {
        if ("PENDING".equals(refund.getApprovalStatus())) {
            refund.setMerchantVisibleMessage("退款申请待平台处理");
        } else if ("REJECTED".equals(refund.getApprovalStatus())
                || "EXPIRED".equals(refund.getApprovalStatus())) {
            refund.setMerchantVisibleMessage("退款未获批准，请联系平台");
        } else if ("PENDING".equals(refund.getTransactionStatus())
                || "PROCESSING".equals(refund.getTransactionStatus())) {
            refund.setMerchantVisibleMessage("退款结果确认中");
        } else if ("SUCCESS".equals(refund.getTransactionStatus())) {
            refund.setMerchantVisibleMessage("退款成功");
        } else {
            refund.setMerchantVisibleMessage("退款失败，请联系平台");
        }
    }

    private void exportExcel(String merchantId,
                             RefundQuery query,
                             String operator,
                             HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        String titleKey = "excel.refund.merchantTitle";
        String title = excelI18nMessageResolver.resolve(titleKey, locale);
        LocalDateTime now = LocalDateTime.now();
        excelExportService.exportPaged(
                ExcelPagedExportRequest.<MerchantRefundExportRow>builder()
                        .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                        .sheetName(title)
                        .titleKey(titleKey)
                        .operator(operator)
                        .exportTime(now)
                        .locale(locale)
                        .querySummary("beginTime=" + query.getBeginTime() + ", endTime=" + query.getEndTime()
                                + ", status=" + query.getTransactionStatus())
                        .rowClass(MerchantRefundExportRow.class)
                        .pageSize(EXPORT_PAGE_SIZE)
                        .pageLoader(pageNo -> loadExportPage(merchantId, query, pageNo))
                        .build(), response);
    }

    private List<MerchantRefundExportRow> loadExportPage(String merchantId, RefundQuery query, int pageNo) {
        query.setPageNo(pageNo);
        query.setPageSize(EXPORT_PAGE_SIZE);
        RefundSearchResponse searchResponse = search(merchantId, query);
        if (searchResponse == null || searchResponse.getPage() == null) {
            return List.of();
        }
        return searchResponse.getPage().getRecords().stream().map(this::toExportRow).toList();
    }

    private MerchantRefundExportRow toExportRow(RefundRecord source) {
        MerchantRefundExportRow row = new MerchantRefundExportRow();
        row.setRefundTransactionId(source.getRefundTransactionId());
        row.setSourceTransactionId(source.getSourceTransactionId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setTransactionType(source.getTransactionType());
        row.setRefundScope(source.getRefundScope());
        row.setTransactionAmount(source.getTransactionAmount());
        row.setTransactionCurrency(source.getTransactionCurrency());
        row.setTransactionStatus(source.getTransactionStatus());
        row.setApprovalStatus(source.getApprovalStatus());
        row.setMerchantVisibleMessage(source.getMerchantVisibleMessage());
        row.setPaymentMethod(source.getPaymentMethod());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setCompleteTime(source.getCompleteTime());
        return row;
    }
}
