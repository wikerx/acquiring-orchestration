package com.scott.payment.component.web.trace;

import com.scott.payment.component.core.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TraceIdFilter
 * @date : 2026-07-26 15:20
 * @email : scott_x@163.com
 * @description : Servlet 请求 traceId 过滤器，在业务处理前绑定 TraceContext 和 MDC，并在响应结束后清理线程上下文。
 * @status : create
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String traceId = TraceContext.resolveOrCreate(request.getHeader(TraceContext.TRACE_ID_HEADER));
        TraceContext.setTraceId(traceId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        try {
            log.info("event=HTTP_REQUEST_START method: {} path: {} clientIp: {} userAgent: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    safeUserAgent(request.getHeader("User-Agent")));
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info("event=HTTP_REQUEST_END method: {} path: {} status: {} durationMs: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs);
            TraceContext.clear();
        }
    }

    private String safeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 120 ? userAgent : userAgent.substring(0, 120);
    }
}
