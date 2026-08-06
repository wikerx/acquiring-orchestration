package com.scott.payment.merchant.client.admin;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.merchant.config.AdminInternalClientProperties;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.IpWhitelistItem;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.IpWhitelistSubmitRequest;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlItem;
import com.scott.payment.merchant.dto.access.MerchantAccessConfigDTOs.SourceUrlSubmitRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAccessAdminRestClient
 * @date : 2026-08-06 00:00
 * @description : service-admin 商户访问配置 REST 客户端，为每次内部调用生成独立 HMAC 时间戳、nonce 和签名。
 * @status : create
 */
@Slf4j
@Service
public class MerchantAccessAdminRestClient implements MerchantAccessAdminClient {

    private static final String BASE_URL = "http://service-admin";
    private static final String SOURCE_URL_SEARCH = "/source-urls/search";
    private static final String SOURCE_URL_SUBMIT = "/source-urls";
    private static final String IP_SEARCH = "/ip-whitelists/search";
    private static final String IP_SUBMIT = "/ip-whitelists";

    private final RestTemplate restTemplate;
    private final AdminInternalClientProperties properties;

    /** 创建管理系统内部客户端。 */
    public MerchantAccessAdminRestClient(
            @Qualifier("merchantAdminInternalRestTemplate") RestTemplate restTemplate,
            AdminInternalClientProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public List<SourceUrlItem> listSourceUrls(String merchantId) {
        CommonResult<List<SourceUrlItem>> result = post(uri(merchantId, SOURCE_URL_SEARCH), Map.of(),
                new TypeReference<CommonResult<List<SourceUrlItem>>>() { });
        return unwrap(result);
    }

    @Override
    public List<SourceUrlItem> submitSourceUrls(String merchantId, SourceUrlSubmitRequest request) {
        CommonResult<List<SourceUrlItem>> result = post(uri(merchantId, SOURCE_URL_SUBMIT), request,
                new TypeReference<CommonResult<List<SourceUrlItem>>>() { });
        return unwrap(result);
    }

    @Override
    public List<IpWhitelistItem> listIpWhitelists(String merchantId) {
        CommonResult<List<IpWhitelistItem>> result = post(uri(merchantId, IP_SEARCH), Map.of(),
                new TypeReference<CommonResult<List<IpWhitelistItem>>>() { });
        return unwrap(result);
    }

    @Override
    public List<IpWhitelistItem> submitIpWhitelists(String merchantId, IpWhitelistSubmitRequest request) {
        CommonResult<List<IpWhitelistItem>> result = post(uri(merchantId, IP_SUBMIT), request,
                new TypeReference<CommonResult<List<IpWhitelistItem>>>() { });
        return unwrap(result);
    }

    private <T> T post(URI uri, Object body, TypeReference<T> typeReference) {
        try {
            String responseBody = restTemplate.exchange(
                    uri, HttpMethod.POST, signedEntity(uri, body), String.class).getBody();
            return JsonUtils.parseObject(responseBody, typeReference);
        } catch (RestClientException exception) {
            log.warn("service-admin merchant access call failed, path: {}, exceptionType: {}",
                    uri.getPath(), exception.getClass().getSimpleName());
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-admin merchant access call failed");
        }
    }

    private URI uri(String merchantId, String endpoint) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "merchantId is required");
        }
        return UriComponentsBuilder.fromUriString(BASE_URL)
                .pathSegment("internal", "merchant", merchantId.trim(), "access-config")
                .path(endpoint)
                .build()
                .encode()
                .toUri();
    }

    private HttpEntity<String> signedEntity(URI uri, Object body) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String caller = properties.getInternalCaller();
        String signature = InternalServiceSignature.sign(
                HttpMethod.POST.name(), uri.getRawPath(), timestamp, nonce, caller, properties.getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalServiceSignature.HEADER_CALLER, caller);
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(JsonUtils.toJsonString(body), headers);
    }

    private <T> T unwrap(CommonResult<T> result) {
        if (result == null || !CommonResult.isSuccess(result)) {
            throw new ApiException(result == null ? ApiResultEnum.BAD_GATEWAY.getCode() : result.getCode(),
                    result == null ? "service-admin response is empty" : result.getMessage());
        }
        return result.getData();
    }
}
