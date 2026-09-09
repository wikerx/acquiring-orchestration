package com.scott.payment.component.nacos.encryption;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ServiceScopedNacosConfigurationContractTest
 * @date : 2026-09-02 00:00
 * @email : scott_x@163.com
 * @description : 锁定每个后端服务只导入原有服务配置的约束，防止内部鉴权再次创建独立或 cipher 服务 DataId。
 * @status : create
 */
class ServiceScopedNacosConfigurationContractTest {

    private static final List<String> SERVICES = Arrays.asList(
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
            "service-settlement");

    private static final List<String> PROFILES = Arrays.asList("dev", "test", "uat", "prod", "sample");

    private static final String SERVICE_IMPORT =
            "- optional:nacos:${spring.application.name}-${spring.profiles.active}."
                    + "${spring.cloud.nacos.config.file-extension}";

    /** 五套环境的每个服务必须只导入原有服务 DataId，不得加载独立调用边或 cipher 服务 DataId。 */
    @Test
    void everyServiceProfileShouldImportOneOrdinaryServiceConfiguration() throws IOException {
        Path repositoryRoot = findRepositoryRoot();

        for (String service : SERVICES) {
            for (String profile : PROFILES) {
                Path profileFile = repositoryRoot.resolve(service)
                        .resolve("src/main/resources/application-" + profile + ".yml");
                String configuration = read(profileFile);

                assertThat(countOccurrences(configuration, SERVICE_IMPORT))
                        .as("%s %s service import count", service, profile)
                        .isEqualTo(1);
                assertThat(configuration)
                        .doesNotContain(
                                "cipher-acqaesgcm-internal-auth-",
                                "cipher-acqaesgcm-${spring.application.name}-${spring.profiles.active}");
            }
        }
    }

    /** 管理脚本必须按服务发布并使用两阶段轮换，旧配置删除必须具备显式保护。 */
    @Test
    void managementScriptShouldProtectServiceBundleMigrationAndCleanup() throws IOException {
        String script = read(findRepositoryRoot().resolve("scripts/manage-nacos-internal-auth.sh"));

        assertThat(script)
                .contains(
                        "printf '%s-%s.yaml'",
                        "encrypted_service_data_id",
                        "migrate_service_bundles",
                        "rotate-prepare",
                        "rotate-activate",
                        "CONFIRM_DELETE_LEGACY_CONFIGS=YES")
                .doesNotContain("publish_missing_edges");
    }

    private Path findRepositoryRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 8 && current != null; depth++) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("service-admin"))
                    && Files.isDirectory(current.resolve("component-library"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("acquiring-orchestration repository root cannot be located");
    }

    private String read(Path path) throws IOException {
        assertThat(path).exists();
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private int countOccurrences(String source, String expected) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(expected, offset)) >= 0) {
            count++;
            offset += expected.length();
        }
        return count;
    }
}
