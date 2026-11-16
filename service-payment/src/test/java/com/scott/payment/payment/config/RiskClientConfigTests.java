package com.scott.payment.payment.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import com.scott.payment.component.web.trace.TraceIdRestTemplateInterceptor;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.AbstractClientHttpRequestFactoryWrapper;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Payment 到 Risk 的同步调用显式使用有界、共享的 Apache HttpClient 连接池。
 */
class RiskClientConfigTests {

    /**
     * 验证直连和服务发现客户端共享指定容量的连接池，并继续传播 traceId。
     *
     * @throws Exception 关闭测试 HTTP 客户端失败
     */
    @Test
    void shouldUseConfiguredConnectionPoolForDirectAndLoadBalancedClients() throws Exception {
        RiskClientProperties properties = new RiskClientProperties();
        properties.setInternalSecret("unit-test-payment-risk-secret-at-least-32-bytes");
        properties.setMaxConnectionsTotal(47);
        properties.setMaxConnectionsPerRoute(29);
        RiskClientConfig config = new RiskClientConfig();
        PoolingHttpClientConnectionManager connectionManager =
                config.riskHttpConnectionManager(properties);

        try (CloseableHttpClient httpClient = config.riskHttpClient(connectionManager, properties)) {
            TraceIdRestTemplateInterceptor traceInterceptor = new TraceIdRestTemplateInterceptor();
            RestTemplate directRestTemplate = config.riskRestTemplate(
                    new TraceIdRestTemplateCustomizer(traceInterceptor), properties, httpClient);
            RestTemplate loadBalancedRestTemplate = config.riskLoadBalancedRestTemplate(
                    new RestTemplateBuilder(), traceInterceptor, properties, httpClient);

            assertThat(connectionManager.getMaxTotal()).isEqualTo(47);
            assertThat(connectionManager.getDefaultMaxPerRoute()).isEqualTo(29);
            assertApacheRequestFactory(directRestTemplate);
            assertApacheRequestFactory(loadBalancedRestTemplate);
            assertThat(directRestTemplate.getInterceptors()).contains(traceInterceptor);
            assertThat(loadBalancedRestTemplate.getInterceptors()).contains(traceInterceptor);
        } finally {
            connectionManager.close();
        }
    }

    /**
     * 解开 traceId 拦截器包装并验证底层使用 Apache HttpClient 请求工厂。
     *
     * @param restTemplate 待验证的内部调用客户端
     */
    private void assertApacheRequestFactory(RestTemplate restTemplate) {
        ClientHttpRequestFactory requestFactory = restTemplate.getRequestFactory();
        if (requestFactory instanceof AbstractClientHttpRequestFactoryWrapper wrapper) {
            requestFactory = wrapper.getDelegate();
        }
        assertThat(requestFactory).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }
}
