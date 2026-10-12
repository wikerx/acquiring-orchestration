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

    /**
     * 为一次 Servlet 请求绑定 traceId，并在请求结束时记录一条成功或异常日志。
     * <p>
     * 请求头中的 traceId 先经过统一校验；finally 中无条件清理 MDC/线程上下文，防止容器
     * 线程复用导致跨请求串号。通用日志不记录客户端 IP、User-Agent、查询参数值或业务报文。
     * </p>
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 后续 Servlet 过滤器链
     * @throws ServletException 下游 Servlet 处理失败
     * @throws IOException      请求或响应读写失败
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String traceId = TraceContext.resolveOrCreate(request.getHeader(TraceContext.TRACE_ID_HEADER));
        TraceContext.setTraceId(traceId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.warn("event: HTTP_REQUEST_ERROR traceId: {} method: {} path: {} status: {} durationMs: {} exceptionType: {}",
                    traceId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    exception.getClass().getSimpleName());
            throw exception;
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
            log.debug("event: HTTP_REQUEST_END traceId: {} method: {} path: {} status: {} responseContentType: {} durationMs: {}",
                    traceId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    response.getContentType(),
                    durationMs);
            TraceContext.clear();
        }
    }

}
