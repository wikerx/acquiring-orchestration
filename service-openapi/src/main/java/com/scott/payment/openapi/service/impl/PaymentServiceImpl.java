package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
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
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.support.OpenApiRequestContext;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import com.scott.payment.openapi.vo.payment.PaymentQueryVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
     * 商户基础信息 Mapper，用于支付接口响应读取商户信息表中的结算币种。
     */
    private final BaseMerchantInfoMapper baseMerchantInfoMapper;

    /**
     * 创建开放接口收单支付服务实现。
     *
     * @param converter               OpenAPI 请求转换器
     * @param paymentInternalClient   service-payment 内部调用客户端
     * @param paymentClientProperties 支付内部调用配置
     * @param keyMaterialFactory      OpenAPI 密钥材料工具
     * @param requestContext          OpenAPI 请求上下文访问器
     * @param isoDictionaryService    ISO 币种字典服务
     * @param baseMerchantInfoMapper  商户基础信息 Mapper
     */
    public PaymentServiceImpl(OpenApiRequestConverter converter,
                              PaymentInternalClient paymentInternalClient,
                              PaymentClientProperties paymentClientProperties,
                              OpenApiKeyMaterialFactory keyMaterialFactory,
                              OpenApiRequestContext requestContext,
                              IsoDictionaryService isoDictionaryService,
                              BaseMerchantInfoMapper baseMerchantInfoMapper) {
        this.converter = converter;
        this.paymentInternalClient = paymentInternalClient;
        this.paymentClientProperties = paymentClientProperties;
        this.keyMaterialFactory = keyMaterialFactory;
        this.requestContext = requestContext;
        this.isoDictionaryService = isoDictionaryService;
        this.baseMerchantInfoMapper = baseMerchantInfoMapper;
    }

    @Override
    public PaymentCreateVO createPayment(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitPayment(encryptedData, requestDTO);
    }

    @Override
    public PaymentCreateVO createAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.AUTHORIZATION);
    }

    @Override
    public PaymentCreateVO createPreAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.PRE_AUTHORIZATION);
    }

    @Override
    public PaymentCreateVO createIncrementalAuthorization(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.INCREMENTAL_AUTHORIZATION);
    }

    @Override
    public PaymentCreateVO capture(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.CAPTURE);
    }

    @Override
    public PaymentCreateVO refund(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.REFUND);
    }

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
        if (!paymentClientProperties.isRemoteEnabled()) {
            PaymentCreateVO localResult = createLocalPaymentResult(requestDTO, operation);
            return converter.toPaymentCreateVO(requestDTO, toClientResponse(localResult), resolveMerchantSettlementCurrency());
        }
        PaymentCreateClientRequestDTO clientRequestDTO = toPaymentClientRequest(encryptedData, requestDTO, operation);
        PaymentCreateClientResponseDTO clientResponseDTO = submitToPayment(clientRequestDTO, operation);
        return converter.toPaymentCreateVO(requestDTO, clientResponseDTO, resolveMerchantSettlementCurrency());
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
        if (!paymentClientProperties.isRemoteEnabled()) {
            PaymentCreateVO localResult = createLocalPaymentResult(requestDTO, OpenApiPaymentOperationEnum.PAYMENT);
            return converter.toPaymentCreateVO(requestDTO, toClientResponse(localResult), resolveMerchantSettlementCurrency());
        }
        PaymentCreateClientRequestDTO clientRequestDTO = toPaymentClientRequest(encryptedData, requestDTO, OpenApiPaymentOperationEnum.PAYMENT);
        PaymentCreateClientResponseDTO clientResponseDTO = submitToPayment(clientRequestDTO, OpenApiPaymentOperationEnum.PAYMENT);
        return converter.toPaymentCreateVO(requestDTO, clientResponseDTO, resolveMerchantSettlementCurrency());
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
        if (!paymentClientProperties.isRemoteEnabled()) {
            return createLocalQueryResult(requestDTO);
        }
        PaymentCreateClientRequestDTO clientRequestDTO = toPaymentClientRequest(encryptedData, requestDTO, OpenApiPaymentOperationEnum.QUERY);
        return converter.toPaymentQueryVO(requestDTO, paymentInternalClient.query(clientRequestDTO));
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
        BaseMerchantInfoDO merchantInfoDO = baseMerchantInfoMapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                        .eq(BaseMerchantInfoDO::getDeleted, 0)
                        .last("LIMIT 1"));
        return merchantInfoDO == null ? null : merchantInfoDO.getSettlementCurrency();
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
        if (requestDTO.getTransactionInfo() != null) {
            clientRequestDTO.setCallbackUrl(requestDTO.getTransactionInfo().getCallbackUrl());
        }
        clientRequestDTO.setRequestFingerprint(keyMaterialFactory.fingerprint(encryptedData));
        clientRequestDTO.setOpenApiRequestPath(resolveRequestPath());
        clientRequestDTO.setOpenApiRequestTime(LocalDateTime.now());
        clientRequestDTO.setMerchantRequestCipherMasked(maskCipher(encryptedData));
        clientRequestDTO.setMerchantRequestPlainJsonMasked(SensitiveDataMaskUtils.maskJson(JsonUtils.toJsonString(requestDTO)));
        clientRequestDTO.setSubMerchantInfo(converter.toPaymentClientSubMerchantInfo(
                requestDTO.getMerchantInfo() == null ? null : requestDTO.getMerchantInfo().getSubMerchantInfo()));
        clientRequestDTO.setBillingCardHolderInfo(converter.toPaymentClientBillingCardHolderInfo(requestDTO.getBillingCardHolderInfo()));
        clientRequestDTO.setCardInfo(converter.toPaymentClientCardInfo(requestDTO.getCardInfo()));
        clientRequestDTO.setThreeDsInfo(converter.toPaymentClientThreeDsInfo(requestDTO.getThreeDsInfo()));
        clientRequestDTO.setTransactionInfo(converter.toPaymentClientTransactionInfo(requestDTO.getTransactionInfo()));
        populateRequestSource(clientRequestDTO);
        return clientRequestDTO;
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
