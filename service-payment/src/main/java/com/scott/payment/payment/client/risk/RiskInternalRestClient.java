package com.scott.payment.payment.client.risk;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientRequestDTO;
import com.scott.payment.payment.client.risk.dto.RiskPaymentEvaluateClientResponseDTO;
import com.scott.payment.payment.config.RiskClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskInternalRestClient
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : service-risk REST 客户端，位于 service-payment 客户端层，负责内部 HMAC 签名、服务发现选择和统一响应解包。
 * @status : create
 */
@Service
@ConditionalOnProperty(prefix = "payment.risk-client", name = "remote-enabled", havingValue = "true")
@Slf4j
public class RiskInternalRestClient implements RiskInternalClient {

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
     * 直连 RestTemplate，用于 localhost、IP 或完整域名。
     */
    private final RestTemplate directRestTemplate;

    /**
     * 负载均衡 RestTemplate，用于 `http://service-risk/...` 这类服务名。
     */
    private final RestTemplate loadBalancedRestTemplate;

    /**
     * 风控内部客户端配置。
     */
    private final RiskClientProperties riskClientProperties;

    /**
     * 创建 service-risk REST 客户端。
     *
     * @param directRestTemplate       直连 RestTemplate
     * @param loadBalancedRestTemplate 负载均衡 RestTemplate
     * @param riskClientProperties     风控内部客户端配置
     */
    public RiskInternalRestClient(@Qualifier("riskRestTemplate") RestTemplate directRestTemplate,
                                  @Qualifier("riskLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
                                  RiskClientProperties riskClientProperties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.riskClientProperties = riskClientProperties;
    }

    /**
     * 调用 service-risk 执行支付路由前风控评估。
     *
     * @param requestDTO 风控评估请求
     * @return 风控评估响应
     */
    @Override
    public RiskPaymentEvaluateClientResponseDTO evaluatePayment(RiskPaymentEvaluateClientRequestDTO requestDTO) {
        long startNanos = System.nanoTime();
        String evaluateUrl = riskClientProperties.getEvaluateUrl();
        URI uri = URI.create(evaluateUrl);
        log.info("event: PAYMENT_RISK_CALL_START stage=RISK_CALL traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} currency: {} amount: {} targetService: {} path: {} requestSummary: {}",
                TraceContext.getTraceId(),
                requestDTO == null ? null : requestDTO.getMerchantId(),
                requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                requestDTO == null ? null : requestDTO.getTransactionId(),
                requestDTO == null ? null : requestDTO.getTransactionType(),
                requestDTO == null ? null : requestDTO.getCurrency(),
                requestDTO == null ? null : requestDTO.getAmount(),
                uri.getHost(),
                uri.getPath(),
                requestSummary(requestDTO));
        try {
            String responseBody = chooseRestTemplate(evaluateUrl).postForObject(
                    evaluateUrl,
                    buildSignedEntity(uri, requestDTO),
                    String.class
            );
            CommonResult<RiskPaymentEvaluateClientResponseDTO> result = JsonUtils.parseObject(
                    responseBody,
                    new TypeReference<CommonResult<RiskPaymentEvaluateClientResponseDTO>>() {
                    }
            );
            RiskPaymentEvaluateClientResponseDTO responseDTO = unwrapResult(result);
            log.info("event: PAYMENT_RISK_CALL_END stage=RISK_CALL traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} decision: {} riskRecordNo: {} reasonCode: {} platformCode: {} responseDigest: {} responseSummary: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    requestDTO == null ? null : requestDTO.getMerchantId(),
                    requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                    requestDTO == null ? null : requestDTO.getTransactionId(),
                    requestDTO == null ? null : requestDTO.getTransactionType(),
                    responseDTO.getDecision(),
                    responseDTO.getRiskRecordNo(),
                    responseDTO.getReasonCode(),
                    result == null ? null : result.getCode(),
                    digest16(responseBody),
                    responseSummary(responseDTO),
                    elapsedMillis(startNanos));
            return responseDTO;
        } catch (RestClientException exception) {
            log.warn("event: PAYMENT_RISK_CALL_END stage=RISK_CALL traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} targetService: {} path: {} durationMs: {} exceptionType: {}",
                    TraceContext.getTraceId(),
                    requestDTO == null ? null : requestDTO.getMerchantId(),
                    requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                    requestDTO == null ? null : requestDTO.getTransactionId(),
                    requestDTO == null ? null : requestDTO.getTransactionType(),
                    uri.getHost(),
                    uri.getPath(),
                    elapsedMillis(startNanos),
                    exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-risk call failed", exception);
        }
    }

    private RestTemplate chooseRestTemplate(String evaluateUrl) {
        URI uri = URI.create(evaluateUrl);
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-risk url host is empty");
        }
        if (LOCALHOST.equalsIgnoreCase(host) || IPV6_LOOPBACK.equals(host)
                || IPV4_HOST_PATTERN.matcher(host).matches() || host.contains(DOMAIN_SEPARATOR)) {
            return directRestTemplate;
        }
        return loadBalancedRestTemplate;
    }

    private HttpEntity<RiskPaymentEvaluateClientRequestDTO> buildSignedEntity(URI uri, RiskPaymentEvaluateClientRequestDTO requestDTO) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String caller = riskClientProperties.getInternalCaller();
        String signature = InternalServiceSignature.sign(
                "POST",
                uri.getPath(),
                timestamp,
                nonce,
                caller,
                riskClientProperties.getInternalSecret()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.add(InternalServiceSignature.HEADER_CALLER, caller);
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(requestDTO, headers);
    }

    /**
     * 生成风控请求脱敏摘要。
     *
     * @param requestDTO 风控请求
     * @return 可写入日志的 JSON 摘要
     */
    private String requestSummary(RiskPaymentEvaluateClientRequestDTO requestDTO) {
        return requestDTO == null ? null : SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(requestDTO));
    }

    /**
     * 生成风控响应脱敏摘要。
     *
     * @param responseDTO 风控响应
     * @return 可写入日志的 JSON 摘要
     */
    private String responseSummary(RiskPaymentEvaluateClientResponseDTO responseDTO) {
        return responseDTO == null ? null : SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(responseDTO));
    }

    /**
     * 计算耗时毫秒。
     *
     * @param startNanos 起始纳秒
     * @return 耗时毫秒
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 计算响应体短摘要。
     *
     * @param value 原始响应体
     * @return SHA-256 前 16 位；空响应返回 null
     */
    private String digest16(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            return "sha256_unavailable";
        }
    }

    /**
     * 转换解包结果，把下游响应、异常或包装结果映射为当前模块统一语义。
     * <p>
     * 前置条件：调用方已准备 支付核心服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param result 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private RiskPaymentEvaluateClientResponseDTO unwrapResult(CommonResult<RiskPaymentEvaluateClientResponseDTO> result) {
        if (result == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-risk response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ServiceException(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-risk response data is empty");
        }
        return result.getData();
    }
}
