package com.scott.payment.admin.client.settlement;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.admin.config.SettlementInternalClientProperties;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchCommandResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.InternalBatchCommandRequest;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
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
 * @classname : SettlementInternalRestClient
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : Admin 结算 HMAC REST 客户端；签名覆盖方法、路径、查询、时间戳、nonce、caller 和请求体摘要。
 * @status : create
 */
@Service
@Slf4j
public class SettlementInternalRestClient implements SettlementInternalClient {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final String BATCHES = "/internal/settlement/v1/batches";

    private final RestTemplate direct;
    private final RestTemplate loadBalanced;
    private final SettlementInternalClientProperties properties;

    public SettlementInternalRestClient(
            @Qualifier("adminSettlementInternalRestTemplate") RestTemplate direct,
            @Qualifier("adminSettlementInternalLoadBalancedRestTemplate") RestTemplate loadBalanced,
            SettlementInternalClientProperties properties) {
        this.direct = direct;
        this.loadBalanced = loadBalanced;
        this.properties = properties;
    }

    @Override
    public BatchSearchResponse search(BatchSearchRequest request) {
        return post(BATCHES + "/search", request,
                new TypeReference<CommonResult<BatchSearchResponse>>() { });
    }

    @Override
    public BatchDetailResponse detail(String settlementBatchNo) {
        return exchange(URI.create(baseUrl() + BATCHES + "/" + settlementBatchNo),
                HttpMethod.GET, null, new TypeReference<CommonResult<BatchDetailResponse>>() { });
    }

    @Override
    public BatchCommandResponse cancel(String settlementBatchNo, InternalBatchCommandRequest request) {
        return post(BATCHES + "/" + settlementBatchNo + "/cancel", request,
                new TypeReference<CommonResult<BatchCommandResponse>>() { });
    }

    @Override
    public BatchCommandResponse reverse(String settlementBatchNo, InternalBatchCommandRequest request) {
        return post(BATCHES + "/" + settlementBatchNo + "/reverse", request,
                new TypeReference<CommonResult<BatchCommandResponse>>() { });
    }

    private <T> T post(String path, Object request, TypeReference<CommonResult<T>> type) {
        return exchange(URI.create(baseUrl() + path), HttpMethod.POST, request, type);
    }

    /** 对内部请求签名并统一解包 CommonResult，远端异常不向浏览器暴露服务内部正文。 */
    private <T> T exchange(URI uri, HttpMethod method, Object request,
                           TypeReference<CommonResult<T>> type) {
        try {
            String body = choose(uri).exchange(
                    uri, method, signed(uri, method, request), String.class).getBody();
            CommonResult<T> result = JsonUtils.parseObject(body, type);
            if (result == null) {
                throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                        "service-settlement response is empty");
            }
            if (!CommonResult.isSuccess(result)) {
                throw new ServiceException(result.getCode(), result.getMessage());
            }
            return result.getData();
        } catch (HttpStatusCodeException exception) {
            log.warn("service-settlement management call rejected, path: {}, status: {}",
                    uri.getPath(), exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-settlement management call rejected");
        } catch (RestClientException exception) {
            log.warn("service-settlement management call failed, path: {}, exceptionType: {}",
                    uri.getPath(), exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-settlement management call failed");
        }
    }

    private HttpEntity<String> signed(URI uri, HttpMethod method, Object request) {
        String body = request == null ? null : JsonUtils.toJsonString(request);
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String signature = InternalServiceSignature.sign(
                method.name(), InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                timestamp, nonce, properties.getInternalCaller(),
                InternalServiceSignature.payloadSha256(body), properties.getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        if (request != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        headers.add(InternalServiceSignature.HEADER_CALLER, properties.getInternalCaller());
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(body, headers);
    }

    private RestTemplate choose(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-settlement host is empty");
        }
        return "localhost".equalsIgnoreCase(host) || "::1".equals(host)
                || IPV4.matcher(host).matches() || host.contains(".") ? direct : loadBalanced;
    }

    private String baseUrl() {
        String value = properties.getBaseUrl();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("admin settlement-client base-url is required");
        }
        String normalized = value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
