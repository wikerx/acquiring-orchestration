package com.scott.payment.admin.client.data;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.CreateRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.DownloadRequest;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.SearchRequestDTO;
import com.scott.payment.admin.client.data.dto.AdminDataExportTaskClientDTOs.TaskRequest;
import com.scott.payment.admin.config.DataExportClientProperties;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Data 异步导出 REST 客户端，统一处理 HMAC 签名、响应解包和私有文件流代理。
 */
@Slf4j
@Service
public class DataExportTaskInternalRestClient implements DataExportTaskInternalClient {
    private static final String BASE_PATH = "/internal/data/export-tasks";
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private final RestTemplate direct;
    private final RestTemplate loadBalanced;
    private final DataExportClientProperties properties;

    /**
     * 创建只允许访问 Data 导出任务路径的内部客户端。
     */
    public DataExportTaskInternalRestClient(
            @Qualifier("adminDataExportRestTemplate") RestTemplate direct,
            @Qualifier("adminDataExportLoadBalancedRestTemplate") RestTemplate loadBalanced,
            DataExportClientProperties properties) {
        this.direct = direct;
        this.loadBalanced = loadBalanced;
        this.properties = properties;
    }

    /**
     * 幂等提交导出任务并解包安全摘要。
     */
    @Override
    public TaskSummary submit(CreateRequest request) {
        return post("/submit", request, new TypeReference<CommonResult<TaskSummary>>() { });
    }

    /**
     * 分页查询当前账号创建的导出任务。
     */
    @Override
    public PageResult<TaskSummary> search(SearchRequestDTO request) {
        return post("/search", request,
                new TypeReference<CommonResult<PageResult<TaskSummary>>>() { });
    }

    /**
     * 查询当前账号拥有的单个任务。
     */
    @Override
    public TaskSummary detail(TaskRequest request) {
        return post("/detail", request, new TypeReference<CommonResult<TaskSummary>>() { });
    }

    /**
     * 使用版本条件重试失败任务。
     */
    @Override
    public TaskSummary retry(TaskRequest request) {
        return post("/retry", request, new TypeReference<CommonResult<TaskSummary>>() { });
    }

    /**
     * 将内部文件流直接复制到已鉴权浏览器响应，只转发批准的文件元数据响应头。
     */
    @Override
    public void download(DownloadRequest request, HttpServletResponse response) {
        URI uri = uri("/download");
        String body = JsonUtils.toJsonString(request);
        SignedHeaders signed = signed(uri, body);
        try {
            choose(uri).execute(uri, HttpMethod.POST, clientRequest -> {
                clientRequest.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                signed.apply(clientRequest.getHeaders());
                StreamUtils.copy(body, StandardCharsets.UTF_8, clientRequest.getBody());
            }, clientResponse -> {
                MediaType contentType = clientResponse.getHeaders().getContentType();
                response.setContentType(contentType == null
                        ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType.toString());
                copyHeader(clientResponse.getHeaders(), response, HttpHeaders.CONTENT_DISPOSITION);
                copyHeader(clientResponse.getHeaders(), response, HttpHeaders.CONTENT_LENGTH);
                copyHeader(clientResponse.getHeaders(), response, "X-File-SHA256");
                StreamUtils.copy(clientResponse.getBody(), response.getOutputStream());
                return null;
            });
        } catch (HttpStatusCodeException exception) {
            log.warn("导出文件内部下载被拒绝, event: ADMIN_EXPORT_DOWNLOAD_REJECTED, httpStatus: {}",
                    exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "export file download rejected");
        } catch (RestClientException exception) {
            log.warn("导出文件内部下载失败, event: ADMIN_EXPORT_DOWNLOAD_FAILED, exceptionType: {}",
                    exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "export file download failed");
        }
    }

    /**
     * 执行带独立服务签名的普通 JSON POST 并统一校验 CommonResult。
     */
    private <T> T post(String path, Object request, TypeReference<CommonResult<T>> responseType) {
        URI uri = uri(path);
        String body = JsonUtils.toJsonString(request);
        SignedHeaders signed = signed(uri, body);
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            signed.apply(headers);
            String responseBody = choose(uri).exchange(uri, HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(body, headers), String.class).getBody();
            CommonResult<T> result = JsonUtils.parseObject(responseBody, responseType);
            if (result == null) {
                throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-data response is empty");
            }
            if (!CommonResult.isSuccess(result)) {
                throw new ServiceException(result.getCode(), result.getMessage());
            }
            return result.getData();
        } catch (HttpStatusCodeException exception) {
            log.warn("异步导出内部调用被拒绝, event: ADMIN_EXPORT_TASK_REJECTED, targetPath: {}, httpStatus: {}",
                    uri.getPath(), exception.getStatusCode().value());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-data export call rejected");
        } catch (RestClientException exception) {
            log.warn("异步导出内部调用失败, event: ADMIN_EXPORT_TASK_FAILED, targetPath: {}, exceptionType: {}",
                    uri.getPath(), exception.getClass().getSimpleName());
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-data export call failed");
        }
    }

    /**
     * 构造绑定方法、路径、正文摘要和调用方的 HMAC 请求头。
     */
    private SignedHeaders signed(URI uri, String body) {
        properties.validate();
        long timestamp = InternalServiceSignature.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();
        String signature = InternalServiceSignature.sign(HttpMethod.POST.name(),
                InternalServiceSignature.requestTarget(uri.getRawPath(), uri.getRawQuery()),
                timestamp, nonce, properties.getInternalCaller(),
                InternalServiceSignature.payloadSha256(body), properties.getInternalSecret());
        return new SignedHeaders(properties.getInternalCaller(), timestamp, nonce, signature);
    }

    /**
     * 拼接受控内部路径，禁止调用方提供完整外部地址。
     */
    private URI uri(String path) {
        return URI.create(properties.getBaseUrl().trim() + BASE_PATH + path);
    }

    /**
     * 固定主机走直连，逻辑服务名走负载均衡客户端。
     */
    private RestTemplate choose(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(), "service-data host is empty");
        }
        return "localhost".equalsIgnoreCase(host) || "::1".equals(host)
                || IPV4.matcher(host).matches() || host.contains(".") ? direct : loadBalanced;
    }

    /**
     * 仅转发内部响应中批准的单值文件元数据头。
     */
    private void copyHeader(HttpHeaders source, HttpServletResponse target, String name) {
        String value = source.getFirst(name);
        if (value != null) {
            target.setHeader(name, value);
        }
    }

    /**
     * 单次请求签名头快照，密钥本身不进入该对象。
     */
    private record SignedHeaders(String caller, long timestamp, String nonce, String signature) {
        /**
         * 将签名元数据写入请求头。
         */
        private void apply(HttpHeaders headers) {
            headers.add(InternalServiceSignature.HEADER_CALLER, caller);
            headers.add(InternalServiceSignature.HEADER_TIMESTAMP, String.valueOf(timestamp));
            headers.add(InternalServiceSignature.HEADER_NONCE, nonce);
            headers.add(InternalServiceSignature.HEADER_SIGNATURE, signature);
        }
    }
}
