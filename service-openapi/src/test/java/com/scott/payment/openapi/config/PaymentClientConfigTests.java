package com.scott.payment.openapi.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateInterceptor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.AbstractClientHttpRequestFactoryWrapper;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 OpenAPI 到 Payment 的同步调用显式使用有界 Apache HttpClient 连接池。
 */
class PaymentClientConfigTests {

    @Test
    void loadBalancedClientShouldUseConfiguredConnectionPool() throws Exception {
        PaymentClientProperties properties = new PaymentClientProperties();
        properties.setInternalSecret("unit-test-openapi-payment-secret-at-least-32-bytes");
        properties.setMaxConnectionsTotal(37);
        properties.setMaxConnectionsPerRoute(23);
        PaymentClientConfig config = new PaymentClientConfig();
        PoolingHttpClientConnectionManager connectionManager =
                config.paymentHttpConnectionManager(properties);

        try (CloseableHttpClient httpClient = config.paymentHttpClient(connectionManager, properties)) {
            TraceIdRestTemplateInterceptor traceInterceptor = new TraceIdRestTemplateInterceptor();
            RestTemplate restTemplate = config.paymentLoadBalancedRestTemplate(
                    new RestTemplateBuilder(), traceInterceptor, properties, httpClient);

            assertThat(connectionManager.getMaxTotal()).isEqualTo(37);
            assertThat(connectionManager.getDefaultMaxPerRoute()).isEqualTo(23);
            AbstractClientHttpRequestFactoryWrapper interceptingFactory =
                    (AbstractClientHttpRequestFactoryWrapper) restTemplate.getRequestFactory();
            assertThat(interceptingFactory.getDelegate())
                    .isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
            assertThat(restTemplate.getInterceptors()).contains(traceInterceptor);
        } finally {
            connectionManager.close();
        }
    }
}
