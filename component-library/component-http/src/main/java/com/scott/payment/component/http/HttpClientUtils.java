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

    private static HttpResponseResult execute(HttpRequest request) {
        try (HttpResponse response = request.execute()) {
            return new HttpResponseResult(response.getStatus(), response.body());
        }
    }

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
