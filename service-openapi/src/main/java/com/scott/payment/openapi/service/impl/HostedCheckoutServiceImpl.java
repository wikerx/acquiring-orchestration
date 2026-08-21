package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.checkout.PaymentCheckoutClientDTOs;
import com.scott.payment.openapi.config.HostedCheckoutProperties;
import com.scott.payment.openapi.dto.body.HostedCheckoutBrowserRequestDTOs;
import com.scott.payment.openapi.dto.body.HostedCheckoutSessionCreateRequestDTO;
import com.scott.payment.openapi.service.OpenApiSystemConfigService;
import com.scott.payment.openapi.service.HostedCheckoutService;
import com.scott.payment.openapi.support.HostedCheckoutTokenSupport;
import com.scott.payment.openapi.support.OpenApiRequestAttributes;
import com.scott.payment.openapi.support.OpenApiRequestContext;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutPaymentResultVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionCreateVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutSessionVO;
import com.scott.payment.openapi.vo.checkout.HostedCheckoutCardBinVO;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Hosted Checkout 开放接口默认实现。
 */
@Slf4j
@Service
public class HostedCheckoutServiceImpl implements HostedCheckoutService {

    /**
     * 当前 Hosted Checkout 支持的银行卡支付方式编码。
     */
    private static final String PAYMENT_METHOD_BANK_CARD = "BANK_CARD";

    /**
     * 代理链客户端 IP 请求头。
     */
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * 反向代理真实客户端 IP 请求头。
     */
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    /**
     * 浏览器请求来源头。
     */
    private static final String HEADER_ORIGIN = "Origin";

    /**
     * 浏览器引用页请求头。
     */
    private static final String HEADER_REFERER = "Referer";

    /**
     * 浏览器 User-Agent 请求头。
     */
    private static final String HEADER_USER_AGENT = "User-Agent";

    /** 浏览器声明可接收内容类型的请求头，MPGS PAYER_BROWSER 认证要求提供。 */
    private static final String HEADER_ACCEPT = "Accept";

    /**
     * 平台收银台前端基础地址的系统参数键。
     */
    private static final String CHECKOUT_FRONTEND_BASE_URL_CONFIG_KEY = "platform.checkout.frontend-base-url";

    /**
     * 内部无时区时间转换为对外时间时使用的平台默认时区。
     */
    private static final ZoneId DEFAULT_RESPONSE_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * service-payment 内部客户端；支付状态和幂等事实均由支付核心持久化。
     */
    private final PaymentInternalClient paymentInternalClient;

    /**
     * Hosted Checkout 会话有效期等运行参数。
     */
    private final HostedCheckoutProperties properties;

    /**
     * 平台系统参数读取服务，用于获取受控收银台前端地址。
     */
    private final OpenApiSystemConfigService systemConfigService;

    /**
     * 当前 OpenAPI 商户身份上下文。
     */
    private final OpenApiRequestContext requestContext;

    /**
     * 密钥材料工具，仅用于密文和令牌指纹计算，不输出原始敏感值。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    /**
     * ISO 币种字典服务，用于确定金额小数位数。
     */
    private final IsoDictionaryService isoDictionaryService;

    /**
     * 创建 Hosted Checkout 服务。
     *
     * @param paymentInternalClient service-payment 内部客户端
     * @param properties            Hosted Checkout 运行参数
     * @param systemConfigService   平台系统参数读取服务
     * @param requestContext        OpenAPI 商户身份上下文
     * @param keyMaterialFactory    密文与令牌指纹工具
     * @param isoDictionaryService  ISO 币种字典服务
     */
    public HostedCheckoutServiceImpl(PaymentInternalClient paymentInternalClient,
                                     HostedCheckoutProperties properties,
                                     OpenApiSystemConfigService systemConfigService,
                                     OpenApiRequestContext requestContext,
                                     OpenApiKeyMaterialFactory keyMaterialFactory,
                                     IsoDictionaryService isoDictionaryService) {
        this.paymentInternalClient = paymentInternalClient;
        this.properties = properties;
        this.systemConfigService = systemConfigService;
        this.requestContext = requestContext;
        this.keyMaterialFactory = keyMaterialFactory;
        this.isoDictionaryService = isoDictionaryService;
    }

    /**
     * 创建 Hosted Checkout 会话快照。
     *
     * <p>先校验请求商户与已验签上下文的绑定关系，再将金额、币种精度、展示信息和请求指纹
     * 交由支付核心持久化；Redis 不作为会话或幂等结果的唯一事实来源。</p>
     *
     * @param encryptedData 商户原始密文，仅用于计算请求指纹
     * @param requestDTO    解密并校验后的会话创建请求
     * @return 会话标识、过期时间及付款人访问地址
     */
    @Override
    public HostedCheckoutSessionCreateVO createSession(String encryptedData,
                                                       HostedCheckoutSessionCreateRequestDTO requestDTO) {
        long startNanos = System.nanoTime();
        validateMerchantBinding(requestDTO);
        validatePayerIpAddress(requestDTO);
        validateCreateRequestAmounts(requestDTO);
        PaymentCheckoutClientDTOs.SessionCreateRequest clientRequest = toClientCreateRequest(encryptedData, requestDTO);
        log.info("event: OPENAPI_CHECKOUT_CREATE_START stage=OPENAPI_SERVICE traceId: {} merchantId: {} merchantOrderNo: {} merchantRequestId: {} amount: {} currency: {} checkoutDomain: {} requestFingerprint: {} payerCountry: {}",
                TraceContext.getTraceId(),
                clientRequest.getMerchantId(),
                clientRequest.getMerchantOrderNo(),
                clientRequest.getMerchantRequestId(),
                clientRequest.getAmount(),
                clientRequest.getCurrency(),
                clientRequest.getCheckoutDomain(),
                clientRequest.getRequestFingerprint(),
                clientRequest.getPayerCountry());
        PaymentCheckoutClientDTOs.SessionCreateResponse clientResponse =
                paymentInternalClient.createCheckoutSession(clientRequest);
        HostedCheckoutSessionCreateVO responseVO = toCreateVO(requestDTO, clientResponse);
        log.info("event: OPENAPI_CHECKOUT_CREATE_END stage=OPENAPI_SERVICE traceId: {} merchantId: {} checkoutSessionId: {} checkoutStatus: {} idempotentHit: {} durationMs: {}",
                TraceContext.getTraceId(),
                clientRequest.getMerchantId(),
                clientResponse.getCheckoutSessionId(),
                clientResponse.getCheckoutStatus(),
                clientResponse.getIdempotentHit(),
                elapsedMillis(startNanos));
        return responseVO;
    }

    /**
     * 使用不透明令牌摘要查询收银台会话展示快照。
     *
     * @param requestDTO 浏览器会话查询请求
     * @return 支付核心返回的会话展示快照
     */
    @Override
    public HostedCheckoutSessionVO querySession(HostedCheckoutBrowserRequestDTOs.SessionQueryRequest requestDTO) {
        PaymentCheckoutClientDTOs.SessionQueryRequest clientRequest = new PaymentCheckoutClientDTOs.SessionQueryRequest();
        clientRequest.setTokenHash(tokenHash(requestDTO.getOpaqueToken()));
        clientRequest.setCover(requestDTO.getCover());
        fillBrowserSecurity(clientRequest, requestDTO.getClientContext());
        PaymentCheckoutClientDTOs.SessionQueryResponse response = paymentInternalClient.queryCheckoutSession(clientRequest);
        return toSessionVO(response);
    }

    /**
     * 提交一次 Hosted Checkout 付款尝试。
     *
     * <p>OpenAPI 只转发浏览器密文信封，不接触 PAN、CVV 或有效期明文；
     * 尝试幂等、nonce 消费、解密和交易状态流转由支付核心负责。</p>
     *
     * @param requestDTO 浏览器支付提交请求
     * @return 当前支付结果、处理中状态或 3DS 动作
     */
    @Override
    public HostedCheckoutPaymentResultVO submitPayment(HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest requestDTO) {
        PaymentCheckoutClientDTOs.PaymentSubmitRequest clientRequest = new PaymentCheckoutClientDTOs.PaymentSubmitRequest();
        clientRequest.setTokenHash(tokenHash(requestDTO.getOpaqueToken()));
        clientRequest.setCheckoutSessionId(requestDTO.getCheckoutSessionId());
        clientRequest.setAttemptRequestId(requestDTO.getAttemptRequestId());
        clientRequest.setPaymentMethod(normalizePaymentMethod(requestDTO.getPaymentMethod()));
        clientRequest.setRequestFingerprint(requestFingerprintWithoutRawCard(requestDTO));
        clientRequest.setTraceId(TraceContext.getTraceId());
        fillBrowserSecurity(clientRequest, requestDTO.getClientContext());
        clientRequest.setBrowserInfoJson(browserInfoJson(requestDTO.getClientContext()));
        clientRequest.setDeviceInfoJson(deviceInfoJson(requestDTO.getClientContext()));
        clientRequest.setCardDataEnvelope(toClientCardDataEnvelope(requestDTO.getCardDataEnvelope()));
        clientRequest.setBillingCardHolderInfo(toClientBillingInfo(requestDTO.getBillingCardHolderInfo()));
        PaymentCheckoutClientDTOs.PaymentResultResponse response = paymentInternalClient.submitCheckoutPayment(clientRequest);
        return toPaymentResultVO(response);
    }

    /**
     * 查询指定会话及支付尝试的当前状态。
     *
     * @param requestDTO 浏览器支付状态查询请求
     * @return 支付核心数据库事实对应的当前状态
     */
    @Override
    public HostedCheckoutPaymentResultVO queryPaymentStatus(
            HostedCheckoutBrowserRequestDTOs.PaymentStatusRequest requestDTO) {
        PaymentCheckoutClientDTOs.PaymentStatusRequest clientRequest = new PaymentCheckoutClientDTOs.PaymentStatusRequest();
        clientRequest.setTokenHash(tokenHash(requestDTO.getOpaqueToken()));
        clientRequest.setCheckoutSessionId(requestDTO.getCheckoutSessionId());
        clientRequest.setCheckoutAttemptId(requestDTO.getCheckoutAttemptId());
        clientRequest.setTraceId(TraceContext.getTraceId());
        fillBrowserSecurity(clientRequest, requestDTO.getClientContext());
        PaymentCheckoutClientDTOs.PaymentResultResponse response =
                paymentInternalClient.queryCheckoutPaymentStatus(clientRequest);
        return toPaymentResultVO(response);
    }

    /**
     * 将 3DS 回跳提交给支付核心继续处理。
     *
     * <p>一次性回跳令牌仅传递摘要，认证数据先脱敏后进入内部请求；本层不根据浏览器回跳
     * 直接覆盖支付终态。</p>
     *
     * @param requestDTO 浏览器 3DS 回跳请求
     * @return 支付核心处理后的当前支付状态
     */
    @Override
    public HostedCheckoutPaymentResultVO handleThreeDsReturn(
            HostedCheckoutBrowserRequestDTOs.ThreeDsReturnRequest requestDTO) {
        PaymentCheckoutClientDTOs.ThreeDsReturnRequest clientRequest = new PaymentCheckoutClientDTOs.ThreeDsReturnRequest();
        clientRequest.setThreeDsReturnTokenHash(tokenHash(requestDTO.getThreeDsReturnToken()));
        clientRequest.setCheckoutSessionId(requestDTO.getCheckoutSessionId());
        clientRequest.setCheckoutAttemptId(requestDTO.getCheckoutAttemptId());
        clientRequest.setAuthenticationDataJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(requestDTO.getAuthenticationData()));
        clientRequest.setCardDataEnvelope(toClientCardDataEnvelope(requestDTO.getCardDataEnvelope()));
        clientRequest.setBillingCardHolderInfo(toClientBillingInfo(requestDTO.getBillingCardHolderInfo()));
        clientRequest.setBrowserInfoJson(browserInfoJson(requestDTO.getClientContext()));
        clientRequest.setTraceId(TraceContext.getTraceId());
        fillBrowserSecurity(clientRequest, requestDTO.getClientContext());
        PaymentCheckoutClientDTOs.PaymentResultResponse response =
                paymentInternalClient.handleCheckoutThreeDsReturn(clientRequest);
        return toPaymentResultVO(response);
    }

    /** 卡 BIN 查询只传递前缀和令牌摘要，不接收或记录完整 PAN。 */
    @Override
    public HostedCheckoutCardBinVO resolveCardBin(HostedCheckoutBrowserRequestDTOs.CardBinRequest requestDTO) {
        PaymentCheckoutClientDTOs.CardBinRequest clientRequest = new PaymentCheckoutClientDTOs.CardBinRequest();
        clientRequest.setTokenHash(tokenHash(requestDTO.getOpaqueToken()));
        clientRequest.setCheckoutSessionId(requestDTO.getCheckoutSessionId());
        clientRequest.setCardBin(requestDTO.getCardBin());
        clientRequest.setTraceId(TraceContext.getTraceId());
        PaymentCheckoutClientDTOs.CardBinResponse response = paymentInternalClient.resolveCheckoutCardBin(clientRequest);
        HostedCheckoutCardBinVO result = new HostedCheckoutCardBinVO();
        result.setCardBrand(response.getCardBrand());
        result.setRecognized(response.getRecognized());
        result.setSupported(response.getSupported());
        return result;
    }

    /**
     * 将商户创建会话请求转换为 payment 内部会话快照，收银台前端域名只取平台参数表配置。
     */
    private PaymentCheckoutClientDTOs.SessionCreateRequest toClientCreateRequest(
            String encryptedData,
            HostedCheckoutSessionCreateRequestDTO requestDTO) {
        HostedCheckoutSessionCreateRequestDTO.OrderInfoDTO orderInfo = requestDTO.getOrderInfo();
        HostedCheckoutSessionCreateRequestDTO.TransactionInfoDTO transactionInfo = requestDTO.getTransactionInfo();
        PaymentCheckoutClientDTOs.SessionCreateRequest target = new PaymentCheckoutClientDTOs.SessionCreateRequest();
        target.setMerchantId(requestContext.getRequiredMerchantId());
        target.setMerchantOrderNo(orderInfo.getOrderNo());
        target.setMerchantRequestId(orderInfo.getOrderId());
        target.setRequestFingerprint(keyMaterialFactory.fingerprint(encryptedData));
        target.setAmount(orderInfo.getAmount());
        target.setCurrency(normalizeCurrency(orderInfo.getCurrency()));
        target.setCurrencyExponent(resolveCurrencyExponent(orderInfo.getCurrency()));
        target.setPaymentAction("PAYMENT");
        target.setOrderSubject(null);
        target.setOrderDescription(transactionInfo == null ? null : transactionInfo.getDescription());
        target.setOrderItemsJson(requestDTO.getGoodsInfo() == null
                ? null : JsonUtils.toJsonString(requestDTO.getGoodsInfo()));
        target.setAllowedPaymentMethods(List.of());
        target.setCheckoutDomain(resolveCheckoutFrontendBaseUrl());
        target.setLocale(transactionInfo == null ? null : transactionInfo.getLanguage());
        target.setMerchantDisplayName(resolveMerchantDisplayName(requestDTO));
        target.setMerchantLogoUrl(null);
        String callbackUrl = transactionInfo == null ? null : transactionInfo.getCallbackUrl();
        target.setMerchantNotifyUrl(callbackUrl);
        target.setSubMerchantInfoJson(toJson(requestDTO.getMerchantInfo().getSubMerchantInfo()));
        target.setPayerInfoJson(toJson(requestDTO.getPayerInfo()));
        target.setBillingInfoJson(toJson(requestDTO.getBillingCardHolderInfo()));
        target.setShippingInfoJson(requestDTO.getShippingInfo() == null
                ? null : JsonUtils.toJsonString(requestDTO.getShippingInfo()));
        String redirectUrl = transactionInfo == null ? null : transactionInfo.getRedirectUrl();
        target.setRedirectUrl(redirectUrl);
        target.setPayerCountry(requestDTO.getPayerInfo() == null ? null : requestDTO.getPayerInfo().getCountry());
        target.setPayerEmail(requestDTO.getPayerInfo() == null ? null : requestDTO.getPayerInfo().getEmail());
        target.setPayerEmailHash(requestDTO.getPayerInfo() == null ? null
                : sha256Hex(requestDTO.getPayerInfo().getEmail()));
        target.setRetryAllowed(1);
        target.setExpireTime(LocalDateTime.now().plusMinutes(properties.getDefaultExpireMinutes()));
        target.setRequestSource(requestSourceSummary());
        target.setTraceId(TraceContext.getTraceId());
        return target;
    }

    /** 将允许明文展示的收银台预填对象固化为 JSON。 */
    private String toJson(Object source) {
        return source == null ? null : JsonUtils.toJsonString(source);
    }

    /** 原样转换浏览器卡数据密文信封，OpenAPI 不持有对应私钥。 */
    private PaymentCheckoutClientDTOs.CardDataEnvelope toClientCardDataEnvelope(
            HostedCheckoutBrowserRequestDTOs.CardDataEnvelopeDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCheckoutClientDTOs.CardDataEnvelope target = new PaymentCheckoutClientDTOs.CardDataEnvelope();
        target.setAlgorithm(source.getAlgorithm());
        target.setKeyId(source.getKeyId());
        target.setEncryptedKey(source.getEncryptedKey());
        target.setIv(source.getIv());
        target.setCiphertext(source.getCiphertext());
        target.setNonce(source.getNonce());
        return target;
    }

    /**
     * 转换账单持卡人信息，供 MPGS 3DS 和后续授权请求使用。
     */
    private PaymentCheckoutClientDTOs.BillingCardHolderInfo toClientBillingInfo(
            HostedCheckoutBrowserRequestDTOs.BillingCardHolderInfoDTO source) {
        if (source == null) {
            return null;
        }
        PaymentCheckoutClientDTOs.BillingCardHolderInfo target = new PaymentCheckoutClientDTOs.BillingCardHolderInfo();
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        return target;
    }

    /**
     * 组装商户创建响应，时间格式统一转换为 OpenAPI 既有 OffsetDateTime 风格。
     */
    private HostedCheckoutSessionCreateVO toCreateVO(HostedCheckoutSessionCreateRequestDTO requestDTO,
                                                     PaymentCheckoutClientDTOs.SessionCreateResponse response) {
        HostedCheckoutSessionCreateVO vo = new HostedCheckoutSessionCreateVO();
        vo.setMerchantInfo(copySnapshot(requestDTO.getMerchantInfo(), PaymentCreateVO.MerchantInfoVO.class));
        vo.setOrderInfo(copySnapshot(requestDTO.getOrderInfo(), PaymentCreateVO.OrderInfoVO.class));
        vo.setGoodsInfo(copySnapshotList(requestDTO.getGoodsInfo(), PaymentCreateVO.GoodsInfoVO.class));
        vo.setBillingCardHolderInfo(copySnapshot(
                requestDTO.getBillingCardHolderInfo(), PaymentCreateVO.BillingCardHolderInfoVO.class));
        vo.setPayerInfo(copySnapshot(requestDTO.getPayerInfo(), PaymentCreateVO.PayerInfoVO.class));
        vo.setShippingInfo(copySnapshot(requestDTO.getShippingInfo(), PaymentCreateVO.ShippingInfoVO.class));
        vo.setTransactionInfo(copySnapshot(
                requestDTO.getTransactionInfo(), HostedCheckoutSessionCreateVO.TransactionInfoVO.class));
        vo.setCheckoutUrl(response.getCheckoutUrl());
        return vo;
    }

    /** 通过 JSON 结构转换只回显外部 VO 明确定义的字段。 */
    private <T> T copySnapshot(Object source, Class<T> targetType) {
        return source == null ? null : JsonUtils.parseObject(JsonUtils.toJsonString(source), targetType);
    }

    /** 转换可选快照集合；未提供时保持 null 以满足条件返回契约。 */
    private <T> List<T> copySnapshotList(List<?> source, Class<T> targetType) {
        return source == null ? null : source.stream()
                .map(item -> copySnapshot(item, targetType))
                .toList();
    }

    /**
     * 组装收银台首屏模型，非法访问时下游可能只返回拦截页面状态。
     */
    private HostedCheckoutSessionVO toSessionVO(PaymentCheckoutClientDTOs.SessionQueryResponse response) {
        HostedCheckoutSessionVO vo = new HostedCheckoutSessionVO();
        vo.setCheckoutSessionId(response.getCheckoutSessionId());
        vo.setPageState(response.getPageState());
        vo.setMerchant(toMerchantVO(response.getMerchant()));
        vo.setOrder(toOrderVO(response.getOrder()));
        vo.setPaymentMethods(response.getPaymentMethods() == null ? List.of() : response.getPaymentMethods().stream()
                .map(this::toPaymentMethodVO)
                .toList());
        vo.setCheckout(toCheckoutVO(response.getCheckout()));
        vo.setPayerInfo(toPayerInfoVO(response.getPayerInfo()));
        vo.setBillingInfo(toBillingInfoVO(response.getBillingInfo()));
        vo.setPaymentResult(response.getPaymentResult() == null ? null : toPaymentResultVO(response.getPaymentResult()));
        vo.setCardEncryption(toCardEncryptionVO(response.getCardEncryption()));
        return vo;
    }

    private HostedCheckoutSessionVO.CardEncryptionVO toCardEncryptionVO(
            PaymentCheckoutClientDTOs.CardEncryption source) {
        if (source == null) {
            return null;
        }
        HostedCheckoutSessionVO.CardEncryptionVO target = new HostedCheckoutSessionVO.CardEncryptionVO();
        target.setAlgorithm(source.getAlgorithm());
        target.setKeyId(source.getKeyId());
        target.setPublicKey(source.getPublicKey());
        target.setNonce(source.getNonce());
        return target;
    }

    private HostedCheckoutSessionVO.PayerInfoVO toPayerInfoVO(PaymentCheckoutClientDTOs.PayerInfo source) {
        if (source == null) {
            return null;
        }
        HostedCheckoutSessionVO.PayerInfoVO target = new HostedCheckoutSessionVO.PayerInfoVO();
        target.setPayerId(source.getPayerId());
        target.setEmail(source.getEmail());
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setPhone(source.getPhone());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        return target;
    }

    private HostedCheckoutSessionVO.BillingInfoVO toBillingInfoVO(PaymentCheckoutClientDTOs.BillingInfo source) {
        if (source == null) {
            return null;
        }
        HostedCheckoutSessionVO.BillingInfoVO target = new HostedCheckoutSessionVO.BillingInfoVO();
        target.setFirstName(source.getFirstName());
        target.setLastName(source.getLastName());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setCountry(source.getCountry());
        target.setState(source.getState());
        target.setCity(source.getCity());
        target.setStreet(source.getStreet());
        target.setPostal(source.getPostal());
        return target;
    }

    /**
     * 组装付款结果模型，前端根据 pageState 决定展示结果页、处理中页或 3DS bridge。
     */
    private HostedCheckoutPaymentResultVO toPaymentResultVO(PaymentCheckoutClientDTOs.PaymentResultResponse response) {
        HostedCheckoutPaymentResultVO vo = new HostedCheckoutPaymentResultVO();
        vo.setCheckoutSessionId(response.getCheckoutSessionId());
        vo.setCheckoutAttemptId(response.getCheckoutAttemptId());
        vo.setPageState(response.getPageState());
        vo.setResult(toPaymentResultVO(response.getResult()));
        vo.setThreeDsAction(toThreeDsActionVO(response.getThreeDsAction()));
        vo.setFailure(toFailureVO(response.getFailure()));
        vo.setPolling(toPollingVO(response.getPolling()));
        vo.setActions(toActionVO(response.getActions()));
        return vo;
    }

    /**
     * 转换商户展示信息；非法 token 场景允许为空，避免泄露商户主体名称。
     */
    private HostedCheckoutSessionVO.MerchantVO toMerchantVO(PaymentCheckoutClientDTOs.Merchant source) {
        if (source == null) {
            return null;
        }
        HostedCheckoutSessionVO.MerchantVO target = new HostedCheckoutSessionVO.MerchantVO();
        target.setDisplayName(source.getDisplayName());
        target.setLogoUrl(source.getLogoUrl());
        return target;
    }

    /**
     * 转换订单展示快照，金额和币种沿用创建会话时 payment 已落库的数据。
     */
    private HostedCheckoutSessionVO.OrderVO toOrderVO(PaymentCheckoutClientDTOs.Order source) {
        if (source == null) {
            return null;
        }
        HostedCheckoutSessionVO.OrderVO target = new HostedCheckoutSessionVO.OrderVO();
        target.setOrderNo(source.getOrderNo());
        target.setSubject(source.getSubject());
        target.setDescription(source.getDescription());
        target.setAmount(source.getAmount());
        target.setCurrency(source.getCurrency());
        target.setCurrencyExponent(source.getCurrencyExponent());
        target.setItemsJson(source.getItemsJson());
        return target;
    }

    /**
     * 转换前端可展示的支付方式，卡组织和 3DS 模式来自会话快照。
     */
    private HostedCheckoutSessionVO.PaymentMethodVO toPaymentMethodVO(PaymentCheckoutClientDTOs.PaymentMethod source) {
        HostedCheckoutSessionVO.PaymentMethodVO target = new HostedCheckoutSessionVO.PaymentMethodVO();
        target.setPaymentMethod(source.getPaymentMethod());
        target.setChannelCode(source.getChannelCode());
        target.setBrands(source.getBrands());
        target.setThreeDsMode(source.getThreeDsMode());
        return target;
    }

    /**
     * 转换收银台会话控制信息，包括过期时间、重试开关和轮询间隔。
     */
    private HostedCheckoutSessionVO.CheckoutVO toCheckoutVO(PaymentCheckoutClientDTOs.Checkout source) {
        if (source == null) {
            return null;
        }
        HostedCheckoutSessionVO.CheckoutVO target = new HostedCheckoutSessionVO.CheckoutVO();
        target.setExpireTime(toOffsetDateTime(source.getExpireTime()));
        target.setServerTime(toOffsetDateTime(source.getServerTime()));
        target.setRetryAllowed(source.getRetryAllowed());
        target.setPollingIntervalSeconds(source.getPollingIntervalSeconds());
        return target;
    }

    /**
     * 转换结果页付款摘要，卡号仅透出 payment 层生成的掩码值。
     */
    private HostedCheckoutPaymentResultVO.PaymentResultVO toPaymentResultVO(PaymentCheckoutClientDTOs.PaymentResult source) {
        if (source == null) {
            return null;
        }
        HostedCheckoutPaymentResultVO.PaymentResultVO target = new HostedCheckoutPaymentResultVO.PaymentResultVO();
        target.setAmount(source.getAmount());
        target.setCurrency(source.getCurrency());
        target.setMerchantOrderNo(source.getMerchantOrderNo());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setCardBrand(source.getCardBrand());
        target.setCardNumberMasked(source.getCardNumberMasked());
        target.setTransactionId(source.getTransactionId());
        target.setTransactionDateTime(toOffsetDateTime(source.getTransactionDateTime()));
        target.setAuthCode(source.getAuthCode());
        return target;
    }

    /**
     * 转换 3DS 下一步动作，HTML 内容交给自有收银台页面渲染。
     */
    private HostedCheckoutPaymentResultVO.ThreeDsActionVO toThreeDsActionVO(PaymentCheckoutClientDTOs.ThreeDsAction source) {
        if (source == null) {
            return null;
        }
        HostedCheckoutPaymentResultVO.ThreeDsActionVO target = new HostedCheckoutPaymentResultVO.ThreeDsActionVO();
        target.setActionType(source.getActionType());
        target.setPhase(source.getPhase());
        target.setHtml(source.getHtml());
        target.setReturnUrl(source.getReturnUrl());
        target.setTimeoutSeconds(source.getTimeoutSeconds());
        target.setCardEncryption(toCardEncryptionVO(source.getCardEncryption()));
        return target;
    }

    /**
     * 转换失败展示信息，重试标记由 payment 状态机计算。
     */
    private HostedCheckoutPaymentResultVO.FailureVO toFailureVO(PaymentCheckoutClientDTOs.Failure source) {
        if (source == null) {
            return null;
        }
        HostedCheckoutPaymentResultVO.FailureVO target = new HostedCheckoutPaymentResultVO.FailureVO();
        target.setReasonCode(source.getReasonCode());
        target.setMessage(source.getMessage());
        target.setRetryAllowed(source.getRetryAllowed());
        return target;
    }

    /**
     * 转换处理中轮询配置，OpenAPI 只透传建议间隔，不主动推进订单状态。
     */
    private HostedCheckoutPaymentResultVO.PollingVO toPollingVO(PaymentCheckoutClientDTOs.Polling source) {
        if (source == null) {
            return null;
        }
        HostedCheckoutPaymentResultVO.PollingVO target = new HostedCheckoutPaymentResultVO.PollingVO();
        target.setStatusUrl(source.getStatusUrl());
        target.setIntervalSeconds(source.getIntervalSeconds());
        target.setMaxIntervalSeconds(source.getMaxIntervalSeconds());
        return target;
    }

    /** 转换终态浏览器 Form POST 动作，不把 redirectUrl 当作服务端通知地址。 */
    private HostedCheckoutPaymentResultVO.ActionVO toActionVO(PaymentCheckoutClientDTOs.Action source) {
        if (source == null) {
            return null;
        }
        HostedCheckoutPaymentResultVO.ActionVO target = new HostedCheckoutPaymentResultVO.ActionVO();
        target.setMethod(source.getMethod());
        target.setRedirectUrl(source.getRedirectUrl());
        target.setDelaySeconds(source.getDelaySeconds());
        if (source.getFormFields() != null) {
            HostedCheckoutPaymentResultVO.FormFieldsVO form = new HostedCheckoutPaymentResultVO.FormFieldsVO();
            form.setMerchantId(source.getFormFields().getMerchantId());
            form.setOrderNo(source.getFormFields().getOrderNo());
            form.setOrderId(source.getFormFields().getOrderId());
            form.setTransactionId(source.getFormFields().getTransactionId());
            form.setTransactionType(source.getFormFields().getTransactionType());
            form.setTransactionStatus(source.getFormFields().getTransactionStatus());
            form.setTransactionDateTime(toOffsetDateTime(source.getFormFields().getTransactionDateTime()));
            form.setCode(source.getFormFields().getCode());
            form.setMessage(source.getFormFields().getMessage());
            target.setFormFields(form);
        }
        return target;
    }

    /**
     * 校验请求体 merchantId 必须等于已鉴权商户，防止一个商户代创建另一个商户收银台。
     */
    private void validateMerchantBinding(HostedCheckoutSessionCreateRequestDTO requestDTO) {
        String contextMerchantId = requestContext.getRequiredMerchantId();
        String requestMerchantId = requestDTO.getMerchantInfo() == null ? null : requestDTO.getMerchantInfo().getMerchantId();
        if (!contextMerchantId.equals(requestMerchantId)) {
            throw new ApiException(ApiResultEnum.MERCHANT_INVALID, "merchantInfo.merchantId does not match authorization");
        }
    }

    /** payerInfo.ipAddress 必须是单个规范 IPv4/IPv6 字面量，禁止接受代理链或附加文本。 */
    private void validatePayerIpAddress(HostedCheckoutSessionCreateRequestDTO requestDTO) {
        String payerIp = requestDTO.getPayerInfo() == null ? null : requestDTO.getPayerInfo().getIpAddress();
        if (!StringUtils.hasText(payerIp)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "payerInfo.ipAddress is required");
        }
        try {
            IpAddressNormalizer.normalizeExact(payerIp);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID,
                    "payerInfo.ipAddress must be a valid IPv4 or IPv6 literal");
        }
    }

    /** 校验金额只保留最多两位有效小数，并继续服从币种自身辅币位规则。 */
    private void validateCreateRequestAmounts(HostedCheckoutSessionCreateRequestDTO requestDTO) {
        HostedCheckoutSessionCreateRequestDTO.OrderInfoDTO orderInfo = requestDTO.getOrderInfo();
        validateAmount(orderInfo.getAmount(), orderInfo.getCurrency(), "orderInfo.amount");
        if (requestDTO.getGoodsInfo() == null) {
            return;
        }
        for (com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO.GoodsInfoDTO goods
                : requestDTO.getGoodsInfo()) {
            if (goods == null) {
                continue;
            }
            if (!Objects.equals(normalizeCurrency(orderInfo.getCurrency()), normalizeCurrency(goods.getCurrency()))) {
                throw new ApiException(ApiResultEnum.PARAM_INVALID, "goodsInfo.currency must match orderInfo.currency");
            }
            validateAmount(goods.getAmount(), goods.getCurrency(), "goodsInfo.amount");
        }
    }

    /** 拒绝非正金额、第三位非零小数和超过 ISO 币种辅币位的金额。 */
    private void validateAmount(java.math.BigDecimal amount, String currency, String field) {
        if (amount == null) {
            return;
        }
        if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID,
                    field + " fraction digits exceed supported precision");
        }
        if (StringUtils.hasText(currency) && !isoDictionaryService.isCurrencyFractionValid(amount, currency)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID,
                    field + " fraction digits exceed currency minor unit");
        }
    }

    /**
     * 将浏览器 URL 中的原始 token 转为 HMAC 摘要，内部服务和数据库都不接收 raw token。
     */
    private String tokenHash(String opaqueToken) {
        return HostedCheckoutTokenSupport.hmacSha256Hex(opaqueToken, properties.getTokenPepper());
    }

    /**
     * 规范化币种代码，保持 OpenAPI 到 payment 的 ISO 4217 大写格式。
     */
    private String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 规范化付款方式，当前收银台默认走银行卡支付。
     */
    private String normalizePaymentMethod(String paymentMethod) {
        String normalized = paymentMethod == null ? null : paymentMethod.trim().toUpperCase(Locale.ROOT);
        return StringUtils.hasText(normalized) ? normalized : PAYMENT_METHOD_BANK_CARD;
    }

    /**
     * 规范化可选渠道编码；未指定时保持为空，由支付服务根据商户路由选择。
     */
    private String normalizeChannelCode(String channelCode) {
        String normalized = channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    /**
     * 从 ISO 字典解析币种小数位，无法识别时按两位小数展示。
     */
    private Integer resolveCurrencyExponent(String currency) {
        return isoDictionaryService.getCurrency(currency)
                .map(IsoCurrencyInfo::defaultFractionDigits)
                .filter(exponent -> exponent >= 0)
                .orElse(2);
    }

    /**
     * 按平台上限计算会话过期时间，商户不能通过请求放大有效期。
     */
    private LocalDateTime resolveExpireTime(Integer expireMinutes) {
        int minutes = expireMinutes == null || expireMinutes <= 0
                ? properties.getDefaultExpireMinutes()
                : Math.min(expireMinutes, properties.getMaxExpireMinutes());
        return LocalDateTime.now().plusMinutes(minutes);
    }

    /**
     * 将内部 LocalDateTime 转为 OpenAPI 统一响应时区的 OffsetDateTime。
     */
    private OffsetDateTime toOffsetDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(DEFAULT_RESPONSE_ZONE).toOffsetDateTime();
    }

    /**
     * 从参数设置表读取平台收银台前端域名，商户请求中的 checkoutDomain 不参与拼装 URL。
     */
    private String resolveCheckoutFrontendBaseUrl() {
        String baseUrl = systemConfigService.requiredEnabledValue(CHECKOUT_FRONTEND_BASE_URL_CONFIG_KEY);
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR,
                    "system config is not a valid checkout frontend base url");
        }
        // 收银台入口是平台资产，不能被商户请求中的 checkoutDomain 覆盖。
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * 解析收银台展示的商户主体名称，优先使用子商户主体信息。
     */
    private String resolveMerchantDisplayName(HostedCheckoutSessionCreateRequestDTO requestDTO) {
        if (requestDTO.getMerchantInfo() != null && requestDTO.getMerchantInfo().getSubMerchantInfo() != null) {
            String companyName = requestDTO.getMerchantInfo().getSubMerchantInfo().getSubCompanyName();
            if (StringUtils.hasText(companyName)) {
                return companyName;
            }
            String name = requestDTO.getMerchantInfo().getSubMerchantInfo().getSubName();
            if (StringUtils.hasText(name)) {
                return name;
            }
        }
        return "Merchant " + requestContext.getRequiredMerchantId();
    }

    /**
     * 填充打开收银台时的浏览器安全摘要，用于非法 token 和异常访问审计。
     */
    private void fillBrowserSecurity(PaymentCheckoutClientDTOs.SessionQueryRequest target,
                                     HostedCheckoutBrowserRequestDTOs.ClientContextDTO context) {
        HttpServletRequest request = currentRequest();
        target.setClientIpHash(sha256Hex(resolveClientIp(request)));
        target.setUserAgentHash(sha256Hex(request == null ? null : request.getHeader(HEADER_USER_AGENT)));
        target.setOriginHash(sha256Hex(request == null ? null : request.getHeader(HEADER_ORIGIN)));
        target.setRefererHash(sha256Hex(request == null ? null : request.getHeader(HEADER_REFERER)));
        target.setDeviceIdHash(sha256Hex(context == null ? null : context.getDeviceId()));
        target.setLanguage(context == null ? null : context.getLanguage());
        target.setTimezoneOffset(context == null ? null : context.getTimezoneOffset());
        target.setTraceId(TraceContext.getTraceId());
    }

    /**
     * 填充支付提交时的浏览器安全摘要，避免在内部命令中携带完整 IP/UA。
     */
    private void fillBrowserSecurity(PaymentCheckoutClientDTOs.PaymentSubmitRequest target,
                                     HostedCheckoutBrowserRequestDTOs.ClientContextDTO context) {
        HttpServletRequest request = currentRequest();
        String payerIp = normalizedClientIp(request);
        target.setClientIpHash(sha256Hex(payerIp));
        target.setPayerIp(payerIp);
        target.setUserAgentHash(sha256Hex(request == null ? null : request.getHeader(HEADER_USER_AGENT)));
        target.setOriginHash(sha256Hex(request == null ? null : request.getHeader(HEADER_ORIGIN)));
        target.setRefererHash(sha256Hex(request == null ? null : request.getHeader(HEADER_REFERER)));
    }

    /**
     * 填充轮询状态请求的浏览器摘要，轮询接口不采集完整设备信息。
     */
    private void fillBrowserSecurity(PaymentCheckoutClientDTOs.PaymentStatusRequest target,
                                     HostedCheckoutBrowserRequestDTOs.ClientContextDTO context) {
        HttpServletRequest request = currentRequest();
        target.setClientIpHash(sha256Hex(resolveClientIp(request)));
        target.setUserAgentHash(sha256Hex(request == null ? null : request.getHeader(HEADER_USER_AGENT)));
    }

    /**
     * 填充 3DS 回跳请求的浏览器摘要，用于识别非法或串单回跳。
     */
    private void fillBrowserSecurity(PaymentCheckoutClientDTOs.ThreeDsReturnRequest target,
                                     HostedCheckoutBrowserRequestDTOs.ClientContextDTO context) {
        HttpServletRequest request = currentRequest();
        String payerIp = normalizedClientIp(request);
        target.setClientIpHash(sha256Hex(payerIp));
        target.setPayerIp(payerIp);
        target.setUserAgentHash(sha256Hex(request == null ? null : request.getHeader(HEADER_USER_AGENT)));
    }

    /** Normalize the payer IP before it enters the transient 3DS provider call chain. */
    private String normalizedClientIp(HttpServletRequest request) {
        String clientIp = resolveClientIp(request);
        if (!StringUtils.hasText(clientIp)) {
            return null;
        }
        try {
            return IpAddressNormalizer.normalizeExact(clientIp).ipValue();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "payer IP address is invalid");
        }
    }

    /**
     * 生成脱敏后的 browserInfo JSON，供 MPGS 3DS 请求和审计快照使用。
     */
    private String browserInfoJson(HostedCheckoutBrowserRequestDTOs.ClientContextDTO context) {
        HttpServletRequest request = currentRequest();
        Map<String, Object> browserInfo = new LinkedHashMap<>();
        browserInfo.put("userAgent", request == null ? null : request.getHeader(HEADER_USER_AGENT));
        browserInfo.put("acceptHeaders", request == null ? null : request.getHeader(HEADER_ACCEPT));
        if (context != null) {
            browserInfo.put("challengeWindowSize", context.getChallengeWindowSize());
            browserInfo.put("colorDepth", context.getColorDepth());
            browserInfo.put("javaEnabled", context.getJavaEnabled());
            browserInfo.put("javaScriptEnabled", context.getJavaScriptEnabled());
            browserInfo.put("language", context.getLanguage());
            browserInfo.put("screenHeight", context.getScreenHeight());
            browserInfo.put("screenWidth", context.getScreenWidth());
            browserInfo.put("timezoneOffset", context.getTimezoneOffset());
        }
        return SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(browserInfo));
    }

    /**
     * 生成脱敏后的设备信息 JSON，避免保存完整浏览器指纹。
     */
    private String deviceInfoJson(HostedCheckoutBrowserRequestDTOs.ClientContextDTO context) {
        return SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(context));
    }

    /** 生成支付提交幂等指纹，只使用信封元数据，不解密或派生卡号信息。 */
    private String requestFingerprintWithoutRawCard(HostedCheckoutBrowserRequestDTOs.PaymentSubmitRequest requestDTO) {
        HostedCheckoutBrowserRequestDTOs.CardDataEnvelopeDTO envelope = requestDTO.getCardDataEnvelope();
        return sha256Hex(requestDTO.getCheckoutSessionId() + ":" + requestDTO.getAttemptRequestId() + ":"
                + requestDTO.getPaymentMethod() + ":" + (envelope == null ? null : envelope.getKeyId()) + ":"
                + (envelope == null ? null : envelope.getNonce()));
    }

    /**
     * 生成创建会话来源摘要，只记录 hash 和接口上下文，不记录完整浏览器指纹。
     */
    private String requestSourceSummary() {
        HttpServletRequest request = currentRequest();
        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
        summary.put("path", request == null ? null : request.getRequestURI());
        summary.put("clientIpHash", sha256Hex(resolveClientIp(request)));
        summary.put("originHash", sha256Hex(request == null ? null : request.getHeader(HEADER_ORIGIN)));
        summary.put("refererHash", sha256Hex(request == null ? null : request.getHeader(HEADER_REFERER)));
        summary.put("apiVersion", requestAttribute(OpenApiRequestAttributes.API_VERSION));
        summary.put("interfaceType", requestAttribute(OpenApiRequestAttributes.INTERFACE_TYPE));
        return JsonUtils.toJsonString(summary);
    }

    /**
     * 解析客户端 IP，优先取网关透传头，调用方会立即转换为 hash。
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader(HEADER_X_REAL_IP);
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 读取 OpenAPI 安全过滤器写入的请求属性，用于审计来源摘要。
     */
    private Object requestAttribute(String attributeName) {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getAttribute(attributeName);
    }

    /**
     * 获取当前 HTTP 请求；异步或测试上下文不存在请求时允许返回 null。
     */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    /**
     * 生成审计摘要，空值不落摘要，避免无意义 hash 干扰排查。
     */
    private String sha256Hex(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR, "digest failed");
        }
    }

    /**
     * 计算接口处理耗时，用于 OpenAPI 创建收银台链路日志。
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
