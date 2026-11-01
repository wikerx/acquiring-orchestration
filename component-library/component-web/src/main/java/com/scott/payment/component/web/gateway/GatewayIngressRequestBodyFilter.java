package com.scott.payment.component.web.gateway;

import com.scott.payment.component.core.security.GatewayIngressSignature;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 缓存受保护外部请求正文，限制大小并向入口验签提供完整 SHA-256。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public class GatewayIngressRequestBodyFilter extends OncePerRequestFilter {

    public static final String BODY_SHA256_ATTRIBUTE =
            GatewayIngressRequestBodyFilter.class.getName() + ".BODY_SHA256";

    private final GatewayIngressAuthProperties properties;

    /**
     * 创建使用统一入口大小限制的请求正文缓存过滤器。
     *
     * @param properties Gateway 入口验签配置
     */
    public GatewayIngressRequestBodyFilter(GatewayIngressAuthProperties properties) {
        this.properties = properties;
    }

    /**
     * 缓存受保护入口的请求体，使签名校验和后续控制器可以读取同一份原始字节。
     * 超出配置上限的报文直接拒绝，避免在网关内存中构造无界缓冲区。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param filterChain Servlet 后续过滤器链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        int maxBodyBytes = Math.max(properties.getMaxRequestBodyBytes(), 1);
        byte[] body = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (body.length > maxBodyBytes) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            return;
        }
        ReplayRequestWrapper wrapper = new ReplayRequestWrapper(request, body);
        wrapper.setAttribute(BODY_SHA256_ATTRIBUTE, GatewayIngressSignature.payloadSha256(body));
        filterChain.doFilter(wrapper, response);
    }

    /**
     * 判断当前请求是否无需缓存报文体；只处理配置要求保护的入口路径。
     *
     * @param request 当前 HTTP 请求
     * @return 无需执行该过滤器时返回 {@code true}
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !GatewayIngressSignature.isProtectedCheckoutPath(path)
                && !(properties.isProtectPortalPaths() && GatewayIngressSignature.isProtectedPortalPath(path));
    }

    /**
     * 保存受控大小原始请求字节并允许验签和控制器重复读取的请求包装器。
     */
    private static final class ReplayRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        /**
         * 创建请求包装器并复制正文，避免外部数组后续修改验签内容。
         *
         * @param request 原始 Servlet 请求
         * @param body 已按大小上限读取的原始正文
         */
        private ReplayRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body == null ? new byte[0] : body.clone();
        }


        /**
         * 返回从缓存正文起点读取的新 Servlet 输入流。
         *
         * @return 可重复读取的 Servlet 输入流
         */
        @Override
        public ServletInputStream getInputStream() {
            return new ReplayServletInputStream(new ByteArrayInputStream(body));
        }


        /**
         * 按请求字符集返回从缓存正文起点读取的字符流。
         *
         * @return 可重复读取的字符流
         */
        @Override
        public BufferedReader getReader() {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }


        /**
         * 获取缓存请求正文的整数长度。
         *
         * @return 正文字节数
         */
        @Override
        public int getContentLength() {
            return body.length;
        }


        /**
         * 获取缓存请求正文的长整数长度。
         *
         * @return 正文字节数
         */
        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    /**
     * 基于内存字节流提供同步及非阻塞 Servlet 读取语义。
     */
    private static final class ReplayServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream inputStream;

        /**
         * 创建可重复请求正文的 Servlet 输入流适配器。
         *
         * @param inputStream 请求包装器持有的内存字节流
         */
        private ReplayServletInputStream(ByteArrayInputStream inputStream) {
            this.inputStream = inputStream;
        }


        /**
         * 从当前内存正文位置读取一个字节。
         *
         * @return 无符号字节值，读取完成时返回 -1
         */
        @Override
        public int read() {
            return inputStream.read();
        }


        /**
         * 判断缓存正文是否已经读取完毕。
         *
         * @return 没有剩余字节时返回 true
         */
        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }


        /**
         * 表明内存正文无需等待外部 I/O，始终可以读取。
         *
         * @return 固定返回 true
         */
        @Override
        public boolean isReady() {
            return true;
        }

        /**
         * 注册 Servlet 非阻塞读取监听器；缓存报文已完整就绪，立即通知数据可读和读取完成。
         *
         * @param readListener Servlet 非阻塞读取监听器
         */
        @Override
        public void setReadListener(ReadListener readListener) {
            if (readListener == null) {
                return;
            }
            try {
                if (!isFinished()) {
                    readListener.onDataAvailable();
                }
                if (isFinished()) {
                    readListener.onAllDataRead();
                }
            } catch (IOException exception) {
                readListener.onError(exception);
            }
        }
    }
}
