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

/** 缓存受保护外部请求正文，限制大小并向入口验签提供完整 SHA-256。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
public class GatewayIngressRequestBodyFilter extends OncePerRequestFilter {

    public static final String BODY_SHA256_ATTRIBUTE =
            GatewayIngressRequestBodyFilter.class.getName() + ".BODY_SHA256";

    private final GatewayIngressAuthProperties properties;

    public GatewayIngressRequestBodyFilter(GatewayIngressAuthProperties properties) {
        this.properties = properties;
    }

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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !GatewayIngressSignature.isProtectedCheckoutPath(request.getRequestURI());
    }

    private static final class ReplayRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        private ReplayRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ReplayServletInputStream(new ByteArrayInputStream(body));
        }

        @Override
        public BufferedReader getReader() {
            Charset charset = getCharacterEncoding() == null
                    ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

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

        @Override
        public int read() {
            return inputStream.read();
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

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
