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
     * 管理后台调用调度中心的内部路径是固定服务契约，不通过配置中心覆盖。
     */
    private static final String SERVICE_JOB_BASE_URL = "http://service-job";

    /** 调度任务分页查询内部接口。 */
    private static final String TASK_SEARCH_PATH = "/internal/job/tasks/search";

    /** 已注册任务处理器列表内部接口。 */
    private static final String HANDLER_LIST_PATH = "/internal/job/tasks/handlers";

    /** 调度任务增删改查内部接口前缀。 */
    private static final String TASK_BASE_PATH = "/internal/job/tasks";

    /** 任务运行日志分页查询内部接口。 */
    private static final String RUN_LOG_SEARCH_PATH = "/internal/job/logs/search";

    /** 任务运行日志列表内部接口。 */
    private static final String RUN_LOG_LIST_PATH = "/internal/job/logs/list";

    /** 单条任务运行日志内部接口前缀。 */
    private static final String RUN_LOG_BASE_PATH = "/internal/job/logs";

    /** 历史任务运行日志清理内部接口。 */
    private static final String RUN_LOG_CLEAN_PATH = "/internal/job/logs/clean";

    /** 调度执行节点列表内部接口。 */
    private static final String NODE_LIST_PATH = "/internal/job/nodes";

    /** 分表创建预演内部接口，仅返回计划，不执行 DDL。 */
    private static final String SHARDING_TABLE_CREATE_DRY_RUN_PATH = "/internal/job/sharding/table-create/dry-run";

    /** 分表创建执行内部接口，调用前必须经过预演和权限确认。 */
    private static final String SHARDING_TABLE_CREATE_EXECUTE_PATH = "/internal/job/sharding/table-create/execute";

    /**
     * direct Rest Template，用于定位邮件、通知或渠道参数模板。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final RestTemplate directRestTemplate;
    /**
     * job Scheduler Client Properties 依赖，用于 Job Scheduler Internal Rest Client 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final JobSchedulerClientProperties jobSchedulerClientProperties;
    /**
     * discovery Client 依赖，用于 Job Scheduler Internal Rest Client 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 配置和构造器注入的内部客户端依赖。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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

    /**
     * 查询调度中心已注册的任务处理器选项。
     *
     * @return 可用于创建任务的处理器列表
     */
    @Override
    public List<JobHandlerOptionResponse> listHandlers() {
        String responseBody = doGet(serviceJobUrl(HANDLER_LIST_PATH));
        CommonResult<List<JobHandlerOptionResponse>> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<List<JobHandlerOptionResponse>>>() {
                }
        );
        return unwrapData(result);
    }

    /**
     * 分页查询调度任务。
     *
     * @param request 任务状态、处理器和分页条件
     * @return 调度任务分页结果
     */
    @Override
    public PageResult<JobTaskResponse> pageTasks(JobTaskQueryRequest request) {
        String responseBody = doPost(serviceJobUrl(TASK_SEARCH_PATH), request);
        CommonResult<PageResult<JobTaskResponse>> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<PageResult<JobTaskResponse>>>() {
                }
        );
        return unwrapData(result);
    }

    /**
     * 在调度中心创建任务。
     *
     * @param request 处理器、调度表达式、超时和重试等任务配置
     * @return 创建后的任务详情
     */
    @Override
    public JobTaskResponse createTask(JobTaskRemoteSaveRequest request) {
        String responseBody = doPost(serviceJobUrl(TASK_BASE_PATH), request);
        CommonResult<JobTaskResponse> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<JobTaskResponse>>() {
                }
        );
        return unwrapData(result);
    }

    /**
     * 更新指定调度任务。
     *
     * @param taskId 调度任务主键
     * @param request 任务配置更新请求
     * @return 更新后的任务详情
     */
    @Override
    public JobTaskResponse updateTask(Long taskId, JobTaskRemoteSaveRequest request) {
        String responseBody = doPut(serviceJobUrl(TASK_BASE_PATH) + "/" + taskId, request);
        CommonResult<JobTaskResponse> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<JobTaskResponse>>() {
                }
        );
        return unwrapData(result);
    }

    /**
     * 切换调度任务状态并透传操作人用于审计。
     *
     * @param taskId 调度任务主键
     * @param status 目标状态
     * @param operator 当前操作人
     * @return 更新后的任务详情
     */
    @Override
    public JobTaskResponse changeStatus(Long taskId, String status, String operator) {
        String url = serviceJobUrl(TASK_BASE_PATH)
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

    /**
     * 手工触发指定调度任务。
     *
     * @param taskId 调度任务主键
     * @param request 分片参数和操作人等触发上下文
     * @return 调度中心生成的运行标识
     */
    @Override
    public String trigger(Long taskId, JobManualTriggerRequest request) {
        String responseBody = doPost(serviceJobUrl(TASK_BASE_PATH) + "/" + taskId + "/trigger", request);
        CommonResult<String> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<String>>() {
                }
        );
        return unwrapData(result);
    }

    /**
     * 删除指定调度任务并透传操作人用于审计。
     *
     * @param taskId 调度任务主键
     * @param operator 当前操作人
     */
    @Override
    public void deleteTask(Long taskId, String operator) {
        String responseBody = doDelete(serviceJobUrl(TASK_BASE_PATH) + "/" + taskId + "?operator=" + encode(operator));
        CommonResult<Void> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<Void>>() {
                }
        );
        unwrap(result);
    }

    /**
     * 分页查询任务运行日志。
     *
     * @param request 任务、运行状态、时间范围和分页条件
     * @return 运行日志分页结果
     */
    @Override
    public PageResult<JobRunLogResponse> pageRunLogs(JobRunLogQueryRequest request) {
        String responseBody = doPost(serviceJobUrl(RUN_LOG_SEARCH_PATH), request);
        CommonResult<PageResult<JobRunLogResponse>> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<PageResult<JobRunLogResponse>>>() {
                }
        );
        return unwrapData(result);
    }

    /**
     * 查询符合条件的任务运行日志列表，不应用分页响应包装。
     *
     * @param request 任务、运行状态和时间范围条件
     * @return 运行日志列表
     */
    @Override
    public List<JobRunLogResponse> listRunLogs(JobRunLogQueryRequest request) {
        String responseBody = doPost(serviceJobUrl(RUN_LOG_LIST_PATH), request);
        CommonResult<List<JobRunLogResponse>> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<List<JobRunLogResponse>>>() {
                }
        );
        return unwrapData(result);
    }

    /**
     * 删除指定任务运行日志。
     *
     * @param id 运行日志主键
     */
    @Override
    public void removeRunLog(Long id) {
        String responseBody = doDelete(serviceJobUrl(RUN_LOG_BASE_PATH) + "/" + id);
        CommonResult<Void> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<Void>>() {
                }
        );
        unwrap(result);
    }

    /**
     * 整理清理run日志，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    @Override
    public int cleanRunLogs(JobRunLogQueryRequest request) {
        String responseBody = doPost(serviceJobUrl(RUN_LOG_CLEAN_PATH), request);
        CommonResult<Integer> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<Integer>>() {
                }
        );
        return unwrapData(result);
    }

    /**
     * 查询调度中心当前注册的执行节点。
     *
     * @return 执行节点列表
     */
    @Override
    public List<JobExecutorNodeResponse> listNodes() {
        String responseBody = doGet(serviceJobUrl(NODE_LIST_PATH));
        CommonResult<List<JobExecutorNodeResponse>> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<List<JobExecutorNodeResponse>>>() {
                }
        );
        return unwrapData(result);
    }

    /**
     * 预演分表物理表创建计划，不执行 DDL。
     *
     * @param request 逻辑表、时间范围和分片规则
     * @return 待创建、已存在和异常表的预演结果
     */
    @Override
    public ShardingTablePreCreateResultResponse dryRunShardingTableCreate(ShardingTablePreCreateRemoteRequest request) {
        String responseBody = doPost(serviceJobUrl(SHARDING_TABLE_CREATE_DRY_RUN_PATH), request);
        CommonResult<ShardingTablePreCreateResultResponse> result = JsonUtils.parseObject(
                responseBody,
                new TypeReference<CommonResult<ShardingTablePreCreateResultResponse>>() {
                }
        );
        return unwrapData(result);
    }

    /**
     * 请求调度中心立即创建缺失的分表物理表。
     *
     * @param request 逻辑表、时间范围、分片规则和操作人
     * @return 实际创建、已存在和失败表的执行结果
     */
    @Override
    public ShardingTablePreCreateResultResponse executeShardingTableCreate(ShardingTablePreCreateRemoteRequest request) {
        String responseBody = doPost(serviceJobUrl(SHARDING_TABLE_CREATE_EXECUTE_PATH), request);
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
            log.warn("service-job get call failed, targetUri: {}", targetUri, exception);
            throw new ApiException(ApiResultEnum.BAD_GATEWAY, "service-job get call failed");
        }
    }

    /**
     * 拼接固定 service-job 服务名与受控内部路径。
     *
     * @param path 本类声明的内部接口路径
     * @return service-job 完整调用地址
     */
    private String serviceJobUrl(String path) {
        return SERVICE_JOB_BASE_URL + path;
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
            log.warn("service-job post call failed, targetUri: {}", targetUri, exception);
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
            log.warn("service-job put call failed, targetUri: {}", targetUri, exception);
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
            log.warn("service-job delete call failed, targetUri: {}", targetUri, exception);
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
        log.warn("service-job {} call returned non-success status, targetUri: {}, status: {}",
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
