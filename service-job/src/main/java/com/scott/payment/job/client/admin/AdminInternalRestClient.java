package com.scott.payment.job.client.admin;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.job.client.admin.dto.ExchangeRateAlertEvaluationClientDTOs.Request;
import com.scott.payment.job.client.admin.dto.ExchangeRateAlertEvaluationClientDTOs.Response;
import com.scott.payment.job.client.admin.dto.OperationalWorkItemSyncClientDTOs;
import com.scott.payment.job.config.AdminInternalClientProperties;
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
 * service-admin 内部 REST 客户端，负责服务发现、HMAC 签名和响应解包。
 */
@Slf4j
@Service
public class AdminInternalRestClient implements AdminInternalClient {

    private static final Pattern IPV4_HOST_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    /** service-admin 服务发现地址。 */
    private static final String SERVICE_ADMIN_BASE_URL = "http://service-admin";

    /** 汇率波动评估内部接口路径。 */
    private static final String EVALUATE_ALERTS_PATH = "/internal/admin/exchange/alerts/evaluate";

    /** 统一运营待办同步内部接口路径。 */
    private static final String SYNC_OPERATIONAL_WORK_ITEMS_PATH = "/internal/admin/operations/work-items/sync";

    private final RestTemplate directRestTemplate;
    private final RestTemplate loadBalancedRestTemplate;
    private final AdminInternalClientProperties properties;

    /**
     * 创建 service-admin 内部客户端。
     *
     * @param directRestTemplate 直连客户端
     * @param loadBalancedRestTemplate 服务发现客户端
     * @param properties 内部签名配置
     */
    public AdminInternalRestClient(
            @Qualifier("jobAdminInternalRestTemplate") RestTemplate directRestTemplate,
            @Qualifier("jobAdminInternalLoadBalancedRestTemplate") RestTemplate loadBalancedRestTemplate,
            AdminInternalClientProperties properties) {
        this.directRestTemplate = directRestTemplate;
        this.loadBalancedRestTemplate = loadBalancedRestTemplate;
        this.properties = properties;
    }

    /**
     * 调用管理服务执行汇率波动评估。
     *
     * @param request 任务运行标识和评估基准时间
     * @return 管理服务已持久化的幂等评估结果
     */
    @Override
    public Response evaluateExchangeRateAlerts(Request request) {
        URI uri = URI.create(SERVICE_ADMIN_BASE_URL + EVALUATE_ALERTS_PATH);
        try {
            String responseBody = chooseRestTemplate(uri).exchange(
                    uri,
                    HttpMethod.POST,
                    buildSignedEntity(uri, request),
                    String.class).getBody();
            CommonResult<Response> result = JsonUtils.parseObject(
                    responseBody, new TypeReference<CommonResult<Response>>() {
                    });
            if (result == null || result.getData() == null) {
                throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                        "service-admin response is empty");
            }
            if (!CommonResult.isSuccess(result)) {
                throw new ServiceException(result.getCode(), result.getMessage());
            }
            return result.getData();
        } catch (HttpStatusCodeException exception) {
            log.warn("管理服务汇率波动评估调用被拒绝, event: JOB_ADMIN_RATE_ALERT_REJECTED, targetPath: {}, httpStatus: {}",
                    uri.getPath(), exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-admin exchange rate alert call rejected");
        } catch (RestClientException exception) {
            log.warn("管理服务汇率波动评估调用失败, event: JOB_ADMIN_RATE_ALERT_FAILED, targetPath: {}, exceptionType: {}",
                    uri.getPath(), exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-admin exchange rate alert call failed");
        }
    }

    /** 调用管理服务幂等同步统一运营待办。 */
    @Override
    public OperationalWorkItemSyncClientDTOs.Response synchronizeOperationalWorkItems(
            OperationalWorkItemSyncClientDTOs.Request request) {
        URI uri = URI.create(SERVICE_ADMIN_BASE_URL + SYNC_OPERATIONAL_WORK_ITEMS_PATH);
        try {
            String responseBody = chooseRestTemplate(uri).exchange(
                    uri,
                    HttpMethod.POST,
                    buildSignedEntity(uri, request),
                    String.class).getBody();
            CommonResult<OperationalWorkItemSyncClientDTOs.Response> result =
                    JsonUtils.parseObject(responseBody,
                            new TypeReference<CommonResult<OperationalWorkItemSyncClientDTOs.Response>>() {
                            });
            if (result == null || result.getData() == null) {
                throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                        "service-admin response is empty");
            }
            if (!CommonResult.isSuccess(result)) {
                throw new ServiceException(result.getCode(), result.getMessage());
            }
            return result.getData();
        } catch (HttpStatusCodeException exception) {
            log.warn("管理服务统一运营待办同步调用被拒绝, event: JOB_ADMIN_WORK_ITEM_SYNC_REJECTED, targetPath: {}, httpStatus: {}",
                    uri.getPath(), exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-admin operational work item sync rejected");
        } catch (RestClientException exception) {
            log.warn("管理服务统一运营待办同步调用失败, event: JOB_ADMIN_WORK_ITEM_SYNC_FAILED, targetPath: {}, exceptionType: {}",
                    uri.getPath(), exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-admin operational work item sync failed");
        }
    }

    /**
     * 构造绑定方法、路径、请求体、时间戳和 nonce 的签名请求。
     */
    private HttpEntity<String> buildSignedEntity(URI uri, Object request) {
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String body = JsonUtils.toJsonString(request);
        String caller = properties.getInternalCaller();
        String signature = InternalServiceSignature.sign(
                HttpMethod.POST.name(),
                InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                timestamp,
                nonce,
                caller,
                InternalServiceSignature.payloadSha256(body),
                properties.getInternalSecret());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(InternalServiceSignature.HEADER_CALLER, caller);
        headers.set(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
        headers.set(InternalServiceSignature.HEADER_NONCE, nonce);
        headers.set(InternalServiceSignature.HEADER_SIGNATURE, signature);
        return new HttpEntity<>(body, headers);
    }

    /**
     * 服务名使用负载均衡客户端，IP、localhost 和域名使用直连客户端。
     */
    private RestTemplate chooseRestTemplate(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-admin URL host is empty");
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
