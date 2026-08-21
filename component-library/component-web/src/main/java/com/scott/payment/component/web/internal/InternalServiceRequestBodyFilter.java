package com.scott.payment.component.web.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalServiceRequestBodyFilter
 * @date : 2026-08-20 23:50
 * @email : scott_x@163.com
 * @description : 预读并回放内部服务请求体，向 HMAC 拦截器提供与 Controller 完全一致的原始字节 SHA-256 摘要
 * @status : create
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class InternalServiceRequestBodyFilter extends OncePerRequestFilter {

    /** 原始请求体完整 SHA-256 的请求属性名。 */
    public static final String BODY_SHA256_ATTRIBUTE =
            InternalServiceRequestBodyFilter.class.getName() + ".BODY_SHA256";

    /** 内部 JSON 请求体最大允许大小，单位字节。 */
    static final int MAX_INTERNAL_BODY_BYTES = 1024 * 1024;

    /**
     * 缓存内部请求正文并将相同字节回放给后续过滤器和 MVC 参数解析器。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param filterChain 后续过滤器链
     * @throws ServletException 下游处理失败
     * @throws IOException 请求体读取失败
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        byte[] body = request.getInputStream().readNBytes(MAX_INTERNAL_BODY_BYTES + 1);
        if (body.length > MAX_INTERNAL_BODY_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        ReplayRequestWrapper wrapper = new ReplayRequestWrapper(request, body);
        wrapper.setAttribute(BODY_SHA256_ATTRIBUTE, InternalServiceSignature.payloadSha256(body));
        filterChain.doFilter(wrapper, response);
    }

    /** 仅处理受内部 HMAC 保护的服务间调用路径。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String internalPrefix = request.getContextPath() + "/internal/";
        return !request.getRequestURI().startsWith(internalPrefix);
    }

    /** 可重复读取预读正文的请求包装器。 */
    private static final class ReplayRequestWrapper extends HttpServletRequestWrapper {

        /** 原始请求体字节副本。 */
        private final byte[] body;

        /** 创建正文回放请求。 */
        private ReplayRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body == null ? new byte[0] : body.clone();
        }

        /** {@inheritDoc} */
        @Override
        public ServletInputStream getInputStream() {
            return new ReplayServletInputStream(new ByteArrayInputStream(body));
        }

        /** {@inheritDoc} */
        @Override
        public BufferedReader getReader() {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        /** {@inheritDoc} */
        @Override
        public int getContentLength() {
            return body.length;
        }

        /** {@inheritDoc} */
        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    /** 从内存字节流回放 Servlet 请求正文。 */
    private static final class ReplayServletInputStream extends ServletInputStream {

        /** 正文内存流。 */
        private final ByteArrayInputStream inputStream;

        /** 创建回放输入流。 */
        private ReplayServletInputStream(ByteArrayInputStream inputStream) {
            this.inputStream = inputStream;
        }

        /** {@inheritDoc} */
        @Override
        public int read() {
            return inputStream.read();
        }

        /** {@inheritDoc} */
        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        /** {@inheritDoc} */
        @Override
        public boolean isReady() {
            return true;
        }

        /** {@inheritDoc} */
        @Override
        public void setReadListener(ReadListener readListener) {
            if (readListener == null) {
                throw new IllegalArgumentException("readListener is required");
            }
            try {
                if (isFinished()) {
                    readListener.onAllDataRead();
                } else {
                    readListener.onDataAvailable();
                }
            } catch (IOException exception) {
                readListener.onError(exception);
            }
        }
    }
}
