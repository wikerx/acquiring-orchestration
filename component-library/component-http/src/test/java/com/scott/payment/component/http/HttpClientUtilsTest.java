package com.scott.payment.component.http;

import com.scott.payment.component.core.trace.TraceContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HttpClientUtilsTest
 * @date : 未确认
 * @email : scott_x@163.com
 * @description : Http Client Utils Test 通用函数集合，位于 公共组件库，封装格式化、校验、脱敏、加密、编码或标准化逻辑，调用方以静态方法获取本地计算结果。
 * @status : create
 */
class HttpClientUtilsTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void shouldPropagateTraceIdWhenHeadersAreEmpty() throws IOException {
        TraceContext.setTraceId("trace-http-001");
        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        HttpServer server = startServer(capturedTraceId);

        try {
            int port = server.getAddress().getPort();
            HttpResponseResult result = HttpClientUtils.get("http://127.0.0.1:" + port + "/trace", Map.of());

            assertThat(result.getStatus()).isEqualTo(200);
            assertThat(result.getBody()).isEqualTo("ok");
            assertThat(capturedTraceId.get()).isEqualTo("trace-http-001");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepCallerProvidedTraceIdHeader() throws IOException {
        AtomicReference<String> capturedTraceId = new AtomicReference<>();
        HttpServer server = startServer(capturedTraceId);

        try {
            int port = server.getAddress().getPort();
            HttpResponseResult result = HttpClientUtils.get(
                    "http://127.0.0.1:" + port + "/trace",
                    Map.of(TraceContext.TRACE_ID_HEADER, "trace-from-caller")
            );

            assertThat(result.getStatus()).isEqualTo(200);
            assertThat(capturedTraceId.get()).isEqualTo("trace-from-caller");
        } finally {
            server.stop(0);
        }
    }

    private HttpServer startServer(AtomicReference<String> capturedTraceId) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/trace", exchange -> handle(exchange, capturedTraceId));
        server.start();
        return server;
    }

    private void handle(HttpExchange exchange, AtomicReference<String> capturedTraceId) throws IOException {
        capturedTraceId.set(exchange.getRequestHeaders().getFirst(TraceContext.TRACE_ID_HEADER));
        byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }
}
