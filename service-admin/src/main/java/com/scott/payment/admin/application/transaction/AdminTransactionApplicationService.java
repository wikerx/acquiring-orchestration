package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.payment.PaymentInternalClient;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.admin.dto.export.TransactionMerchantNotificationExportRow;
import com.scott.payment.admin.dto.export.TransactionOperationExportRow;
import com.scott.payment.admin.dto.export.TransactionOrderExportRow;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelLogQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelMatchRequeryRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelMatchRequeryResponse;
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
     * 内部分页拉取大小，受 PageRequest 安全上限保护。
     */
    private static final int EXPORT_PAGE_SIZE = 500;
    /** 异常退出后 Redis 并发租约的最长自恢复时间。 */
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
    /** 交易查询和同步导出资源预算。 */
    private final TransactionShardingProperties shardingProperties;
    /** 跨实例限制同一后台账号并发导出的 Redis 租约服务。 */
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

    /** 校验后台动作携带的生命周期根主单分片时间。 */
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
     * 查询商户回调任务及每次投递尝试的脱敏审计详情。
     *
     * @param notifyId 通知任务号
     * @param transactionDateTime 页面列表返回的真实交易分片时间
     * @return 通知任务和按尝试次数排序的投递日志
     */
    public Map<String, Object> merchantNotificationDetail(String notifyId,
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
     * 查询全部交易主单，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param sourceQuery 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
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
     * 查询全部交易动作，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param sourceQuery 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
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
     * 查询全部商户通知任务，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param sourceQuery 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 查询得到的业务对象、分页结果或空结果
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

    /** 在同一后台账号的集群级并发预算内执行一次同步交易导出。 */
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

    /** 返回稳定账号身份；Redis Key 构造器只保存该值的 SHA-256 摘要。 */
    private String exportIdentity(String operator) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account != null && account.getAccountId() != null) {
            return "admin-account:" + account.getAccountId();
        }
        return StringUtils.hasText(operator) ? "admin-operator:" + operator : "admin-operator:unknown";
    }

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
     * 构造订单exportrow对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
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
     * 构造动作exportrow对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
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

    /** 将数据库 0/1 标志转换为当前 Excel 语言下的可读状态。 */
    private String binaryLabel(Integer value, String enabledKey, String disabledKey, Locale locale) {
        return excelI18nMessageResolver.resolve(Integer.valueOf(1).equals(value) ? enabledKey : disabledKey, locale);
    }

    /**
     * 构造商户通知exportrow对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
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
     * 构造交易查询对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 构造通知查询对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 查询汇总数据，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
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
     * 整理通知查询汇总，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 创建查询条件，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param conditions conditions 输入值，参与 conditions 的查询、校验、转换、写入或日志摘要
     * @param labelKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param locale locale 输入值，参与 locale 的查询、校验、转换、写入或日志摘要
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
     * 整理文本值，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param camelKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param snakeKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String textValue(Map<String, Object> source, String camelKey, String snakeKey) {
        Object value = value(source, camelKey, snakeKey);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 整理整数值，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param camelKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param snakeKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 整理时间值，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param camelKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param snakeKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 整理值，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param camelKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param snakeKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Object value(Map<String, Object> source, String camelKey, String snakeKey) {
        if (source == null) {
            return null;
        }
        return source.containsKey(camelKey) ? source.get(camelKey) : source.get(snakeKey);
    }

/**
 * 构造action请求对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param sourceOperation source Operation 输入值，参与 来源动作 的查询、校验、转换、写入或日志摘要
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param labelAmount 金额值，单位必须结合 currency 或同名币种字段解释
 * @param transactionAmount 金额值，单位必须结合 currency 或同名币种字段解释
 * @param orderIdPrefix order ID Prefix 输入值，参与 订单IDprefix 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
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

    /** 返回审批审计使用的稳定申请人显示名。 */
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
     * 解析resolve来源动作，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param detailResponse 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @param transactionId 平台交易号，用于定位主单、动作单、渠道请求和回调记录
     * @return 构造、转换或解析后的业务值
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
     * 解析resolvelabel币种，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param sourceOperation source Operation 输入值，参与 来源动作 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
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
     * 整理full交易金额，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param sourceOperation source Operation 输入值，参与 来源动作 的查询、校验、转换、写入或日志摘要
     * @param preferredAmount 金额值，单位必须结合 currency 或同名币种字段解释
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private BigDecimal fullTransactionAmount(TransactionOperationResponse sourceOperation, BigDecimal preferredAmount) {
        BigDecimal amount = preferredAmount == null ? sourceOperation.getTransactionAmount() : preferredAmount;
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "action amount must be greater than 0");
        }
        return amount;
    }

    /**
     * 整理fulllabel金额，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param sourceOperation source Operation 输入值，参与 来源动作 的查询、校验、转换、写入或日志摘要
     * @param transactionAmount 金额值，单位必须结合 currency 或同名币种字段解释
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 构造交易金额对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param sourceOperation source Operation 输入值，参与 来源动作 的查询、校验、转换、写入或日志摘要
     * @param labelAmount 金额值，单位必须结合 currency 或同名币种字段解释
     * @return 构造、转换或解析后的业务值
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
