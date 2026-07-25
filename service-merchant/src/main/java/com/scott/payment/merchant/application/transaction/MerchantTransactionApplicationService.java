package com.scott.payment.merchant.application.transaction;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.merchant.client.payment.PaymentInternalClient;
import com.scott.payment.merchant.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionActionRequest;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionActionResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionAmountSummaryResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery;
import com.scott.payment.merchant.service.MerchantTransactionQueryService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionApplicationService
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户后台交易应用服务，位于 service-merchant 应用层，查询和导出直接读取交易查询库并强制当前商户边界，退款等状态变更动作才调用支付核心。
 * @status : create
 */
@Service
public class MerchantTransactionApplicationService {

    /**
     * 同步导出最大记录数。
     */
    private static final int MAX_SYNC_EXPORT_ROWS = 5000;

    /**
     * 内部分页拉取大小。
     */
    private static final int EXPORT_PAGE_SIZE = 500;

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 商户后台请款动作幂等号前缀。
     */
    private static final String MERCHANT_CAPTURE_ORDER_ID_PREFIX = "MCHCP";

    /**
     * 商户后台退款动作幂等号前缀。
     */
    private static final String MERCHANT_REFUND_ORDER_ID_PREFIX = "MCHRF";

    /**
     * 商户后台撤销动作幂等号前缀。
     */
    private static final String MERCHANT_VOID_ORDER_ID_PREFIX = "MCHVD";

    /**
     * 可作为退款源的交易动作类型。
     */
    private static final Set<String> REFUND_SOURCE_TYPES = Set.of("PAYMENT", "CAPTURE");

    /**
     * 可作为请款源的授权类动作类型。
     */
    private static final Set<String> CAPTURE_SOURCE_TYPES = Set.of("AUTHORIZATION", "PRE_AUTHORIZATION");

    /**
     * 可作为撤销源的授权类动作类型。
     */
    private static final Set<String> VOID_SOURCE_TYPES = Set.of("AUTHORIZATION", "PRE_AUTHORIZATION");

    /**
     * UTF-8 BOM，便于 Excel 正确识别 CSV 中文。
     */
    private static final String UTF8_BOM = "\uFEFF";

    private final PaymentInternalClient paymentInternalClient;

    private final MerchantTransactionQueryService transactionQueryService;

    /**
     * 创建商户后台交易查询应用服务。
     *
     * @param paymentInternalClient service-payment 状态变更内部客户端
     * @param transactionQueryService 商户交易只读查询服务
     */
    public MerchantTransactionApplicationService(PaymentInternalClient paymentInternalClient,
                                                 MerchantTransactionQueryService transactionQueryService) {
        this.paymentInternalClient = paymentInternalClient;
        this.transactionQueryService = transactionQueryService;
    }

    /**
     * 分页查询当前商户交易主单。
     *
     * @param merchantId 当前登录商户号
     * @param query      查询条件
     * @return 主单分页结果
     */
    public PageResult<TransactionOrderResponse> pageOrders(String merchantId, TransactionPageQuery query) {
        return transactionQueryService.pageOrders(merchantScopedQuery(merchantId, query));
    }

    /**
     * 分页查询当前商户交易动作单，并返回统计。
     *
     * @param merchantId 当前登录商户号
     * @param query      查询条件
     * @return 动作单分页与统计结果
     */
    public TransactionOperationSearchResponse searchOperations(String merchantId, TransactionPageQuery query) {
        return transactionQueryService.searchOperations(merchantScopedQuery(merchantId, query));
    }

    /**
     * 查询当前商户交易详情。
     *
     * @param merchantId     当前登录商户号
     * @param transactionId  平台交易 ID
     * @return 交易聚合详情
     */
    public TransactionDetailResponse detail(String merchantId, String transactionId) {
        return transactionQueryService.detail(merchantId, transactionId);
    }

    /**
     * 当前商户发起全额请款动作。
     *
     * @param merchantId    当前登录商户号
     * @param transactionId 原授权平台交易 ID
     * @param request       请款请求
     * @return 请款动作结果
     */
    public TransactionActionResponse capture(String merchantId, String transactionId, TransactionActionRequest request) {
        TransactionDetailResponse detailResponse = detail(merchantId, transactionId);
        ensureBelongsToMerchant(merchantId, detailResponse);
        TransactionOperationResponse sourceOperation = resolveSourceOperation(detailResponse, transactionId);
        if (!"SUCCESS".equals(sourceOperation.getTransactionStatus())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED, "only successful authorizations can be captured");
        }
        if (!CAPTURE_SOURCE_TYPES.contains(sourceOperation.getTransactionType())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED);
        }
        BigDecimal labelAmount = fullLabelAmount(sourceOperation, sourceOperation.getAvailableCaptureAmount());
        BigDecimal transactionAmount = fullTransactionAmount(sourceOperation, sourceOperation.getAvailableCaptureAmount());
        PaymentTransactionActionClientRequestDTO requestDTO = buildActionRequest(
                merchantId,
                sourceOperation,
                request,
                labelAmount,
                transactionAmount,
                MERCHANT_CAPTURE_ORDER_ID_PREFIX);
        return paymentInternalClient.capture(requestDTO);
    }

    /**
     * 发起当前商户退款动作。
     *
     * @param merchantId    当前登录商户号
     * @param transactionId 原平台交易 ID
     * @param request       退款请求
     * @return 退款动作结果
     */
    public TransactionActionResponse refund(String merchantId, String transactionId, TransactionActionRequest request) {
        TransactionDetailResponse detailResponse = detail(merchantId, transactionId);
        ensureBelongsToMerchant(merchantId, detailResponse);
        TransactionOperationResponse sourceOperation = resolveSourceOperation(detailResponse, transactionId);
        if (!"SUCCESS".equals(sourceOperation.getTransactionStatus())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED, "only successful transactions can be refunded");
        }
        if (!REFUND_SOURCE_TYPES.contains(sourceOperation.getTransactionType())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED);
        }
        BigDecimal amount = request == null ? null : request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "refund amount must be greater than 0");
        }
        BigDecimal transactionAmount = toTransactionAmount(sourceOperation, amount);
        BigDecimal availableRefundAmount = sourceOperation.getAvailableRefundAmount();
        if (availableRefundAmount != null && transactionAmount.compareTo(availableRefundAmount) > 0) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "refund amount exceeds available refund amount");
        }
        PaymentTransactionActionClientRequestDTO requestDTO = buildActionRequest(
                merchantId,
                sourceOperation,
                request,
                amount,
                transactionAmount,
                MERCHANT_REFUND_ORDER_ID_PREFIX);
        return paymentInternalClient.refund(requestDTO);
    }

    /**
     * 当前商户发起全额撤销动作。
     *
     * @param merchantId    当前登录商户号
     * @param transactionId 原授权平台交易 ID
     * @param request       撤销请求
     * @return 撤销动作结果
     */
    public TransactionActionResponse voidPayment(String merchantId, String transactionId, TransactionActionRequest request) {
        TransactionDetailResponse detailResponse = detail(merchantId, transactionId);
        ensureBelongsToMerchant(merchantId, detailResponse);
        TransactionOperationResponse sourceOperation = resolveSourceOperation(detailResponse, transactionId);
        if (!"SUCCESS".equals(sourceOperation.getTransactionStatus())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED, "only successful authorizations can be voided");
        }
        if (!VOID_SOURCE_TYPES.contains(sourceOperation.getTransactionType())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED);
        }
        PaymentTransactionActionClientRequestDTO requestDTO = buildActionRequest(
                merchantId,
                sourceOperation,
                request,
                fullLabelAmount(sourceOperation, sourceOperation.getTransactionAmount()),
                fullTransactionAmount(sourceOperation, sourceOperation.getTransactionAmount()),
                MERCHANT_VOID_ORDER_ID_PREFIX);
        return paymentInternalClient.voidPayment(requestDTO);
    }

    /**
     * 按查询条件导出当前商户交易动作流水 CSV。
     *
     * @param merchantId 当前登录商户号
     * @param query      查询条件
     * @param operator   操作人
     * @param response   HTTP 响应
     */
    public void exportOrders(String merchantId, TransactionPageQuery query, String operator, HttpServletResponse response) {
        List<TransactionOperationResponse> rows = loadAllOperations(merchantId, query);
        String fileName = "merchant_transaction_operations_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()) + ".csv";
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        try (PrintWriter writer = response.getWriter()) {
            writer.write(UTF8_BOM);
            writer.println("操作人," + csv(operator));
            writer.println("导出时间," + csv(LocalDateTime.now()));
            writer.println();
            writer.println(String.join(",",
                    "系统订单号",
                    "原系统订单号",
                    "商户订单号",
                    "请求号",
                    "标签金额",
                    "标签币种",
                    "交易金额",
                    "交易币种",
                    "交易汇率",
                    "交易类型",
                    "交易状态",
                    "支付方式",
                    "支付品牌",
                    "卡BIN",
                    "授权码",
                    "ARN",
                    "商户响应码",
                    "商户响应描述",
                    "渠道编码",
                    "渠道订单号",
                    "渠道交易ID",
                    "勾兑状态",
                    "结算状态",
                    "对账状态",
                    "动作时间",
                    "交易时间"
            ));
            rows.forEach(row -> writer.println(toOperationCsvLine(row)));
        } catch (IOException exception) {
            throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR, "export merchant transactions failed");
        }
    }

    private TransactionPageQuery merchantScopedQuery(String merchantId, TransactionPageQuery source) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant context missing");
        }
        TransactionPageQuery query = copyTransactionQuery(source);
        query.setMerchantId(merchantId);
        return query;
    }

    private List<TransactionOperationResponse> loadAllOperations(String merchantId, TransactionPageQuery sourceQuery) {
        TransactionPageQuery query = merchantScopedQuery(merchantId, sourceQuery);
        query.setPageNo(1);
        query.setPageSize(EXPORT_PAGE_SIZE);
        PageResult<TransactionOperationResponse> firstPage = transactionQueryService.searchOperations(query).getPage();
        ensureExportSize(firstPage.getTotal());
        List<TransactionOperationResponse> rows = new ArrayList<>(firstPage.getRecords());
        for (int pageNo = 2; rows.size() < firstPage.getTotal(); pageNo++) {
            query.setPageNo(pageNo);
            PageResult<TransactionOperationResponse> page = transactionQueryService.searchOperations(query).getPage();
            if (page.getRecords().isEmpty()) {
                break;
            }
            rows.addAll(page.getRecords());
        }
        return rows;
    }

    private void ensureExportSize(long total) {
        if (total > MAX_SYNC_EXPORT_ROWS) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "export result exceeds " + MAX_SYNC_EXPORT_ROWS + " rows, please narrow the query range");
        }
    }

    private TransactionPageQuery copyTransactionQuery(TransactionPageQuery source) {
        TransactionPageQuery query = source == null ? new TransactionPageQuery() : source;
        TransactionPageQuery copy = new TransactionPageQuery();
        copy.setPageNo(query.getPageNo());
        copy.setPageSize(query.getPageSize());
        copy.setMerchantOrderNo(query.getMerchantOrderNo());
        copy.setTransactionId(query.getTransactionId());
        copy.setSourceTransactionId(query.getSourceTransactionId());
        copy.setTransactionType(query.getTransactionType());
        copy.setTransactionStatus(query.getTransactionStatus());
        copy.setPaymentMethod(query.getPaymentMethod());
        copy.setPaymentBrand(query.getPaymentBrand());
        copy.setChannelOrderNo(query.getChannelOrderNo());
        copy.setMerchantResponseCode(query.getMerchantResponseCode());
        copy.setChannelMatchStatus(query.getChannelMatchStatus());
        copy.setReconciliationStatus(query.getReconciliationStatus());
        copy.setSettlementStatus(query.getSettlementStatus());
        copy.setBeginTime(query.getBeginTime());
        copy.setEndTime(query.getEndTime());
        copy.setQueryTimeZone(query.getQueryTimeZone());
        return copy;
    }

    private void ensureBelongsToMerchant(String merchantId, TransactionDetailResponse detailResponse) {
        if (detailResponse == null || detailResponse.getOrder() == null) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        if (!merchantId.equals(detailResponse.getOrder().getMerchantId())) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        boolean hasCrossMerchantOperation = detailResponse.getOperations().stream()
                .anyMatch(operation -> !merchantId.equals(operation.getMerchantId()));
        if (hasCrossMerchantOperation) {
            throw new ApiException(ApiResultEnum.FORBIDDEN, "transaction detail merchant mismatch");
        }
    }

    private TransactionOperationResponse resolveSourceOperation(TransactionDetailResponse detailResponse, String transactionId) {
        if (detailResponse == null || detailResponse.getOperations() == null) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return detailResponse.getOperations().stream()
                .filter(operation -> transactionId.equals(operation.getTransactionId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiResultEnum.ORDER_NOT_FOUND));
    }

    private PaymentTransactionActionClientRequestDTO buildActionRequest(String merchantId,
                                                                       TransactionOperationResponse sourceOperation,
                                                                       TransactionActionRequest request,
                                                                       BigDecimal labelAmount,
                                                                       BigDecimal transactionAmount,
                                                                       String orderIdPrefix) {
        LocalDateTime transactionDateTime = LocalDateTime.now();
        String merchantOrderId = request == null ? null : request.getMerchantOrderId();
        if (!StringUtils.hasText(merchantOrderId)) {
            merchantOrderId = PaymentOrderNoGenerator.nextOrderNo(orderIdPrefix, transactionDateTime);
        }
        PaymentTransactionActionClientRequestDTO requestDTO = new PaymentTransactionActionClientRequestDTO();
        requestDTO.setMerchantId(merchantId);
        requestDTO.setMerchantOrderNo(sourceOperation.getMerchantOrderNo());
        requestDTO.setMerchantOrderId(merchantOrderId);
        requestDTO.setRequestId(merchantOrderId);
        requestDTO.setAmount(transactionAmount);
        requestDTO.setCurrency(sourceOperation.getTransactionCurrency());
        requestDTO.setLabelAmount(labelAmount);
        requestDTO.setLabelCurrency(resolveLabelCurrency(sourceOperation, request));
        requestDTO.setTransactionDateTime(transactionDateTime);
        PaymentTransactionActionClientRequestDTO.TransactionInfoDTO transactionInfoDTO =
                new PaymentTransactionActionClientRequestDTO.TransactionInfoDTO();
        transactionInfoDTO.setSourceTransactionId(sourceOperation.getTransactionId());
        transactionInfoDTO.setDescription(request == null ? null : request.getReason());
        requestDTO.setTransactionInfo(transactionInfoDTO);
        return requestDTO;
    }

    private String resolveLabelCurrency(TransactionOperationResponse sourceOperation, TransactionActionRequest request) {
        if (StringUtils.hasText(request == null ? null : request.getCurrency())) {
            return request.getCurrency().trim().toUpperCase(Locale.ROOT);
        }
        if (StringUtils.hasText(sourceOperation.getLabelCurrency())) {
            return sourceOperation.getLabelCurrency();
        }
        return sourceOperation.getTransactionCurrency();
    }

    private BigDecimal fullTransactionAmount(TransactionOperationResponse sourceOperation, BigDecimal preferredAmount) {
        BigDecimal amount = preferredAmount == null ? sourceOperation.getTransactionAmount() : preferredAmount;
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "action amount must be greater than 0");
        }
        return amount;
    }

    private BigDecimal fullLabelAmount(TransactionOperationResponse sourceOperation, BigDecimal transactionAmount) {
        BigDecimal sourceTransactionAmount = sourceOperation.getTransactionAmount();
        BigDecimal sourceLabelAmount = sourceOperation.getLabelAmount();
        if (sourceTransactionAmount == null || sourceTransactionAmount.compareTo(BigDecimal.ZERO) <= 0
                || sourceLabelAmount == null || sourceLabelAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return fullTransactionAmount(sourceOperation, transactionAmount);
        }
        BigDecimal amount = fullTransactionAmount(sourceOperation, transactionAmount);
        return amount.multiply(sourceLabelAmount).divide(sourceTransactionAmount, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal toTransactionAmount(TransactionOperationResponse sourceOperation, BigDecimal labelAmount) {
        if (labelAmount == null || labelAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "action amount must be greater than 0");
        }
        BigDecimal sourceLabelAmount = sourceOperation.getLabelAmount();
        BigDecimal sourceTransactionAmount = sourceOperation.getTransactionAmount();
        if (sourceLabelAmount == null || sourceLabelAmount.compareTo(BigDecimal.ZERO) <= 0
                || sourceTransactionAmount == null || sourceTransactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return labelAmount;
        }
        return labelAmount.multiply(sourceTransactionAmount).divide(sourceLabelAmount, 6, RoundingMode.HALF_UP);
    }

    private String toOperationCsvLine(TransactionOperationResponse row) {
        return String.join(",",
                csv(row.getTransactionId()),
                csv(row.getSourceTransactionId()),
                csv(row.getMerchantOrderNo()),
                csv(row.getMerchantOrderId()),
                csv(row.getLabelAmount()),
                csv(row.getLabelCurrency()),
                csv(row.getTransactionAmount()),
                csv(row.getTransactionCurrency()),
                csv(row.getTransactionRate()),
                csv(row.getTransactionType()),
                csv(row.getTransactionStatus()),
                csv(row.getPaymentMethod()),
                csv(row.getPaymentBrand()),
                csv(row.getCardBin()),
                csv(row.getAuthCode()),
                csv(row.getAcquirerReferenceNo()),
                csv(row.getMerchantResponseCode()),
                csv(row.getMerchantResponseMessage()),
                csv(row.getChannelCode()),
                csv(row.getChannelOrderNo()),
                csv(row.getChannelTransactionId()),
                csv(row.getChannelMatchStatus()),
                csv(row.getSettlementStatus()),
                csv(row.getReconciliationStatus()),
                csv(row.getOperationTime()),
                csv(row.getTransactionDateTime())
        );
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value instanceof TransactionAmountSummaryResponse summary
                ? summary.getCurrency() + " " + summary.getAmount()
                : String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
