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

    @Override
    public ClientHttpResponse intercept(HttpRequest request,
                                        byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set(TraceContext.TRACE_ID_HEADER, TraceContext.getOrCreateTraceId());
        return execution.execute(request, body);
    }
}
