package com.scott.payment.component.web.trace;

import com.scott.payment.component.core.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

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
     * 为一次 Servlet 请求绑定 traceId，并记录不含业务报文的入口、异常和结束日志。
     * <p>
     * 请求头中的 traceId 先经过统一校验；finally 中无条件清理 MDC/线程上下文，防止容器
     * 线程复用导致跨请求串号。查询参数只记录名称，User-Agent 受长度限制。
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
            log.info("event: HTTP_REQUEST_START traceId: {} method: {} path: {} queryKeys: {} clientIp: {} userAgent: {} contentType: {} contentLength: {}",
                    traceId,
                    request.getMethod(),
                    request.getRequestURI(),
                    queryKeys(request),
                    clientIp(request),
                    safeUserAgent(request.getHeader("User-Agent")),
                    request.getContentType(),
                    request.getContentLengthLong());
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
            log.info("event: HTTP_REQUEST_END traceId: {} method: {} path: {} status: {} responseContentType: {} durationMs: {}",
                    traceId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    response.getContentType(),
                    durationMs);
            TraceContext.clear();
        }
    }

    /**
     * 提取请求查询参数名摘要，用于定位调用方传参差异。
     * <p>
     * 仅返回参数名列表，不返回参数值，避免密码、token、卡号或商户业务数据进入通用 HTTP 日志。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 查询参数名列表，按字典序排列；无参数时返回空列表
     */
    private List<String> queryKeys(HttpServletRequest request) {
        if (request.getParameterMap().isEmpty()) {
            return Collections.emptyList();
        }
        return request.getParameterMap().keySet().stream().sorted().toList();
    }

    /**
     * 解析客户端来源 IP。
     * <p>
     * 优先读取代理透传的 X-Forwarded-For 首个地址，缺失时读取 X-Real-IP，
     * 最后回退到 Servlet 远端地址；不查询或打印 IP 库明细。
     * </p>
     * @param request 当前 Servlet 请求
     * @return 客户端 IP 摘要
     */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 截断浏览器或客户端 User-Agent。
     * <p>
     * User-Agent 仅用于定位 SDK、浏览器或网关转发差异，长度超过 120 字符时截断。
     * </p>
     * @param userAgent 原始 User-Agent 请求头
     * @return 可写入日志的 User-Agent 摘要
     */
    private String safeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= 120 ? userAgent : userAgent.substring(0, 120);
    }
}
