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

    private static final class ReplayRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        private ReplayRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body == null ? new byte[0] : body.clone();
        }

        /**
         * 查询{@code getInputStream}；筛选条件、分页上限和数据范围由方法参数共同限定。
         * <p>
         * 只读操作；实现必须沿用 公共组件库 既有权限、数据范围和空结果约定。
         * </p>
         * @return 查询得到的业务对象、分页结果或空结果
         */
        @Override
        public ServletInputStream getInputStream() {
            return new ReplayServletInputStream(new ByteArrayInputStream(body));
        }

        /**
         * 查询{@code getReader}；筛选条件、分页上限和数据范围由方法参数共同限定。
         * <p>
         * 只读操作；实现必须沿用 公共组件库 既有权限、数据范围和空结果约定。
         * </p>
         * @return 查询得到的业务对象、分页结果或空结果
         */
        @Override
        public BufferedReader getReader() {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        /**
         * 查询{@code getContentLength}；筛选条件、分页上限和数据范围由方法参数共同限定。
         * <p>
         * 只读操作；实现必须沿用 公共组件库 既有权限、数据范围和空结果约定。
         * </p>
         * @return 查询得到的业务对象、分页结果或空结果
         */
        @Override
        public int getContentLength() {
            return body.length;
        }

        /**
         * 查询{@code getContentLengthLong}；筛选条件、分页上限和数据范围由方法参数共同限定。
         * <p>
         * 只读操作；实现必须沿用 公共组件库 既有权限、数据范围和空结果约定。
         * </p>
         * @return 查询得到的业务对象、分页结果或空结果
         */
        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class ReplayServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream inputStream;

        private ReplayServletInputStream(ByteArrayInputStream inputStream) {
            this.inputStream = inputStream;
        }

        /**
         * 规范化读取，返回调用链后续步骤可直接使用的业务值。
         * @return 当前方法计算的数量、版本或状态数值
         */
        @Override
        public int read() {
            return inputStream.read();
        }

        /**
         * 判断 is finished 条件是否成立，用于控制 Replay Servlet Input Stream 的后续分支。
         * <p>
         * 纯判断操作，不修改业务状态。
         * </p>
         * @return 条件满足时返回 true，否则返回 false
         */
        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        /**
         * 判断 is ready 条件是否成立，用于控制 Replay Servlet Input Stream 的后续分支。
         * <p>
         * 纯判断操作，不修改业务状态。
         * </p>
         * @return 条件满足时返回 true，否则返回 false
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
