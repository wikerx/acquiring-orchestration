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
     * 完成 execute 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 HttpClientUtils 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private static HttpResponseResult execute(HttpRequest request) {
        try (HttpResponse response = request.execute()) {
            return new HttpResponseResult(response.getStatus(), response.body());
        }
    }

    /**
     * 计算 add Headers 对应的数值结果，调用方负责保证金额和币种上下文一致。
     * <p>
     * 层级边界：公共组件层；输入来源、输出结构和异常语义由 HttpClientUtils 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param headers headers 输入值，含义由调用方法名称和所属业务对象限定
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
