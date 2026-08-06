package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentQueryClientResponseDTO;
import com.scott.payment.openapi.config.PaymentClientProperties;
import com.scott.payment.openapi.converter.OpenApiRequestConverter;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.enums.OpenApiPaymentOperationEnum;
import com.scott.payment.openapi.enums.OpenApiPaymentStatusEnum;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessService;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.support.OpenApiRequestContext;
import com.scott.payment.openapi.support.OpenApiRequestAttributes;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import com.scott.payment.openapi.vo.payment.PaymentQueryVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentServiceImpl
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 收单交易服务实现，位于 service-openapi 服务层，负责把独立交易动作转换为 service-payment 内部请求；敏感卡信息只允许内存透传到支付核心。
 * @status : create
 */
@Service
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    /**
     * 默认卡交易支付方式，后续可由外部请求或商户产品配置显式传入。
     */
    private static final String DEFAULT_PAYMENT_METHOD = "BANK_CARD";

    /**
     * 网关转发客户端 IP 请求头。
     */
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * 代理转发真实 IP 请求头。
     */
    private static final String X_REAL_IP = "X-Real-IP";

    /**
     * 请求来源请求头。
     */
    private static final String ORIGIN = "Origin";

    /**
     * 请求引用页请求头。
     */
    private static final String REFERER = "Referer";

    /**
     * 浏览器 User-Agent 请求头。
     */
    private static final String USER_AGENT = "User-Agent";

    /**
     * 日志摘要最大字符数。
     */
    private static final int MAX_LOG_SUMMARY_LENGTH = 1600;

    /**
     * 这里仅用于日志输出目标服务和路径；实际调用契约由 PaymentInternalRestClient 执行。
     */
    private static final String SERVICE_PAYMENT_BASE_URL = "http://service-payment";

    /**
     * service-payment 授权内部接口路径，属于微服务固定契约。
     */
    private static final String AUTHORIZATION_PATH = "/internal/payment/authorization";

    /**
     * service-payment 支付内部接口路径，属于微服务固定契约。
     */
    private static final String PAYMENT_PATH = "/internal/payment/payment";

    /**
     * service-payment 预授权内部接口路径，属于微服务固定契约。
     */
    private static final String PRE_AUTHORIZATION_PATH = "/internal/payment/pre-authorization";

    /**
     * service-payment 增量授权内部接口路径，属于微服务固定契约。
     */
    private static final String INCREMENTAL_AUTHORIZATION_PATH = "/internal/payment/incremental-authorization";

    /**
     * service-payment 请款内部接口路径，属于微服务固定契约。
     */
    private static final String CAPTURE_PATH = "/internal/payment/capture";

    /**
     * service-payment 预授权完成内部接口路径，属于微服务固定契约。
     */
    private static final String PRE_AUTH_COMPLETION_PATH = "/internal/payment/pre-auth-completion";

    /**
     * service-payment 退款内部接口路径，属于微服务固定契约。
     */
    private static final String REFUND_PATH = "/internal/payment/refund";

    /**
     * service-payment 撤销内部接口路径，属于微服务固定契约。
     */
    private static final String VOID_PATH = "/internal/payment/void";

    /**
     * service-payment 交易查询内部接口路径，属于微服务固定契约。
     */
    private static final String QUERY_PATH = "/internal/payment/query";

    /**
     * OpenAPI 请求转换器，负责把外部公共请求 DTO 转换成当前接口响应或内部服务对象。
     */
    private final OpenApiRequestConverter converter;

    /**
     * service-payment 内部调用客户端，负责完成 OpenAPI 到支付核心服务的微服务调用。
     */
    private final PaymentInternalClient paymentInternalClient;

    /**
     * 支付内部调用配置，用于测试或本地模式切换远程调用。
     */
    private final PaymentClientProperties paymentClientProperties;

    /**
     * OpenAPI 密钥材料工具，用于计算密文指纹，避免把完整密文或卡信息传入日志。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    /**
     * OpenAPI 请求上下文访问器。
     */
    private final OpenApiRequestContext requestContext;

    /**
     * ISO 币种字典服务，用于本地降级模式按币种精度转换响应金额。
     */
    private final IsoDictionaryService isoDictionaryService;

    /**
     * 商户基础资料缓存服务，用于 OpenAPI 本地降级响应读取商户结算币种。
     */
    private final MerchantRuntimeProfileCacheService merchantRuntimeProfileCacheService;

    /**
     * 创建开放接口收单支付服务实现。
     *
     * @param converter               OpenAPI 请求转换器
     * @param paymentInternalClient   service-payment 内部调用客户端
     * @param paymentClientProperties 支付内部调用配置
     * @param keyMaterialFactory      OpenAPI 密钥材料工具
     * @param requestContext          OpenAPI 请求上下文访问器
     * @param isoDictionaryService    ISO 币种字典服务
     * @param merchantRuntimeProfileCacheService 商户运行时资料缓存服务
     */
    public PaymentServiceImpl(OpenApiRequestConverter converter,
                              PaymentInternalClient paymentInternalClient,
                              PaymentClientProperties paymentClientProperties,
                              OpenApiKeyMaterialFactory keyMaterialFactory,
                              OpenApiRequestContext requestContext,
                              IsoDictionaryService isoDictionaryService,
                              MerchantRuntimeProfileCacheService merchantRuntimeProfileCacheService) {
        this.converter = converter;
        this.paymentInternalClient = paymentInternalClient;
        this.paymentClientProperties = paymentClientProperties;
        this.keyMaterialFactory = keyMaterialFactory;
        this.requestContext = requestContext;
        this.isoDictionaryService = isoDictionaryService;
        this.merchantRuntimeProfileCacheService = merchantRuntimeProfileCacheService;
    }

    /**
     * 创建一步支付交易。
     *
     * @param encryptedData 商户原始密文，仅用于请求指纹和安全审计摘要
     * @param requestDTO    解密并校验后的统一支付请求
     * @return 支付核心受理结果
     */
    @Override
    public PaymentCreateVO createPayment(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitPayment(encryptedData, requestDTO);
    }

    /**
     * 创建独立授权交易，不执行后续请款。
     *
     * @param encryptedData 商户原始密文，仅用于请求指纹和安全审计摘要
     * @param requestDTO    解密并校验后的授权请求
     * @return 支付核心受理结果
     */
    @Override
    public PaymentCreateVO createAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.AUTHORIZATION);
    }

    /**
     * 创建预授权交易。
     *
     * @param encryptedData 商户原始密文，仅用于请求指纹和安全审计摘要
     * @param requestDTO    解密并校验后的预授权请求
     * @return 支付核心受理结果
     */
    @Override
    public PaymentCreateVO createPreAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.PRE_AUTHORIZATION);
    }

    /**
     * 对既有预授权发起增量授权。
     *
     * @param encryptedData 商户原始密文，仅用于请求指纹和安全审计摘要
     * @param requestDTO    解密并校验后的增量授权请求，必须携带原交易关联信息
     * @return 支付核心受理结果
     */
    @Override
    public PaymentCreateVO createIncrementalAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.INCREMENTAL_AUTHORIZATION);
    }

    /**
     * 对既有授权发起请款。
     *
     * @param encryptedData 商户原始密文，仅用于请求指纹和安全审计摘要
     * @param requestDTO    解密并校验后的请款请求，金额校验由支付核心完成
     * @return 支付核心受理结果
     */
    @Override
    public PaymentCreateVO capture(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.CAPTURE);
    }

    /**
     * 完成预授权并进入后续扣款处理。
     *
     * @param encryptedData 商户原始密文，仅用于请求指纹和安全审计摘要
     * @param requestDTO    解密并校验后的预授权完成请求
     * @return 支付核心受理结果
     */
    @Override
    public PaymentCreateVO preAuthCompletion(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.PRE_AUTH_COMPLETION);
    }

    /**
     * 对原支付交易发起退款。
     *
     * @param encryptedData 商户原始密文，仅用于请求指纹和安全审计摘要
     * @param requestDTO    解密并校验后的退款请求，累计可退金额以数据库状态为准
     * @return 支付核心受理结果
     */
    @Override
    public PaymentCreateVO refund(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.REFUND);
    }

    /**
     * 对符合状态约束的原交易发起撤销。
     *
     * @param encryptedData 商户原始密文，仅用于请求指纹和安全审计摘要
     * @param requestDTO    解密并校验后的撤销请求
     * @return 支付核心受理结果
     */
    @Override
    public PaymentCreateVO voidPayment(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.VOID);
    }

    /**
     * 提交收单交易动作。
     * <p>
     * 所有创建类动作响应都需要合并商户请求回显字段和支付核心处理结果，避免授权、预授权等接口遗漏
     * merchantInfo、billingCardHolderInfo、description、callbackUrl 或商户结算币种。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹
     * @param requestDTO    解密后的统一请求参数
     * @param operation     交易动作
     * @return 交易受理响应
     */
    private PaymentCreateVO submitTransaction(String encryptedData,
                                              ApiMerchantPaymentRequestDTO requestDTO,
                                              OpenApiPaymentOperationEnum operation) {
        long startNanos = System.nanoTime();
        if (!paymentClientProperties.isRemoteEnabled()) {
            logOpenApiPaymentSubmitStart(encryptedData, requestDTO, operation, null);
            PaymentCreateVO localResult = createLocalPaymentResult(requestDTO, operation);
            PaymentCreateVO responseVO = converter.toPaymentCreateVO(requestDTO, toClientResponse(localResult), resolveMerchantSettlementCurrency());
            logOpenApiPaymentSubmitEnd(requestDTO, operation, responseVO, startNanos);
            return responseVO;
        }
        PaymentCreateClientRequestDTO clientRequestDTO = toPaymentClientRequest(encryptedData, requestDTO, operation);
        logOpenApiPaymentSubmitStart(encryptedData, requestDTO, operation, clientRequestDTO);
        PaymentCreateClientResponseDTO clientResponseDTO = submitToPayment(clientRequestDTO, operation);
        PaymentCreateVO responseVO = converter.toPaymentCreateVO(requestDTO, clientResponseDTO, resolveMerchantSettlementCurrency());
        logOpenApiPaymentSubmitEnd(requestDTO, operation, responseVO, startNanos);
        return responseVO;
    }

    /**
     * 提交一步支付交易。
     * <p>
     * 支付接口响应需要按商户请求原样回显 merchantInfo、orderInfo 和 billingCardHolderInfo，
     * 并从商户信息表读取商户结算币种，不能使用渠道、MID 或交易币种兜底。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹
     * @param requestDTO    解密后的支付请求参数
     * @return 一步支付响应
     */
    private PaymentCreateVO submitPayment(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        long startNanos = System.nanoTime();
        if (!paymentClientProperties.isRemoteEnabled()) {
            logOpenApiPaymentSubmitStart(encryptedData, requestDTO, OpenApiPaymentOperationEnum.PAYMENT, null);
            PaymentCreateVO localResult = createLocalPaymentResult(requestDTO, OpenApiPaymentOperationEnum.PAYMENT);
            PaymentCreateVO responseVO = converter.toPaymentCreateVO(requestDTO, toClientResponse(localResult), resolveMerchantSettlementCurrency());
            logOpenApiPaymentSubmitEnd(requestDTO, OpenApiPaymentOperationEnum.PAYMENT, responseVO, startNanos);
            return responseVO;
        }
        PaymentCreateClientRequestDTO clientRequestDTO = toPaymentClientRequest(encryptedData, requestDTO, OpenApiPaymentOperationEnum.PAYMENT);
        logOpenApiPaymentSubmitStart(encryptedData, requestDTO, OpenApiPaymentOperationEnum.PAYMENT, clientRequestDTO);
        PaymentCreateClientResponseDTO clientResponseDTO = submitToPayment(clientRequestDTO, OpenApiPaymentOperationEnum.PAYMENT);
        PaymentCreateVO responseVO = converter.toPaymentCreateVO(requestDTO, clientResponseDTO, resolveMerchantSettlementCurrency());
        logOpenApiPaymentSubmitEnd(requestDTO, OpenApiPaymentOperationEnum.PAYMENT, responseVO, startNanos);
        return responseVO;
    }

    /**
     * 查询收单交易状态。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹
     * @param requestDTO 解密后的查询请求参数
     * @return 交易查询响应
     */
    @Override
    public PaymentQueryVO queryTransaction(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        long startNanos = System.nanoTime();
        if (!paymentClientProperties.isRemoteEnabled()) {
            logOpenApiPaymentSubmitStart(encryptedData, requestDTO, OpenApiPaymentOperationEnum.QUERY, null);
            PaymentQueryVO responseVO = createLocalQueryResult(requestDTO);
            log.info("event: OPENAPI_PAYMENT_SUBMIT_END stage=OPENAPI_SERVICE traceId: {} operation: {} merchantId: {} merchantOrderNo: {} sourceTransactionId: {} transactionType: {} remoteEnabled=false responseSummary: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    OpenApiPaymentOperationEnum.QUERY.getTransactionType(),
                    requestContext.getRequiredMerchantId(),
                    resolveMerchantOrderNo(requestDTO),
                    requestDTO == null || requestDTO.getTransactionInfo() == null ? null : requestDTO.getTransactionInfo().getSourceTransactionId(),
                    OpenApiPaymentOperationEnum.QUERY.getTransactionType(),
                    responseSummary(responseVO),
                    elapsedMillis(startNanos));
            return responseVO;
        }
        PaymentCreateClientRequestDTO clientRequestDTO = toPaymentClientRequest(encryptedData, requestDTO, OpenApiPaymentOperationEnum.QUERY);
        logOpenApiPaymentSubmitStart(encryptedData, requestDTO, OpenApiPaymentOperationEnum.QUERY, clientRequestDTO);
        PaymentQueryVO responseVO = converter.toPaymentQueryVO(requestDTO, paymentInternalClient.query(clientRequestDTO));
        log.info("event: OPENAPI_PAYMENT_SUBMIT_END stage=OPENAPI_SERVICE traceId: {} operation: {} merchantId: {} merchantOrderNo: {} sourceTransactionId: {} transactionType: {} remoteEnabled=true responseSummary: {} durationMs: {}",
                TraceContext.getTraceId(),
                OpenApiPaymentOperationEnum.QUERY.getTransactionType(),
                requestContext.getRequiredMerchantId(),
                resolveMerchantOrderNo(requestDTO),
                requestDTO == null || requestDTO.getTransactionInfo() == null ? null : requestDTO.getTransactionInfo().getSourceTransactionId(),
                OpenApiPaymentOperationEnum.QUERY.getTransactionType(),
                responseSummary(responseVO),
                elapsedMillis(startNanos));
        return responseVO;
    }

    /**
     * 构建本地降级模式交易查询响应。
     *
     * @param requestDTO 解密后的查询请求参数
     * @return 本地查询响应
     */
    private PaymentQueryVO createLocalQueryResult(ApiMerchantPaymentRequestDTO requestDTO) {
        PaymentCreateVO localCreateVO = createLocalPaymentResult(requestDTO, OpenApiPaymentOperationEnum.QUERY);
        PaymentQueryClientResponseDTO responseDTO = new PaymentQueryClientResponseDTO();
        if (localCreateVO.getOrderInfo() != null) {
            responseDTO.setMerchantOrderNo(localCreateVO.getOrderInfo().getOrderNo());
            responseDTO.setMerchantOrderId(localCreateVO.getOrderInfo().getOrderId());
            responseDTO.setOrderAmount(localCreateVO.getOrderInfo().getAmount());
            responseDTO.setOrderCurrency(localCreateVO.getOrderInfo().getCurrency());
        }
        if (localCreateVO.getMerchantInfo() != null) {
            responseDTO.setMerchantId(localCreateVO.getMerchantInfo().getMerchantId());
        }
        if (localCreateVO.getBillingInfo() != null) {
            responseDTO.setLabelAmount(localCreateVO.getBillingInfo().getLabelAmount());
            responseDTO.setLabelCurrency(localCreateVO.getBillingInfo().getLabelCurrency());
            responseDTO.setTransactionAmount(localCreateVO.getBillingInfo().getTransactionAmount());
            responseDTO.setTransactionCurrency(localCreateVO.getBillingInfo().getTransactionCurrency());
            responseDTO.setTransactionRate(localCreateVO.getBillingInfo().getTransactionRate());
            responseDTO.setSettlementCurrency(localCreateVO.getBillingInfo().getSettlementCurrency());
        }
        PaymentCreateVO.TransactionInfoVO localTransactionInfo = localCreateVO.getTransactionInfo();
        if (localTransactionInfo != null) {
            PaymentQueryClientResponseDTO.TransactionInfoDTO transactionInfoDTO = new PaymentQueryClientResponseDTO.TransactionInfoDTO();
            transactionInfoDTO.setTransactionId(resolveRequestedTransactionId(requestDTO, localTransactionInfo.getTransactionId()));
            transactionInfoDTO.setSourceTransactionId(localTransactionInfo.getSourceTransactionId());
            transactionInfoDTO.setCode(localTransactionInfo.getCode());
            transactionInfoDTO.setMessage(localTransactionInfo.getMessage());
            transactionInfoDTO.setTransactionType(localTransactionInfo.getTransactionType());
            transactionInfoDTO.setTransactionDateTime(LocalDateTime.now());
            transactionInfoDTO.setPaymentMethod(localTransactionInfo.getPaymentMethod());
            transactionInfoDTO.setCardBrand(localTransactionInfo.getCardBrand());
            transactionInfoDTO.setCardBin(localTransactionInfo.getCardBin());
            transactionInfoDTO.setAuthCode(localTransactionInfo.getAuthCode());
            transactionInfoDTO.setArn(localTransactionInfo.getArn());
            transactionInfoDTO.setDescription(localTransactionInfo.getDescription());
            transactionInfoDTO.setCallbackUrl(localTransactionInfo.getCallbackUrl());
            responseDTO.getTransactionInfo().add(transactionInfoDTO);
        }
        return converter.toPaymentQueryVO(requestDTO, responseDTO);
    }

    /**
     * 构建本地降级模式交易响应。
     * <p>
     * 单元测试或本地只启动 `service-openapi` 时不依赖 `service-payment`，但仍返回平台订单号和状态，
     * 方便商户侧验证响应加密、字段解析和接口契约。
     *
     * @param requestDTO 解密后的统一请求参数
     * @param operation 交易动作
     * @return 本地模拟交易响应
     */
    private PaymentCreateVO createLocalPaymentResult(ApiMerchantPaymentRequestDTO requestDTO,
                                                     OpenApiPaymentOperationEnum operation) {
        PaymentCreateVO vo = converter.toPaymentCreateVO(requestDTO);
        vo.setStatus(OpenApiPaymentStatusEnum.PROCESSING.getCode());
        PaymentCreateVO.TransactionInfoVO transactionInfoVO = new PaymentCreateVO.TransactionInfoVO();
        transactionInfoVO.setTransactionId(PaymentOrderNoGenerator.nextTransactionId());
        transactionInfoVO.setSourceTransactionId(requestDTO.getTransactionInfo() == null ? null : requestDTO.getTransactionInfo().getSourceTransactionId());
        transactionInfoVO.setCode(ApiResultEnum.PROCESSING.getCode());
        transactionInfoVO.setMessage(ApiResultEnum.PROCESSING.getMessage());
        transactionInfoVO.setTransactionType(operation.getTransactionType());
        transactionInfoVO.setTransactionStatus(vo.getStatus());
        transactionInfoVO.setTransactionDateTime(LocalDateTime.now().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime());
        transactionInfoVO.setPaymentMethod(DEFAULT_PAYMENT_METHOD);
        transactionInfoVO.setCardBrand(resolveCardBrandFromCardNo(requestDTO));
        transactionInfoVO.setCardBin(maskCardBin(requestDTO));
        transactionInfoVO.setDescription(requestDTO.getTransactionInfo() == null ? null : requestDTO.getTransactionInfo().getDescription());
        transactionInfoVO.setCallbackUrl(requestDTO.getTransactionInfo() == null ? null : requestDTO.getTransactionInfo().getCallbackUrl());
        vo.setTransactionInfo(transactionInfoVO);
        if (requestDTO.getOrderInfo() != null && requestDTO.getOrderInfo().getAmount() != null) {
            PaymentCreateVO.BillingInfoVO billingInfoVO = new PaymentCreateVO.BillingInfoVO();
            billingInfoVO.setLabelAmount(requestDTO.getOrderInfo().getAmount());
            billingInfoVO.setLabelCurrency(requestDTO.getOrderInfo().getCurrency());
            billingInfoVO.setTransactionAmount(requestDTO.getOrderInfo().getAmount());
            billingInfoVO.setTransactionCurrency(requestDTO.getOrderInfo().getCurrency());
            billingInfoVO.setTransactionRate(new java.math.BigDecimal("1.00000000"));
            billingInfoVO.setSettlementCurrency(resolveMerchantSettlementCurrency());
            vo.setBillingInfo(billingInfoVO);
        }
        if (OpenApiPaymentOperationEnum.QUERY == operation) {
            vo.setStatus(OpenApiPaymentStatusEnum.PENDING.getCode());
            transactionInfoVO.setCode(ApiResultEnum.PENDING.getCode());
            transactionInfoVO.setMessage(ApiResultEnum.PENDING.getMessage());
            transactionInfoVO.setTransactionStatus(vo.getStatus());
        }
        return vo;
    }

    /**
     * 从商户信息表读取当前商户结算币种。
     *
     * @return 商户结算币种，未配置时返回 null 并由响应空字段策略省略
     */
    private String resolveMerchantSettlementCurrency() {
        String merchantId = requestContext.getRequiredMerchantId();
        MerchantRuntimeProfile profile = merchantRuntimeProfileCacheService.findRuntimeProfile(merchantId);
        return profile == null ? null : profile.getSettlementCurrency();
    }

    /**
     * 将本地降级响应转成内部响应 DTO，复用支付接口统一商户响应组装规则。
     *
     * @param vo 本地降级响应
     * @return 内部响应 DTO
     */
    private PaymentCreateClientResponseDTO toClientResponse(PaymentCreateVO vo) {
        PaymentCreateClientResponseDTO responseDTO = new PaymentCreateClientResponseDTO();
        if (vo == null) {
            return responseDTO;
        }
        if (vo.getOrderInfo() != null) {
            responseDTO.setMerchantOrderNo(vo.getOrderInfo().getOrderNo());
            responseDTO.setMerchantOrderId(vo.getOrderInfo().getOrderId());
            responseDTO.setOrderAmount(vo.getOrderInfo().getAmount());
            responseDTO.setOrderCurrency(vo.getOrderInfo().getCurrency());
            responseDTO.setTotalAuthorizedAmount(vo.getOrderInfo().getTotalAuthorizedAmount());
            responseDTO.setTotalCapturedAmount(vo.getOrderInfo().getTotalCapturedAmount());
            responseDTO.setTotalRefundAmount(vo.getOrderInfo().getTotalRefundAmount());
            responseDTO.setTotalAuthorizedCancelAmount(vo.getOrderInfo().getTotalAuthorizedCancelAmount());
            responseDTO.setTotalRefuseAmount(vo.getOrderInfo().getTotalRefuseAmount());
        }
        if (vo.getTransactionInfo() != null) {
            responseDTO.setTransactionId(vo.getTransactionInfo().getTransactionId());
            responseDTO.setSourceTransactionId(vo.getTransactionInfo().getSourceTransactionId());
            responseDTO.setMerchantResponseCode(vo.getTransactionInfo().getCode());
            responseDTO.setMerchantResponseMessage(vo.getTransactionInfo().getMessage());
            responseDTO.setTransactionType(vo.getTransactionInfo().getTransactionType());
            responseDTO.setTransactionDateTime(vo.getTransactionInfo().getTransactionDateTime() == null
                    ? null : vo.getTransactionInfo().getTransactionDateTime().toLocalDateTime());
            responseDTO.setTransactionTimeZone("Asia/Shanghai");
            responseDTO.setPaymentMethod(vo.getTransactionInfo().getPaymentMethod());
            responseDTO.setPaymentBrand(vo.getTransactionInfo().getCardBrand());
            responseDTO.setCardBin(vo.getTransactionInfo().getCardBin());
        }
        if (vo.getBillingInfo() != null) {
            responseDTO.setLabelAmount(vo.getBillingInfo().getLabelAmount());
            responseDTO.setLabelCurrency(vo.getBillingInfo().getLabelCurrency());
            responseDTO.setTransactionAmount(vo.getBillingInfo().getTransactionAmount());
            responseDTO.setTransactionCurrency(vo.getBillingInfo().getTransactionCurrency());
            responseDTO.setTransactionRate(vo.getBillingInfo().getTransactionRate());
        }
        responseDTO.setStatus(vo.getStatus());
        return responseDTO;
    }

    /**
     * 本地降级模式按 ISO 4217 默认辅币位转换响应金额。
     *
     * @param requestDTO 解密后的统一请求参数
     * @return 最小辅币单位金额
     */
    private Long toMinorAmount(ApiMerchantPaymentRequestDTO requestDTO) {
        try {
            return isoDictionaryService.toMinorUnit(requestDTO.getOrderInfo().getAmount(), requestDTO.getOrderInfo().getCurrency());
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "amount fraction digits exceed currency minor unit", exception);
        }
    }

    /**
     * 构建调用 service-payment 的内部请求。
     *
     * @param encryptedData 商户原始密文
     * @param requestDTO    解密后的统一请求参数
     * @return 支付内部创建请求
     */
    private PaymentCreateClientRequestDTO toPaymentClientRequest(String encryptedData,
                                                                 ApiMerchantPaymentRequestDTO requestDTO,
                                                                 OpenApiPaymentOperationEnum operation) {
        PaymentCreateClientRequestDTO clientRequestDTO = new PaymentCreateClientRequestDTO();
        clientRequestDTO.setMerchantId(requestContext.getRequiredMerchantId());
        clientRequestDTO.setMerchantOrderNo(resolveMerchantOrderNo(requestDTO));
        clientRequestDTO.setMerchantOrderId(resolveMerchantOrderId(requestDTO));
        clientRequestDTO.setTransactionType(operation.getTransactionType());
        clientRequestDTO.setPaymentMethod(DEFAULT_PAYMENT_METHOD);
        if (requestDTO.getOrderInfo() != null) {
            clientRequestDTO.setAmount(requestDTO.getOrderInfo().getAmount());
            clientRequestDTO.setCurrency(requestDTO.getOrderInfo().getCurrency());
        }
        clientRequestDTO.setTransactionDateTime(LocalDateTime.now());
        clientRequestDTO.setRequestId(resolveMerchantOrderId(requestDTO));
        clientRequestDTO.setRequestSource("OPENAPI");
        clientRequestDTO.setApplicantId(requestContext.getRequiredMerchantId());
        if (requestDTO.getTransactionInfo() != null) {
            clientRequestDTO.setCallbackUrl(requestDTO.getTransactionInfo().getCallbackUrl());
            clientRequestDTO.setRequestReason(requestDTO.getTransactionInfo().getDescription());
        }
        clientRequestDTO.setRequestFingerprint(keyMaterialFactory.fingerprint(encryptedData));
        clientRequestDTO.setOpenApiRequestPath(resolveRequestPath());
        clientRequestDTO.setOpenApiRequestTime(LocalDateTime.now());
        clientRequestDTO.setMerchantRequestCipherMasked(maskCipher(encryptedData));
        clientRequestDTO.setMerchantRequestPlainJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(requestDTO)));
        clientRequestDTO.setSubMerchantInfo(converter.toPaymentClientSubMerchantInfo(
                requestDTO.getMerchantInfo() == null ? null : requestDTO.getMerchantInfo().getSubMerchantInfo()));
        clientRequestDTO.setBillingCardHolderInfo(converter.toPaymentClientBillingCardHolderInfo(requestDTO.getBillingCardHolderInfo()));
        clientRequestDTO.setCardInfo(converter.toPaymentClientCardInfo(requestDTO.getCardInfo()));
        clientRequestDTO.setThreeDsInfo(converter.toPaymentClientThreeDsInfo(requestDTO.getThreeDsInfo()));
        clientRequestDTO.setTransactionInfo(converter.toPaymentClientTransactionInfo(requestDTO.getTransactionInfo()));
        clientRequestDTO.setRiskContext(converter.toPaymentClientRiskContext(requestDTO.getRiskContext()));
        populateRequestSource(clientRequestDTO);
        return clientRequestDTO;
    }

    /**
     * 记录 OpenAPI 进入 payment 调用前的配置和请求摘要。
     * <p>
     * 日志覆盖商户号、订单号、交易类型、金额币种、远程调用开关、目标服务地址、请求密文指纹和明文脱敏摘要；
     * 不输出完整密文、卡号、CVV、JWT 或内部服务签名密钥。
     * </p>
     * @param encryptedData 商户请求 data 密文或请求体
     * @param requestDTO 解密后的商户请求
     * @param operation 当前 OpenAPI 交易动作
     * @param clientRequestDTO 准备发送到 service-payment 的内部请求，本地模式时为空
     */
    private void logOpenApiPaymentSubmitStart(String encryptedData,
                                              ApiMerchantPaymentRequestDTO requestDTO,
                                              OpenApiPaymentOperationEnum operation,
                                              PaymentCreateClientRequestDTO clientRequestDTO) {
        String targetUrl = paymentTargetUrl(operation);
        URI targetUri = targetUrl == null ? null : URI.create(targetUrl);
        log.info("event: OPENAPI_PAYMENT_SUBMIT_START stage=OPENAPI_SERVICE traceId: {} operation: {} merchantId: {} merchantOrderNo: {} merchantOrderId: {} transactionType: {} paymentMethod: {} currency: {} amount: {} remoteEnabled: {} targetService: {} targetPath: {} requestFingerprint: {} cipherMasked: {} openApiPath: {} apiVersion: {} interfaceType: {} requestSource: {} cipherRequestSummary: {} plainRequestSummary: {}",
                TraceContext.getTraceId(),
                operation.getTransactionType(),
                requestContext.getRequiredMerchantId(),
                resolveMerchantOrderNo(requestDTO),
                resolveMerchantOrderId(requestDTO),
                operation.getTransactionType(),
                DEFAULT_PAYMENT_METHOD,
                requestDTO == null || requestDTO.getOrderInfo() == null ? null : requestDTO.getOrderInfo().getCurrency(),
                requestDTO == null || requestDTO.getOrderInfo() == null ? null : requestDTO.getOrderInfo().getAmount(),
                paymentClientProperties.isRemoteEnabled(),
                targetUri == null ? null : targetUri.getHost(),
                targetUri == null ? null : targetUri.getPath(),
                clientRequestDTO == null ? keyMaterialFactory.fingerprint(encryptedData) : clientRequestDTO.getRequestFingerprint(),
                clientRequestDTO == null ? maskCipher(encryptedData) : clientRequestDTO.getMerchantRequestCipherMasked(),
                resolveRequestPath(),
                requestAttribute(OpenApiRequestAttributes.API_VERSION),
                requestAttribute(OpenApiRequestAttributes.INTERFACE_TYPE),
                requestSourceSummary(clientRequestDTO),
                requestAttribute(OpenApiRequestAttributes.REQUEST_CIPHER_SUMMARY),
                plainRequestSummary(requestDTO));
    }

    /**
     * 记录 OpenAPI payment 响应组装完成。
     *
     * @param requestDTO 商户请求
     * @param operation 当前交易动作
     * @param responseVO 返回商户前的响应对象
     * @param startNanos 请求开始时间，单位为纳秒
     */
    private void logOpenApiPaymentSubmitEnd(ApiMerchantPaymentRequestDTO requestDTO,
                                            OpenApiPaymentOperationEnum operation,
                                            PaymentCreateVO responseVO,
                                            long startNanos) {
        PaymentCreateVO.TransactionInfoVO transactionInfo = responseVO == null ? null : responseVO.getTransactionInfo();
        log.info("event: OPENAPI_PAYMENT_SUBMIT_END stage=OPENAPI_SERVICE traceId: {} operation: {} merchantId: {} merchantOrderNo: {} transactionId: {} sourceTransactionId: {} transactionType: {} platformStatus: {} responseSummary: {} durationMs: {}",
                TraceContext.getTraceId(),
                operation.getTransactionType(),
                requestContext.getRequiredMerchantId(),
                resolveMerchantOrderNo(requestDTO),
                transactionInfo == null ? null : transactionInfo.getTransactionId(),
                transactionInfo == null ? null : transactionInfo.getSourceTransactionId(),
                transactionInfo == null ? operation.getTransactionType() : transactionInfo.getTransactionType(),
                responseVO == null ? null : responseVO.getStatus(),
                responseSummary(responseVO),
                elapsedMillis(startNanos));
    }

    /**
     * 获取当前交易动作的 service-payment 目标地址。
     *
     * @param operation OpenAPI 交易动作
     * @return 内部服务目标 URL
     */
    private String paymentTargetUrl(OpenApiPaymentOperationEnum operation) {
        if (OpenApiPaymentOperationEnum.PAYMENT == operation) {
            return servicePaymentUrl(PAYMENT_PATH);
        }
        if (OpenApiPaymentOperationEnum.AUTHORIZATION == operation) {
            return servicePaymentUrl(AUTHORIZATION_PATH);
        }
        if (OpenApiPaymentOperationEnum.PRE_AUTHORIZATION == operation) {
            return servicePaymentUrl(PRE_AUTHORIZATION_PATH);
        }
        if (OpenApiPaymentOperationEnum.INCREMENTAL_AUTHORIZATION == operation) {
            return servicePaymentUrl(INCREMENTAL_AUTHORIZATION_PATH);
        }
        if (OpenApiPaymentOperationEnum.CAPTURE == operation) {
            return servicePaymentUrl(CAPTURE_PATH);
        }
        if (OpenApiPaymentOperationEnum.PRE_AUTH_COMPLETION == operation) {
            return servicePaymentUrl(PRE_AUTH_COMPLETION_PATH);
        }
        if (OpenApiPaymentOperationEnum.REFUND == operation) {
            return servicePaymentUrl(REFUND_PATH);
        }
        if (OpenApiPaymentOperationEnum.VOID == operation) {
            return servicePaymentUrl(VOID_PATH);
        }
        if (OpenApiPaymentOperationEnum.QUERY == operation) {
            return servicePaymentUrl(QUERY_PATH);
        }
        return null;
    }

    /**
     * 拼接日志展示用 service-payment URL，真实调用入口仍由内部客户端按服务发现处理。
     */
    private String servicePaymentUrl(String path) {
        return SERVICE_PAYMENT_BASE_URL + path;
    }

    /**
     * 生成 OpenAPI 调用 payment 的请求来源摘要。
     *
     * @param clientRequestDTO 内部请求
     * @return 请求来源摘要 JSON
     */
    private String requestSourceSummary(PaymentCreateClientRequestDTO clientRequestDTO) {
        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
        if (clientRequestDTO != null) {
            summary.put("openApiRequestPath", clientRequestDTO.getOpenApiRequestPath());
            summary.put("openApiRequestTime", clientRequestDTO.getOpenApiRequestTime());
            summary.put("payerIp", clientRequestDTO.getPayerIp());
            summary.put("sourceUrl", maskUrl(clientRequestDTO.getSourceUrl()));
            summary.put("userAgent", truncate(clientRequestDTO.getUserAgent(), 160));
            return JsonUtils.toJsonString(summary);
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        summary.put("openApiRequestPath", resolveRequestPath());
        summary.put("payerIp", resolveClientIp(request));
        summary.put("sourceUrl", maskUrl(StringUtils.hasText(request.getHeader(ORIGIN))
                ? request.getHeader(ORIGIN)
                : request.getHeader(REFERER)));
        summary.put("userAgent", truncate(request.getHeader(USER_AGENT), 160));
        return JsonUtils.toJsonString(summary);
    }

    /**
     * 从当前 Servlet 请求读取 OpenAPI 链路属性。
     *
     * @param attributeName 请求属性名
     * @return 请求属性值；非 HTTP 线程或属性不存在时返回 null
     */
    private Object requestAttribute(String attributeName) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest().getAttribute(attributeName);
    }

    /**
     * 生成商户请求明文脱敏摘要。
     *
     * @param requestDTO 解密后的商户请求
     * @return 脱敏摘要
     */
    private String plainRequestSummary(ApiMerchantPaymentRequestDTO requestDTO) {
        return truncate(SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(requestDTO)), MAX_LOG_SUMMARY_LENGTH);
    }

    /**
     * 生成响应脱敏摘要。
     *
     * @param responseVO OpenAPI 响应对象
     * @return 脱敏摘要
     */
    private String responseSummary(Object responseVO) {
        return truncate(SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(responseVO)), MAX_LOG_SUMMARY_LENGTH);
    }

    /**
     * 解析商户订单号。
     *
     * @param requestDTO 解密后的统一请求参数
     * @return 商户订单号
     */
    private String resolveMerchantOrderNo(ApiMerchantPaymentRequestDTO requestDTO) {
        if (requestDTO.getOrderInfo() != null && StringUtils.hasText(requestDTO.getOrderInfo().getOrderNo())) {
            return requestDTO.getOrderInfo().getOrderNo();
        }
        return null;
    }

    /**
     * 解析商户本次 API 请求唯一标识。
     *
     * @param requestDTO 解密后的统一请求参数
     * @return 商户请求唯一标识
     */
    private String resolveMerchantOrderId(ApiMerchantPaymentRequestDTO requestDTO) {
        if (requestDTO.getOrderInfo() != null && StringUtils.hasText(requestDTO.getOrderInfo().getOrderId())) {
            return requestDTO.getOrderInfo().getOrderId();
        }
        return null;
    }

    /**
     * 解析查询请求中指定的平台交易 ID。
     *
     * @param requestDTO 商户查询请求
     * @param fallback   本地降级生成的交易 ID
     * @return 商户指定的平台交易 ID 或默认交易 ID
     */
    private String resolveRequestedTransactionId(ApiMerchantPaymentRequestDTO requestDTO, String fallback) {
        if (requestDTO != null && requestDTO.getTransactionInfo() != null
                && StringUtils.hasText(requestDTO.getTransactionInfo().getTransactionId())) {
            return requestDTO.getTransactionInfo().getTransactionId();
        }
        return fallback;
    }

    /**
     * 按交易动作调用 service-payment 对应内部接口。
     *
     * @param clientRequestDTO 内部交易请求
     * @param operation 交易动作
     * @return 内部交易响应
     */
    private PaymentCreateClientResponseDTO submitToPayment(PaymentCreateClientRequestDTO clientRequestDTO,
                                                           OpenApiPaymentOperationEnum operation) {
        if (OpenApiPaymentOperationEnum.PAYMENT == operation) {
            return paymentInternalClient.createPayment(clientRequestDTO);
        }
        if (OpenApiPaymentOperationEnum.AUTHORIZATION == operation) {
            return paymentInternalClient.createAuthorization(clientRequestDTO);
        }
        if (OpenApiPaymentOperationEnum.PRE_AUTHORIZATION == operation) {
            return paymentInternalClient.createPreAuthorization(clientRequestDTO);
        }
        if (OpenApiPaymentOperationEnum.INCREMENTAL_AUTHORIZATION == operation) {
            return paymentInternalClient.createIncrementalAuthorization(clientRequestDTO);
        }
        if (OpenApiPaymentOperationEnum.CAPTURE == operation) {
            return paymentInternalClient.capture(clientRequestDTO);
        }
        if (OpenApiPaymentOperationEnum.PRE_AUTH_COMPLETION == operation) {
            return paymentInternalClient.preAuthCompletion(clientRequestDTO);
        }
        if (OpenApiPaymentOperationEnum.REFUND == operation) {
            return paymentInternalClient.refund(clientRequestDTO);
        }
        if (OpenApiPaymentOperationEnum.VOID == operation) {
            return paymentInternalClient.voidPayment(clientRequestDTO);
        }
        throw new ServiceException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED);
    }

    /**
     * 填充当前 HTTP 请求来源信息，用于风控和 3DS 上下文。
     *
     * @param clientRequestDTO 内部创建请求
     */
    private void populateRequestSource(PaymentCreateClientRequestDTO clientRequestDTO) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return;
        }
        HttpServletRequest request = attributes.getRequest();
        clientRequestDTO.setPayerIp(resolveClientIp(request));
        clientRequestDTO.setUserAgent(request.getHeader(USER_AGENT));
        String origin = request.getHeader(ORIGIN);
        clientRequestDTO.setSourceUrl(StringUtils.hasText(origin) ? origin : request.getHeader(REFERER));
    }

    /**
     * 解析客户端 IP，优先使用网关转发头。
     *
     * @param request 当前 HTTP 请求
     * @return 客户端 IP
     */
    private String resolveClientIp(HttpServletRequest request) {
        String gatewayClientIp = request.getHeader(MerchantIpWhitelistAccessService.HEADER_GATEWAY_CLIENT_IP);
        if (StringUtils.hasText(gatewayClientIp)) {
            return gatewayClientIp.trim();
        }
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader(X_REAL_IP);
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * 解析当前 OpenAPI 请求路径，用于后台商户请求日志定位接口入口。
     *
     * @return 请求 URI，无法获取上下文时返回 null
     */
    private String resolveRequestPath() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String queryString = request.getQueryString();
        return StringUtils.hasText(queryString) ? request.getRequestURI() + "?" + queryString : request.getRequestURI();
    }

    /**
     * 脱敏 URL 查询参数。
     * <p>
     * OpenAPI 日志只保留 URL scheme、host、path 和查询参数名，不输出 callbackUrl 或来源地址中的 query 值。
     * </p>
     * @param url 原始 URL
     * @return 脱敏 URL
     */
    private String maskUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        int queryIndex = url.indexOf('?');
        if (queryIndex < 0) {
            return url;
        }
        return url.substring(0, queryIndex) + "?...";
    }

    /**
     * 计算耗时毫秒。
     *
     * @param startNanos 起始纳秒时间
     * @return 耗时，单位毫秒
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 截断日志摘要。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断后文本
     */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    /**
     * 生成密文掩码摘要，仅保留首尾短片段用于核对，禁止传递或保存完整密文。
     *
     * @param encryptedData 商户请求密文
     * @return 密文掩码
     */
    private String maskCipher(String encryptedData) {
        if (!StringUtils.hasText(encryptedData)) {
            return null;
        }
        String normalized = encryptedData.trim();
        if (normalized.length() <= 16) {
            return "***";
        }
        return normalized.substring(0, 8) + "***" + normalized.substring(normalized.length() - 8);
    }

    /**
     * 从卡号提取可安全展示的 BIN 与尾号摘要。
     * <p>
     * 本地降级模式没有支付核心卡 BIN 库，只保留前 6 后 4 的脱敏结果用于测试响应；
     * 完整 PAN 不写入日志、异常或数据库。
     * </p>
     * @param requestDTO 解密后的商户请求
     * @return 卡号脱敏摘要；卡号缺失或长度不足时返回 null
     */
    private String maskCardBin(ApiMerchantPaymentRequestDTO requestDTO) {
        if (requestDTO == null || requestDTO.getCardInfo() == null || !StringUtils.hasText(requestDTO.getCardInfo().getCardNo())) {
            return null;
        }
        String cardNo = requestDTO.getCardInfo().getCardNo().trim();
        if (cardNo.length() < 10) {
            return null;
        }
        return cardNo.substring(0, 6) + "****" + cardNo.substring(cardNo.length() - 4);
    }

    /**
     * 本地降级模式按常见 BIN 前缀推断卡品牌，生产远程链路以支付核心卡 BIN 库识别结果为准。
     *
     * @param requestDTO 解密后的统一请求参数
     * @return 统一卡品牌枚举，无法识别时返回 null
     */
    private String resolveCardBrandFromCardNo(ApiMerchantPaymentRequestDTO requestDTO) {
        if (requestDTO == null || requestDTO.getCardInfo() == null || !StringUtils.hasText(requestDTO.getCardInfo().getCardNo())) {
            return null;
        }
        String cardNo = requestDTO.getCardInfo().getCardNo().trim();
        if (cardNo.startsWith("4")) {
            return "VISA";
        }
        if (cardNo.startsWith("34") || cardNo.startsWith("37")) {
            return "AMEX";
        }
        if (cardNo.startsWith("35")) {
            return "JCB";
        }
        if (cardNo.startsWith("62")) {
            return "UNIONPAY";
        }
        if (cardNo.startsWith("5") || cardNo.startsWith("22")) {
            return "MASTERCARD";
        }
        return null;
    }

}
