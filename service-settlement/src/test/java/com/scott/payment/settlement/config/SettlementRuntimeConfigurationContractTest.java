package com.scott.payment.settlement.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** 锁定结算服务的环境配置、Nacos 基础设施导入和统一日志资源。 */
class SettlementRuntimeConfigurationContractTest {

    private static final List<String> PROFILES = List.of("dev", "test", "uat", "prod", "sample");
    private static final List<String> REQUIRED_IMPORTS = List.of(
            "${spring.application.name}-${spring.profiles.active}",
            "common-${spring.profiles.active}",
            "dataSource-${spring.profiles.active}",
            "sharding-${spring.profiles.active}",
            "redis-${spring.profiles.active}",
            "rocketmq-${spring.profiles.active}"
    );

    /** 每套环境只连接 Nacos，并导入结算实际依赖的六个 DataId。 */
    @Test
    void runtimeProfilesShouldImportRequiredInfrastructureAndUnifiedLogging() throws Exception {
        for (String profile : PROFILES) {
            Path path = Path.of("src/main/resources/application-" + profile + ".yml");
            assertThat(path).exists();
            String yaml = Files.readString(path);
            Properties properties = parseYaml(yaml);

            assertThat(properties.getProperty("logging.config"))
                    .isEqualTo("classpath:log-config/logback-spring.xml");
            assertThat(yaml).contains(REQUIRED_IMPORTS.toArray(String[]::new));
            assertThat(yaml).doesNotContain(
                    "optional:nacos:seata-",
                    "optional:nacos:xxl-job-",
                    "settlement.enabled",
                    "shadow-mode",
                    "allowlist",
                    "rollout-percentage"
            );
        }
    }

    /** UAT 和生产配置不得携带 Nacos 开发账号或密码默认值。 */
    @Test
    void restrictedProfilesShouldRequireNacosCredentials() throws Exception {
        for (String profile : List.of("uat", "prod")) {
            String yaml = Files.readString(Path.of("src/main/resources/application-" + profile + ".yml"));
            assertThat(yaml)
                    .contains("username: ${NACOS_USERNAME}", "password: ${NACOS_PASSWORD}")
                    .doesNotContain("${NACOS_USERNAME:nacos}", "${NACOS_PASSWORD:nacos}");
        }
    }

    /** Logback 文件必须可解析，并使用结算服务名和统一链路日志格式。 */
    @Test
    void logbackConfigurationShouldBeLoadableAndUseSettlementIdentity() throws Exception {
        Path path = Path.of("src/main/resources/log-config/logback-spring.xml");
        assertThat(path).exists();
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());

        String xml = Files.readString(path);
        assertThat(xml).contains(
                "defaultValue=\"service-settlement\"",
                "traceId=%X{traceId:-}",
                "SizeAndTimeBasedRollingPolicy",
                "ASYNC_FILE"
        );
    }

    /** 专属 DataId 只保留内部安全参数，不得引入结算业务开关或资金规则。 */
    @Test
    void serviceNacosBaselineShouldContainSecurityOnly() throws Exception {
        Path path = Path.of("../docs/deployment/nacos/service-settlement-dev.yaml");
        assertThat(path).exists();
        String yaml = Files.readString(path);
        Properties properties = parseYaml(yaml);

        assertThat(properties.getProperty("internal-service.auth.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("internal-service.auth.secret"))
                .isEqualTo("${INTERNAL_SERVICE_AUTH_SECRET}");
        assertThat(yaml).doesNotContain(
                "settlement.enabled",
                "shadow-mode",
                "allowlist",
                "rollout-percentage",
                "fee-rate",
                "exchange-rate",
                "reserve-rate"
        );
    }

    private Properties parseYaml(String yaml) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
