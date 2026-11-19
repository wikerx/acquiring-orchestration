package com.scott.payment.checkout.application;

import com.scott.payment.checkout.controller.CheckoutCountryConfigController;
import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 随机端口的隔离 HTTP 服务读取真实配置，验证响应协商和解压内容；不启动现有业务进程。 */
class CheckoutCountryCompressionTest {
    @Test
    void configuredJsonCompressionPreservesActualCountryProjectionAndPlainClients() throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = new Binder(new MapConfigurationPropertySource(yaml.getObject()))
                .bind("server", Bindable.of(ServerProperties.class)).get();
        var dictionary = mock(IsoDictionaryService.class);
        when(dictionary.listCountries()).thenReturn(List.of(
                country("US", "USA"), country("CA", "CAN"), country("SG", "SGP"),
                country("HK", "HKG"), country("CN", "CHN"), country("JP", "JPN")));
        var endpoint = new CheckoutCountryConfigController(new CheckoutCountryConfigApplicationService(dictionary));
        byte[] payload = JsonUtils.toJsonString(endpoint.listCountries()).getBytes(StandardCharsets.UTF_8);
        assertThat(payload.length).isGreaterThan((int) properties.getCompression().getMinResponseSize().toBytes());
        var factory = new TomcatServletWebServerFactory(0);
        factory.setCompression(properties.getCompression());
        var server = factory.getWebServer(context -> {
            var servlet = context.addServlet("countries", new HttpServlet() {
                @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                    response.setContentType("application/json");
                    response.getOutputStream().write(payload);
                }
            });
            servlet.addMapping("/checkout/config/countries");
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getPort() + "/checkout/config/countries");
            var client = HttpClient.newHttpClient();
            var compressed = client.send(HttpRequest.newBuilder(uri).header("Accept-Encoding", "gzip").build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(compressed.statusCode()).isEqualTo(200);
            assertThat(compressed.headers().firstValue("Content-Encoding")).contains("gzip");
            assertThat(compressed.body().length).isLessThan(payload.length / 2);
            try (var decoded = new GZIPInputStream(new ByteArrayInputStream(compressed.body()))) {
                assertThat(decoded.readAllBytes()).isEqualTo(payload);
            }
            var plain = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(plain.headers().firstValue("Content-Encoding")).isEmpty();
            assertThat(plain.body()).isEqualTo(payload);
        } finally {
            server.stop();
        }
    }

    private IsoCountryInfo country(String alpha2, String alpha3) {
        return new IsoCountryInfo(alpha2, alpha3, "000", "Fixture country", "Fixture country", "隔离国家配置",
                "AS", "亚洲", "🌐", "en", "English", "英语", "USD");
    }
}
