package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.admin.dto.export.TransactionMerchantNotificationExportRow;
import com.scott.payment.admin.dto.export.TransactionOperationExportRow;
import com.scott.payment.admin.dto.export.TransactionOrderExportRow;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelInteractionLogResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelLogQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelMatchRequeryRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelMatchRequeryResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.enums.RefundReasonEnum;
import com.scott.payment.component.core.model.RefundContext;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import org.springframework.context.i18n.LocaleContextHolder;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.excel.model.ExcelPagedExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.redis.concurrency.RedisConcurrencyLimiter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 管理端交易应用服务，用于运营后台，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 */
@Service
public class AdminTransactionApplicationService {

    /**
     * 内部分页拉取大小，受 PageRequest 安全上限保护。
     */
    private static final int EXPORT_PAGE_SIZE = 500;
    /**
     * 退款预览沿用支付核心非终态退款查询的状态口径。
     */
    private static final Set<String> REFUND_IN_PROGRESS_STATUSES = Set.of("PROCESSING", "PENDING");
    /**
     * 退款动作类型，用于区分 {@code AdminTransactionApplicationService} 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String REFUND_OPERATION_TYPE = "REFUND";
    /**
     * 成功状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private static final String SUCCESS_STATUS = "SUCCESS";
    /**
     * 异常退出后 Redis 并发租约的最长自恢复时间。
     */
    private static final Duration EXPORT_LEASE_TIME = Duration.ofMinutes(5);

    /**
     * 导出文件时间戳格式。
     */
    private static final DateTimeFormatter EXPORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 管理端请款动作幂等号前缀。
     */
    private static final String ADMIN_CAPTURE_ORDER_ID_PREFIX = "ADMCP";

    /**
     * 管理端预授权完成动作幂等号前缀。
     */
    private static final String ADMIN_PRE_AUTH_COMPLETION_ORDER_ID_PREFIX = "ADMPA";

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
    private static final Set<String> CAPTURE_SOURCE_TYPES = Set.of("AUTHORIZATION");

    /**
     * 可作为预授权完成来源的动作类型。
     */
    private static final Set<String> PRE_AUTH_COMPLETION_SOURCE_TYPES = Set.of("PRE_AUTHORIZATION");

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
     * 交易查询和同步导出资源预算。
     */
    private final TransactionShardingProperties shardingProperties;
    /**
     * 跨实例限制同一后台账号并发导出的 Redis 租约服务。
     */
    private final RedisConcurrencyLimiter exportConcurrencyLimiter;

    /**
     * 创建管理后台交易查询应用服务。
     *
     * @param paymentInternalClient service-payment 内部状态变更客户端
     * @param transactionQueryService 管理后台交易只读查询服务
     * @param excelExportService Excel 导出服务
     * @param excelI18nMessageResolver Excel 国际化文案解析器
     * @param excelLocaleResolver 当前请求导出语言解析器
     * @param shardingProperties 交易查询和同步导出资源预算
     * @param exportConcurrencyLimiter 跨实例账号导出并发租约
     */
    public AdminTransactionApplicationService(PaymentInternalClient paymentInternalClient,
                                              AdminTransactionQueryService transactionQueryService,
                                              ExcelExportService excelExportService,
                                              ExcelI18nMessageResolver excelI18nMessageResolver,
                                              ExcelLocaleResolver excelLocaleResolver,
                                              TransactionShardingProperties shardingProperties,
                                              RedisConcurrencyLimiter exportConcurrencyLimiter) {
        this.paymentInternalClient = paymentInternalClient;
        this.transactionQueryService = transactionQueryService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
        this.shardingProperties = shardingProperties;
        this.exportConcurrencyLimiter = exportConcurrencyLimiter;
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
     * 分页查询交易生命周期主单，并返回相同条件下的全量统计。
     *
     * @param query 查询条件
     * @return 主单分页和全量统计
     */
    public TransactionOrderSearchResponse searchOrders(TransactionPageQuery query) {
        return transactionQueryService.searchOrders(query);
    }

    /**
     * 按查询条件导出交易生命周期主单。
     *
     * @param query 查询条件
     * @param operator 导出操作人
     * @param response HTTP 响应
     */
    public void exportOrders(TransactionPageQuery query, String operator, HttpServletResponse response) {
        runExport(operator, () -> {
            Locale locale = excelLocaleResolver.resolveCurrentLocale();
            exportPagedExcel(
                    "excel.transaction.order.title",
                    TransactionOrderExportRow.class,
                    pageNo -> loadOrderExportPage(query, pageNo, locale),
                    querySummary(query, locale), operator, locale, response);
        });
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
        runExport(operator, () -> {
            Locale locale = excelLocaleResolver.resolveCurrentLocale();
            exportPagedExcel(
                    "excel.transaction.operation.title",
                    TransactionOperationExportRow.class,
                    pageNo -> loadOperationExportPage(query, pageNo, locale),
                    querySummary(query, locale), operator, locale, response);
        });
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
        TransactionDetailResponse detailResponse = detail(
                transactionId, requiredTransactionDateTime(request), requiredRootTransactionDateTime(request));
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
     * 管理后台发起全额预授权完成动作。
     * <p>
     * 预授权完成与请款共享渠道 Capture 能力，但平台动作类型、幂等记录和操作审计必须保持独立。
     *
     * @param transactionId 原预授权平台交易 ID
     * @param request 预授权完成动作请求
     * @return 预授权完成动作结果
     */
    public TransactionActionResponse preAuthCompletion(String transactionId, TransactionActionRequest request) {
        TransactionDetailResponse detailResponse = detail(
                transactionId, requiredTransactionDateTime(request), requiredRootTransactionDateTime(request));
        TransactionOperationResponse sourceOperation = resolveSourceOperation(detailResponse, transactionId);
        if (!"SUCCESS".equals(sourceOperation.getTransactionStatus())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED,
                    "only successful pre-authorizations can be completed");
        }
        if (!PRE_AUTH_COMPLETION_SOURCE_TYPES.contains(sourceOperation.getTransactionType())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED);
        }
        BigDecimal labelAmount = fullLabelAmount(sourceOperation, sourceOperation.getAvailableCaptureAmount());
        BigDecimal transactionAmount = fullTransactionAmount(sourceOperation, sourceOperation.getAvailableCaptureAmount());
        PaymentTransactionActionClientRequestDTO requestDTO = buildActionRequest(
                sourceOperation,
                request,
                labelAmount,
                transactionAmount,
                ADMIN_PRE_AUTH_COMPLETION_ORDER_ID_PREFIX);
        return paymentInternalClient.preAuthCompletion(requestDTO);
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
        TransactionDetailResponse detailResponse = detail(
                transactionId, requiredTransactionDateTime(request), requiredRootTransactionDateTime(request));
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
        request.setReason(RefundReasonEnum.resolveRequestReason(
                request.getReasonCode(), request.getRefundDescription(), request.getReason(),
                LocaleContextHolder.getLocale()));
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
        TransactionDetailResponse detailResponse = detail(
                transactionId, requiredTransactionDateTime(request), requiredRootTransactionDateTime(request));
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
     * 主动重查并勾兑单笔交易，支付核心负责勾兑状态白名单和终态 CAS 门禁。
     *
     * @param transactionId 平台交易号
     * @param request 真实交易分片时间
     * @return 单笔勾兑结果
     */
    public ChannelMatchRequeryResponse requeryChannelMatch(
            String transactionId,
            ChannelMatchRequeryRequest request) {
        if (!StringUtils.hasText(transactionId)
                || request == null
                || request.getTransactionDateTime() == null) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID);
        }
        return paymentInternalClient.requeryChannelMatch(transactionId, request);
    }

    /**
     * 查询交易聚合详情。
     *
     * @param transactionId 平台交易 ID
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 交易聚合详情
     */
    public TransactionDetailResponse detail(String transactionId,
                                            LocalDateTime transactionDateTime,
                                            LocalDateTime rootTransactionDateTime) {
        return transactionQueryService.detail(transactionId, transactionDateTime, rootTransactionDateTime);
    }

    /**
     * 校验后台动作携带的源交易分片时间，防止支付动作广播查询。
     *
     * @param request 后台交易动作请求
     * @return 源交易分片时间
     */
    private LocalDateTime requiredTransactionDateTime(TransactionActionRequest request) {
        if (request == null || request.getTransactionDateTime() == null) {
            throw new ApiException(ApiResultEnum.PARAM_MISSING, "transactionDateTime is required");
        }
        return request.getTransactionDateTime();
    }

    /**
     * 校验后台动作携带的生命周期根主单分片时间。
     */
    private LocalDateTime requiredRootTransactionDateTime(TransactionActionRequest request) {
        if (request == null || request.getRootTransactionDateTime() == null) {
            throw new ApiException(ApiResultEnum.PARAM_MISSING, "rootTransactionDateTime is required");
        }
        return request.getRootTransactionDateTime();
    }

    /**
     * 分页查询渠道交互日志。
     *
     * @param query 查询条件
     * @return 渠道交互日志分页结果
     */
    public PageResult<ChannelInteractionLogResponse> pageChannelLogs(ChannelLogQuery query) {
        return transactionQueryService.pageChannelLogs(query);
    }

    /**
     * 分页查询渠道回调业务记录。
     *
     * @param query 查询条件
     * @return 渠道回调分页结果
     */
    public PageResult<ChannelCallbackResponse> pageChannelCallbacks(ChannelCallbackQuery query) {
        return transactionQueryService.pageChannelCallbacks(query);
    }

    /**
     * 分页查询商户通知任务。
     *
     * @param query 查询条件
     * @return 商户通知任务分页结果
     */
    public PageResult<MerchantNotificationResponse> pageMerchantNotifications(MerchantNotificationQuery query) {
        return transactionQueryService.pageMerchantNotifications(query);
    }

    /**
     * 查询商户回调任务及每次投递尝试的脱敏审计详情。
     *
     * @param notifyId 通知任务号
     * @param transactionDateTime 页面列表返回的真实交易分片时间
     * @return 通知任务和按尝试次数排序的投递日志
     */
    public MerchantNotificationDetailResponse merchantNotificationDetail(String notifyId,
                                                                          LocalDateTime transactionDateTime) {
        return transactionQueryService.merchantNotificationDetail(notifyId, transactionDateTime);
    }

    /**
     * 按查询条件导出商户通知任务。
     *
     * @param query 查询条件
     * @param operator 导出操作人
     * @param response HTTP 响应
     */
    public void exportMerchantNotifications(MerchantNotificationQuery query, String operator, HttpServletResponse response) {
        runExport(operator, () -> {
            Locale locale = excelLocaleResolver.resolveCurrentLocale();
            exportPagedExcel(
                    "excel.transaction.notification.title",
                    TransactionMerchantNotificationExportRow.class,
                    pageNo -> loadMerchantNotificationExportPage(query, pageNo),
                    notificationQuerySummary(query, locale), operator, locale, response);
        });
    }

    /**
     * 复制查询条件并加载一页交易主单导出数据。
     *
     * @param sourceQuery 原始页面查询条件
     * @param pageNo 导出分页页码
     * @param locale 导出语言
     * @return 已转换为 Excel 行模型的交易主单
     */
    private List<TransactionOrderExportRow> loadOrderExportPage(TransactionPageQuery sourceQuery,
                                                                int pageNo,
                                                                Locale locale) {
        TransactionPageQuery query = copyTransactionQuery(sourceQuery);
        query.setPageNo(pageNo);
        query.setPageSize(EXPORT_PAGE_SIZE);
        return transactionQueryService.pageOrders(query).getRecords().stream()
                .map(row -> toOrderExportRow(row, locale))
                .toList();
    }

    /**
     * 复制查询条件并加载一页交易动作导出数据。
     *
     * @param sourceQuery 原始页面查询条件
     * @param pageNo 导出分页页码
     * @param locale 导出语言
     * @return 已转换为 Excel 行模型的交易动作
     */
    private List<TransactionOperationExportRow> loadOperationExportPage(TransactionPageQuery sourceQuery,
                                                                        int pageNo,
                                                                        Locale locale) {
        TransactionPageQuery query = copyTransactionQuery(sourceQuery);
        query.setPageNo(pageNo);
        query.setPageSize(EXPORT_PAGE_SIZE);
        return transactionQueryService.pageOperations(query).getRecords().stream()
                .map(row -> toOperationExportRow(row, locale))
                .toList();
    }

    /**
     * 复制查询条件并加载一页商户通知导出数据。
     *
     * @param sourceQuery 原始页面查询条件
     * @param pageNo 导出分页页码
     * @return 已转换为 Excel 行模型的商户通知任务
     */
    private List<TransactionMerchantNotificationExportRow> loadMerchantNotificationExportPage(
            MerchantNotificationQuery sourceQuery, int pageNo) {
        MerchantNotificationQuery query = copyNotificationQuery(sourceQuery);
        query.setPageNo(pageNo);
        query.setPageSize(EXPORT_PAGE_SIZE);
        return transactionQueryService.pageMerchantNotifications(query).getRecords().stream()
                .map(this::toMerchantNotificationExportRow)
                .toList();
    }

    /**
     * 在同一后台账号的集群级并发预算内执行一次同步交易导出。
     */
    private void runExport(String operator, Runnable action) {
        boolean acquired = exportConcurrencyLimiter.execute(
                "transaction",
                "admin-export",
                exportIdentity(operator),
                shardingProperties.getQueryBudget().getMaxConcurrentExportsPerUser(),
                EXPORT_LEASE_TIME,
                action
        );
        if (!acquired) {
            throw new ApiException(ApiResultEnum.TOO_MANY_REQUESTS,
                    "another transaction export is already running");
        }
    }

    /**
     * 返回稳定账号身份；Redis Key 构造器只保存该值的 SHA-256 摘要。
     */
    private String exportIdentity(String operator) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account != null && account.getAccountId() != null) {
            return "admin-account:" + account.getAccountId();
        }
        return StringUtils.hasText(operator) ? "admin-operator:" + operator : "admin-operator:unknown";
    }

    /**
     * 按有界分页查询导出 Excel 数据。
     */
    private <T> void exportPagedExcel(String titleKey,
                                      Class<T> rowClass,
                                      java.util.function.IntFunction<List<T>> pageLoader,
                                      String querySummary,
                                      String operator,
                                      Locale locale,
                                      HttpServletResponse response) {
        LocalDateTime now = LocalDateTime.now();
        String title = excelI18nMessageResolver.resolve(titleKey, locale);
        excelExportService.exportPaged(
                ExcelPagedExportRequest.<T>builder()
                        .fileName(title + "_" + EXPORT_TIME_FORMATTER.format(now))
                        .sheetName(title)
                        .titleKey(titleKey)
                        .operator(operator)
                        .exportTime(now)
                        .locale(locale)
                        .querySummary(querySummary)
                        .rowClass(rowClass)
                        .pageSize(EXPORT_PAGE_SIZE)
                        .pageLoader(pageLoader)
                        .build(),
                response
        );
    }

    /**
     * 转换为交易订单导出行数据。
     */
    private TransactionOrderExportRow toOrderExportRow(TransactionOrderResponse source, Locale locale) {
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
        row.setThreeDs(binaryLabel(source.getThreeDsEnabled(), "excel.common.yes", "excel.common.no", locale));
        row.setDcc(binaryLabel(source.getDccEnabled(), "excel.transaction.common.capabilityEnabled", "excel.transaction.common.capabilityDisabled", locale));
        row.setEdc(binaryLabel(source.getEdcEnabled(), "excel.transaction.common.capabilityEnabled", "excel.transaction.common.capabilityDisabled", locale));
        row.setReconciliationStatus(source.getReconciliationStatus());
        row.setSettlementStatus(source.getSettlementStatus());
        row.setTransactionDateTime(source.getTransactionDateTime());
        return row;
    }

    /**
     * 转换为交易动作导出行数据。
     */
    private TransactionOperationExportRow toOperationExportRow(TransactionOperationResponse source, Locale locale) {
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
        row.setThreeDs(binaryLabel(source.getThreeDsEnabled(), "excel.common.yes", "excel.common.no", locale));
        row.setDcc(binaryLabel(source.getDccEnabled(), "excel.transaction.common.capabilityEnabled", "excel.transaction.common.capabilityDisabled", locale));
        row.setEdc(binaryLabel(source.getEdcEnabled(), "excel.transaction.common.capabilityEnabled", "excel.transaction.common.capabilityDisabled", locale));
        row.setReconciliationStatus(source.getReconciliationStatus());
        row.setSettlementStatus(source.getSettlementStatus());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setOperationTime(source.getOperationTime());
        return row;
    }

    /**
     * 将数据库 0/1 标志转换为当前 Excel 语言下的可读状态。
     */
    private String binaryLabel(Integer value, String enabledKey, String disabledKey, Locale locale) {
        return excelI18nMessageResolver.resolve(Integer.valueOf(1).equals(value) ? enabledKey : disabledKey, locale);
    }

    /**
     * 转换为商户通知导出行数据。
     */
    private TransactionMerchantNotificationExportRow toMerchantNotificationExportRow(MerchantNotificationResponse source) {
        TransactionMerchantNotificationExportRow row = new TransactionMerchantNotificationExportRow();
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
        row.setLastNotifyTime(source.getSuccessTime());
        row.setLastFailReason(source.getFailReason());
        row.setTransactionDateTime(source.getTransactionDateTime());
        row.setCreateTime(source.getCreateTime());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /**
     * 复制交易查询。
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
     * 复制通知查询。
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
     * 查询摘要。
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
     * 构建商户通知查询审计摘要。
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
     * 将非空查询条件追加到导出审计摘要。
     *
     * @param conditions 查询条件摘要集合
     * @param labelKey 条件名称国际化键
     * @param value 条件值
     * @param locale 导出语言
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
     * 组装管理端交易动作的支付核心请求，沿用源交易的商户、币种和分片信息。
     *
     * @param sourceOperation 源交易动作
     * @param request 管理端动作请求
     * @param labelAmount 面向商户的标签金额
     * @param transactionAmount 面向渠道的交易金额
     * @param orderIdPrefix 管理端幂等订单号前缀
     * @return 支付核心内部动作请求
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
        InternalAuthAccount applicant = InternalAuthContextHolder.get();
        requestDTO.setRequestSource("ADMIN_PORTAL");
        requestDTO.setApplicantId(applicant == null || applicant.getAccountId() == null
                ? "admin" : applicant.getAccountId().toString());
        requestDTO.setApplicantName(resolveApplicantName(applicant, "admin"));
        requestDTO.setRequestReason(request == null ? null : request.getReason());
        requestDTO.setAmount(transactionAmount);
        requestDTO.setCurrency(sourceOperation.getTransactionCurrency());
        requestDTO.setLabelAmount(labelAmount);
        requestDTO.setLabelCurrency(resolveLabelCurrency(sourceOperation, request));
        requestDTO.setTransactionDateTime(transactionDateTime);
        PaymentTransactionActionClientRequestDTO.TransactionInfoDTO transactionInfoDTO =
                new PaymentTransactionActionClientRequestDTO.TransactionInfoDTO();
        transactionInfoDTO.setSourceTransactionId(sourceOperation.getTransactionId());
        transactionInfoDTO.setSourceTransactionDateTime(sourceOperation.getTransactionDateTime());
        transactionInfoDTO.setRootTransactionDateTime(sourceOperation.getRootTransactionDateTime());
        transactionInfoDTO.setDescription(request == null ? null : request.getReason());
        requestDTO.setTransactionInfo(transactionInfoDTO);
        return requestDTO;
    }

    /**
     * 返回审批审计使用的稳定申请人显示名。
     */
    private String resolveApplicantName(InternalAuthAccount account, String fallback) {
        if (account == null) {
            return fallback;
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        return StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount() : fallback;
    }

    /**
     * 解析原交易动作。
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
     * 按真实分片时间从主库读取退款额度，使用与支付核心一致的非终态退款口径。
     * @param transactionId 原交易号
     * @param request 包含原交易及生命周期根主单分片时间的请求
     * @return 标签币种下的已退款、处理中及可退金额快照
     */
    public RefundContext refundContext(String transactionId, TransactionActionRequest request) {
        TransactionDetailResponse detailResponse = detail(
                transactionId, requiredTransactionDateTime(request), requiredRootTransactionDateTime(request));
        TransactionOperationResponse source = resolveSourceOperation(detailResponse, transactionId);
        if (!SUCCESS_STATUS.equals(source.getTransactionStatus()) || !REFUND_SOURCE_TYPES.contains(source.getTransactionType())) {
            throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED);
        }
        String currency = resolveLabelCurrency(source, null);
        int digits = IsoCurrencyResolver.resolve(currency)
                .map(info -> info.defaultFractionDigits()).filter(value -> value >= 0)
                .orElseThrow(() -> new ApiException(ApiResultEnum.PARAM_INVALID));
        if (currency.equalsIgnoreCase(source.getTransactionCurrency())
                && source.getCurrencyExponent() != null && source.getCurrencyExponent() >= 0) {
            digits = source.getCurrencyExponent();
        }
        BigDecimal available = source.getAvailableRefundAmount();
        if (available == null) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID);
        }
        BigDecimal pending = detailResponse.getOperations().stream()
                .filter(operation -> REFUND_OPERATION_TYPE.equals(operation.getTransactionType()))
                .filter(operation -> operation.getTransactionStatus() != null
                        && REFUND_IN_PROGRESS_STATUSES.contains(operation.getTransactionStatus()))
                .map(TransactionOperationResponse::getTransactionAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refunded = source.getRefundedAmount() == null ? BigDecimal.ZERO : source.getRefundedAmount();
        return new RefundContext(currency, digits,
                refundLabelAmount(source, refunded, digits, RoundingMode.HALF_UP),
                refundLabelAmount(source, pending, digits, RoundingMode.HALF_UP),
                refundLabelAmount(source, available.subtract(pending).max(BigDecimal.ZERO), digits, RoundingMode.DOWN));
    }

    /**
     * 仅展示时换算标签金额；可退上限向下取整，避免展示金额反算后超过实际余额。
     */
    private BigDecimal refundLabelAmount(TransactionOperationResponse source, BigDecimal amount,
                                         int digits, RoundingMode roundingMode) {
        if (source.getLabelAmount() != null && source.getTransactionAmount() != null
                && source.getTransactionAmount().signum() > 0) {
            return amount.multiply(source.getLabelAmount()).divide(source.getTransactionAmount(), digits, roundingMode);
        }
        return amount.setScale(digits, roundingMode);
    }

    /**
     * 解析标签币种。
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
     * 解析后续动作使用的完整交易币种金额，并拒绝非正数。
     */
    private BigDecimal fullTransactionAmount(TransactionOperationResponse sourceOperation, BigDecimal preferredAmount) {
        BigDecimal amount = preferredAmount == null ? sourceOperation.getTransactionAmount() : preferredAmount;
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "action amount must be greater than 0");
        }
        return amount;
    }

    /**
     * 按原交易冻结比例将交易金额换算为商户标签金额。
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
     * 转换为交易金额。
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
