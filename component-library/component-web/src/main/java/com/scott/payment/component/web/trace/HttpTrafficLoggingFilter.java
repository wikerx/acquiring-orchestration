package com.scott.payment.component.web.trace;

import com.scott.payment.component.core.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HttpTrafficLoggingFilter
 * @date : 2026-07-26 16:45
 * @email : scott_x@163.com
 * @description : Servlet HTTP 请求响应摘要日志过滤器，位于 component-web 公共 Web 层，为各微服务输出统一的请求体、响应体长度、指纹和脱敏摘要。
 * @status : create
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class HttpTrafficLoggingFilter extends OncePerRequestFilter {

    /**
     * 请求体长度请求属性名，供 OpenAPI 异常日志在认证失败等未进入 Controller 的场景补充请求摘要。
     */
    public static final String REQUEST_BODY_LENGTH_ATTRIBUTE = HttpTrafficLoggingFilter.class.getName() + ".REQUEST_BODY_LENGTH";

    /**
     * 请求体 SHA-256 短摘要请求属性名，只保存不可逆指纹，不保存完整 body。
     */
    public static final String REQUEST_BODY_DIGEST_ATTRIBUTE = HttpTrafficLoggingFilter.class.getName() + ".REQUEST_BODY_DIGEST";

    /**
     * 请求体脱敏摘要请求属性名，敏感字段、密文 data 和 token 会被遮蔽后再写入。
     */
    public static final String REQUEST_BODY_SUMMARY_ATTRIBUTE = HttpTrafficLoggingFilter.class.getName() + ".REQUEST_BODY_SUMMARY";

    /**
     * HTTP 正文固定省略标记。正文只计算长度和不可逆指纹，禁止把支付数据或未知扩展字段写入日志。
     */
    private static final String BODY_OMITTED_SUMMARY = "[BODY_OMITTED]";

    /**
     * 请求体预读最大字节数，保证认证失败等业务未读取 body 的场景也可记录密文摘要。
     */
    private static final int MAX_REQUEST_CAPTURE_BYTES = 16 * 1024;

    /**
     * 响应体预览最大字节数，避免导出文件或大列表完整进入内存。
     */
    private static final int MAX_RESPONSE_CAPTURE_BYTES = 16 * 1024;

    /**
     * 截取一次 Servlet 请求和响应的有限摘要并在链路结束后记录统一日志。
     * <p>
     * 小文本请求可预读后回放；未知长度、大请求和二进制请求不预读。响应始终直接写回客户端，
     * 只在内存中保留有限预览并累计全量长度与 SHA-256 指纹。
     * </p>
     *
     * @param request     当前 HTTP 请求
     * @param response    当前 HTTP 响应
     * @param filterChain 后续 Servlet 过滤器链
     * @throws ServletException 下游 Servlet 处理失败
     * @throws IOException      请求或响应流读写失败
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        HttpServletRequest requestWrapper;
        byte[] requestBody;
        if (shouldPreReadRequest(request)) {
            requestBody = request.getInputStream().readAllBytes();
            requestWrapper = new RequestReplayWrapper(request, requestBody);
            exposeRequestBodySummary(requestWrapper, requestBody);
        } else {
            ContentCachingRequestWrapper cachingRequestWrapper = new ContentCachingRequestWrapper(request, MAX_REQUEST_CAPTURE_BYTES);
            requestWrapper = cachingRequestWrapper;
            requestBody = null;
        }
        ResponseCaptureWrapper responseWrapper = new ResponseCaptureWrapper(response);
        Throwable failure = null;
        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (ServletException | IOException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            responseWrapper.flushCapture();
            logTraffic(requestWrapper, responseWrapper, requestBody, startNanos, failure);
        }
    }

    /**
     * 输出一次 HTTP 请求响应摘要。
     * <p>
     * 摘要覆盖 traceId、路径、状态、请求/响应长度、短摘要和脱敏 JSON 片段，用于微服务间排障。
     * 方法不记录完整 Authorization、完整密文、完整卡号、CVV 或大 body。
     * </p>
     * @param request 请求包装器
     * @param response 响应缓存包装器
     * @param preReadRequestBody 预读请求体；未预读时从 ContentCachingRequestWrapper 获取
     * @param startNanos 请求开始时间，单位为纳秒
     * @param failure 业务链路抛出的异常，正常完成时为空
     */
    private void logTraffic(HttpServletRequest request,
                            ResponseCaptureWrapper response,
                            byte[] preReadRequestBody,
                            long startNanos,
                            Throwable failure) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        byte[] requestBody = requestBody(request, preReadRequestBody);
        exposeRequestBodySummary(request, requestBody);
        log.info("event: HTTP_TRAFFIC_SUMMARY traceId: {} method: {} path: {} status: {} requestContentType: {} requestLength: {} requestDigest: {} requestSummary: {} responseContentType: {} responseLength: {} responseDigest: {} responseSummary: {} durationMs: {} exceptionType: {}",
                TraceContext.getTraceId(),
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                request.getContentType(),
                bodyLength(requestBody, request.getContentLengthLong()),
                digest16(requestBody),
                bodySummary(requestBody, request.getContentType(), request.getCharacterEncoding()),
                response.getContentType(),
                response.bodyLength(),
                response.bodyDigest(),
                bodySummary(response.previewBytes(), response.getContentType(), response.getCharacterEncoding()),
                durationMs,
                failure == null ? null : failure.getClass().getSimpleName());
    }

    /**
     * 将通用请求体排障摘要挂到当前请求。
     * <p>
     * 只暴露长度、摘要和脱敏片段，供异常处理器在请求尚未进入业务方法时仍能记录商户密文报文线索。
     * </p>
     * @param request 当前请求包装器
     * @param requestBody 已缓存或预读的请求体字节
     */
    private void exposeRequestBodySummary(HttpServletRequest request, byte[] requestBody) {
        if (requestBody == null || requestBody.length == 0) {
            return;
        }
        request.setAttribute(REQUEST_BODY_LENGTH_ATTRIBUTE, requestBody.length);
        request.setAttribute(REQUEST_BODY_DIGEST_ATTRIBUTE, digest16(requestBody));
        request.setAttribute(REQUEST_BODY_SUMMARY_ATTRIBUTE,
                bodySummary(requestBody, request.getContentType(), request.getCharacterEncoding()));
    }

    /**
     * 判断是否需要在进入业务链路前预读请求体。
     * <p>
     * 仅对小体积文本请求执行预读，覆盖 OpenAPI 在鉴权失败时业务层不会读取 body 的诊断需求。
     * 大请求、未知长度请求和二进制请求继续使用下游读取时缓存的方式。
     * </p>
     * @param request 当前 HTTP 请求
     * @return true 表示可安全预读并回放请求体
     */
    private boolean shouldPreReadRequest(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        return contentLength >= 0
                && contentLength <= MAX_REQUEST_CAPTURE_BYTES
                && isTextBody(request.getContentType());
    }

    /**
     * 读取可用于日志的请求体字节。
     *
     * @param request 当前请求包装器
     * @param preReadRequestBody 预读请求体
     * @return 请求体字节；未读取时返回空数组
     */
    private byte[] requestBody(HttpServletRequest request, byte[] preReadRequestBody) {
        if (preReadRequestBody != null) {
            return preReadRequestBody;
        }
        if (request instanceof ContentCachingRequestWrapper cachingRequestWrapper) {
            return cachingRequestWrapper.getContentAsByteArray();
        }
        return new byte[0];
    }

    /**
     * 计算 body 长度。
     * <p>
     * 请求体尚未被下游读取时缓存为空，此时回退到 Content-Length，便于定位客户端是否实际发送 body。
     * </p>
     * @param cachedBody 已缓存 body 字节
     * @param declaredLength 请求头声明长度
     * @return 可用于日志的长度
     */
    private long bodyLength(byte[] cachedBody, long declaredLength) {
        if (cachedBody.length > 0) {
            return cachedBody.length;
        }
        return Math.max(declaredLength, 0);
    }

    /**
     * 生成请求或响应 body 摘要。
     * <p>
     * 文本正文统一省略，只输出固定标记；其它二进制或未知类型不输出内容。
     * 具体报文通过独立的长度和 SHA-256 短指纹关联，避免新增字段绕过枚举式脱敏规则。
     * </p>
     * @param body body 字节
     * @param contentType Content-Type
     * @param encoding 字符编码
     * @return 可写入日志的 body 摘要
     */
    private String bodySummary(byte[] body, String contentType, String encoding) {
        if (body.length == 0 || !isTextBody(contentType)) {
            return null;
        }
        return BODY_OMITTED_SUMMARY;
    }

    /**
     * 判断 body 类型是否适合输出文本摘要。
     * <p>
     * 只允许 JSON、文本和 XML 类响应进入脱敏摘要；文件、图片、表格和二进制流不输出内容。
     * </p>
     * @param contentType Content-Type
     * @return true 表示允许生成文本摘要
     */
    private boolean isTextBody(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.contains(MediaType.APPLICATION_JSON_VALUE)
                || contentType.startsWith("text/")
                || contentType.contains("xml");
    }

    /**
     * 解析 body 字符集。
     * <p>
     * 缺失或非法编码统一使用 UTF-8，避免日志摘要阶段影响真实业务响应。
     * </p>
     * @param encoding Servlet 提供的字符编码
     * @return 可用于构造字符串的字符集
     */
    private Charset resolveCharset(String encoding) {
        if (encoding == null || encoding.isBlank()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (RuntimeException exception) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * 计算 body 短摘要。
     * <p>
     * 使用 SHA-256 前 16 位十六进制，便于比对请求响应内容是否一致，不记录完整 body。
     * </p>
     * @param bytes body 字节
     * @return 短摘要；空 body 返回 null
     */
    private String digest16(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            return "sha256_unavailable";
        }
    }

    /**
     * 小体积请求体回放包装器。
     * <p>
     * 过滤器预读请求体后通过该包装器把相同字节重新提供给 MVC 参数解析器，保证日志诊断不改变业务读取行为。
     * </p>
     */
    private static final class RequestReplayWrapper extends HttpServletRequestWrapper {

        /**
         * 已预读的请求体字节。
         */
        private final byte[] body;

        /**
         * 创建请求体回放包装器。
         *
         * @param request 原始 HTTP 请求
         * @param body 已预读 body 字节
         */
        private RequestReplayWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body == null ? new byte[0] : body.clone();
        }

        /**
         * 为下游参数解析器创建一个从预读副本读取的输入流。
         *
         * @return 可重复读取当前请求体副本的 Servlet 输入流
         */
        @Override
        public ServletInputStream getInputStream() {
            return new ReplayServletInputStream(new ByteArrayInputStream(body));
        }

        /**
         * 按请求声明字符集创建请求体文本读取器。
         *
         * @return 基于回放输入流的字符读取器
         */
        @Override
        public BufferedReader getReader() {
            Charset charset = resolveReaderCharset(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        /**
         * 返回回放请求体的字节长度，替代原请求的长度声明。
         *
         * @return 请求体字节数
         */
        @Override
        public int getContentLength() {
            return body.length;
        }

        /**
         * 返回回放请求体的长整型字节长度。
         *
         * @return 请求体字节数
         */
        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        /**
         * 解析请求读取字符集，非法或缺失编码回退 UTF-8，且不影响业务请求处理。
         *
         * @param encoding Servlet 请求声明的字符编码
         * @return 可用字符集
         */
        private Charset resolveReaderCharset(String encoding) {
            if (encoding == null || encoding.isBlank()) {
                return StandardCharsets.UTF_8;
            }
            try {
                return Charset.forName(encoding);
            } catch (RuntimeException exception) {
                return StandardCharsets.UTF_8;
            }
        }
    }

    /**
     * 请求体回放输入流。
     * <p>
     * 该流包装内存中的请求体副本，满足 ServletInputStream 接口并支持 MVC 再次读取 body。
     * </p>
     */
    private static final class ReplayServletInputStream extends ServletInputStream {

        /**
         * 请求体字节输入流。
         */
        private final ByteArrayInputStream delegate;

        /**
         * 创建请求体回放输入流。
         *
         * @param delegate 请求体字节输入流
         */
        private ReplayServletInputStream(ByteArrayInputStream delegate) {
            this.delegate = delegate;
        }

        /**
         * 判断内存请求体是否已被全部读取。
         *
         * @return 无剩余字节时返回 {@code true}
         */
        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        /**
         * 内存回放流不依赖网络 I/O，始终可立即读取。
         *
         * @return 固定为 {@code true}
         */
        @Override
        public boolean isReady() {
            return true;
        }

        /**
         * 向非阻塞读取监听器同步通知内存流可读和读完状态。
         *
         * @param listener Servlet 非阻塞读取监听器；为 {@code null} 时忽略
         */
        @Override
        public void setReadListener(ReadListener listener) {
            if (listener == null) {
                return;
            }
            try {
                listener.onDataAvailable();
                if (isFinished()) {
                    listener.onAllDataRead();
                }
            } catch (IOException exception) {
                listener.onError(exception);
            }
        }

        /**
         * 从预读请求体副本读取下一个字节。
         *
         * @return 无符号字节值；到达末尾时返回 {@code -1}
         */
        @Override
        public int read() {
            return delegate.read();
        }
    }

    /**
     * 响应体截取包装器。
     * <p>
     * 包装器将响应继续写给客户端，同时只保留前 16KB 文本预览并计算全量字节数和 SHA-256 指纹，
     * 避免后台导出、文件下载或大列表响应被完整缓存到内存。
     * </p>
     */
    private static final class ResponseCaptureWrapper extends HttpServletResponseWrapper {

        /**
         * 响应体捕获器。
         */
        private final ResponseBodyCapture capture = new ResponseBodyCapture();

        /**
         * Servlet 输出流包装器。
         */
        private CapturingServletOutputStream outputStream;

        /**
         * Writer 包装器。
         */
        private PrintWriter writer;

        /**
         * 创建响应体截取包装器。
         *
         * @param response 原始 Servlet 响应对象
         */
        private ResponseCaptureWrapper(HttpServletResponse response) {
            super(response);
        }

        /**
         * 获取直接写向客户端、同时累计响应摘要的字节输出流。
         *
         * @return 响应捕获输出流
         * @throws IOException 底层响应输出流创建失败
         * @throws IllegalStateException 已经选择字符 Writer 写响应时抛出
         */
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (writer != null) {
                throw new IllegalStateException("getWriter() has already been called");
            }
            if (outputStream == null) {
                outputStream = new CapturingServletOutputStream(getResponse().getOutputStream(), capture);
            }
            return outputStream;
        }

        /**
         * 获取按响应字符集编码并经过摘要捕获的字符 Writer。
         *
         * @return 响应字符 Writer
         * @throws IOException 底层响应输出流创建失败
         */
        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                Charset charset = resolveWriterCharset(getCharacterEncoding());
                writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), charset));
            }
            return writer;
        }

        /**
         * 先刷新捕获包装器，再刷新原始 Servlet 响应缓冲区。
         *
         * @throws IOException 响应流刷新失败
         */
        @Override
        public void flushBuffer() throws IOException {
            flushCapture();
            super.flushBuffer();
        }

        /**
         * 刷新当前已创建的 Writer 或输出流，确保结束日志统计到全部已写字节。
         *
         * @throws IOException 底层输出流刷新失败
         */
        private void flushCapture() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            if (outputStream != null) {
                outputStream.flush();
            }
        }

        /**
         * 返回已写入客户端响应的累计字节数。
         *
         * @return 响应体全量字节数
         */
        private long bodyLength() {
            return capture.length();
        }

        /**
         * 返回基于完整响应体计算的 SHA-256 短指纹。
         *
         * @return 16 位十六进制摘要；响应体为空时返回 {@code null}
         */
        private String bodyDigest() {
            return capture.digest16();
        }

        /**
         * 返回用于脱敏日志的有限响应体预览。
         *
         * @return 最多 16KB 的响应前缀副本
         */
        private byte[] previewBytes() {
            return capture.previewBytes();
        }

        /**
         * 解析响应 Writer 字符集，缺失或非法编码回退 UTF-8。
         *
         * @param encoding Servlet 响应声明的字符编码
         * @return 可用字符集
         */
        private Charset resolveWriterCharset(String encoding) {
            if (encoding == null || encoding.isBlank()) {
                return StandardCharsets.UTF_8;
            }
            try {
                return Charset.forName(encoding);
            } catch (RuntimeException exception) {
                return StandardCharsets.UTF_8;
            }
        }
    }

    /**
     * 响应输出流代理。
     * <p>
     * 所有写入先进入摘要捕获器再写到原始 Servlet 输出流，不改变业务响应。
     * </p>
     */
    private static final class CapturingServletOutputStream extends ServletOutputStream {

        /**
         * 原始响应输出流。
         */
        private final ServletOutputStream delegate;

        /**
         * 响应体捕获器。
         */
        private final ResponseBodyCapture capture;

        /**
         * 创建响应输出流代理。
         *
         * @param delegate 原始 Servlet 输出流
         * @param capture 响应体摘要捕获器
         */
        private CapturingServletOutputStream(ServletOutputStream delegate, ResponseBodyCapture capture) {
            this.delegate = delegate;
            this.capture = capture;
        }

        /**
         * 复用底层 Servlet 输出流的非阻塞就绪状态。
         *
         * @return 底层输出流当前是否可写
         */
        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        /**
         * 将非阻塞写监听器注册到真实响应输出流。
         *
         * @param listener Servlet 非阻塞写监听器
         */
        @Override
        public void setWriteListener(WriteListener listener) {
            delegate.setWriteListener(listener);
        }

        /**
         * 捕获单字节摘要信息后将原值写给客户端。
         *
         * @param value 待写入的低八位字节值
         * @throws IOException 底层响应写入失败
         */
        @Override
        public void write(int value) throws IOException {
            capture.capture(new byte[]{(byte) value}, 0, 1);
            delegate.write(value);
        }

        /**
         * 捕获指定字节区间的摘要信息后将同一区间写给客户端。
         *
         * @param bytes 待写入字节数组
         * @param off   起始偏移
         * @param len   写入字节数
         * @throws IOException 底层响应写入失败
         */
        @Override
        public void write(byte[] bytes, int off, int len) throws IOException {
            capture.capture(bytes, off, len);
            delegate.write(bytes, off, len);
        }

        /**
         * 刷新真实响应输出流；摘要捕获器不额外缓存待写字节。
         *
         * @throws IOException 底层响应刷新失败
         */
        @Override
        public void flush() throws IOException {
            delegate.flush();
        }
    }

    /**
     * 响应体摘要捕获器。
     * <p>
     * 捕获器统计全量响应长度、全量 SHA-256 摘要，并只保留有限预览字节用于脱敏日志。
     * </p>
     */
    private static final class ResponseBodyCapture {

        /**
         * 响应预览缓冲区。
         */
        private final ByteArrayOutputStream preview = new ByteArrayOutputStream(MAX_RESPONSE_CAPTURE_BYTES);

        /**
         * 响应体 SHA-256 摘要器。
         */
        private final MessageDigest digest = newDigest();

        /**
         * 响应体全量字节数。
         */
        private long length;

        /**
         * 捕获响应字节并继续累计摘要。
         * <p>
         * 全量字节参与长度和 SHA-256 计算，预览区只保留前 16KB，避免大响应完整进内存。
         * </p>
         * @param bytes 响应写入字节数组
         * @param off 起始偏移
         * @param len 写入长度，单位为字节
         */
        private void capture(byte[] bytes, int off, int len) {
            if (bytes == null || len <= 0) {
                return;
            }
            digest.update(bytes, off, len);
            length += len;
            int remaining = MAX_RESPONSE_CAPTURE_BYTES - preview.size();
            if (remaining > 0) {
                preview.write(bytes, off, Math.min(len, remaining));
            }
        }

        /**
         * 返回响应体累计长度。
         *
         * @return 响应体全量字节数
         */
        private long length() {
            return length;
        }

        /**
         * 返回响应体短摘要。
         * <p>
         * 摘要基于全量响应体 SHA-256 前 16 位，用于跨日志比对同一响应内容。
         * </p>
         * @return 响应体短摘要；无响应体时返回 null
         */
        private String digest16() {
            if (length == 0) {
                return null;
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        }

        /**
         * 返回响应体预览字节。
         * <p>
         * 预览只包含前 16KB，用于生成脱敏日志片段，不代表完整响应体。
         * </p>
         * @return 响应体预览字节数组
         */
        private byte[] previewBytes() {
            return preview.toByteArray();
        }

        /**
         * 创建响应体 SHA-256 摘要器。
         *
         * @return 新的摘要器实例
         * @throws IllegalStateException 当前 Java 运行时不支持标准 SHA-256 算法时抛出
         */
        private static MessageDigest newDigest() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 digest unavailable", exception);
            }
        }
    }
}
