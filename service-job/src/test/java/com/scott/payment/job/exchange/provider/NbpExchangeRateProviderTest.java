package com.scott.payment.job.exchange.provider;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.RawRateItem;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateSourceDO;
import com.scott.payment.job.exchange.parser.NbpExchangeRateJsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NbpExchangeRateProviderTest
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : 验证 NBP Provider 通过 HTTP 获取 Table C JSON，并输出统一原始汇率模型。
 * @status : create
 */
class NbpExchangeRateProviderTest {

    private HttpServer server;

    /**
     * 释放测试使用的本地 HTTP 服务。
     */
    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * 验证 Provider 请求来源配置 URL，并把官方响应交给 NBP 解析器。
     *
     * @throws IOException 本地 HTTP 服务创建失败时测试失败
     */
    @Test
    void shouldFetchAndParseNbpTableCResponse() throws IOException {
        String responseBody = """
                [{
                  "table":"C",
                  "no":"151/C/NBP/2026",
                  "tradingDate":"2026-08-06",
                  "effectiveDate":"2026-08-07",
                  "rates":[{"currency":"euro","code":"EUR","bid":4.2123,"ask":4.2973}]
                }]
                """;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/exchangerates/tables/C", exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        ExchangeRateSourceDO source = new ExchangeRateSourceDO();
        source.setRequestUrl("http://127.0.0.1:" + server.getAddress().getPort()
                + "/api/exchangerates/tables/C?format=json");
        source.setTimeoutSeconds(2);
        NbpExchangeRateProvider provider = new NbpExchangeRateProvider(new NbpExchangeRateJsonParser());

        List<RawRateItem> items = provider.fetch(source);

        assertThat(provider.sourceCode()).isEqualTo("NBP");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getBaseCurrency()).isEqualTo("EUR");
        assertThat(items.get(0).getQuoteCurrency()).isEqualTo("PLN");
        assertThat(items.get(0).getSpotBuyRate()).isEqualByComparingTo("4.2123");
        assertThat(items.get(0).getSpotSellRate()).isEqualByComparingTo("4.2973");
    }

    /**
     * 验证外部 API 非成功状态不会解析响应正文或返回空报价伪装成功。
     *
     * @throws IOException 本地 HTTP 服务创建失败时测试失败
     */
    @Test
    void shouldRejectNonSuccessfulHttpStatus() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/exchangerates/tables/C", exchange -> {
            byte[] body = "upstream detail must not be exposed".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ExchangeRateSourceDO source = new ExchangeRateSourceDO();
        source.setRequestUrl("http://127.0.0.1:" + server.getAddress().getPort()
                + "/api/exchangerates/tables/C?format=json");
        source.setTimeoutSeconds(2);
        NbpExchangeRateProvider provider = new NbpExchangeRateProvider(new NbpExchangeRateJsonParser());

        assertThatThrownBy(() -> provider.fetch(source))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("status: 503")
                .hasMessageNotContaining("upstream detail");
    }

    /**
     * 验证零条有效报价不会被抓取编排记录为成功任务。
     *
     * @throws IOException 本地 HTTP 服务创建失败时测试失败
     */
    @Test
    void shouldRejectResponseWithoutValidTableCRates() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/exchangerates/tables/C", exchange -> {
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ExchangeRateSourceDO source = new ExchangeRateSourceDO();
        source.setRequestUrl("http://127.0.0.1:" + server.getAddress().getPort()
                + "/api/exchangerates/tables/C?format=json");
        source.setTimeoutSeconds(2);
        NbpExchangeRateProvider provider = new NbpExchangeRateProvider(new NbpExchangeRateJsonParser());

        assertThatThrownBy(() -> provider.fetch(source))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("no valid Table C rates");
    }
}
