package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.config.PaymentClientProperties;
import com.scott.payment.openapi.converter.OpenApiRequestConverter;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.enums.OpenApiPaymentOperationEnum;
import com.scott.payment.openapi.enums.OpenApiPaymentStatusEnum;
import com.scott.payment.openapi.service.PaymentService;
import com.scott.payment.openapi.support.OpenApiRequestContext;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
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
     * 创建开放接口收单支付服务实现。
     *
     * @param converter               OpenAPI 请求转换器
     * @param paymentInternalClient   service-payment 内部调用客户端
     * @param paymentClientProperties 支付内部调用配置
     * @param keyMaterialFactory      OpenAPI 密钥材料工具
     * @param requestContext          OpenAPI 请求上下文访问器
     * @param isoDictionaryService    ISO 币种字典服务
     */
    public PaymentServiceImpl(OpenApiRequestConverter converter,
                              PaymentInternalClient paymentInternalClient,
                              PaymentClientProperties paymentClientProperties,
                              OpenApiKeyMaterialFactory keyMaterialFactory,
                              OpenApiRequestContext requestContext,
                              IsoDictionaryService isoDictionaryService) {
        this.converter = converter;
        this.paymentInternalClient = paymentInternalClient;
        this.paymentClientProperties = paymentClientProperties;
        this.keyMaterialFactory = keyMaterialFactory;
        this.requestContext = requestContext;
        this.isoDictionaryService = isoDictionaryService;
    }

    @Override
    public PaymentCreateVO createPayment(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        return submitTransaction(encryptedData, requestDTO, OpenApiPaymentOperationEnum.PAYMENT);
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
            return createLocalPaymentResult(requestDTO, operation);
        }
        PaymentCreateClientRequestDTO clientRequestDTO = toPaymentClientRequest(encryptedData, requestDTO, operation);
        PaymentCreateClientResponseDTO clientResponseDTO = submitToPayment(clientRequestDTO, operation);
        return toPaymentCreateVO(clientResponseDTO);
    }

    /**
     * 查询收单交易状态。
     *
     * @param encryptedData 商户原始密文，仅用于生成安全指纹
     * @param requestDTO 解密后的查询请求参数
     * @return 交易查询响应
     */
    @Override
    public PaymentCreateVO queryTransaction(String encryptedData, ApiMerchantPaymentRequestDTO requestDTO) {
        if (!paymentClientProperties.isRemoteEnabled()) {
            return createLocalPaymentResult(requestDTO, OpenApiPaymentOperationEnum.QUERY);
        }
        PaymentCreateClientRequestDTO clientRequestDTO = toPaymentClientRequest(encryptedData, requestDTO, OpenApiPaymentOperationEnum.QUERY);
        return toPaymentCreateVO(paymentInternalClient.query(clientRequestDTO));
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
        transactionInfoVO.setCardBrand(requestDTO.getTransactionInfo() == null ? null : requestDTO.getTransactionInfo().getCardBrand());
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
     * 转换 service-payment 内部响应为商户 OpenAPI 响应。
     *
     * @param clientResponseDTO 支付内部创建响应
     * @return OpenAPI 创建响应
     */
    private PaymentCreateVO toPaymentCreateVO(PaymentCreateClientResponseDTO clientResponseDTO) {
        return converter.toPaymentCreateVO(clientResponseDTO);
    }
}
