package com.scott.payment.job.client.clearing;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.job.client.clearing.dto.ClearingCompensationClientDTOs.Request;
import com.scott.payment.job.client.clearing.dto.ClearingCompensationClientDTOs.Response;
import com.scott.payment.job.config.ClearingInternalClientProperties;
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

/** 带 HMAC、防重放随机数和 CommonResult 解包的清分内部 REST 客户端。 */
@Service
@Slf4j
public class ClearingInternalRestClient implements ClearingInternalClient {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final String PATH = "/internal/clearing/v1/compensations/scan";

    private final RestTemplate direct;
    private final RestTemplate loadBalanced;
    private final ClearingInternalClientProperties properties;

    public ClearingInternalRestClient(
            @Qualifier("jobClearingInternalRestTemplate") RestTemplate direct,
            @Qualifier("jobClearingInternalLoadBalancedRestTemplate") RestTemplate loadBalanced,
            ClearingInternalClientProperties properties) {
        this.direct = direct;
        this.loadBalanced = loadBalanced;
        this.properties = properties;
    }

    @Override
    public Response scan(Request request) {
        URI uri = URI.create(normalizeBaseUrl(properties.getBaseUrl()) + PATH);
        try {
            String body = choose(uri).exchange(uri, HttpMethod.POST, signed(uri, request), String.class).getBody();
            CommonResult<Response> result = JsonUtils.parseObject(
                    body, new TypeReference<CommonResult<Response>>() { });
            if (result == null || !CommonResult.isSuccess(result) || result.getData() == null) {
                throw new ServiceException(result == null ? ApiResultEnum.BAD_GATEWAY.getCode() : result.getCode(),
                        result == null ? "service-clearing response is empty" : result.getMessage());
            }
            return result.getData();
        } catch (HttpStatusCodeException exception) {
            log.warn("service-clearing compensation call rejected, status: {}",
                    exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-clearing compensation call rejected");
        } catch (RestClientException exception) {
            log.warn("service-clearing compensation call failed, exceptionType: {}",
                    exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-clearing compensation call failed");
        }
    }

    private HttpEntity<String> signed(URI uri, Request request) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String body = JsonUtils.toJsonString(request);
        String signature = InternalServiceSignature.sign(
                HttpMethod.POST.name(), InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                timestamp, nonce, properties.getInternalCaller(),
                InternalServiceSignature.payloadSha256(body), properties.getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalServiceSignature.HEADER_CALLER, properties.getInternalCaller());
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(body, headers);
    }

    private RestTemplate choose(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-clearing host is empty");
        }
        return "localhost".equalsIgnoreCase(host) || "::1".equals(host)
                || IPV4.matcher(host).matches() || host.contains(".") ? direct : loadBalanced;
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("job clearing-client base-url is required");
        }
        String normalized = value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
