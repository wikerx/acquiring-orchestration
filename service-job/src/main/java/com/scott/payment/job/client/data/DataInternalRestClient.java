package com.scott.payment.job.client.data;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.job.client.data.dto.DataMerchantNotificationNotifyDueClientRequestDTO;
import com.scott.payment.job.config.DataInternalClientProperties;
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
 * @classname : DataInternalRestClient
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 内部补偿 REST 客户端，负责服务发现选择、HMAC-SHA256 签名和 CommonResult 解包
 * @status : create
 */
@Slf4j
@Service
public class DataInternalRestClient implements DataInternalClient {

    /** IPv4 主机识别规则，用于选择直连客户端。 */
    private static final Pattern IPV4_HOST_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    /** service-data 服务发现地址。 */
    private static final String SERVICE_DATA_BASE_URL = "http://service-data";

    /** 商户通知到期补偿内部接口路径。 */
    private static final String NOTIFY_DUE_PATH = "/internal/data/merchant-notifications/notify-due";

    /** 直连 HTTP 客户端。 */
    private final RestTemplate directRestTemplate;

    /** Nacos 服务发现 HTTP 客户端。 */
    private final RestTemplate loadBalancedRestTemplate;

    /** 内部调用签名配置。 */
    private final DataInternalClientProperties properties;

    /**
     * 创建 service-data 内部客户端。
     *
     * @param directRestTemplate 直连客户端
     * @param loadBalancedRestTemplate 服务发现客户端
     * @param properties 内部签名配置
     */
    public DataInternalRestClient(
            @Qualifier("jobDataInternalRestTemplate") RestTemplate directRestTemplate,
            @Qualifier("jobDataInternalLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
            DataInternalClientProperties properties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer notifyDueMerchantNotifications(DataMerchantNotificationNotifyDueClientRequestDTO requestDTO) {
        URI uri = URI.create(SERVICE_DATA_BASE_URL + NOTIFY_DUE_PATH);
        try {
            String responseBody = chooseRestTemplate(uri).exchange(
                    uri,
                    HttpMethod.POST,
                    buildSignedEntity(uri, requestDTO),
                    String.class).getBody();
            CommonResult<Integer> result = JsonUtils.parseObject(
                    responseBody,
                    new TypeReference<CommonResult<Integer>>() {
                    });
            if (result == null) {
                throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-data response is empty");
            }
            if (!CommonResult.isSuccess(result)) {
                throw new ServiceException(result.getCode(), result.getMessage());
            }
            return result.getData();
        } catch (HttpStatusCodeException exception) {
            log.warn("service-data compensation call rejected, targetPath: {}, httpStatus: {}",
                    uri.getPath(), exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-data compensation call rejected", exception);
        } catch (RestClientException exception) {
            log.warn("service-data compensation call failed, targetPath: {}, exceptionType: {}",
                    uri.getPath(), exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-data compensation call failed", exception);
        }
    }

    /**
     * 构造绑定 HTTP 方法、路径、时间戳、nonce 和调用方的内部签名请求。
     *
     * @param uri 目标内部 URI
     * @param body 请求 DTO
     * @return JSON 请求实体，签名密钥不会进入请求或日志
     */
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
                properties.getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(InternalServiceSignature.HEADER_CALLER, caller);
        headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(body == null ? null : JsonUtils.toJsonString(body), headers);
    }

    /**
     * 根据主机形式选择直连或服务发现客户端。
     *
     * @param uri 目标 URI
     * @return IP、localhost 和域名使用直连，服务名使用负载均衡客户端
     */
    private RestTemplate chooseRestTemplate(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-data URL host is empty");
        }
        if ("localhost".equalsIgnoreCase(host)
                || "::1".equals(host)
                || IPV4_HOST_PATTERN.matcher(host).matches()
                || host.contains(".")) {
            return directRestTemplate;
        }
        return loadBalancedRestTemplate;
    }
}
