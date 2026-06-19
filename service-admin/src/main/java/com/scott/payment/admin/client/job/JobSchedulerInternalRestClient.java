package com.scott.payment.admin.client.job;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.admin.client.job.dto.JobTaskRemoteSaveRequest;
import com.scott.payment.admin.config.JobSchedulerClientProperties;
import com.scott.payment.admin.dto.monitor.JobExecutorNodeResponse;
import com.scott.payment.admin.dto.monitor.JobHandlerOptionResponse;
import com.scott.payment.admin.dto.monitor.JobManualTriggerRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogQueryRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogResponse;
import com.scott.payment.admin.dto.monitor.JobTaskQueryRequest;
import com.scott.payment.admin.dto.monitor.JobTaskResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerInternalRestClient
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台调用调度中心的 REST 客户端实现
 * @status : create
 */

@Service
@Slf4j
public class JobSchedulerInternalRestClient implements JobSchedulerInternalClient {

    /**
     * 内部鉴权请求头名称。
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

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

    private final RestTemplate directRestTemplate;
    private final JobSchedulerClientProperties jobSchedulerClientProperties;
    private final DiscoveryClient discoveryClient;

    /**
     * 创建调度中心 REST 客户端。
     *
     * @param directRestTemplate       直连 RestTemplate
     * @param jobSchedulerClientProperties 调度中心客户端配置
     * @param discoveryClient          Spring Cloud 服务发现客户端
     */
    public JobSchedulerInternalRestClient(@Qualifier("jobSchedulerRestTemplate") RestTemplate directRestTemplate,
                                          JobSchedulerClientProperties jobSchedulerClientProperties,
                                          DiscoveryClient discoveryClient) {
        this.directRestTemplate = directRestTemplate;
        this.jobSchedulerClientProperties = jobSchedulerClientProperties;
        this.discoveryClient = discoveryClient;
    }

    @Override
    public List<JobHandlerOptionResponse> listHandlers() {
        String responseBody = doGet(jobSchedulerClientProperties.getHandlerListUrl());
        CommonResult<List<JobHandlerOptionResponse>> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<List<JobHandlerOptionResponse>>>() {
                }
        );
        return unwrapData(result);
    }

    @Override
    public PageResult<JobTaskResponse> pageTasks(JobTaskQueryRequest request) {
        String responseBody = doPost(jobSchedulerClientProperties.getTaskSearchUrl(), request);
        CommonResult<PageResult<JobTaskResponse>> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<PageResult<JobTaskResponse>>>() {
                }
        );
        return unwrapData(result);
    }

    @Override
    public JobTaskResponse createTask(JobTaskRemoteSaveRequest request) {
        String responseBody = doPost(jobSchedulerClientProperties.getTaskBaseUrl(), request);
        CommonResult<JobTaskResponse> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<JobTaskResponse>>() {
                }
        );
        return unwrapData(result);
    }

    @Override
    public JobTaskResponse updateTask(Long taskId, JobTaskRemoteSaveRequest request) {
        String responseBody = doPut(jobSchedulerClientProperties.getTaskBaseUrl() + "/" + taskId, request);
        CommonResult<JobTaskResponse> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<JobTaskResponse>>() {
                }
        );
        return unwrapData(result);
    }

    @Override
    public JobTaskResponse changeStatus(Long taskId, String status, String operator) {
        String url = jobSchedulerClientProperties.getTaskBaseUrl()
                + "/" + taskId
                + "/status?status=" + encode(status)
                + "&operator=" + encode(operator);
        String responseBody = doPut(url, null);
        CommonResult<JobTaskResponse> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<JobTaskResponse>>() {
                }
        );
        return unwrapData(result);
    }

    @Override
    public String trigger(Long taskId, JobManualTriggerRequest request) {
        String responseBody = doPost(jobSchedulerClientProperties.getTaskBaseUrl() + "/" + taskId + "/trigger", request);
        CommonResult<String> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<String>>() {
                }
        );
        return unwrapData(result);
    }

    @Override
    public void deleteTask(Long taskId, String operator) {
        String responseBody = doDelete(jobSchedulerClientProperties.getTaskBaseUrl() + "/" + taskId + "?operator=" + encode(operator));
        CommonResult<Void> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<Void>>() {
                }
        );
        unwrap(result);
    }

    @Override
    public PageResult<JobRunLogResponse> pageRunLogs(JobRunLogQueryRequest request) {
        String responseBody = doPost(jobSchedulerClientProperties.getRunLogSearchUrl(), request);
        CommonResult<PageResult<JobRunLogResponse>> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<PageResult<JobRunLogResponse>>>() {
                }
        );
        return unwrapData(result);
    }

    @Override
    public List<JobExecutorNodeResponse> listNodes() {
        String responseBody = doGet(jobSchedulerClientProperties.getNodeListUrl());
        CommonResult<List<JobExecutorNodeResponse>> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<List<JobExecutorNodeResponse>>>() {
                }
        );
        return unwrapData(result);
    }

    /**
     * 执行 GET 请求。
     *
     * @param url 请求地址
     * @return 响应体
     */
    private String doGet(String url) {
        URI targetUri = resolveRequestTarget(url);
        try {
            return executeRequest(targetUri, HttpMethod.GET, null);
        } catch (HttpStatusCodeException exception) {
            throw translateHttpException("get", targetUri, exception);
        } catch (RestClientException exception) {
            log.warn("service-job get call failed, targetUri={}", targetUri, exception);
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-job get call failed");
        }
    }

    /**
     * 执行 POST 请求。
     *
     * @param url  请求地址
     * @param body 请求体
     * @return 响应体
     */
    private String doPost(String url, Object body) {
        URI targetUri = resolveRequestTarget(url);
        try {
            return executeRequest(targetUri, HttpMethod.POST, body);
        } catch (HttpStatusCodeException exception) {
            throw translateHttpException("post", targetUri, exception);
        } catch (RestClientException exception) {
            log.warn("service-job post call failed, targetUri={}", targetUri, exception);
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-job post call failed");
        }
    }

    /**
     * 执行 PUT 请求。
     *
     * @param url  请求地址
     * @param body 请求体
     * @return 响应体
     */
    private String doPut(String url, Object body) {
        URI targetUri = resolveRequestTarget(url);
        try {
            return executeRequest(targetUri, HttpMethod.PUT, body);
        } catch (HttpStatusCodeException exception) {
            throw translateHttpException("put", targetUri, exception);
        } catch (RestClientException exception) {
            log.warn("service-job put call failed, targetUri={}", targetUri, exception);
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-job put call failed");
        }
    }

    /**
     * 执行 DELETE 请求。
     *
     * @param url 请求地址
     * @return 响应体
     */
    private String doDelete(String url) {
        URI targetUri = resolveRequestTarget(url);
        try {
            return executeRequest(targetUri, HttpMethod.DELETE, null);
        } catch (HttpStatusCodeException exception) {
            throw translateHttpException("delete", targetUri, exception);
        } catch (RestClientException exception) {
            log.warn("service-job delete call failed, targetUri={}", targetUri, exception);
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-job delete call failed");
        }
    }

    /**
     * 使用解析后的请求地址执行请求。
     *
     * @param targetUri    目标地址
     * @param httpMethod   HTTP 方法
     * @param body         请求体
     * @return 响应体
     */
    private String executeRequest(URI targetUri, HttpMethod httpMethod, Object body) {
        ResponseEntity<String> response = directRestTemplate.exchange(targetUri, httpMethod, buildRequestEntity(body), String.class);
        return response.getBody();
    }

    /**
     * 构建携带当前登录态的请求实体。
     *
     * @param body 请求体
     * @return 请求实体
     */
    private HttpEntity<Object> buildRequestEntity(Object body) {
        HttpHeaders headers = new HttpHeaders();
        String authorization = resolveAuthorizationHeader();
        if (StringUtils.hasText(authorization)) {
            headers.set(AUTHORIZATION_HEADER, authorization);
        }
        return new HttpEntity<>(body, headers);
    }

    /**
     * 读取当前线程绑定的 Authorization 请求头，用于服务间内部鉴权透传。
     *
     * @return Authorization 请求头
     */
    private String resolveAuthorizationHeader() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest().getHeader(AUTHORIZATION_HEADER);
    }

    /**
     * 将下游 HTTP 状态翻译为平台稳定错误码。
     *
     * @param method    HTTP 方法
     * @param exception HTTP 状态异常
     * @return 平台异常
     */
    private ApiException translateHttpException(String method, URI targetUri, HttpStatusCodeException exception) {
        log.warn("service-job {} call returned non-success status, targetUri={}, status={}",
                method,
                targetUri,
                exception.getStatusCode().value(),
                exception);
        if (exception.getStatusCode().value() == 401) {
            return new ApiException(ApiResultEnum.UNAUTHORIZED, "service-job " + method + " call unauthorized");
        }
        if (exception.getStatusCode().value() == 403) {
            return new ApiException(ApiResultEnum.FORBIDDEN, "service-job " + method + " call forbidden");
        }
        return new ApiException(ApiResultEnum.BAD_GATEWAY, "service-job " + method + " call failed");
    }

    /**
     * 将服务名 URL 解析为实例直连地址；如果本身已经是 IP、localhost 或完整域名，则直接返回原地址。
     *
     * @param url 原始请求地址
     * @return 实际请求地址
     */
    private URI resolveRequestTarget(String url) {
        URI originalUri = URI.create(url);
        String serviceName = originalUri.getHost();
        if (!StringUtils.hasText(serviceName)) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-job url host is empty");
        }
        if (LOCALHOST.equalsIgnoreCase(serviceName)
                || IPV6_LOOPBACK.equals(serviceName)
                || IPV4_HOST_PATTERN.matcher(serviceName).matches()
                || serviceName.contains(DOMAIN_SEPARATOR)) {
            return originalUri;
        }
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceName);
        if (instances == null || instances.isEmpty()) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-job instance not found");
        }
        ServiceInstance serviceInstance = instances.get(0);
        return UriComponentsBuilder.fromUri(serviceInstance.getUri())
                .path(originalUri.getRawPath())
                .query(originalUri.getRawQuery())
                .build(true)
                .toUri();
    }

    /**
     * 解包统一响应，忽略 data 内容。
     *
     * @param result 统一响应
     */
    private void unwrap(CommonResult<?> result) {
        if (result == null) {
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-job response is empty");
        }
        if (!CommonResult.isSuccess(result)) {
            throw new ApiException(result.getCode(), result.getMessage());
        }
    }

    /**
     * 解包统一响应中的 data。
     *
     * @param result 统一响应
     * @param <T>    数据类型
     * @return data 数据
     */
    private <T> T unwrapData(CommonResult<T> result) {
        unwrap(result);
        return result.getData();
    }

    /**
     * 简单 URL 编码。
     *
     * @param value 参数值
     * @return 编码后的参数值
     */
    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
