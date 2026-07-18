package com.scott.payment.job.client.payment;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.job.client.payment.dto.PaymentMerchantNotificationNotifyDueClientRequestDTO;
import com.scott.payment.job.config.PaymentInternalClientProperties;
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
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : service-payment 内部补偿 REST 客户端，位于 service-job 客户端层，负责内部 HMAC 签名、服务发现选择和统一响应解包。
 * @status : create
 */
@Slf4j
@Service
public class PaymentInternalRestClient implements PaymentInternalClient {

    private static final Pattern IPV4_HOST_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private static final String LOCALHOST = "localhost";

    private static final String IPV6_LOOPBACK = "::1";

    private static final String DOMAIN_SEPARATOR = ".";

    private final RestTemplate directRestTemplate;

    private final RestTemplate loadBalancedRestTemplate;

    private final PaymentInternalClientProperties properties;

    /**
     * 创建 service-payment 内部补偿 REST 客户端。
     *
     * @param directRestTemplate 直连 RestTemplate
     * @param loadBalancedRestTemplate 负载均衡 RestTemplate
     * @param properties 内部客户端配置
     */
    public PaymentInternalRestClient(@Qualifier("jobPaymentInternalRestTemplate") RestTemplate directRestTemplate,
                                     @Qualifier("jobPaymentInternalLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
                                     PaymentInternalClientProperties properties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.properties = properties;
    }

    /**
     * 触发指定交易时间分表中的到期商户通知补偿。
     *
     * @param requestDTO 补偿请求
     * @return 成功通知数量
     */
    @Override
    public Integer notifyDueMerchantNotifications(PaymentMerchantNotificationNotifyDueClientRequestDTO requestDTO) {
        CommonResult<Integer> result = post(
                properties.getMerchantNotificationNotifyDueUrl(),
                requestDTO,
                new TypeReference<CommonResult<Integer>>() {
                });
        return unwrapData(result);
    }

    private <T> T post(String url, Object body, TypeReference<T> typeReference) {
        URI uri = URI.create(url);
        try {
            String responseBody = chooseRestTemplate(uri).exchange(uri, HttpMethod.POST,
                    buildSignedEntity(uri, body), String.class).getBody();
            return JsonUtils.parseObject(responseBody, typeReference);
        } catch (HttpStatusCodeException exception) {
            throw translateHttpException(uri, exception);
        } catch (RestClientException exception) {
            log.warn("service-payment compensation call failed, targetUri={}", uri, exception);
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-payment compensation call failed", exception);
        }
    }

    private HttpEntity<String> buildSignedEntity(URI uri, Object body) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String caller = properties.getInternalCaller();
        String signature = InternalServiceSignature.sign(
                HttpMethod.POST.name(),
                uri.getPath(),
                timestamp,
                nonce,
                caller,
                properties.getInternalSecret()
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalServiceSignature.HEADER_CALLER, caller);
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(body == null ? null : JsonUtils.toJsonString(body), headers);
    }

    private RestTemplate chooseRestTemplate(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-payment url host is empty");
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
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-payment response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ServiceException(result.getCode(), result.getMessage());
        }
        return result.getData();
    }

    private ServiceException translateHttpException(URI uri, HttpStatusCodeException exception) {
        log.warn("service-payment compensation call returned non-success status, targetUri={}, status={}",
                uri,
                exception.getStatusCode().value(),
                exception);
        if (exception.getStatusCode().value() == 401) {
            return new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "service-payment call unauthorized");
        }
        if (exception.getStatusCode().value() == 403) {
            return new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "service-payment call forbidden");
        }
        return new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-payment compensation call failed");
    }
}
