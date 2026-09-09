package com.scott.payment.clearing.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingDeploymentConfigurationContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 清分环境接入和自动运行合同，防止服务脱离基础设施或重新引入业务启停、商户范围过滤配置。
 * @status : create
 */
class ClearingDeploymentConfigurationContractTest {

    private static final List<String> INFRASTRUCTURE_IMPORTS = List.of(
            "common-${spring.profiles.active}",
            "dataSource-${spring.profiles.active}",
            "sharding-${spring.profiles.active}",
            "redis-${spring.profiles.active}",
            "rocketmq-${spring.profiles.active}");

    /** 所有部署 Profile 必须加载清分原有服务配置和五个必需公共 DataId。 */
    @Test
    void allProfilesShouldImportRequiredNacosDataIds() throws IOException {
        for (String profile : List.of("dev", "test", "uat", "prod", "sample")) {
            String configuration = readRepositoryFile(
                    "service-clearing/src/main/resources/application-" + profile + ".yml");

            assertThat(configuration)
                    .contains("optional:nacos:${spring.application.name}-${spring.profiles.active}")
                    .contains(INFRASTRUCTURE_IMPORTS)
                    .contains("logging:", "config: classpath:log-config/logback-spring.xml")
                    .doesNotContain("cipher-acqaesgcm-");
        }
    }

    /** UAT 和生产不得为 Nacos 凭据或内部 HMAC 密钥提供仓库默认值。 */
    @Test
    void protectedProfilesShouldRequireInjectedSecrets() throws IOException {
        for (String profile : List.of("uat", "prod")) {
            String configuration = readRepositoryFile(
                    "service-clearing/src/main/resources/application-" + profile + ".yml");

            assertThat(configuration)
                    .contains("username: ${NACOS_USERNAME}",
                            "password: ${NACOS_PASSWORD}")
                    .doesNotContain("INTERNAL_SERVICE_AUTH_SECRET", "dev-internal-service-secret");
        }
    }

    /** 清分 DataId 只保留容量、安全和调度周期参数，并通过共享凭据边引用内部鉴权密钥。 */
    @Test
    void devDataIdShouldContainRuntimeTuningWithoutBusinessSwitches() throws IOException {
        String configuration = readRepositoryFile("docs/deployment/nacos/service-clearing-dev.yaml");

        assertThat(configuration).contains(
                "processing-timeout-seconds: ${CLEARING_PROCESSING_TIMEOUT_SECONDS:120}",
                "consumer-min-threads: ${CLEARING_CONSUMER_MIN_THREADS:16}",
                "initial-delay-ms: ${CLEARING_METRICS_INITIAL_DELAY_MS:30000}",
                "clearing.duration: true",
                "clearing.tier.lock: true",
                "internal-service:",
                "active-secret: ${acquiring.internal-auth.edges.admin-clearing.active-secret}",
                "active-secret: ${acquiring.internal-auth.edges.job-clearing.active-secret}")
                .doesNotContain(
                        "CLEARING_ENABLED",
                        "shadow-mode:",
                        "merchant-rollout-mode:",
                        "merchant-allowlist:",
                        "merchant-rollout-basis-points:",
                        "merchant-rollout-salt:",
                        "CLEARING_METRICS_REFRESH_ENABLED",
                        "internal-secret:",
                        "dev-internal-service-secret");
    }

    /** 两个消息消费者和指标调度器必须随服务自动装配，不能依赖 ConditionalOnProperty。 */
    @Test
    void clearingRuntimeComponentsShouldNotHaveBusinessEnableConditions() throws IOException {
        for (String source : List.of(
                "service-clearing/src/main/java/com/scott/payment/clearing/mq/TransactionTerminalClearingConsumer.java",
                "service-clearing/src/main/java/com/scott/payment/clearing/mq/ClearingRetryDueConsumer.java",
                "service-clearing/src/main/java/com/scott/payment/clearing/support/ClearingOperationalMetricsScheduler.java")) {
            assertThat(readRepositoryFile(source))
                    .contains("@Component")
                    .doesNotContain("ConditionalOnProperty");
        }
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct);
        }
        return Files.readString(Path.of("..").resolve(relativePath).normalize());
    }
}
