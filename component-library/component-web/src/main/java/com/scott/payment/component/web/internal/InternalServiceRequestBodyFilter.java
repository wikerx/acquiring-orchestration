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
 * 预读并回放内部服务请求体，向 HMAC 拦截器提供与 Controller 完全一致的原始字节 SHA-256 摘要
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class InternalServiceRequestBodyFilter extends OncePerRequestFilter {

    /**
     * 原始请求体完整 SHA-256 的请求属性名。
     */
    public static final String BODY_SHA256_ATTRIBUTE =
            InternalServiceRequestBodyFilter.class.getName() + ".BODY_SHA256";

    /**
     * 内部 JSON 请求体最大允许大小，单位字节。
     */
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

    /**
     * 仅处理受内部 HMAC 保护的服务间调用路径。
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String internalPrefix = request.getContextPath() + "/internal/";
        return !request.getRequestURI().startsWith(internalPrefix);
    }

    /**
     * 可重复读取预读正文的请求包装器。
     */
    private static final class ReplayRequestWrapper extends HttpServletRequestWrapper {

        /**
         * 原始请求体字节副本。
         */
        private final byte[] body;

        /**
         * 创建正文回放请求。
         */
        private ReplayRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body == null ? new byte[0] : body.clone();
        }

        /**
         * 为后续验签和控制器读取创建新的正文回放输入流。
         *
         * @return 从缓存正文起点读取的 Servlet 输入流
         */
        @Override
        public ServletInputStream getInputStream() {
            return new ReplayServletInputStream(new ByteArrayInputStream(body));
        }

        /**
         * 按请求声明字符集读取缓存正文，未声明时使用 UTF-8。
         *
         * @return 从缓存正文起点读取的字符流
         */
        @Override
        public BufferedReader getReader() {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        /**
         * 返回缓存正文的字节长度。
         *
         * @return 不超过整数范围的请求体字节数
         */
        @Override
        public int getContentLength() {
            return body.length;
        }

        /**
         * 返回缓存正文的长整型字节长度。
         *
         * @return 请求体字节数
         */
        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    /**
     * 从内存字节流回放 Servlet 请求正文。
     */
    private static final class ReplayServletInputStream extends ServletInputStream {

        /**
         * 正文内存流。
         */
        private final ByteArrayInputStream inputStream;

        /**
         * 创建回放输入流。
         */
        private ReplayServletInputStream(ByteArrayInputStream inputStream) {
            this.inputStream = inputStream;
        }

        /**
         * 从内存正文流读取下一个字节。
         *
         * @return 无符号字节值；到达末尾时返回 {@code -1}
         */
        @Override
        public int read() {
            return inputStream.read();
        }

        /**
         * 判断缓存正文是否已经全部读取。
         *
         * @return 没有剩余字节时返回 {@code true}
         */
        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        /**
         * 表示内存正文无需等待底层网络输入即可读取。
         *
         * @return 固定返回 {@code true}
         */
        @Override
        public boolean isReady() {
            return true;
        }

        /**
         * 注册非阻塞读取监听器，并根据当前剩余字节立即通知可读或读取完成。
         *
         * @param readListener Servlet 非阻塞读取监听器
         */
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
