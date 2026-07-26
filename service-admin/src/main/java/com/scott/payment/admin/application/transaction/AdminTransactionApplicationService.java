package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.admin.dto.export.TransactionMerchantNotificationExportRow;
import com.scott.payment.admin.dto.export.TransactionOperationExportRow;
import com.scott.payment.admin.dto.export.TransactionOrderExportRow;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelLogQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionApplicationService
 * @date : 2026-07-14 23:58
 * @email : scott_x@163.com
 * @description : 管理后台交易查询应用服务，位于 service-admin 应用层，编排管理端权限入口与 service-payment 交易分表查询能力。
 * @status : create
 */
@Service
public class AdminTransactionApplicationService {

    /**
     * 同步导出最大记录数，超过该上限应改用后续异步导出任务能力。
     */
    private static final int MAX_SYNC_EXPORT_ROWS = 5000;

    /**
     * 内部分页拉取大小，受 PageRequest 安全上限保护。
     */
    private static final int EXPORT_PAGE_SIZE = 500;

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 管理端请款动作幂等号前缀。
     */
    private static final String ADMIN_CAPTURE_ORDER_ID_PREFIX = "ADMCP";

    /**
     * 管理端退款动作幂等号前缀。
     */
    private static final String ADMIN_REFUND_ORDER_ID_PREFIX = "ADMRF";

    /**
     * 管理端撤销动作幂等号前缀。
     */
    private static final String ADMIN_VOID_ORDER_ID_PREFIX = "ADMVD";

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
     * service-payment 内部查询客户端。
     */
    private final PaymentInternalClient paymentInternalClient;

    /**
     * 管理后台交易只读查询服务。
     */
    private final AdminTransactionQueryService transactionQueryService;

    /**
     * 统一 Excel 导出服务。
     */
    private final ExcelExportService excelExportService;

    /**
     * Excel 国际化文案解析器。
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;

    /**
     * Excel 导出语言解析器。
     */
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建管理后台交易查询应用服务。
     *
     * @param paymentInternalClient service-payment 内部状态变更客户端
     * @param transactionQueryService 管理后台交易只读查询服务
     */
    public AdminTransactionApplicationService(PaymentInternalClient paymentInternalClient,
                                              AdminTransactionQueryService transactionQueryService,
                                              ExcelExportService excelExportService,
                                              ExcelI18nMessageResolver excelI18nMessageResolver,
                                              ExcelLocaleResolver excelLocaleResolver) {
        this.paymentInternalClient = paymentInternalClient;
        this.transactionQueryService = transactionQueryService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 分页查询交易生命周期主单。
     *
     * @param query 查询条件
     * @return 主单分页结果
     */
    public PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query) {
        return transactionQueryService.pageOrders(query);
    }

    /**
     * 按查询条件导出交易生命周期主单。
     *
     * @param query 查询条件
     * @param operator 导出操作人
     * @param response HTTP 响应
     */
    public void exportOrders(TransactionPageQuery query, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<TransactionOrderExportRow> rows = loadAllOrders(query).stream()
                .map(this::toOrderExportRow)
                .toList();
        exportExcel("excel.transaction.order.title", TransactionOrderExportRow.class, rows, querySummary(query, locale), operator, locale, response);
    }

    /**
     * 分页查询交易动作单。
     *
     * @param query 查询条件
     * @return 动作单分页结果
     */
    public PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query) {
        return transactionQueryService.pageOperations(query);
    }

    /**
     * 按查询条件导出交易动作流水。
     *
     * @param query 查询条件
     * @param operator 导出操作人
     * @param response HTTP 响应
     */
    public void exportOperations(TransactionPageQuery query, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<TransactionOperationExportRow> rows = loadAllOperations(query).stream()
                .map(this::toOperationExportRow)
                .toList();
        exportExcel("excel.transaction.operation.title", TransactionOperationExportRow.class, rows, querySummary(query, locale), operator, locale, response);
    }

    /**
     * 分页查询交易动作单，并返回当前查询条件下的全量统计。
     *
     * @param query 查询条件
     * @return 动作单分页与统计结果
     */
    public TransactionOperationSearchResponse searchOperations(TransactionPageQuery query) {
        return transactionQueryService.searchOperations(query);
    }

    /**
     * 管理后台发起全额请款动作。
     * <p>
     * 后台只允许授权类成功交易做全额请款；页面展示标签币种，调用支付核心时按交易币种金额发起渠道请求。
     *
     * @param transactionId 原授权平台交易 ID
     * @param request 请款动作请求
     * @return 请款动作结果
     */
    public TransactionActionResponse capture(String transactionId, TransactionActionRequest request) {
        TransactionDetailResponse detailResponse = detail(transactionId);
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
                sourceOperation,
                request,
                labelAmount,
                transactionAmount,
                ADMIN_CAPTURE_ORDER_ID_PREFIX);
        return paymentInternalClient.capture(requestDTO);
    }

    /**
     * 管理后台发起退款动作。
     * <p>
     * 后台不直接修改交易表，统一转换为 service-payment 后续动作命令，由支付核心执行幂等、状态机和渠道调用。
     *
     * @param transactionId 原平台交易 ID
     * @param request 退款动作请求
     * @return 退款动作结果
     */
    public TransactionActionResponse refund(String transactionId, TransactionActionRequest request) {
        TransactionDetailResponse detailResponse = detail(transactionId);
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
                sourceOperation,
                request,
                amount,
                transactionAmount,
                ADMIN_REFUND_ORDER_ID_PREFIX);
        return paymentInternalClient.refund(requestDTO);
    }

    /**
     * 管理后台发起撤销动作。
     * <p>
     * 撤销仍走支付核心后续动作入口，由核心按原交易状态判断是否允许撤销。
     *
     * @param transactionId 原平台交易 ID
     * @param request 撤销动作请求
     * @return 撤销动作结果
     */
    public TransactionActionResponse voidPayment(String transactionId, TransactionActionRequest request) {
        TransactionDetailResponse detailResponse = detail(transactionId);
        TransactionOperationResponse sourceOperation = resolveSourceOperation(detailResponse, transactionId);
        if (!"SUCCESS".equals(sourceOperation.getTransactionStatus())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED, "only successful authorizations can be voided");
        }
        if (!VOID_SOURCE_TYPES.contains(sourceOperation.getTransactionType())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED);
        }
        PaymentTransactionActionClientRequestDTO requestDTO = buildActionRequest(
                sourceOperation,
                request,
                fullLabelAmount(sourceOperation, sourceOperation.getTransactionAmount()),
                fullTransactionAmount(sourceOperation, sourceOperation.getTransactionAmount()),
                ADMIN_VOID_ORDER_ID_PREFIX);
        return paymentInternalClient.voidPayment(requestDTO);
    }

    /**
     * 查询交易聚合详情。
     *
     * @param transactionId 平台交易 ID
     * @return 交易聚合详情
     */
    public TransactionDetailResponse detail(String transactionId) {
        return transactionQueryService.detail(transactionId);
    }

    /**
     * 分页查询渠道交互日志。
     *
     * @param query 查询条件
     * @return 渠道交互日志分页结果
     */
    public PageResult<Map<String, Object>> pageChannelLogs(ChannelLogQuery query) {
        return transactionQueryService.pageChannelLogs(query);
    }

    /**
     * 分页查询渠道回调业务记录。
     *
     * @param query 查询条件
     * @return 渠道回调分页结果
     */
    public PageResult<Map<String, Object>> pageChannelCallbacks(ChannelCallbackQuery query) {
        return transactionQueryService.pageChannelCallbacks(query);
    }

    /**
     * 分页查询商户通知任务。
     *
     * @param query 查询条件
     * @return 商户通知任务分页结果
     */
    public PageResult<Map<String, Object>> pageMerchantNotifications(MerchantNotificationQuery query) {
        return transactionQueryService.pageMerchantNotifications(query);
    }

    /**
     * 按查询条件导出商户通知任务。
     *
     * @param query 查询条件
     * @param operator 导出操作人
     * @param response HTTP 响应
     */
    public void exportMerchantNotifications(MerchantNotificationQuery query, String operator, HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        List<TransactionMerchantNotificationExportRow> rows = loadAllMerchantNotifications(query).stream()
                .map(this::toMerchantNotificationExportRow)
                .toList();
        exportExcel("excel.transaction.notification.title", TransactionMerchantNotificationExportRow.class, rows, notificationQuerySummary(query, locale), operator, locale, response);
    }

    /**
     * 编排 load All Orders 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sourceQuery source Query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private List<TransactionOrderResponse> loadAllOrders(TransactionPageQuery sourceQuery) {
        TransactionPageQuery query = copyTransactionQuery(sourceQuery);
        query.setPageNo(1);
        query.setPageSize(EXPORT_PAGE_SIZE);
        PageResult<TransactionOrderResponse> firstPage = transactionQueryService.pageOrders(query);
        ensureExportSize(firstPage.getTotal());
        List<TransactionOrderResponse> rows = new ArrayList<>(firstPage.getRecords());
        for (int pageNo = 2; rows.size() < firstPage.getTotal(); pageNo++) {
            query.setPageNo(pageNo);
            PageResult<TransactionOrderResponse> page = transactionQueryService.pageOrders(query);
            if (page.getRecords().isEmpty()) {
                break;
            }
            rows.addAll(page.getRecords());
        }
        return rows;
    }

    /**
     * 编排 load All Operations 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sourceQuery source Query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private List<TransactionOperationResponse> loadAllOperations(TransactionPageQuery sourceQuery) {
        TransactionPageQuery query = copyTransactionQuery(sourceQuery);
        query.setPageNo(1);
        query.setPageSize(EXPORT_PAGE_SIZE);
        PageResult<TransactionOperationResponse> firstPage = transactionQueryService.pageOperations(query);
        ensureExportSize(firstPage.getTotal());
        List<TransactionOperationResponse> rows = new ArrayList<>(firstPage.getRecords());
        for (int pageNo = 2; rows.size() < firstPage.getTotal(); pageNo++) {
            query.setPageNo(pageNo);
            PageResult<TransactionOperationResponse> page = transactionQueryService.pageOperations(query);
            if (page.getRecords().isEmpty()) {
                break;
            }
            rows.addAll(page.getRecords());
        }
        return rows;
    }

    /**
     * 编排 load All Merchant Notifications 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sourceQuery source Query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private List<Map<String, Object>> loadAllMerchantNotifications(MerchantNotificationQuery sourceQuery) {
        MerchantNotificationQuery query = copyNotificationQuery(sourceQuery);
        query.setPageNo(1);
        query.setPageSize(EXPORT_PAGE_SIZE);
        PageResult<Map<String, Object>> firstPage = transactionQueryService.pageMerchantNotifications(query);
        ensureExportSize(firstPage.getTotal());
        List<Map<String, Object>> rows = new ArrayList<>(firstPage.getRecords());
        for (int pageNo = 2; rows.size() < firstPage.getTotal(); pageNo++) {
            query.setPageNo(pageNo);
            PageResult<Map<String, Object>> page = transactionQueryService.pageMerchantNotifications(query);
            if (page.getRecords().isEmpty()) {
                break;
            }
            rows.addAll(page.getRecords());
        }
        return rows;
    }

    /**
     * 编排 ensure Export Size 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param total total 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void ensureExportSize(long total) {
        if (total > MAX_SYNC_EXPORT_ROWS) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "export result exceeds " + MAX_SYNC_EXPORT_ROWS + " rows, please narrow the query range");
        }
    }

    private <T> void exportExcel(String titleKey,
                                 Class<T> rowClass,
                                 List<T> rows,
                                 String querySummary,
                                 String operator,
                                 Locale locale,
                                 HttpServletResponse response) {
        LocalDateTime now = LocalDateTime.now();
        String title = excelI18nMessageResolver.resolve(titleKey, locale);
        excelExportService.export(
                ExcelExportRequest.<T>builder()
                        .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                        .sheetName(title)
                        .titleKey(titleKey)
                        .operator(operator)
                        .exportTime(now)
                        .locale(locale)
                        .querySummary(querySummary)
                        .rowClass(rowClass)
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 编排 to Order Export Row 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private TransactionOrderExportRow toOrderExportRow(TransactionOrderResponse source) {
        TransactionOrderExportRow row = new TransactionOrderExportRow();
        row.setRootTransactionId(source.getRootTransactionId());
        row.setLatestTransactionId(source.getLatestTransactionId());
        row.setMerchantId(source.getMerchantId());
        row.setMerchantOrderNo(source.getMerchantOrderNo());
        row.setMerchantOrderId(source.getMerchantOrderId());
        row.setTransactionType(source.getTransactionType());
        row.setTransactionStatus(source.getTransactionStatus());
        row.setLifecycleStatus(source.getLifecycleStatus());
        row.setCurrentAmount(source.getCurrentAmount() == null ? source.getTransactionAmount() : source.getCurrentAmount());
        row.setCurrentCurrency(StringUtils.hasText(source.getCurrentCurrency()) ? source.getCurrentCurrency() : source.getTransactionCurrency());
        row.setAuthorizedAmount(source.getAuthorizedAmount());
        row.setCapturedAmount(source.getCapturedAmount());
        row.setRefundedAmount(source.getRefundedAmount());
        row.setTransactionRate(source.getTransactionRate());
        row.setChannelCode(source.getChannelCode());
        row.setChannelOrderNo(source.getChannelOrderNo());
        row.setMerchantResponseCode(source.getMerchantResponseCode());
        row.setMerchantResponseMessage(source.getMerchantResponseMessage());
        row.setChannelMatchStatus(source.getChannelMatchStatus());
        row.setReconciliationStatus(source.getReconciliationStatus());
        row.setSettlementStatus(source.getSettlementStatus());
        row.setTransactionDateTime(source.getTransactionDateTime());
        return row;
    }

    /**
     * 编排 to Operation Export Row 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private TransactionOperationExportRow toOperationExportRow(TransactionOperationResponse source) {
        TransactionOperationExportRow row = new TransactionOperationExportRow();
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
        row.setReconciliationStatus(source.getReconciliationStatus());
        row.setSettlementStatus(source.getSettlementStatus());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setOperationTime(source.getOperationTime());
        return row;
    }

    /**
     * 编排 to Merchant Notification Export Row 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private TransactionMerchantNotificationExportRow toMerchantNotificationExportRow(Map<String, Object> source) {
        TransactionMerchantNotificationExportRow row = new TransactionMerchantNotificationExportRow();
        row.setNotifyId(textValue(source, "notifyId", "notify_id"));
        row.setTransactionId(textValue(source, "transactionId", "transaction_id"));
        row.setOperationId(textValue(source, "operationId", "operation_id"));
        row.setMerchantId(textValue(source, "merchantId", "merchant_id"));
        row.setMerchantOrderNo(textValue(source, "merchantOrderNo", "merchant_order_no"));
        row.setNotifyType(textValue(source, "notifyType", "notify_type"));
        row.setEventType(textValue(source, "eventType", "event_type"));
        row.setNotifyStatus(textValue(source, "notifyStatus", "notify_status"));
        row.setLastAttemptNo(integerValue(source, "lastAttemptNo", "last_attempt_no"));
        row.setMaxRetryCount(integerValue(source, "maxRetryCount", "max_retry_count"));
        row.setNextRetryTime(timeValue(source, "nextRetryTime", "next_retry_time"));
        row.setLastNotifyTime(timeValue(source, "lastNotifyTime", "last_notify_time"));
        row.setLastFailReason(textValue(source, "lastFailReason", "last_fail_reason"));
        row.setTransactionDateTime(timeValue(source, "transactionDateTime", "transaction_date_time"));
        row.setCreateTime(timeValue(source, "createTime", "create_time"));
        row.setUpdateTime(timeValue(source, "updateTime", "update_time"));
        return row;
    }

    /**
     * 编排 copy Transaction Query 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private TransactionPageQuery copyTransactionQuery(TransactionPageQuery source) {
        TransactionPageQuery query = source == null ? new TransactionPageQuery() : source;
        TransactionPageQuery copy = new TransactionPageQuery();
        copy.setMerchantId(query.getMerchantId());
        copy.setMerchantOrderNo(query.getMerchantOrderNo());
        copy.setTransactionId(query.getTransactionId());
        copy.setSourceTransactionId(query.getSourceTransactionId());
        copy.setTransactionType(query.getTransactionType());
        copy.setTransactionStatus(query.getTransactionStatus());
        copy.setChannelCode(query.getChannelCode());
        copy.setPaymentMethod(query.getPaymentMethod());
        copy.setPaymentBrand(query.getPaymentBrand());
        copy.setCardBin(query.getCardBin());
        copy.setChannelOrderNo(query.getChannelOrderNo());
        copy.setMerchantResponseCode(query.getMerchantResponseCode());
        copy.setChannelResponseCode(query.getChannelResponseCode());
        copy.setAuthCode(query.getAuthCode());
        copy.setAcquirerReferenceNo(query.getAcquirerReferenceNo());
        copy.setChannelMatchStatus(query.getChannelMatchStatus());
        copy.setReconciliationStatus(query.getReconciliationStatus());
        copy.setSettlementStatus(query.getSettlementStatus());
        copy.setBeginTime(query.getBeginTime());
        copy.setEndTime(query.getEndTime());
        copy.setQueryTimeZone(query.getQueryTimeZone());
        return copy;
    }

    /**
     * 编排 copy Notification Query 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private MerchantNotificationQuery copyNotificationQuery(MerchantNotificationQuery source) {
        MerchantNotificationQuery query = source == null ? new MerchantNotificationQuery() : source;
        MerchantNotificationQuery copy = new MerchantNotificationQuery();
        copy.setMerchantId(query.getMerchantId());
        copy.setTransactionId(query.getTransactionId());
        copy.setNotifyStatus(query.getNotifyStatus());
        copy.setBeginTime(query.getBeginTime());
        copy.setEndTime(query.getEndTime());
        copy.setQueryTimeZone(query.getQueryTimeZone());
        return copy;
    }

    /**
     * 编排 query Summary 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String querySummary(TransactionPageQuery query, Locale locale) {
        TransactionPageQuery safeQuery = query == null ? new TransactionPageQuery() : query;
        List<String> conditions = new ArrayList<>();
        addCondition(conditions, "excel.transaction.common.merchantId", safeQuery.getMerchantId(), locale);
        addCondition(conditions, "excel.transaction.common.merchantOrderNo", safeQuery.getMerchantOrderNo(), locale);
        addCondition(conditions, "excel.transaction.operation.transactionId", safeQuery.getTransactionId(), locale);
        addCondition(conditions, "excel.transaction.common.transactionType", safeQuery.getTransactionType(), locale);
        addCondition(conditions, "excel.transaction.common.transactionStatus", safeQuery.getTransactionStatus(), locale);
        addCondition(conditions, "excel.transaction.common.channelCode", safeQuery.getChannelCode(), locale);
        addCondition(conditions, "excel.transaction.operation.paymentMethod", safeQuery.getPaymentMethod(), locale);
        addCondition(conditions, "excel.transaction.operation.paymentBrand", safeQuery.getPaymentBrand(), locale);
        addCondition(conditions, "excel.transaction.common.channelOrderNo", safeQuery.getChannelOrderNo(), locale);
        addCondition(conditions, "excel.transaction.query.beginTime", safeQuery.getBeginTime(), locale);
        addCondition(conditions, "excel.transaction.query.endTime", safeQuery.getEndTime(), locale);
        return conditions.isEmpty() ? excelI18nMessageResolver.resolve("excel.common.noCondition", locale) : String.join("; ", conditions);
    }

    /**
     * 编排 notification Query Summary 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String notificationQuerySummary(MerchantNotificationQuery query, Locale locale) {
        MerchantNotificationQuery safeQuery = query == null ? new MerchantNotificationQuery() : query;
        List<String> conditions = new ArrayList<>();
        addCondition(conditions, "excel.transaction.common.merchantId", safeQuery.getMerchantId(), locale);
        addCondition(conditions, "excel.transaction.operation.transactionId", safeQuery.getTransactionId(), locale);
        addCondition(conditions, "excel.transaction.notification.notifyStatus", safeQuery.getNotifyStatus(), locale);
        addCondition(conditions, "excel.transaction.query.beginTime", safeQuery.getBeginTime(), locale);
        addCondition(conditions, "excel.transaction.query.endTime", safeQuery.getEndTime(), locale);
        return conditions.isEmpty() ? excelI18nMessageResolver.resolve("excel.common.noCondition", locale) : String.join("; ", conditions);
    }

    /**
     * 编排 add Condition 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param conditions conditions 输入值，含义由调用方法名称和所属业务对象限定
     * @param labelKey label Key 输入值，含义由调用方法名称和所属业务对象限定
     * @param value 待校验或转换的原始值
     * @param locale locale 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void addCondition(List<String> conditions, String labelKey, Object value, Locale locale) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && !StringUtils.hasText(stringValue)) {
            return;
        }
        conditions.add(excelI18nMessageResolver.resolve(labelKey, locale) + "=" + value);
    }

    /**
     * 编排 text Value 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @param camelKey camel Key 输入值，含义由调用方法名称和所属业务对象限定
     * @param snakeKey snake Key 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String textValue(Map<String, Object> source, String camelKey, String snakeKey) {
        Object value = value(source, camelKey, snakeKey);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 编排 integer Value 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @param camelKey camel Key 输入值，含义由调用方法名称和所属业务对象限定
     * @param snakeKey snake Key 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private Integer integerValue(Map<String, Object> source, String camelKey, String snakeKey) {
        Object value = value(source, camelKey, snakeKey);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    /**
     * 编排 time Value 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @param camelKey camel Key 输入值，含义由调用方法名称和所属业务对象限定
     * @param snakeKey snake Key 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private LocalDateTime timeValue(Map<String, Object> source, String camelKey, String snakeKey) {
        Object value = value(source, camelKey, snakeKey);
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return LocalDateTime.parse(String.valueOf(value));
    }

    /**
     * 编排 value 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @param camelKey camel Key 输入值，含义由调用方法名称和所属业务对象限定
     * @param snakeKey snake Key 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private Object value(Map<String, Object> source, String camelKey, String snakeKey) {
        if (source == null) {
            return null;
        }
        return source.containsKey(camelKey) ? source.get(camelKey) : source.get(snakeKey);
    }

/**
 * 编排 build Action Request 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
 * <p>
 * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param sourceOperation source Operation 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
 * @param labelAmount 金额值，单位由关联币种决定，调用前必须完成币种精度校验
 * @param transactionAmount 金额值，单位由关联币种决定，调用前必须完成币种精度校验
 * @param orderIdPrefix order Id Prefix 输入值，含义由调用方法名称和所属业务对象限定
 * @return 转换或构建后的目标对象
 */
    private PaymentTransactionActionClientRequestDTO buildActionRequest(TransactionOperationResponse sourceOperation,
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
        requestDTO.setMerchantId(sourceOperation.getMerchantId());
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

    /**
     * 编排 resolve Source Operation 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param detailResponse detail Response 输入值，含义由调用方法名称和所属业务对象限定
     * @param transactionId 平台交易号，用于关联订单、操作记录、渠道请求和回调处理结果
     * @return 渠道 API 操作类型或平台操作映射结果
     */
    private TransactionOperationResponse resolveSourceOperation(TransactionDetailResponse detailResponse, String transactionId) {
        if (detailResponse == null || detailResponse.getOperations() == null) {
            throw new ApiException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        return detailResponse.getOperations().stream()
                .filter(operation -> transactionId.equals(operation.getTransactionId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ApiResultEnum.ORDER_NOT_FOUND));
    }

    /**
     * 编排 resolve Label Currency 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sourceOperation source Operation 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 标准化后的 ISO 4217 币种代码
     */
    private String resolveLabelCurrency(TransactionOperationResponse sourceOperation, TransactionActionRequest request) {
        if (StringUtils.hasText(request == null ? null : request.getCurrency())) {
            return request.getCurrency().trim().toUpperCase(Locale.ROOT);
        }
        if (StringUtils.hasText(sourceOperation.getLabelCurrency())) {
            return sourceOperation.getLabelCurrency();
        }
        return sourceOperation.getTransactionCurrency();
    }

    /**
     * 编排 full Transaction Amount 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sourceOperation source Operation 输入值，含义由调用方法名称和所属业务对象限定
     * @param preferredAmount 金额值，单位由关联币种决定，调用前必须完成币种精度校验
     * @return 按渠道协议格式化后的金额字符串或金额计算结果
     */
    private BigDecimal fullTransactionAmount(TransactionOperationResponse sourceOperation, BigDecimal preferredAmount) {
        BigDecimal amount = preferredAmount == null ? sourceOperation.getTransactionAmount() : preferredAmount;
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "action amount must be greater than 0");
        }
        return amount;
    }

    /**
     * 编排 full Label Amount 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sourceOperation source Operation 输入值，含义由调用方法名称和所属业务对象限定
     * @param transactionAmount 金额值，单位由关联币种决定，调用前必须完成币种精度校验
     * @return 按渠道协议格式化后的金额字符串或金额计算结果
     */
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

    /**
     * 编排 to Transaction Amount 应用动作，衔接接口 DTO、登录上下文、领域服务和返回模型。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminTransactionApplicationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param sourceOperation source Operation 输入值，含义由调用方法名称和所属业务对象限定
     * @param labelAmount 金额值，单位由关联币种决定，调用前必须完成币种精度校验
     * @return 按渠道协议格式化后的金额字符串或金额计算结果
     */
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
}
