package com.scott.payment.openapi.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.client.InternalServiceCallExceptionMapper;
import com.scott.payment.openapi.client.InternalServiceResponseParser;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentQueryClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionCallbackInboxAcceptClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionMerchantApiResponseLogUpdateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.checkout.PaymentCheckoutClientDTOs;
import com.scott.payment.openapi.config.PaymentClientProperties;
import com.scott.payment.openapi.enums.OpenApiPaymentOperationEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * service-payment REST 客户端，位于 service-openapi 客户端层，为收单交易动作封装内部 HMAC 签名、负载均衡选择和统一响应解包。
 */
@Service
@Slf4j
public class PaymentInternalRestClient implements PaymentInternalClient {

    /**
     * IPv4 地址格式，用于识别本地或固定地址直连场景。
     */
    private static final Pattern IPV4_HOST_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    /**
     * 本机地址。
     */
    private static final String LOCALHOST = "localhost";

    /**
     * IPv6 本机地址。
     */
    private static final String IPV6_LOOPBACK = "::1";

    /**
     * URL 主机分隔符。
     */
    private static final String DOMAIN_SEPARATOR = ".";

    /**
     * service-payment 服务发现名称和内部路径属于服务契约，避免放入 Nacos 或业务参数表。
     */
    private static final String SERVICE_PAYMENT_BASE_URL = "http://service-payment";

    /**
     * service-payment 授权内部接口路径，固定随服务版本演进。
     */
    private static final String AUTHORIZATION_PATH = "/internal/payment/authorization";

    /**
     * service-payment 支付内部接口路径，固定随服务版本演进。
     */
    private static final String PAYMENT_PATH = "/internal/payment/payment";

    /**
     * service-payment 预授权内部接口路径，固定随服务版本演进。
     */
    private static final String PRE_AUTHORIZATION_PATH = "/internal/payment/pre-authorization";

    /**
     * service-payment 增量授权内部接口路径，固定随服务版本演进。
     */
    private static final String INCREMENTAL_AUTHORIZATION_PATH = "/internal/payment/incremental-authorization";

    /**
     * service-payment 请款内部接口路径，固定随服务版本演进。
     */
    private static final String CAPTURE_PATH = "/internal/payment/capture";

    /**
     * service-payment 预授权完成内部接口路径，固定随服务版本演进。
     */
    private static final String PRE_AUTH_COMPLETION_PATH = "/internal/payment/pre-auth-completion";

    /**
     * service-payment 退款内部接口路径，固定随服务版本演进。
     */
    private static final String REFUND_PATH = "/internal/payment/refund";

    /**
     * service-payment 撤销内部接口路径，固定随服务版本演进。
     */
    private static final String VOID_PATH = "/internal/payment/void";

    /**
     * service-payment 交易查询内部接口路径，固定随服务版本演进。
     */
    private static final String QUERY_PATH = "/internal/payment/query";

    /**
     * service-payment 渠道回调内部入口，公网回调必须先经过 OpenAPI 安全校验。
     */
    private static final String CHANNEL_CALLBACK_INBOX_PATH = "/internal/payment/channel-callback/inbox";

    /**
     * service-payment 商户响应日志内部入口，只记录脱敏响应摘要。
     */
    private static final String MERCHANT_API_RESPONSE_LOG_PATH = "/internal/payment/transactions/merchant-api-logs/response";

    /**
     * Hosted Checkout 创建会话内部路径。
     */
    private static final String CHECKOUT_SESSION_CREATE_PATH = "/internal/payment/checkout/session";

    /**
     * Hosted Checkout 查询会话内部路径。
     */
    private static final String CHECKOUT_SESSION_QUERY_PATH = "/internal/payment/checkout/session/query";

    /**
     * Hosted Checkout 提交付款内部路径，卡信息只在该调用链中过境。
     */
    private static final String CHECKOUT_PAYMENT_SUBMIT_PATH = "/internal/payment/checkout/payment/submit";

    /**
     * Hosted Checkout 付款状态查询内部路径。
     */
    private static final String CHECKOUT_PAYMENT_STATUS_PATH = "/internal/payment/checkout/payment/status";

    /**
     * Hosted Checkout 3DS bridge 回跳内部路径。
     */
    private static final String CHECKOUT_THREE_DS_RETURN_PATH = "/internal/payment/checkout/3ds/return";

    /**
     * Hosted Checkout 卡 BIN 解析内部路径。
     */
    private static final String CHECKOUT_CARD_BIN_RESOLVE_PATH = "/internal/payment/checkout/card-bin/resolve";

    /**
     * 直连 RestTemplate，用于 localhost、IP 或完整域名。
     */
    private final RestTemplate directRestTemplate;

    /**
     * 负载均衡 RestTemplate，用于 `http://service-payment/...` 这类 Nacos 服务名。
     */
    private final RestTemplate loadBalancedRestTemplate;

    /**
     * 支付内部客户端配置。
     */
    private final PaymentClientProperties paymentClientProperties;

    /**
     * 创建 service-payment REST 客户端。
     *
     * @param directRestTemplate       直连 RestTemplate
     * @param loadBalancedRestTemplate 负载均衡 RestTemplate
     * @param paymentClientProperties  支付内部客户端配置
     */
    public PaymentInternalRestClient(@Qualifier("paymentRestTemplate") RestTemplate directRestTemplate,
                                     @Qualifier("paymentLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
                                     PaymentClientProperties paymentClientProperties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.paymentClientProperties = paymentClientProperties;
    }

    /**
     * 调用 service-payment 创建授权交易。
     *
     * @param requestDTO 创建授权内部请求
     * @return 授权交易内部响应
     */
    @Override
    public PaymentCreateClientResponseDTO createAuthorization(PaymentCreateClientRequestDTO requestDTO) {
        return postTransaction(OpenApiPaymentOperationEnum.AUTHORIZATION, requestDTO);
    }

    /**
     * 调用 service-payment 创建一步支付交易。
     *
     * @param requestDTO 创建交易内部请求
     * @return 创建交易内部响应
     */
    @Override
    public PaymentCreateClientResponseDTO createPayment(PaymentCreateClientRequestDTO requestDTO) {
        return postTransaction(OpenApiPaymentOperationEnum.PAYMENT, requestDTO);
    }

    /**
     * 调用 service-payment 创建预授权交易。
     *
     * @param requestDTO 创建交易内部请求
     * @return 创建交易内部响应
     */
    @Override
    public PaymentCreateClientResponseDTO createPreAuthorization(PaymentCreateClientRequestDTO requestDTO) {
        return postTransaction(OpenApiPaymentOperationEnum.PRE_AUTHORIZATION, requestDTO);
    }

    /**
     * 调用 service-payment 创建增量授权交易。
     *
     * @param requestDTO 创建交易内部请求
     * @return 创建交易内部响应
     */
    @Override
    public PaymentCreateClientResponseDTO createIncrementalAuthorization(PaymentCreateClientRequestDTO requestDTO) {
        return postTransaction(OpenApiPaymentOperationEnum.INCREMENTAL_AUTHORIZATION, requestDTO);
    }

    /**
     * 调用 service-payment 发起请款交易。
     *
     * @param requestDTO 请款内部请求
     * @return 请款内部响应
     */
    @Override
    public PaymentCreateClientResponseDTO capture(PaymentCreateClientRequestDTO requestDTO) {
        return postTransaction(OpenApiPaymentOperationEnum.CAPTURE, requestDTO);
    }

    /**
     * 调用 service-payment 发起预授权完成交易。
     *
     * @param requestDTO 预授权完成内部请求
     * @return 预授权完成内部响应
     */
    @Override
    public PaymentCreateClientResponseDTO preAuthCompletion(PaymentCreateClientRequestDTO requestDTO) {
        return postTransaction(OpenApiPaymentOperationEnum.PRE_AUTH_COMPLETION, requestDTO);
    }

    /**
     * 调用 service-payment 发起退款交易。
     *
     * @param requestDTO 退款内部请求
     * @return 退款内部响应
     */
    @Override
    public PaymentCreateClientResponseDTO refund(PaymentCreateClientRequestDTO requestDTO) {
        return postTransaction(OpenApiPaymentOperationEnum.REFUND, requestDTO);
    }

    /**
     * 调用 service-payment 发起撤销交易。
     *
     * @param requestDTO 撤销内部请求
     * @return 撤销内部响应
     */
    @Override
    public PaymentCreateClientResponseDTO voidPayment(PaymentCreateClientRequestDTO requestDTO) {
        return postTransaction(OpenApiPaymentOperationEnum.VOID, requestDTO);
    }

    /**
     * 调用 service-payment 查询交易状态。
     *
     * @param requestDTO 查询内部请求
     * @return 查询内部响应
     */
    @Override
    public PaymentQueryClientResponseDTO query(PaymentCreateClientRequestDTO requestDTO) {
        long startNanos = System.nanoTime();
        String targetUrl = targetUrl(OpenApiPaymentOperationEnum.QUERY);
        URI uri = URI.create(targetUrl);
        HttpEntity<String> requestEntity = buildSignedEntity(uri, requestDTO);
        log.info("OpenAPI调用支付核心开始, event: OPENAPI_PAYMENT_CALL_START, stage: PAYMENT_CALL, traceId: {}, operation: {}, merchantId: {}, merchantOrderNo: {}, transactionType: {}, targetService: {}, path: {}, requestSummary: {}",
                TraceContext.getTraceId(),
                OpenApiPaymentOperationEnum.QUERY.getTransactionType(),
                requestDTO == null ? null : requestDTO.getMerchantId(),
                requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                requestDTO == null ? null : requestDTO.getTransactionType(),
                uri.getHost(),
                InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                requestSummary(requestDTO));
        try {
            ResponseEntity<String> responseEntity = chooseRestTemplate(targetUrl).postForEntity(
                    targetUrl,
                    requestEntity,
                    String.class
            );
            String responseBody = responseEntity.getBody();
            CommonResult<PaymentQueryClientResponseDTO> result = InternalServiceResponseParser.parse(
                    responseBody,
                    new TypeReference<CommonResult<PaymentQueryClientResponseDTO>>() {
                    },
                    "service-payment query response is invalid",
                    false
            );
            PaymentQueryClientResponseDTO responseDTO = result == null ? null : result.getData();
            log.info("OpenAPI调用支付核心完成, event: OPENAPI_PAYMENT_CALL_END, stage: PAYMENT_CALL, traceId: {}, operation: {}, merchantId: {}, merchantOrderNo: {}, transactionType: {}, transactionId: {}, operationId: {}, platformStatus: {}, httpStatus: {}, platformCode: {}, success: {}, responseSummary: {}, responseLength: {}, responseDigest: {}, durationMs: {}",
                    TraceContext.getTraceId(),
                    OpenApiPaymentOperationEnum.QUERY.getTransactionType(),
                    requestDTO == null ? null : requestDTO.getMerchantId(),
                    requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                    requestDTO == null ? null : requestDTO.getTransactionType(),
                    firstQueryTransactionId(responseDTO),
                    firstQueryOperationId(responseDTO),
                    firstQueryStatus(responseDTO),
                    responseEntity.getStatusCode().value(),
                    result == null ? null : result.getCode(),
                    CommonResult.isSuccess(result),
                    responseSummary(result),
                    responseBody == null ? 0 : responseBody.length(),
                    digest16(responseBody),
                    elapsedMillis(startNanos));
            responseDTO = unwrapQueryResult(result);
            return responseDTO;
        } catch (RestClientException exception) {
            log.warn("OpenAPI调用支付核心失败, event: OPENAPI_PAYMENT_CALL_END, stage: PAYMENT_CALL, traceId: {}, operation: {}, merchantId: {}, merchantOrderNo: {}, transactionType: {}, targetService: {}, path: {}, requestDigest: {}, durationMs: {}, exceptionType: {}",
                    TraceContext.getTraceId(),
                    OpenApiPaymentOperationEnum.QUERY.getTransactionType(),
                    requestDTO == null ? null : requestDTO.getMerchantId(),
                    requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                    requestDTO == null ? null : requestDTO.getTransactionType(),
                    uri.getHost(),
                    uri.getPath(),
                    digest16(JsonUtils.toJsonString(requestDTO)),
                    elapsedMillis(startNanos),
                    exception.getClass().getSimpleName());
            throw InternalServiceCallExceptionMapper.map(
                    exception, "service-payment query call failed", false);
        }
    }

    /**
     * 调用 service-payment 可靠受理渠道回调 Inbox。
     *
     * @param requestDTO 渠道回调内部请求
     * @return Inbox 持久化受理响应
     */
    @Override
    public TransactionCallbackInboxAcceptClientResponseDTO acceptChannelCallback(
            TransactionChannelCallbackClientRequestDTO requestDTO) {
        long startNanos = System.nanoTime();
        String targetUrl = servicePaymentUrl(CHANNEL_CALLBACK_INBOX_PATH);
        URI uri = URI.create(targetUrl);
        HttpEntity<String> requestEntity = buildSignedEntity(uri, requestDTO);
        log.info("OpenAPI渠道回调入箱调用开始, event: OPENAPI_PAYMENT_CALL_START, stage: PAYMENT_CALL, traceId: {}, operation: CHANNEL_CALLBACK_INBOX, channelCode: {}, transactionId: {}, targetService: {}, path: {}, requestSummary: {}",
                TraceContext.getTraceId(),
                requestDTO == null ? null : requestDTO.getChannelCode(),
                requestDTO == null ? null : requestDTO.getTransactionId(),
                uri.getHost(),
                uri.getPath(),
                requestSummary(requestDTO));
        try {
            ResponseEntity<String> responseEntity = chooseRestTemplate(targetUrl).postForEntity(
                    targetUrl,
                    requestEntity,
                    String.class
            );
            String responseBody = responseEntity.getBody();
            CommonResult<TransactionCallbackInboxAcceptClientResponseDTO> result = InternalServiceResponseParser.parse(
                    responseBody,
                    new TypeReference<CommonResult<TransactionCallbackInboxAcceptClientResponseDTO>>() {
                    },
                    "service-payment callback response is invalid",
                    true
            );
            TransactionCallbackInboxAcceptClientResponseDTO responseDTO = result == null ? null : result.getData();
            log.info("OpenAPI渠道回调入箱调用完成, event: OPENAPI_PAYMENT_CALL_END, stage: PAYMENT_CALL, traceId: {}, operation: CHANNEL_CALLBACK_INBOX, channelCode: {}, transactionId: {}, inboxId: {}, inboxStatus: {}, acceptResult: {}, httpStatus: {}, platformCode: {}, success: {}, responseSummary: {}, responseLength: {}, responseDigest: {}, durationMs: {}",
                    TraceContext.getTraceId(),
                    requestDTO == null ? null : requestDTO.getChannelCode(),
                    requestDTO == null ? null : requestDTO.getTransactionId(),
                    responseDTO == null ? null : responseDTO.getInboxId(),
                    responseDTO == null ? null : responseDTO.getInboxStatus(),
                    responseDTO == null ? null : responseDTO.getAcceptResult(),
                    responseEntity.getStatusCode().value(),
                    result == null ? null : result.getCode(),
                    CommonResult.isSuccess(result),
                    responseSummary(result),
                    responseBody == null ? 0 : responseBody.length(),
                    digest16(responseBody),
                    elapsedMillis(startNanos));
            responseDTO = unwrapCallbackInboxResult(result);
            return responseDTO;
        } catch (RestClientException exception) {
            log.warn("OpenAPI渠道回调入箱调用失败, event: OPENAPI_PAYMENT_CALL_END, stage: PAYMENT_CALL, traceId: {}, operation: CHANNEL_CALLBACK_INBOX, channelCode: {}, transactionId: {}, targetService: {}, path: {}, requestDigest: {}, durationMs: {}, exceptionType: {}",
                    TraceContext.getTraceId(),
                    requestDTO == null ? null : requestDTO.getChannelCode(),
                    requestDTO == null ? null : requestDTO.getTransactionId(),
                    uri.getHost(),
                    uri.getPath(),
                    digest16(JsonUtils.toJsonString(requestDTO)),
                    elapsedMillis(startNanos),
                    exception.getClass().getSimpleName());
            throw InternalServiceCallExceptionMapper.map(
                    exception, "service-payment callback call failed", true);
        }
    }

    /**
     * 回写商户 OpenAPI 响应加密后的密文摘要。
     *
     * @param requestDTO 响应日志回写请求
     * @return true 表示 service-payment 命中并更新日志
     */
    @Override
    public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO) {
        long startNanos = System.nanoTime();
        String targetUrl = servicePaymentUrl(MERCHANT_API_RESPONSE_LOG_PATH);
        URI uri = URI.create(targetUrl);
        log.info("OpenAPI商户响应审计回写开始, event: OPENAPI_PAYMENT_CALL_START, stage: PAYMENT_CALL, traceId: {}, operation: MERCHANT_API_RESPONSE_LOG, transactionId: {}, requestId: {}, targetService: {}, path: {}, requestSummary: {}",
                TraceContext.getTraceId(),
                requestDTO == null ? null : requestDTO.getTransactionId(),
                requestDTO == null ? null : requestDTO.getRequestId(),
                uri.getHost(),
                uri.getPath(),
                requestSummary(requestDTO));
        try {
            ResponseEntity<String> responseEntity = chooseRestTemplate(targetUrl).postForEntity(
                    targetUrl,
                    buildSignedEntity(URI.create(targetUrl), requestDTO),
                    String.class
            );
            String responseBody = responseEntity.getBody();
            CommonResult<Boolean> result = InternalServiceResponseParser.parse(
                    responseBody,
                    new TypeReference<CommonResult<Boolean>>() {
                    },
                    "service-payment merchant api log response is invalid",
                    false
            );
            boolean updated = unwrapBooleanResult(result);
            log.info("OpenAPI商户响应审计回写完成, event: OPENAPI_PAYMENT_CALL_END, stage: PAYMENT_CALL, traceId: {}, operation: MERCHANT_API_RESPONSE_LOG, transactionId: {}, requestId: {}, httpStatus: {}, platformCode: {}, updated: {}, responseSummary: {}, responseLength: {}, responseDigest: {}, durationMs: {}",
                    TraceContext.getTraceId(),
                    requestDTO == null ? null : requestDTO.getTransactionId(),
                    requestDTO == null ? null : requestDTO.getRequestId(),
                    responseEntity.getStatusCode().value(),
                    result == null ? null : result.getCode(),
                    updated,
                    responseSummary(result),
                    responseBody == null ? 0 : responseBody.length(),
                    digest16(responseBody),
                    elapsedMillis(startNanos));
            return updated;
        } catch (RestClientException exception) {
            log.warn("OpenAPI商户响应审计回写失败, event: OPENAPI_PAYMENT_CALL_END, stage: PAYMENT_CALL, traceId: {}, operation: MERCHANT_API_RESPONSE_LOG, transactionId: {}, requestId: {}, targetService: {}, path: {}, requestDigest: {}, durationMs: {}, exceptionType: {}",
                    TraceContext.getTraceId(),
                    requestDTO == null ? null : requestDTO.getTransactionId(),
                    requestDTO == null ? null : requestDTO.getRequestId(),
                    uri.getHost(),
                    uri.getPath(),
                    digest16(JsonUtils.toJsonString(requestDTO)),
                    elapsedMillis(startNanos),
                    exception.getClass().getSimpleName());
            throw InternalServiceCallExceptionMapper.map(
                    exception, "service-payment merchant api log update failed", false);
        }
    }

    /**
     * 调用支付核心创建 Hosted Checkout 会话。
     *
     * @param requestDTO 已完成商户绑定和金额精度转换的会话请求
     * @return 支付核心持久化后的会话结果
     */
    @Override
    public PaymentCheckoutClientDTOs.SessionCreateResponse createCheckoutSession(
            PaymentCheckoutClientDTOs.SessionCreateRequest requestDTO) {
        return postCheckout(
                "CHECKOUT_SESSION_CREATE",
                servicePaymentUrl(CHECKOUT_SESSION_CREATE_PATH),
                requestDTO,
                true,
                new TypeReference<CommonResult<PaymentCheckoutClientDTOs.SessionCreateResponse>>() {
                }
        );
    }

    /**
     * 调用支付核心解析卡 BIN，并校验当前收银台会话可用的卡品牌能力。
     *
     * @param requestDTO 会话令牌摘要和卡 BIN 前缀
     * @return 支付核心返回的卡品牌及支持状态
     */
    @Override
    public PaymentCheckoutClientDTOs.CardBinResponse resolveCheckoutCardBin(
            PaymentCheckoutClientDTOs.CardBinRequest requestDTO) {
        return postCheckout(
                "CHECKOUT_CARD_BIN_RESOLVE",
                servicePaymentUrl(CHECKOUT_CARD_BIN_RESOLVE_PATH),
                requestDTO,
                false,
                new TypeReference<CommonResult<PaymentCheckoutClientDTOs.CardBinResponse>>() {
                }
        );
    }

    /**
     * 调用支付核心查询 Hosted Checkout 会话展示快照。
     *
     * @param requestDTO 包含不透明令牌摘要的查询请求
     * @return 会话当前展示状态
     */
    @Override
    public PaymentCheckoutClientDTOs.SessionQueryResponse queryCheckoutSession(
            PaymentCheckoutClientDTOs.SessionQueryRequest requestDTO) {
        return postCheckout(
                "CHECKOUT_SESSION_QUERY",
                servicePaymentUrl(CHECKOUT_SESSION_QUERY_PATH),
                requestDTO,
                false,
                new TypeReference<CommonResult<PaymentCheckoutClientDTOs.SessionQueryResponse>>() {
                }
        );
    }

    /**
     * 调用支付核心提交 Hosted Checkout 付款尝试。
     *
     * <p>内部签名保护服务边界；仅转发卡数据密文信封，OpenAPI 不接收 PAN/CVV 明文。</p>
     *
     * @param requestDTO 付款尝试内部请求
     * @return 支付核心受理结果或 3DS 动作
     */
    @Override
    public PaymentCheckoutClientDTOs.PaymentResultResponse submitCheckoutPayment(
            PaymentCheckoutClientDTOs.PaymentSubmitRequest requestDTO) {
        return postCheckout(
                "CHECKOUT_PAYMENT_SUBMIT",
                servicePaymentUrl(CHECKOUT_PAYMENT_SUBMIT_PATH),
                requestDTO,
                true,
                new TypeReference<CommonResult<PaymentCheckoutClientDTOs.PaymentResultResponse>>() {
                }
        );
    }

    /**
     * 调用支付核心查询 Hosted Checkout 付款尝试状态。
     *
     * @param requestDTO 会话、尝试号及令牌摘要
     * @return 支付核心当前状态
     */
    @Override
    public PaymentCheckoutClientDTOs.PaymentResultResponse queryCheckoutPaymentStatus(
            PaymentCheckoutClientDTOs.PaymentStatusRequest requestDTO) {
        return postCheckout(
                "CHECKOUT_PAYMENT_STATUS",
                servicePaymentUrl(CHECKOUT_PAYMENT_STATUS_PATH),
                requestDTO,
                false,
                new TypeReference<CommonResult<PaymentCheckoutClientDTOs.PaymentResultResponse>>() {
                }
        );
    }

    /**
     * 调用支付核心处理 Hosted Checkout 3DS 回跳。
     *
     * @param requestDTO 包含一次性令牌摘要和脱敏认证数据的内部请求
     * @return 支付核心状态机处理后的当前状态
     */
    @Override
    public PaymentCheckoutClientDTOs.PaymentResultResponse handleCheckoutThreeDsReturn(
            PaymentCheckoutClientDTOs.ThreeDsReturnRequest requestDTO) {
        return postCheckout(
                "CHECKOUT_THREE_DS_RETURN",
                servicePaymentUrl(CHECKOUT_THREE_DS_RETURN_PATH),
                requestDTO,
                true,
                new TypeReference<CommonResult<PaymentCheckoutClientDTOs.PaymentResultResponse>>() {
                }
        );
    }

    /**
     * 按交易动作调用 service-payment 内部接口。
     *
     * @param operation 交易动作
     * @param requestDTO 内部请求
     * @return 内部响应
     */
    private PaymentCreateClientResponseDTO postTransaction(OpenApiPaymentOperationEnum operation,
                                                           PaymentCreateClientRequestDTO requestDTO) {
        long startNanos = System.nanoTime();
        String targetUrl = targetUrl(operation);
        URI uri = URI.create(targetUrl);
        HttpEntity<String> requestEntity = buildSignedEntity(uri, requestDTO);
        log.info("OpenAPI调用支付核心开始, event: OPENAPI_PAYMENT_CALL_START, stage: PAYMENT_CALL, traceId: {}, operation: {}, merchantId: {}, merchantOrderNo: {}, transactionType: {}, targetService: {}, path: {}, requestSummary: {}",
                TraceContext.getTraceId(),
                operation.getTransactionType(),
                requestDTO == null ? null : requestDTO.getMerchantId(),
                requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                requestDTO == null ? null : requestDTO.getTransactionType(),
                uri.getHost(),
                uri.getPath(),
                requestSummary(requestDTO));
        try {
            ResponseEntity<String> responseEntity = chooseRestTemplate(targetUrl).postForEntity(
                    targetUrl,
                    requestEntity,
                    String.class
            );
            String responseBody = responseEntity.getBody();
            CommonResult<PaymentCreateClientResponseDTO> result = InternalServiceResponseParser.parse(
                    responseBody,
                    new TypeReference<CommonResult<PaymentCreateClientResponseDTO>>() {
                    },
                    "service-payment transaction response is invalid",
                    true
            );
            PaymentCreateClientResponseDTO responseDTO = result == null ? null : result.getData();
            log.info("OpenAPI调用支付核心完成, event: OPENAPI_PAYMENT_CALL_END, stage: PAYMENT_CALL, traceId: {}, operation: {}, merchantId: {}, merchantOrderNo: {}, transactionId: {}, operationId: {}, transactionType: {}, platformStatus: {}, httpStatus: {}, platformCode: {}, success: {}, responseSummary: {}, responseLength: {}, responseDigest: {}, durationMs: {}",
                    TraceContext.getTraceId(),
                    operation.getTransactionType(),
                    requestDTO == null ? null : requestDTO.getMerchantId(),
                    requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                    responseDTO == null ? null : responseDTO.getTransactionId(),
                    responseDTO == null ? null : responseDTO.getOperationId(),
                    responseDTO == null
                            ? requestDTO == null ? null : requestDTO.getTransactionType()
                            : responseDTO.getTransactionType(),
                    responseDTO == null ? null : responseDTO.getStatus(),
                    responseEntity.getStatusCode().value(),
                    result == null ? null : result.getCode(),
                    CommonResult.isSuccess(result),
                    responseSummary(result),
                    responseBody == null ? 0 : responseBody.length(),
                    digest16(responseBody),
                    elapsedMillis(startNanos));
            responseDTO = unwrapResult(result);
            return responseDTO;
        } catch (RestClientException exception) {
            log.warn("OpenAPI调用支付核心失败, event: OPENAPI_PAYMENT_CALL_END, stage: PAYMENT_CALL, traceId: {}, operation: {}, merchantId: {}, merchantOrderNo: {}, transactionType: {}, targetService: {}, path: {}, requestDigest: {}, durationMs: {}, exceptionType: {}",
                    TraceContext.getTraceId(),
                    operation.getTransactionType(),
                    requestDTO == null ? null : requestDTO.getMerchantId(),
                    requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                    requestDTO == null ? null : requestDTO.getTransactionType(),
                    uri.getHost(),
                    uri.getPath(),
                    digest16(JsonUtils.toJsonString(requestDTO)),
                    elapsedMillis(startNanos),
                    exception.getClass().getSimpleName());
            throw InternalServiceCallExceptionMapper.map(
                    exception, "service-payment transaction call failed", true);
        }
    }

    /**
     * 调用 Hosted Checkout 内部接口并解包统一响应。
     *
     * @param operation  checkout 操作标识
     * @param targetUrl  service-payment 内部接口地址
     * @param requestDTO 内部请求
     * @param writeOperation 当前调用是否可能改变会话或交易状态
     * @param typeRef    响应泛型
     * @param <T>        响应 data 类型
     * @return 解包后的响应 data
     */
    private <T> T postCheckout(String operation, String targetUrl, Object requestDTO,
                               boolean writeOperation,
                               TypeReference<CommonResult<T>> typeRef) {
        long startNanos = System.nanoTime();
        URI uri = URI.create(targetUrl);
        HttpEntity<String> requestEntity = buildSignedEntity(uri, requestDTO);
        log.info("OpenAPI收银台内部调用开始, event: OPENAPI_PAYMENT_CALL_START, stage: PAYMENT_CALL, traceId: {}, operation: {}, targetService: {}, path: {}, requestSummary: {}",
                TraceContext.getTraceId(),
                operation,
                uri.getHost(),
                uri.getPath(),
                requestSummary(requestDTO));
        try {
            ResponseEntity<String> responseEntity = chooseRestTemplate(targetUrl).postForEntity(
                    targetUrl,
                    requestEntity,
                    String.class
            );
            String responseBody = responseEntity.getBody();
            CommonResult<T> result = InternalServiceResponseParser.parse(
                    responseBody,
                    typeRef,
                    "service-payment checkout response is invalid",
                    writeOperation);
            log.info("OpenAPI收银台内部调用完成, event: OPENAPI_PAYMENT_CALL_END, stage: PAYMENT_CALL, traceId: {}, operation: {}, httpStatus: {}, platformCode: {}, success: {}, responseSummary: {}, responseLength: {}, responseDigest: {}, durationMs: {}",
                    TraceContext.getTraceId(),
                    operation,
                    responseEntity.getStatusCode().value(),
                    result == null ? null : result.getCode(),
                    CommonResult.isSuccess(result),
                    responseSummary(result),
                    responseBody == null ? 0 : responseBody.length(),
                    digest16(responseBody),
                    elapsedMillis(startNanos));
            return unwrapCheckoutResult(result, operation);
        } catch (RestClientException exception) {
            log.warn("OpenAPI收银台内部调用失败, event: OPENAPI_PAYMENT_CALL_END, stage: PAYMENT_CALL, traceId: {}, operation: {}, targetService: {}, path: {}, requestDigest: {}, durationMs: {}, exceptionType: {}",
                    TraceContext.getTraceId(),
                    operation,
                    uri.getHost(),
                    uri.getPath(),
                    digest16(JsonUtils.toJsonString(requestDTO)),
                    elapsedMillis(startNanos),
                    exception.getClass().getSimpleName());
            throw InternalServiceCallExceptionMapper.map(
                    exception, "service-payment checkout call failed", writeOperation);
        }
    }

    /**
     * 获取交易动作对应的内部接口地址。
     *
     * @param operation 交易动作
     * @return 内部接口地址
     */
    private String targetUrl(OpenApiPaymentOperationEnum operation) {
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
        throw new ApiException(OpenApiResultCode.TRANSACTION_TYPE_NOT_SUPPORTED);
    }

    /**
     * 拼接 service-payment 服务发现 URL；服务名和内部路径由代码契约维护，不放业务配置表。
     */
    private String servicePaymentUrl(String path) {
        return SERVICE_PAYMENT_BASE_URL + path;
    }

    /**
     * 根据配置的内部接口地址选择调用客户端。
     * <p>
     * 单段主机名如 `service-payment` 代表服务发现名称，走负载均衡；localhost、IP 和带点域名代表
     * 明确网络地址，直接调用，方便本地联调和固定域名部署。
     *
     * @param targetUrl service-payment 内部接口地址
     * @return 匹配当前地址类型的 RestTemplate
     */
    private RestTemplate chooseRestTemplate(String targetUrl) {
        URI uri = URI.create(targetUrl);
        String host = uri.getHost();
        if (host == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, "service-payment url host is empty");
        }
        if (LOCALHOST.equalsIgnoreCase(host) || IPV6_LOOPBACK.equals(host)
                || IPV4_HOST_PATTERN.matcher(host).matches() || host.contains(DOMAIN_SEPARATOR)) {
            return directRestTemplate;
        }
        return loadBalancedRestTemplate;
    }

    /**
     * 构造带内部服务签名头的请求实体。
     *
     * @param uri        内部服务地址
     * @param requestDTO 创建交易内部请求
     * @return 带签名头的请求实体
     */
    private HttpEntity<String> buildSignedEntity(URI uri, Object requestDTO) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String caller = paymentClientProperties.getInternalCaller();
        String requestBody = JsonUtils.toJsonString(requestDTO);
        String signature = InternalServiceSignature.sign(
                "POST",
                uri.getPath(),
                timestamp,
                nonce,
                caller,
                InternalServiceSignature.payloadSha256(requestBody),
                paymentClientProperties.getInternalSecret()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalServiceSignature.HEADER_CALLER, caller);
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(requestBody, headers);
    }

    /**
     * 生成 OpenAPI 调用 service-payment 的请求摘要。
     * <p>
     * 支付提交只记录会话、尝试号和卡密文信封算法等受控字段；其它请求只记录对象类型、JSON 长度和
     * 不可逆摘要，不输出内部签名头、Authorization、完整卡号、CVV、密文或业务报文。
     * </p>
     * @param requestDTO 内部服务请求对象
     * @return 可写入日志的请求摘要
     */
    private String requestSummary(Object requestDTO) {
        if (requestDTO instanceof PaymentCheckoutClientDTOs.PaymentSubmitRequest submitRequest) {
            PaymentSubmitDiagnosticSummary summary = new PaymentSubmitDiagnosticSummary(
                    submitRequest.getCheckoutSessionId(),
                    submitRequest.getAttemptRequestId(),
                    submitRequest.getPaymentMethod(),
                    submitRequest.getRequestFingerprint(),
                    submitRequest.getCardDataEnvelope() == null
                            ? null : submitRequest.getCardDataEnvelope().getAlgorithm(),
                    submitRequest.getCardDataEnvelope() == null
                            ? null : submitRequest.getCardDataEnvelope().getKeyId(),
                    submitRequest.getBillingCardHolderInfo() == null
                            ? null : submitRequest.getBillingCardHolderInfo().getCountry()
            );
            return truncate(JsonUtils.toJsonString(summary));
        }
        return payloadMetadata(requestDTO);
    }

    /**
     * 生成 service-payment 统一响应摘要。
     * <p>
     * 摘要只包含平台业务码、成功标记、data 类型、长度和不可逆摘要。内部消息和业务字段不写入日志。
     * </p>
     * @param result 内部服务统一响应
     * @return 可写入日志的响应摘要
     */
    private String responseSummary(CommonResult<?> result) {
        if (result == null) {
            return null;
        }
        InternalResponseDiagnosticSummary summary = new InternalResponseDiagnosticSummary(
                result.getCode(),
                CommonResult.isSuccess(result),
                payloadMetadataValue(result.getData())
        );
        return JsonUtils.toJsonString(summary);
    }

    /**
     * 生成不包含业务字段值的内部调用对象元数据。
     *
     * @param value 请求或响应 data 对象
     * @return 对象类型、JSON 长度和不可逆摘要
     */
    private String payloadMetadata(Object value) {
        return value == null ? null : JsonUtils.toJsonString(payloadMetadataValue(value));
    }

    /**
     * 构造强类型对象元数据，供请求和统一响应摘要复用。
     *
     * @param value 请求或响应 data 对象
     * @return 不包含业务字段值的元数据对象
     */
    private PayloadDiagnosticMetadata payloadMetadataValue(Object value) {
        if (value == null) {
            return null;
        }
        String json = JsonUtils.toJsonString(value);
        return new PayloadDiagnosticMetadata(value.getClass().getSimpleName(), json.length(), digest16(json));
    }

    /**
     * 提取查询响应中的首个交易号。
     * <p>
     * 查询接口按订单返回交易动作列表，日志仅记录首个动作的交易号用于排障入口定位，不改变对外响应结构。
     * </p>
     * @param responseDTO service-payment 查询响应
     * @return 首个交易号，响应为空或列表为空时返回 null
     */
    private String firstQueryTransactionId(PaymentQueryClientResponseDTO responseDTO) {
        PaymentQueryClientResponseDTO.TransactionInfoDTO transactionInfo = firstQueryTransaction(responseDTO);
        return transactionInfo == null ? null : transactionInfo.getTransactionId();
    }

    /**
     * 查询响应不向 OpenAPI 暴露 operationId，日志字段保留为空。
     * <p>
     * operationId 仅存在于 payment 内部链路和数据库日志；OpenAPI 查询响应当前没有该字段，
     * 因此这里不新增外部契约字段，只让结构化日志保持字段位。
     * </p>
     * @param responseDTO service-payment 查询响应
     * @return 固定返回 null
     */
    private String firstQueryOperationId(PaymentQueryClientResponseDTO responseDTO) {
        return null;
    }

    /**
     * 从查询响应首个交易动作的商户响应码推导平台状态摘要。
     * <p>
     * 查询 DTO 当前没有 transactionStatus 字段，因此日志用 code 作为最小可用状态线索。
     * </p>
     * @param responseDTO service-payment 查询响应
     * @return 首个交易动作的响应码，响应为空或列表为空时返回 null
     */
    private String firstQueryStatus(PaymentQueryClientResponseDTO responseDTO) {
        PaymentQueryClientResponseDTO.TransactionInfoDTO transactionInfo = firstQueryTransaction(responseDTO);
        return transactionInfo == null ? null : transactionInfo.getCode();
    }

    /**
     * 提取查询响应中的首个交易动作。
     *
     * @param responseDTO service-payment 查询响应
     * @return 首个交易动作，响应为空或列表为空时返回 null
     */
    private PaymentQueryClientResponseDTO.TransactionInfoDTO firstQueryTransaction(PaymentQueryClientResponseDTO responseDTO) {
        if (responseDTO == null || responseDTO.getTransactionInfo() == null || responseDTO.getTransactionInfo().isEmpty()) {
            return null;
        }
        return responseDTO.getTransactionInfo().get(0);
    }

    /**
     * 截断日志摘要字段。
     * <p>
     * OpenAPI 到 payment 的请求响应摘要默认最多保留 1200 字符，避免大对象刷屏或影响日志写入。
     * </p>
     * @param value 原始摘要文本
     * @return 截断后的摘要文本
     */
    private String truncate(String value) {
        if (value == null || value.length() <= 1200) {
            return value;
        }
        return value.substring(0, 1200) + "...";
    }

    /**
     * 计算响应体短摘要。
     * <p>
     * 使用 SHA-256 前 16 位十六进制，便于比对同一次响应体是否一致，不保存完整响应体。
     * </p>
     * @param value 响应体文本
     * @return 响应体短摘要；为空时返回 null
     */
    private String digest16(String value) {
        if (value == null) {
            return null;
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            return "sha256_unavailable";
        }
    }

    /**
     * 解包内部服务统一响应。
     *
     * @param result 内部服务统一响应
     * @return 创建交易内部响应
     */
    private PaymentCreateClientResponseDTO unwrapResult(CommonResult<PaymentCreateClientResponseDTO> result) {
        if (result == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, "service-payment response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, "service-payment response data is empty");
        }
        return result.getData();
    }

    /**
     * 解包交易查询内部服务统一响应。
     *
     * @param result 内部服务统一响应
     * @return 交易查询内部响应
     */
    private PaymentQueryClientResponseDTO unwrapQueryResult(CommonResult<PaymentQueryClientResponseDTO> result) {
        if (result == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, "service-payment query response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, "service-payment query response data is empty");
        }
        return result.getData();
    }

    /**
     * 解包渠道回调 Inbox 内部服务统一响应。
     *
     * @param result 内部服务统一响应
     * @return Inbox 受理响应
     */
    private TransactionCallbackInboxAcceptClientResponseDTO unwrapCallbackInboxResult(
            CommonResult<TransactionCallbackInboxAcceptClientResponseDTO> result) {
        if (result == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, "service-payment callback response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, "service-payment callback response data is empty");
        }
        return result.getData();
    }

    /**
     * 解包内部服务布尔响应。
     *
     * @param result 内部服务统一响应
     * @return 布尔结果
     */
    private boolean unwrapBooleanResult(CommonResult<Boolean> result) {
        if (result == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, "service-payment response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
        return Boolean.TRUE.equals(result.getData());
    }

    /**
     * 解包 Hosted Checkout 内部服务响应。
     *
     * @param result    内部服务统一响应
     * @param operation checkout 操作标识
     * @param <T>       响应 data 类型
     * @return 响应 data
     */
    private <T> T unwrapCheckoutResult(CommonResult<T> result, String operation) {
        if (result == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, "service-payment checkout response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, operation + " response data is empty");
        }
        return result.getData();
    }

    /**
     * 计算 OpenAPI 调用 service-payment 的本地耗时。
     * <p>
     * 入参来自调用开始时刻的纳秒时间戳；返回毫秒值用于链路日志，不参与交易金额、
     * 幂等状态或远程调用结果判断。
     * </p>
     * @param startNanos 调用开始时间，单位为纳秒
     * @return 从开始时间到当前时间的毫秒耗时
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 承载收银台支付提交允许进入日志的非敏感字段。
     *
     * @param checkoutSessionId 收银台会话号
     * @param attemptRequestId 本次支付尝试号
     * @param paymentMethod 支付方式
     * @param requestFingerprint 幂等请求指纹
     * @param cardEnvelopeAlgorithm 卡数据加密算法
     * @param cardEnvelopeKeyId 卡数据加密密钥标识
     * @param billingCountry 账单国家
     */
    private record PaymentSubmitDiagnosticSummary(
            String checkoutSessionId,
            String attemptRequestId,
            String paymentMethod,
            String requestFingerprint,
            String cardEnvelopeAlgorithm,
            String cardEnvelopeKeyId,
            String billingCountry) {
    }

    /**
     * 承载 service-payment 统一响应的安全诊断字段。
     *
     * @param platformCode 平台业务码
     * @param success 内部调用是否成功
     * @param data 响应 data 的不可逆元数据
     */
    private record InternalResponseDiagnosticSummary(
            String platformCode,
            boolean success,
            PayloadDiagnosticMetadata data) {
    }

    /**
     * 承载内部请求或响应对象的不可逆元数据，不保存业务字段值。
     *
     * @param payloadType 对象类型
     * @param jsonLength 序列化字符数
     * @param jsonDigest 序列化内容摘要
     */
    private record PayloadDiagnosticMetadata(String payloadType, int jsonLength, String jsonDigest) {
    }
}
