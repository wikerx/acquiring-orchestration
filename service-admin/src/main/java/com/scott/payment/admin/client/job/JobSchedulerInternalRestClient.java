package com.scott.payment.admin.client.job;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.admin.client.job.dto.JobTaskRemoteSaveRequest;
import com.scott.payment.admin.client.job.dto.ShardingTablePreCreateRemoteRequest;
import com.scott.payment.admin.config.JobSchedulerClientProperties;
import com.scott.payment.admin.dto.monitor.JobExecutorNodeResponse;
import com.scott.payment.admin.dto.monitor.JobHandlerOptionResponse;
import com.scott.payment.admin.dto.monitor.JobManualTriggerRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogQueryRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogResponse;
import com.scott.payment.admin.dto.monitor.JobTaskQueryRequest;
import com.scott.payment.admin.dto.monitor.JobTaskResponse;
import com.scott.payment.admin.dto.monitor.ShardingTablePreCreateResultResponse;
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

    /**
     * direct Rest Template 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final RestTemplate directRestTemplate;
    /**
     * job Scheduler Client Properties 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JobSchedulerClientProperties jobSchedulerClientProperties;
    /**
     * discovery Client 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
    /**
     * 完成 list Handlers 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 完成 page Tasks 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 完成 create Task 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 写入或更新 update Task 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param taskId task Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 完成 change Status 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param taskId task Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @param operator operator 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 完成 trigger 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param taskId task Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 完成 delete Task 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param taskId task Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param operator operator 输入值，含义由调用方法名称和所属业务对象限定
     */
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
    /**
     * 完成 page Run Logs 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 完成 list Run Logs 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public List<JobRunLogResponse> listRunLogs(JobRunLogQueryRequest request) {
        String responseBody = doPost(jobSchedulerClientProperties.getRunLogListUrl(), request);
        CommonResult<List<JobRunLogResponse>> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<List<JobRunLogResponse>>>() {
                }
        );
        return unwrapData(result);
    }

    @Override
    /**
     * 完成 remove Run Log 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void removeRunLog(Long id) {
        String responseBody = doDelete(jobSchedulerClientProperties.getTaskBaseUrl().replace("/tasks", "/logs") + "/" + id);
        CommonResult<Void> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<Void>>() {
                }
        );
        unwrap(result);
    }

    @Override
    /**
     * 完成 clean Run Logs 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public int cleanRunLogs(JobRunLogQueryRequest request) {
        String responseBody = doPost(jobSchedulerClientProperties.getRunLogCleanUrl(), request);
        CommonResult<Integer> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<Integer>>() {
                }
        );
        return unwrapData(result);
    }

    @Override
    /**
     * 完成 list Nodes 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public List<JobExecutorNodeResponse> listNodes() {
        String responseBody = doGet(jobSchedulerClientProperties.getNodeListUrl());
        CommonResult<List<JobExecutorNodeResponse>> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<List<JobExecutorNodeResponse>>>() {
                }
        );
        return unwrapData(result);
    }

    @Override
    /**
     * 完成 dry Run Sharding Table Create 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public ShardingTablePreCreateResultResponse dryRunShardingTableCreate(ShardingTablePreCreateRemoteRequest request) {
        String responseBody = doPost(jobSchedulerClientProperties.getShardingTableCreateDryRunUrl(), request);
        CommonResult<ShardingTablePreCreateResultResponse> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<ShardingTablePreCreateResultResponse>>() {
                }
        );
        return unwrapData(result);
    }

    @Override
    /**
     * 完成 execute Sharding Table Create 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public ShardingTablePreCreateResultResponse executeShardingTableCreate(ShardingTablePreCreateRemoteRequest request) {
        String responseBody = doPost(jobSchedulerClientProperties.getShardingTableCreateExecuteUrl(), request);
        CommonResult<ShardingTablePreCreateResultResponse> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<ShardingTablePreCreateResultResponse>>() {
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
