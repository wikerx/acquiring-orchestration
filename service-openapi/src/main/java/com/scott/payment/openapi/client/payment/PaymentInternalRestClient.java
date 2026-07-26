package com.scott.payment.openapi.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentCreateClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.PaymentQueryClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientRequestDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientResponseDTO;
import com.scott.payment.openapi.client.payment.dto.TransactionMerchantApiResponseLogUpdateClientRequestDTO;
import com.scott.payment.openapi.config.PaymentClientProperties;
import com.scott.payment.openapi.enums.OpenApiPaymentOperationEnum;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalRestClient
 * @date : 2026-07-14 12:30
 * @email : scott_x@163.com
 * @description : service-payment REST 客户端，位于 service-openapi 客户端层，为收单交易动作封装内部 HMAC 签名、负载均衡选择和统一响应解包。
 * @status : create
 */
@Service
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
        try {
            String targetUrl = targetUrl(OpenApiPaymentOperationEnum.QUERY);
            String responseBody = chooseRestTemplate(targetUrl).postForObject(
                    targetUrl,
                    buildSignedEntity(URI.create(targetUrl), requestDTO),
                    String.class
            );
            CommonResult<PaymentQueryClientResponseDTO> result = JsonUtils.parseObject(
                    responseBody,
                    new TypeReference<CommonResult<PaymentQueryClientResponseDTO>>() {
                    }
            );
            return unwrapQueryResult(result);
        } catch (RestClientException exception) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment query call failed");
        }
    }

    /**
     * 调用 service-payment 记录渠道回调。
     *
     * @param requestDTO 渠道回调内部请求
     * @return 渠道回调记录响应
     */
    @Override
    public TransactionChannelCallbackClientResponseDTO recordChannelCallback(TransactionChannelCallbackClientRequestDTO requestDTO) {
        try {
            String targetUrl = paymentClientProperties.getChannelCallbackUrl();
            String responseBody = chooseRestTemplate(targetUrl).postForObject(
                    targetUrl,
                    buildSignedEntity(URI.create(targetUrl), requestDTO),
                    String.class
            );
            CommonResult<TransactionChannelCallbackClientResponseDTO> result = JsonUtils.parseObject(
                    responseBody,
                    new TypeReference<CommonResult<TransactionChannelCallbackClientResponseDTO>>() {
                    }
            );
            return unwrapCallbackResult(result);
        } catch (RestClientException exception) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment callback call failed");
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
        try {
            String targetUrl = paymentClientProperties.getMerchantApiResponseLogUrl();
            String responseBody = chooseRestTemplate(targetUrl).postForObject(
                    targetUrl,
                    buildSignedEntity(URI.create(targetUrl), requestDTO),
                    String.class
            );
            CommonResult<Boolean> result = JsonUtils.parseObject(
                    responseBody,
                    new TypeReference<CommonResult<Boolean>>() {
                    }
            );
            return unwrapBooleanResult(result);
        } catch (RestClientException exception) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment merchant api log update failed");
        }
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
        try {
            String targetUrl = targetUrl(operation);
            String responseBody = chooseRestTemplate(targetUrl).postForObject(
                    targetUrl,
                    buildSignedEntity(URI.create(targetUrl), requestDTO),
                    String.class
            );
            CommonResult<PaymentCreateClientResponseDTO> result = JsonUtils.parseObject(
                    responseBody,
                    new TypeReference<CommonResult<PaymentCreateClientResponseDTO>>() {
                    }
            );
            return unwrapResult(result);
        } catch (RestClientException exception) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment call failed");
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
            return paymentClientProperties.getPaymentUrl();
        }
        if (OpenApiPaymentOperationEnum.AUTHORIZATION == operation) {
            return paymentClientProperties.getAuthorizationUrl();
        }
        if (OpenApiPaymentOperationEnum.PRE_AUTHORIZATION == operation) {
            return paymentClientProperties.getPreAuthorizationUrl();
        }
        if (OpenApiPaymentOperationEnum.INCREMENTAL_AUTHORIZATION == operation) {
            return paymentClientProperties.getIncrementalAuthorizationUrl();
        }
        if (OpenApiPaymentOperationEnum.CAPTURE == operation) {
            return paymentClientProperties.getCaptureUrl();
        }
        if (OpenApiPaymentOperationEnum.PRE_AUTH_COMPLETION == operation) {
            return paymentClientProperties.getPreAuthCompletionUrl();
        }
        if (OpenApiPaymentOperationEnum.REFUND == operation) {
            return paymentClientProperties.getRefundUrl();
        }
        if (OpenApiPaymentOperationEnum.VOID == operation) {
            return paymentClientProperties.getVoidUrl();
        }
        if (OpenApiPaymentOperationEnum.QUERY == operation) {
            return paymentClientProperties.getQueryUrl();
        }
        throw new ApiException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED);
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
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment url host is empty");
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
        String signature = InternalServiceSignature.sign(
                "POST",
                uri.getPath(),
                timestamp,
                nonce,
                caller,
                paymentClientProperties.getInternalSecret()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalServiceSignature.HEADER_CALLER, caller);
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(JsonUtils.toJsonString(requestDTO), headers);
    }

    /**
     * 解包内部服务统一响应。
     *
     * @param result 内部服务统一响应
     * @return 创建交易内部响应
     */
    private PaymentCreateClientResponseDTO unwrapResult(CommonResult<PaymentCreateClientResponseDTO> result) {
        if (result == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment response data is empty");
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
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment query response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment query response data is empty");
        }
        return result.getData();
    }

    /**
     * 解包渠道回调内部服务统一响应。
     *
     * @param result 内部服务统一响应
     * @return 渠道回调记录响应
     */
    private TransactionChannelCallbackClientResponseDTO unwrapCallbackResult(
            CommonResult<TransactionChannelCallbackClientResponseDTO> result) {
        if (result == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment callback response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
        if (result.getData() == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment callback response data is empty");
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
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
        return Boolean.TRUE.equals(result.getData());
    }
}
