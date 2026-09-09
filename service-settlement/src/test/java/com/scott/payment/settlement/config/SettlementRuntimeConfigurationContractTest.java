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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementRuntimeConfigurationContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 锁定结算服务的环境配置、Nacos 基础设施导入和统一日志资源。
 * @status : create
 */
class SettlementRuntimeConfigurationContractTest {

    private static final String SETTLEMENT_AUTH_SECRET_PLACEHOLDER =
            "${acquiring.internal-auth.edges.admin-settlement.active-secret}";

    private static final List<String> PROFILES = List.of("dev", "test", "uat", "prod", "sample");
    private static final List<String> SERVICES = List.of(
            "service-admin",
            "service-checkout",
            "service-clearing",
            "service-data",
            "service-gateway",
            "service-job",
            "service-merchant",
            "service-openapi",
            "service-payment",
            "service-payout",
            "service-risk",
            "service-settlement"
    );
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
            assertThat(yaml).doesNotContain("cipher-acqaesgcm-internal-auth-");
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

    /** 专属 DataId 保存内部调用路由但不保存真实密钥，也不得引入结算业务开关或资金规则。 */
    @Test
    void serviceNacosBaselineShouldContainInternalRoutingWithoutSettlementRules() throws Exception {
        Path path = repositoryPath("docs/deployment/nacos/service-settlement-dev.yaml");
        assertThat(path).exists();
        String yaml = Files.readString(path);
        assertThat(yaml)
                .contains(
                        "internal-service:",
                        "active-secret: ${acquiring.internal-auth.edges.admin-settlement.active-secret}",
                        "allowed-paths:",
                        "/internal/settlement/**")
                .doesNotContain(
                        "acquiring.internal-auth.edges:",
                        "settlement.enabled",
                        "shadow-mode",
                        "allowlist",
                        "rollout-percentage",
                        "fee-rate",
                        "exchange-rate",
                        "reserve-rate");
    }

    /** 所有 application.yml 仅允许启动基线和不可远程关闭的内部鉴权 enabled 门禁。 */
    @Test
    void serviceApplicationsShouldKeepOnlyBootstrapAndLocalSecurityGate() throws Exception {
        for (String service : SERVICES) {
            Path application = repositoryPath(service + "/src/main/resources/application.yml");
            Properties properties = parseYaml(Files.readString(application));
            List<String> unexpected = properties.stringPropertyNames().stream()
                    .filter(key -> !key.startsWith("server."))
                    .filter(key -> !key.startsWith("spring.application."))
                    .filter(key -> !key.startsWith("spring.profiles."))
                    .filter(key -> !key.startsWith("spring.main."))
                    .filter(key -> !key.equals("internal-service.auth.enabled"))
                    .sorted()
                    .toList();

            assertThat(unexpected)
                    .as("unexpected local runtime configuration in %s", service)
                    .isEmpty();
            assertThat(repositoryPath("docs/deployment/nacos/" + service + "-dev.yaml")).exists();
        }
    }

    /** 仓库模板不得携带脚本管理的真实密钥段，内部密钥字段只能引用本服务调用边。 */
    @Test
    void serviceNacosTemplatesShouldReferenceManagedEdgesWithoutEmbeddingBundle() throws Exception {
        for (String service : SERVICES) {
            String yaml = Files.readString(repositoryPath(
                    "docs/deployment/nacos/" + service + "-dev.yaml"));
            assertThat(yaml).doesNotContain(
                    "# --- managed internal-auth bundle",
                    "acquiring.internal-auth.edges:",
                    "cipher-acqaesgcm-");
            for (String line : yaml.lines().toList()) {
                String trimmed = line.trim();
                if (trimmed.startsWith("active-secret:")
                        || trimmed.startsWith("previous-secret:")
                        || trimmed.startsWith("internal-secret:")) {
                    assertThat(trimmed).contains("${acquiring.internal-auth.edges.");
                }
            }
        }
    }

    /** 标准嵌套结构必须继续暴露既有 Spring 属性路径，业务配置与鉴权配置共享唯一 acquiring 根节点。 */
    @Test
    void serviceBundleShouldExposeInternalAuthProperties() {
        String yaml = "acquiring:\n"
                + "  settlement-observability:\n"
                + "    enabled: true\n"
                + "  internal-auth:\n"
                + "    edges:\n"
                + "      admin-settlement:\n"
                + "        active-secret: 0123456789abcdef0123456789abcdef\n"
                + "        previous-secret: \"\"\n";

        Properties properties = parseYaml(yaml);

        assertThat(properties.getProperty("acquiring.settlement-observability.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("acquiring.internal-auth.edges.admin-settlement.active-secret"))
                .isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(yaml).doesNotContain("acquiring.internal-auth.edges:");
    }

    /** Admin 调用端与 Settlement 验签端必须引用同一条中性 edge 属性。 */
    @Test
    void adminAndSettlementNacosShouldUseTheSameInjectedSecret() throws Exception {
        Properties admin = parseYaml(Files.readString(
                repositoryPath("docs/deployment/nacos/service-admin-dev.yaml")));
        Properties settlement = parseYaml(Files.readString(
                repositoryPath("docs/deployment/nacos/service-settlement-dev.yaml")));

        assertThat(admin.getProperty("admin.settlement-client.internal-caller"))
                .isEqualTo("service-admin");
        assertThat(admin.getProperty("admin.settlement-client.internal-secret"))
                .isEqualTo(SETTLEMENT_AUTH_SECRET_PLACEHOLDER)
                .isEqualTo(settlement.getProperty(
                        "internal-service.auth.callers.service-admin.active-secret"));
    }

    private Path repositoryPath(String relativePath) {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }
        return Path.of("..").resolve(relativePath);
    }

    private Properties parseYaml(String yaml) {
        String filteredYaml = yaml.replace("@profiles.active@", "test");
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ByteArrayResource(filteredYaml.getBytes(StandardCharsets.UTF_8)));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        return properties;
    }
}
