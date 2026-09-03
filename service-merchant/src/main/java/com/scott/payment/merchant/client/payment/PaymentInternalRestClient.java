package com.scott.payment.merchant.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.merchant.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.merchant.config.PaymentInternalClientProperties;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionActionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalRestClient
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : service-payment 内部 REST 客户端，位于 service-merchant 客户端层，为商户后台退款等状态变更动作封装 HMAC 签名和统一响应解包。
 * @status : create
 */
@Service
@Slf4j
public class PaymentInternalRestClient implements PaymentInternalClient {

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
     * 域名点分隔符。
     */
    private static final String DOMAIN_SEPARATOR = ".";

    /**
     * 商户后台调用支付核心的内部动作路径是服务契约，不通过配置中心覆盖。
     */
    private static final String SERVICE_PAYMENT_BASE_URL = "http://service-payment";

    /** 商户发起请款时调用的支付核心内部接口。 */
    private static final String CAPTURE_PATH = "/internal/payment/capture";

    /** 商户发起预授权完成时调用的支付核心内部接口。 */
    private static final String PRE_AUTH_COMPLETION_PATH = "/internal/payment/pre-auth-completion";

    /** 商户发起退款时调用的支付核心内部接口。 */
    private static final String REFUND_PATH = "/internal/payment/refund";

    /** 商户发起撤销时调用的支付核心内部接口。 */
    private static final String VOID_PATH = "/internal/payment/void";

    private final RestTemplate directRestTemplate;

    private final RestTemplate loadBalancedRestTemplate;

    private final PaymentInternalClientProperties properties;

    /**
     * 创建 service-payment 内部 REST 客户端。
     *
     * @param directRestTemplate       直连 RestTemplate
     * @param loadBalancedRestTemplate 负载均衡 RestTemplate
     * @param properties               内部客户端配置
     */
    public PaymentInternalRestClient(@Qualifier("merchantPaymentInternalRestTemplate") RestTemplate directRestTemplate,
                                     @Qualifier("merchantPaymentInternalLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
                                     PaymentInternalClientProperties properties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.properties = properties;
    }

    /**
     * 通过支付核心发起请款动作。
     *
     * @param requestDTO 支付核心内部请款命令
     * @return 请款动作结果
     */
    @Override
    public TransactionActionResponse capture(PaymentTransactionActionClientRequestDTO requestDTO) {
        CommonResult<TransactionActionResponse> result = post(
                servicePaymentUrl(CAPTURE_PATH),
                requestDTO,
                new TypeReference<CommonResult<TransactionActionResponse>>() {
                });
        return unwrapData(result);
    }

    /**
     * 通过支付核心发起预授权完成动作。
     *
     * @param requestDTO 支付核心内部预授权完成命令
     * @return 预授权完成动作结果
     */
    @Override
    public TransactionActionResponse preAuthCompletion(PaymentTransactionActionClientRequestDTO requestDTO) {
        CommonResult<TransactionActionResponse> result = post(
                servicePaymentUrl(PRE_AUTH_COMPLETION_PATH),
                requestDTO,
                new TypeReference<CommonResult<TransactionActionResponse>>() {
                });
        return unwrapData(result);
    }

    /**
     * 通过支付核心发起退款动作。
     *
     * @param requestDTO 支付核心内部退款命令
     * @return 退款动作结果
     */
    @Override
    public TransactionActionResponse refund(PaymentTransactionActionClientRequestDTO requestDTO) {
        CommonResult<TransactionActionResponse> result = post(
                servicePaymentUrl(REFUND_PATH),
                requestDTO,
                new TypeReference<CommonResult<TransactionActionResponse>>() {
                });
        return unwrapData(result);
    }

    /**
     * 通过支付核心发起撤销动作。
     *
     * @param requestDTO 支付核心内部撤销命令
     * @return 撤销动作结果
     */
    @Override
    public TransactionActionResponse voidPayment(PaymentTransactionActionClientRequestDTO requestDTO) {
        CommonResult<TransactionActionResponse> result = post(
                servicePaymentUrl(VOID_PATH),
                requestDTO,
                new TypeReference<CommonResult<TransactionActionResponse>>() {
                });
        return unwrapData(result);
    }

    /**
     * 对商户交易状态变更命令生成支付核心内部签名并解析响应；普通查询不经过该远程边界。
     *
     * @param url 支付核心内部命令地址
     * @param body 已绑定认证商户身份的交易命令
     * @param typeReference 响应泛型
     * @param <T> 响应类型
     * @return 支付核心内部响应
     * @throws ApiException 内部鉴权、网络或响应协议失败时抛出
     */
    private <T> T post(String url, Object body, TypeReference<T> typeReference) {
        URI uri = URI.create(url);
        try {
            String responseBody = chooseRestTemplate(uri).exchange(uri, HttpMethod.POST,
                    buildSignedEntity(uri, HttpMethod.POST, body), String.class).getBody();
            return JsonUtils.parseObject(responseBody, typeReference);
        } catch (HttpStatusCodeException exception) {
            throw translateHttpException(HttpMethod.POST, uri, exception);
        } catch (RestClientException exception) {
            log.warn("service-payment post call failed, targetPath: {}, exceptionType: {}",
                    uri.getPath(), exception.getClass().getSimpleName());
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment post call failed");
        }
    }

    /**
     * 拼接 service-payment 内部接口地址。
     *
     * @param path 内部交易接口路径
     * @return 服务发现基地址与路径组成的 URL
     */
    private String servicePaymentUrl(String path) {
        return SERVICE_PAYMENT_BASE_URL + path;
    }

    /**
     * 创建带服务间签名头的 JSON 请求实体。
     * <p>
     * 签名绑定方法、URI 路径、毫秒时间戳、随机 nonce 和调用方标识；内部密钥、签名和交易
     * 请求体均不得写入日志。
     * </p>
     *
     * @param uri    目标内部接口 URI
     * @param method HTTP 方法
     * @param body   交易请求 DTO
     * @return 已签名请求实体
     */
    private HttpEntity<String> buildSignedEntity(URI uri, HttpMethod method, Object body) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String caller = properties.getInternalCaller();
        String requestBody = body == null ? null : JsonUtils.toJsonString(body);
        String signature = InternalServiceSignature.sign(
                method.name(),
                InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                timestamp,
                nonce,
                caller,
                InternalServiceSignature.payloadSha256(requestBody),
                properties.getInternalSecret()
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
     * 根据 URI 主机形式选择直连或服务发现 RestTemplate。
     *
     * @param uri 目标 URI
     * @return IP、localhost 和域名使用直连模板；服务名使用负载均衡模板
     * @throws ApiException URI 缺少主机时抛出
     */
    private RestTemplate chooseRestTemplate(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment url host is empty");
        }
        if (LOCALHOST.equalsIgnoreCase(host)
                || IPV6_LOOPBACK.equals(host)
                || IPV4_HOST_PATTERN.matcher(host).matches()
                || host.contains(DOMAIN_SEPARATOR)) {
            return directRestTemplate;
        }
        return loadBalancedRestTemplate;
    }

    private <T> T unwrapData(CommonResult<T> result) {
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

    private ApiException translateHttpException(HttpMethod method, URI uri, HttpStatusCodeException exception) {
        log.warn("service-payment {} call returned non-success status, targetPath: {}, status: {}, exceptionType: {}",
                method.name(),
                uri.getPath(),
                exception.getStatusCode().value(),
                exception.getClass().getSimpleName());
        if (exception.getStatusCode().value() == 401) {
            return new ApiException(ApiResultEnum.UNAUTHORIZED, "service-payment call unauthorized");
        }
        if (exception.getStatusCode().value() == 403) {
            return new ApiException(ApiResultEnum.FORBIDDEN, "service-payment call forbidden");
        }
        return new ApiException(ApiResultEnum.BAD_GATEWAY, "service-payment call failed");
    }
}
