package com.scott.payment.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证浏览器会话 Cookie 在网关配置中的透传与跨域安全边界。
 */
class GatewayBrowserSessionConfigurationTests {

    private static final List<String> SESSION_ROUTES = List.of("admin", "merchant");
    private static final List<String> STATELESS_ROUTES = List.of("api/rest", "channel", "openapi");

    /**
     * 开发环境网关必须向后台服务透传浏览器会话 Cookie。
     */
    @Test
    void devGatewayShouldPreserveBrowserSessionCookies() throws Exception {
        String yaml = Files.readString(repositoryPath("docs/deployment/nacos/service-gateway-dev.yaml"));

        assertThat(yaml).doesNotContain("RemoveRequestHeader=Cookie");
    }

    /**
     * 各环境仅允许 Admin 与 Merchant 路由携带浏览器凭据。
     */
    @Test
    void gatewayCorsShouldRestrictCredentialedRequestsToSessionRoutes() throws Exception {
        for (Path configuration : gatewayConfigurations()) {
            Properties properties = parseYaml(Files.readString(configuration));

            for (String route : SESSION_ROUTES) {
                assertThat(corsProperty(properties, route, "allow-credentials"))
                        .as("credential policy for /%s/** in %s", route, configuration)
                        .isEqualTo("true");
                assertThat(corsProperty(properties, route, "allowed-origin-patterns[0]"))
                        .as("origin policy for /%s/** in %s", route, configuration)
                        .isNotBlank()
                        .isNotEqualTo("*");
            }

            for (String route : STATELESS_ROUTES) {
                assertThat(corsProperty(properties, route, "allow-credentials"))
                        .as("credential policy for /%s/** in %s", route, configuration)
                        .isEqualTo("false");
            }
        }
    }

    private List<Path> gatewayConfigurations() throws IOException {
        return List.of(
                repositoryPath("docs/deployment/nacos/service-gateway-dev.yaml"),
                modulePath("src/main/resources/application-test.yml"),
                modulePath("src/main/resources/application-uat.yml"),
                modulePath("src/main/resources/application-prod.yml"));
    }

    private String corsProperty(Properties properties, String route, String property) {
        String suffix = "[/" + route + "/**]." + property;
        List<String> matchingKeys = properties.keySet().stream()
                .map(Object::toString)
                .filter(key -> key.startsWith("spring.cloud.gateway.server.webflux.globalcors.cors-configurations"))
                .filter(key -> key.endsWith(suffix))
                .toList();
        assertThat(matchingKeys)
                .as("unique CORS property ending with %s; available keys: %s", suffix,
                        properties.keySet().stream()
                                .map(Object::toString)
                                .filter(key -> key.contains("globalcors"))
                                .sorted()
                                .toList())
                .hasSize(1);
        return String.valueOf(properties.get(matchingKeys.get(0)));
    }

    private Properties parseYaml(String yaml) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }

    private Path repositoryPath(String relativePath) {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }
        return Path.of("..").resolve(relativePath);
    }

    private Path modulePath(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }
        Path module = Path.of("service-gateway").resolve(relativePath);
        if (Files.exists(module)) {
            return module;
        }
        throw new IOException("Gateway configuration file does not exist: " + relativePath);
    }
}
