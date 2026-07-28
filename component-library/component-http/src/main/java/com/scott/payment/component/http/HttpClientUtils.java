package com.scott.payment.component.http;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HttpClientUtils
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : Hutool HTTP 请求工具
 * @status : create
 */
public final class HttpClientUtils {

    /**
     * 默认 HTTP 超时时间，单位毫秒，用于避免渠道或外部系统无响应导致调用线程长期阻塞。
     */
    private static final int DEFAULT_TIMEOUT_MILLIS = 10000;

    private HttpClientUtils() {
    }

    /**
     * 发起 GET 请求，使用默认超时时间。
     *
     * @param url     请求地址
     * @param headers 请求头
     * @return HTTP 响应结果
     */
    public static HttpResponseResult get(String url, Map<String, String> headers) {
        return get(url, headers, DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * 发起 GET 请求。
     *
     * @param url           请求地址
     * @param headers       请求头
     * @param timeoutMillis 超时时间，单位毫秒
     * @return HTTP 响应结果
     */
    public static HttpResponseResult get(String url, Map<String, String> headers, int timeoutMillis) {
        HttpRequest request = HttpRequest.get(url).timeout(timeoutMillis);
        addHeaders(request, headers);
        return execute(request);
    }

    /**
     * 发起 JSON POST 请求，使用默认超时时间。
     *
     * @param url     请求地址
     * @param headers 请求头
     * @param body    请求体对象
     * @return HTTP 响应结果
     */
    public static HttpResponseResult postJson(String url, Map<String, String> headers, Object body) {
        return postJson(url, headers, body, DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * 发起 JSON POST 请求。
     *
     * @param url           请求地址
     * @param headers       请求头
     * @param body          请求体对象
     * @param timeoutMillis 超时时间，单位毫秒
     * @return HTTP 响应结果
     */
    public static HttpResponseResult postJson(String url, Map<String, String> headers, Object body, int timeoutMillis) {
        HttpRequest request = HttpRequest.post(url)
                .timeout(timeoutMillis)
                .contentType(ContentType.JSON.getValue())
                .body(JsonUtils.toJsonString(body));
        addHeaders(request, headers);
        return execute(request);
    }

    /**
     * 处理execute流程，串联校验、状态判断和后续业务动作。
     * <p>
     * 前置条件：调用方已把 公共组件库 的请求、消息或任务参数解析为当前方法可识别的模型。
     * 该方法按业务分支串联校验、状态判断、数据读写、远程调用或消息投递，关键阶段应保留 traceId 日志。
     * 异常边界：幂等冲突、状态不允许、外部系统失败或持久化失败按当前流程返回明确结果。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private static HttpResponseResult execute(HttpRequest request) {
        try (HttpResponse response = request.execute()) {
            return new HttpResponseResult(response.getStatus(), response.body());
        }
    }

    /**
     * 创建请求头，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 公共组件库 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param headers headers 输入值，参与 请求头 的查询、校验、转换、写入或日志摘要
     */
    private static void addHeaders(HttpRequest request, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            request.header(TraceContext.TRACE_ID_HEADER, TraceContext.getOrCreateTraceId());
            return;
        }
        headers.forEach(request::header);
        if (!headers.containsKey(TraceContext.TRACE_ID_HEADER)) {
            request.header(TraceContext.TRACE_ID_HEADER, TraceContext.getOrCreateTraceId());
        }
    }
}
