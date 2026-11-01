package com.scott.payment.openapi.client.payout;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.client.InternalServiceCallExceptionMapper;
import com.scott.payment.openapi.client.InternalServiceResponseParser;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientRequestDTO;
import com.scott.payment.openapi.client.payout.dto.PayoutCreateClientResponseDTO;
import com.scott.payment.openapi.config.PayoutClientProperties;
import com.scott.payment.openapi.result.OpenApiContextualException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 使用内部 HMAC 签名调用代付核心，并保留下游业务码及平台调用结果确定性。
 */
@Service
@Slf4j
public class PayoutInternalRestClient implements PayoutInternalClient {

    /**
     * IPv4 地址格式。
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
     * 域名分隔符。
     */
    private static final String DOMAIN_SEPARATOR = ".";

    /**
     * service-payout 服务名和内部路径是代码级服务契约，不进入 Nacos 或参数设置表。
     */
    private static final String SERVICE_PAYOUT_CREATE_URL = "http://service-payout/internal/payout/create";

    /**
     * 直连 RestTemplate。
     */
    private final RestTemplate directRestTemplate;

    /**
     * 负载均衡 RestTemplate。
     */
    private final RestTemplate loadBalancedRestTemplate;

    /**
     * 代付内部客户端配置。
     */
    private final PayoutClientProperties payoutClientProperties;

    /**
     * 创建 service-payout REST 客户端。
     *
     * @param directRestTemplate       直连 RestTemplate
     * @param loadBalancedRestTemplate 负载均衡 RestTemplate
     * @param payoutClientProperties   代付内部客户端配置
     */
    public PayoutInternalRestClient(@Qualifier("payoutRestTemplate") RestTemplate directRestTemplate,
                                    @Qualifier("payoutLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
                                    PayoutClientProperties payoutClientProperties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.payoutClientProperties = payoutClientProperties;
    }

    /**
     * 调用 service-payout 创建代付交易。
     *
     * @param requestDTO 创建代付内部请求
     * @return 创建代付内部响应
     */
    @Override
    public PayoutCreateClientResponseDTO createPayout(PayoutCreateClientRequestDTO requestDTO) {
        long startNanos = System.nanoTime();
        URI uri = URI.create(SERVICE_PAYOUT_CREATE_URL);
        HttpEntity<String> requestEntity = buildSignedEntity(uri, requestDTO);
        log.info("OpenAPI调用代付核心开始, event: OPENAPI_PAYOUT_CALL_START, stage: PAYOUT_CALL, traceId: {}, operation: PAYOUT_CREATE, merchantId: {}, merchantOrderNo: {}, amount: {}, currency: {}, targetService: {}, path: {}, requestSummary: {}",
                TraceContext.getTraceId(),
                requestDTO == null ? null : requestDTO.getMerchantId(),
                requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                requestDTO == null ? null : requestDTO.getAmount(),
                requestDTO == null ? null : requestDTO.getCurrency(),
                uri.getHost(),
                InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                requestSummary(requestDTO));
        try {
            ResponseEntity<String> responseEntity = chooseRestTemplate(SERVICE_PAYOUT_CREATE_URL).postForEntity(
                    SERVICE_PAYOUT_CREATE_URL,
                    requestEntity,
                    String.class
            );
            String responseBody = responseEntity.getBody();
            CommonResult<PayoutCreateClientResponseDTO> result = InternalServiceResponseParser.parse(
                    responseBody,
                    new TypeReference<CommonResult<PayoutCreateClientResponseDTO>>() {
                    },
                    "service-payout create response is invalid",
                    true
            );
            PayoutCreateClientResponseDTO responseDTO = result == null ? null : result.getData();
            log.info("OpenAPI调用代付核心完成, event: OPENAPI_PAYOUT_CALL_END, stage: PAYOUT_CALL, traceId: {}, operation: PAYOUT_CREATE, merchantId: {}, merchantOrderNo: {}, payoutOrderNo: {}, payoutStatus: {}, httpStatus: {}, platformCode: {}, success: {}, responseSummary: {}, responseLength: {}, responseDigest: {}, durationMs: {}",
                    TraceContext.getTraceId(),
                    requestDTO == null ? null : requestDTO.getMerchantId(),
                    requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                    responseDTO == null ? null : responseDTO.getPayoutOrderNo(),
                    responseDTO == null ? null : responseDTO.getStatus(),
                    responseEntity.getStatusCode().value(),
                    result == null ? null : result.getCode(),
                    CommonResult.isSuccess(result),
                    responseSummary(result),
                    responseBody == null ? 0 : responseBody.length(),
                    digest16(responseBody),
                    elapsedMillis(startNanos));
            return unwrapResult(result);
        } catch (RestClientException exception) {
            ApiException mappedException = InternalServiceCallExceptionMapper.map(
                    exception, "service-payout create call failed", true);
            log.warn("OpenAPI调用代付核心失败, event: OPENAPI_PAYOUT_CALL_END, stage: PAYOUT_CALL, traceId: {}, operation: PAYOUT_CREATE, merchantId: {}, merchantOrderNo: {}, targetService: {}, path: {}, requestDigest: {}, requestSent: {}, outcomeUncertain: {}, durationMs: {}, exceptionType: {}",
                    TraceContext.getTraceId(),
                    requestDTO == null ? null : requestDTO.getMerchantId(),
                    requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                    uri.getHost(),
                    uri.getPath(),
                    digest16(requestEntity.getBody()),
                    requestSent(mappedException, exception),
                    outcomeUncertain(mappedException),
                    elapsedMillis(startNanos),
                    exception.getClass().getSimpleName());
            throw mappedException;
        }
    }

    /**
     * 生成代付内部请求的受控诊断摘要。
     *
     * <p>摘要不包含收款账户、内部签名、请求密文或完整业务报文，只保留定位幂等订单所需的
     * 商户标识、订单号、金额币种、业务时间和请求指纹。</p>
     *
     * @param requestDTO 代付内部请求
     * @return 可写入结构化日志的请求摘要
     */
    private String requestSummary(PayoutCreateClientRequestDTO requestDTO) {
        if (requestDTO == null) {
            return null;
        }
        return JsonUtils.toJsonString(new PayoutRequestDiagnosticSummary(
                requestDTO.getMerchantId(),
                requestDTO.getMerchantOrderNo(),
                requestDTO.getAmount(),
                requestDTO.getCurrency(),
                requestDTO.getTransactionDateTime(),
                requestDTO.getRequestFingerprint()));
    }

    /**
     * 生成代付核心统一响应的受控诊断摘要。
     *
     * @param result 代付核心统一响应
     * @return 仅包含业务码、成功标记和代付状态的响应摘要
     */
    private String responseSummary(CommonResult<PayoutCreateClientResponseDTO> result) {
        if (result == null) {
            return null;
        }
        PayoutCreateClientResponseDTO responseDTO = result.getData();
        return JsonUtils.toJsonString(new PayoutResponseDiagnosticSummary(
                result.getCode(),
                CommonResult.isSuccess(result),
                responseDTO == null ? null : responseDTO.getPayoutOrderNo(),
                responseDTO == null ? null : responseDTO.getMerchantOrderNo(),
                responseDTO == null ? null : responseDTO.getStatus()));
    }

    /**
     * 从结构化异常读取请求是否越过发送边界。
     *
     * @param mappedException 内部服务调用映射后的异常
     * @param sourceException Spring REST 客户端原始异常
     * @return 已知发送事实；无法确定或非结构化异常时返回 null
     */
    private Boolean requestSent(ApiException mappedException, RestClientException sourceException) {
        if (mappedException instanceof OpenApiContextualException contextualException
                && contextualException.getErrorContext() != null) {
            return contextualException.getErrorContext().requestSent();
        }
        if (sourceException instanceof RestClientResponseException) {
            return Boolean.TRUE;
        }
        return null;
    }

    /**
     * 从结构化异常读取代付结果是否处于 unknown 状态。
     *
     * @param exception 内部服务调用映射后的异常
     * @return true 表示写操作可能已被受理但未取得可信响应
     */
    private Boolean outcomeUncertain(ApiException exception) {
        if (exception instanceof OpenApiContextualException contextualException
                && contextualException.getErrorContext() != null) {
            return contextualException.getErrorContext().outcomeUncertain();
        }
        return Boolean.FALSE;
    }

    /**
     * 计算日志关联使用的 SHA-256 短摘要。
     *
     * @param value 请求或响应正文
     * @return SHA-256 前 16 位十六进制；正文为空时返回 null
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
     * 计算 OpenAPI 调用代付核心的本地耗时。
     *
     * @param startNanos 调用开始时的纳秒时间戳
     * @return 调用耗时，单位为毫秒
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 根据 URL 选择客户端。
     *
     * @param createUrl service-payout 创建接口地址
     * @return 对应的 RestTemplate
     */
    private RestTemplate chooseRestTemplate(String createUrl) {
        URI uri = URI.create(createUrl);
        String host = uri.getHost();
        if (host == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, "service-payout url host is empty");
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
     * @param requestDTO 创建代付内部请求
     * @return 带签名头的请求实体
     */
    private HttpEntity<String> buildSignedEntity(URI uri, PayoutCreateClientRequestDTO requestDTO) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String caller = payoutClientProperties.getInternalCaller();
        String requestBody = JsonUtils.toJsonString(requestDTO);
        String signature = InternalServiceSignature.sign(
                "POST",
                InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                timestamp,
                nonce,
                caller,
                InternalServiceSignature.payloadSha256(requestBody),
                payoutClientProperties.getInternalSecret()
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
     * 解包内部服务统一响应。
     *
     * @param result 内部服务统一响应
     * @return 创建代付内部响应
     */
    private PayoutCreateClientResponseDTO unwrapResult(CommonResult<PayoutCreateClientResponseDTO> result) {
        if (result == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, "service-payout response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            throw new ApiException(OpenApiResultCode.SYSTEM_ERROR, "service-payout response data is empty");
        }
        return result.getData();
    }

    /**
     * 承载允许写入代付调用日志的请求字段。
     *
     * @param merchantId 商户号
     * @param merchantOrderNo 商户代付单号
     * @param amount 代付金额
     * @param currency 代付币种
     * @param transactionDateTime 商户业务时间
     * @param requestFingerprint 幂等请求指纹
     */
    private record PayoutRequestDiagnosticSummary(
            String merchantId,
            String merchantOrderNo,
            java.math.BigDecimal amount,
            String currency,
            java.time.LocalDateTime transactionDateTime,
            String requestFingerprint) {
    }

    /**
     * 承载允许写入代付调用日志的响应字段。
     *
     * @param platformCode 平台业务码
     * @param success 统一响应是否成功
     * @param payoutOrderNo 平台代付单号
     * @param merchantOrderNo 商户代付单号
     * @param payoutStatus 当前代付状态
     */
    private record PayoutResponseDiagnosticSummary(
            String platformCode,
            boolean success,
            String payoutOrderNo,
            String merchantOrderNo,
            String payoutStatus) {
    }
}
