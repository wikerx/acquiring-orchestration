package com.scott.payment.component.web.trace;

import com.scott.payment.component.core.trace.TraceContext;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TraceIdRestTemplateInterceptor
 * @date : 2026-07-26 15:20
 * @email : scott_x@163.com
 * @description : RestTemplate traceId 拦截器，在服务间 HTTP 调用时自动透传 X-Trace-Id 请求头。
 * @status : create
 */
public class TraceIdRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    /**
     * 将当前调用链 traceId 写入服务间 HTTP 请求后继续执行。
     *
     * @param request   待发送的 HTTP 请求
     * @param body      原始请求体；本拦截器不读取或记录其内容
     * @param execution RestTemplate 请求执行器
     * @return 下游 HTTP 响应
     * @throws IOException 请求发送或响应读取失败
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set(TraceContext.TRACE_ID_HEADER, TraceContext.getOrCreateTraceId());
        return execution.execute(request, body);
    }
}
