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

/**
 * 带 HMAC、防重放随机数、有界超时和 CommonResult 解包的清分补偿 REST 客户端；不承载补偿业务规则。
 */
@Service
@Slf4j
public class ClearingInternalRestClient implements ClearingInternalClient {

    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private static final String PATH = "/internal/clearing/v1/compensations/scan";

    private final RestTemplate direct;
    private final RestTemplate loadBalanced;
    private final ClearingInternalClientProperties properties;

    /**
     * 创建清分补偿内部 REST 客户端。
     *
     * @param direct 固定地址直连客户端
     * @param loadBalanced 服务发现负载均衡客户端
     * @param properties 清分内部调用地址、身份和超时配置
     */
    public ClearingInternalRestClient(
            @Qualifier("jobClearingInternalRestTemplate") RestTemplate direct,
            @Qualifier("jobClearingInternalLoadBalancedRestTemplate") RestTemplate loadBalanced,
            ClearingInternalClientProperties properties) {
        this.direct = direct;
        this.loadBalanced = loadBalanced;
        this.properties = properties;
    }

    /**
     * 调用 service-clearing 在指定季度半开区间内执行一页幂等补偿扫描。
     * 调用失败或响应结构无效时抛出内部服务异常，不返回部分统计。
     *
     * @param request 扫描模式、时间范围、复合游标和页大小
     * @return 本页处理统计及下一页游标
     */
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
            log.warn("清分服务补偿调用被拒绝, event: JOB_CLEARING_COMPENSATION_REJECTED, status: {}",
                    exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-clearing compensation call rejected");
        } catch (RestClientException exception) {
            log.warn("清分服务补偿调用失败, event: JOB_CLEARING_COMPENSATION_FAILED, exceptionType: {}",
                    exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-clearing compensation call failed");
        }
    }

    /**
     * 序列化补偿请求并生成绑定请求目标、正文摘要、时间戳和 nonce 的 HMAC 请求头。
     *
     * @param uri 清分内部接口 URI
     * @param request 补偿扫描请求
     * @return 已签名 JSON 请求实体
     */
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

    /**
     * IP、localhost 和完整域名使用直连客户端，逻辑服务名使用负载均衡客户端。
     *
     * @param uri 已校验的服务地址
     * @return 与地址类型匹配且配置了有界超时的 RestTemplate
     */
    private RestTemplate choose(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-clearing host is empty");
        }
        return "localhost".equalsIgnoreCase(host) || "::1".equals(host)
                || IPV4.matcher(host).matches() || host.contains(".") ? direct : loadBalanced;
    }

    /**
     * 规范化清分服务根地址，避免拼接路径时产生重复斜杠。
     *
     * @param value 配置的服务根地址
     * @return 不以斜杠结尾的服务根地址
     */
    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("job clearing-client base-url is required");
        }
        String normalized = value.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
