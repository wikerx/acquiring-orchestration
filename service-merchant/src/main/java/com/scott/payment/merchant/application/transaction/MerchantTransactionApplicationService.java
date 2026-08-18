package com.scott.payment.merchant.application.transaction;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.redis.concurrency.RedisConcurrencyLimiter;
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
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
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
     * 内部分页拉取大小。
     */
    private static final int EXPORT_PAGE_SIZE = 500;
    /** 异常退出后 Redis 并发租约的最长自恢复时间。 */
    private static final Duration EXPORT_LEASE_TIME = Duration.ofMinutes(5);

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

    /**
     * payment Internal Client 依赖，用于 Merchant Transaction Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final PaymentInternalClient paymentInternalClient;

    /**
     * transaction Query Service 依赖，用于 Merchant Transaction Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final MerchantTransactionQueryService transactionQueryService;
    /** 交易查询和同步导出资源预算。 */
    private final TransactionShardingProperties shardingProperties;
    /** 跨实例限制同一商户账号并发导出的 Redis 租约服务。 */
    private final RedisConcurrencyLimiter exportConcurrencyLimiter;

    /**
     * 创建商户后台交易查询应用服务。
     *
     * @param paymentInternalClient service-payment 状态变更内部客户端
     * @param transactionQueryService 商户交易只读查询服务
     * @param shardingProperties 交易查询和同步导出资源预算
     * @param exportConcurrencyLimiter 跨实例商户账号导出并发租约
     */
    public MerchantTransactionApplicationService(PaymentInternalClient paymentInternalClient,
                                                  MerchantTransactionQueryService transactionQueryService,
                                                  TransactionShardingProperties shardingProperties,
                                                  RedisConcurrencyLimiter exportConcurrencyLimiter) {
        this.paymentInternalClient = paymentInternalClient;
        this.transactionQueryService = transactionQueryService;
        this.shardingProperties = shardingProperties;
        this.exportConcurrencyLimiter = exportConcurrencyLimiter;
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
    public TransactionDetailResponse detail(String merchantId,
                                            String transactionId,
                                            LocalDateTime transactionDateTime,
                                            LocalDateTime rootTransactionDateTime) {
        return transactionQueryService.detail(
                merchantId, transactionId, transactionDateTime, rootTransactionDateTime);
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
        TransactionDetailResponse detailResponse = detail(
                merchantId, transactionId, requiredTransactionDateTime(request),
                requiredRootTransactionDateTime(request));
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
        TransactionDetailResponse detailResponse = detail(
                merchantId, transactionId, requiredTransactionDateTime(request),
                requiredRootTransactionDateTime(request));
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
        TransactionDetailResponse detailResponse = detail(
                merchantId, transactionId, requiredTransactionDateTime(request),
                requiredRootTransactionDateTime(request));
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
        Locale locale = LocaleContextHolder.getLocale();
        runExport(merchantId, operator, () -> {
            String fileName = "merchant_transaction_operations_" + EXPORT_TIME_FORMATTER.format(LocalDateTime.now()) + ".csv";
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Content-Disposition", "attachment;filename*=utf-8''"
                    + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            try (PrintWriter writer = response.getWriter()) {
                writer.write(UTF8_BOM);
                writer.println(csvLabel(locale, "操作人", "Operator") + "," + csv(operator));
                writer.println(csvLabel(locale, "导出时间", "Export Time") + "," + csv(LocalDateTime.now()));
                writer.println();
                writer.println(String.join(",",
                    csvLabel(locale, "系统订单号", "System Order No."),
                    csvLabel(locale, "原系统订单号", "Source System Order No."),
                    csvLabel(locale, "商户订单号", "Merchant Order No."),
                    csvLabel(locale, "请求号", "Request No."),
                    csvLabel(locale, "标签金额", "Label Amount"),
                    csvLabel(locale, "标签币种", "Label Currency"),
                    csvLabel(locale, "交易金额", "Transaction Amount"),
                    csvLabel(locale, "交易币种", "Transaction Currency"),
                    csvLabel(locale, "交易汇率", "Transaction Rate"),
                    csvLabel(locale, "交易类型", "Transaction Type"),
                    csvLabel(locale, "交易状态", "Transaction Status"),
                    csvLabel(locale, "支付方式", "Payment Method"),
                    csvLabel(locale, "支付品牌", "Payment Brand"),
                    "3DS",
                    "DCC",
                    "EDC",
                    csvLabel(locale, "卡BIN", "Card BIN"),
                    csvLabel(locale, "授权码", "Auth Code"),
                    "ARN",
                    csvLabel(locale, "商户响应码", "Merchant Response Code"),
                    csvLabel(locale, "商户响应描述", "Merchant Response Message"),
                    csvLabel(locale, "渠道编码", "Channel Code"),
                    csvLabel(locale, "渠道订单号", "Channel Order No."),
                    csvLabel(locale, "渠道交易ID", "Channel Transaction ID"),
                    csvLabel(locale, "结算状态", "Settlement Status"),
                    csvLabel(locale, "对账状态", "Reconciliation Status"),
                    csvLabel(locale, "动作时间", "Operation Time"),
                    csvLabel(locale, "交易时间", "Transaction Time")
                ));
                writeOperationPages(writer, merchantId, query, locale);
            } catch (IOException exception) {
                throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR, "export merchant transactions failed");
            }
        });
    }

    /**
     * 整理商户scoped查询，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private TransactionPageQuery merchantScopedQuery(String merchantId, TransactionPageQuery source) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "merchant context missing");
        }
        TransactionPageQuery query = copyTransactionQuery(source);
        query.setMerchantId(merchantId);
        return query;
    }

    /** 按页读取当前商户交易并直接写入响应流，不在内存中聚合全部导出数据。 */
    private void writeOperationPages(PrintWriter writer,
                                     String merchantId,
                                     TransactionPageQuery sourceQuery,
                                     Locale locale) {
        for (int pageNo = 1; ; pageNo++) {
            TransactionPageQuery query = merchantScopedQuery(merchantId, sourceQuery);
            query.setPageNo(pageNo);
            query.setPageSize(EXPORT_PAGE_SIZE);
            List<TransactionOperationResponse> rows =
                    transactionQueryService.searchOperations(query).getPage().getRecords();
            if (rows.isEmpty()) {
                break;
            }
            rows.forEach(row -> writer.println(toOperationCsvLine(row, locale)));
            if (rows.size() < EXPORT_PAGE_SIZE) {
                break;
            }
        }
    }

    /** 在同一商户账号的集群级并发预算内执行一次同步交易导出。 */
    private void runExport(String merchantId, String operator, Runnable action) {
        boolean acquired = exportConcurrencyLimiter.execute(
                "transaction",
                "merchant-export",
                exportIdentity(merchantId, operator),
                shardingProperties.getQueryBudget().getMaxConcurrentExportsPerUser(),
                EXPORT_LEASE_TIME,
                action
        );
        if (!acquired) {
            throw new ApiException(ApiResultEnum.TOO_MANY_REQUESTS,
                    "another transaction export is already running");
        }
    }

    /** 返回商户与账号组合身份；Redis Key 构造器只保存该值的 SHA-256 摘要。 */
    private String exportIdentity(String merchantId, String operator) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        String accountIdentity = account != null && account.getAccountId() != null
                ? account.getAccountId().toString()
                : (StringUtils.hasText(operator) ? operator : "unknown");
        return "merchant-account:" + merchantId + ":" + accountIdentity;
    }

    /**
     * 构造交易查询对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param source 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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
        copy.setReconciliationStatus(query.getReconciliationStatus());
        copy.setSettlementStatus(query.getSettlementStatus());
        copy.setBeginTime(query.getBeginTime());
        copy.setEndTime(query.getEndTime());
        copy.setQueryTimeZone(query.getQueryTimeZone());
        return copy;
    }

    /**
     * 校验确保belongsto商户输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param detailResponse 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     */
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

    /**
     * 解析resolve来源动作，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
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
 * 构造action请求对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
 * @param sourceOperation source Operation 输入值，参与 来源动作 的查询、校验、转换、写入或日志摘要
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param labelAmount 金额值，单位必须结合 currency 或同名币种字段解释
 * @param transactionAmount 金额值，单位必须结合 currency 或同名币种字段解释
 * @param orderIdPrefix order ID Prefix 输入值，参与 订单IDprefix 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
 */
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
        InternalAuthAccount applicant = InternalAuthContextHolder.get();
        requestDTO.setRequestSource("MERCHANT_PORTAL");
        requestDTO.setApplicantId(applicant == null || applicant.getAccountId() == null
                ? merchantId : applicant.getAccountId().toString());
        requestDTO.setApplicantName(resolveApplicantName(applicant, merchantId));
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

    /** 返回审批审计使用的稳定商户申请人显示名。 */
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
     * 校验商户动作携带的源交易分片时间，避免跨商户、跨分片扫描。
     *
     * @param request 商户交易动作请求
     * @return 源交易分片时间
     */
    private LocalDateTime requiredTransactionDateTime(TransactionActionRequest request) {
        if (request == null || request.getTransactionDateTime() == null) {
            throw new ApiException(ApiResultEnum.PARAM_MISSING, "transactionDateTime is required");
        }
        return request.getTransactionDateTime();
    }

    /** 校验商户动作携带的生命周期根主单分片时间。 */
    private LocalDateTime requiredRootTransactionDateTime(TransactionActionRequest request) {
        if (request == null || request.getRootTransactionDateTime() == null) {
            throw new ApiException(ApiResultEnum.PARAM_MISSING, "rootTransactionDateTime is required");
        }
        return request.getRootTransactionDateTime();
    }

    /**
     * 解析resolvelabel币种，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户后台服务 中需要标准化的原始值。
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
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
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
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
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
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
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

    /**
     * 构造动作csvline对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
    private String toOperationCsvLine(TransactionOperationResponse row, Locale locale) {
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
                csv(binaryLabel(row.getThreeDsEnabled(), locale, "是", "否", "Yes", "No")),
                csv(binaryLabel(row.getDccEnabled(), locale, "启用", "未启用", "Enabled", "Disabled")),
                csv(binaryLabel(row.getEdcEnabled(), locale, "启用", "未启用", "Enabled", "Disabled")),
                csv(row.getCardBin()),
                csv(row.getAuthCode()),
                csv(row.getAcquirerReferenceNo()),
                csv(row.getMerchantResponseCode()),
                csv(row.getMerchantResponseMessage()),
                csv(row.getChannelCode()),
                csv(row.getChannelOrderNo()),
                csv(row.getChannelTransactionId()),
                csv(row.getSettlementStatus()),
                csv(row.getReconciliationStatus()),
                csv(row.getOperationTime()),
                csv(row.getTransactionDateTime())
        );
    }

    private String binaryLabel(Integer value,
                               Locale locale,
                               String zhEnabled,
                               String zhDisabled,
                               String enEnabled,
                               String enDisabled) {
        boolean enabled = Integer.valueOf(1).equals(value);
        return csvLabel(locale, enabled ? zhEnabled : zhDisabled, enabled ? enEnabled : enDisabled);
    }

    private String csvLabel(Locale locale, String zhText, String enText) {
        return locale != null && Locale.CHINESE.getLanguage().equalsIgnoreCase(locale.getLanguage())
                ? zhText : enText;
    }

    /**
     * 规范化csv，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 商户后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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
