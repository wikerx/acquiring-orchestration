package com.scott.payment.admin.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地开发启动配置契约测试，确保 Risk、MFA 与默认登录配置统一由环境专属 Nacos DataId 管理。
 */
class LocalDevStartupConfigurationContractTest {

    /**
     * 当前测试使用的隔离临时目录。
     */
    @TempDir
    Path tempDirectory;

    /**
     * 验证 Admin 与 Merchant 的 dev 配置只导入 Nacos，且服务专属 DataId 最后生效。
     */
    @Test
    void shouldLoadManagedRuntimeConfigurationOnlyFromServiceNacosDataId() throws IOException {
        for (String service : new String[]{"service-admin", "service-merchant"}) {
            String content = Files.readString(repositoryPath(
                    service + "/src/main/resources/application-dev.yml"));
            String serviceDataId = "optional:nacos:${spring.application.name}-${spring.profiles.active}."
                    + "${spring.cloud.nacos.config.file-extension}";

            assertThat(content)
                    .doesNotContain("optional:file:")
                    .doesNotContain("runtime/secrets")
                    .doesNotContain("PAYMENT_MFA_SECRET")
                    .doesNotContain("PAYMENT_RISK_SECRET")
                    .doesNotContain("MERCHANT_DEFAULT_LOGIN_PASSWORD");
            assertThat(content.lastIndexOf(serviceDataId))
                    .isGreaterThan(content.lastIndexOf("optional:nacos:xxl-job-"));
        }
    }

    /**
     * 验证 Nacos 模板只保存受管占位符，不包含可运行的固定密钥或默认登录密码。
     */
    @Test
    void shouldKeepOnlyManagedRuntimePlaceholdersInNacosTemplates() throws IOException {
        String adminTemplate = Files.readString(repositoryPath(
                "docs/deployment/nacos/service-admin-dev.yaml"));
        String merchantTemplate = Files.readString(repositoryPath(
                "docs/deployment/nacos/service-merchant-dev.yaml"));

        assertThat(adminTemplate)
                .contains("__NACOS_MANAGED_ADMIN_RISK_SECRET__")
                .contains("__NACOS_MANAGED_ADMIN_MFA_SECRET__")
                .doesNotContain("PAYMENT_RISK_SECRET")
                .doesNotContain("PAYMENT_MFA_SECRET");
        assertThat(merchantTemplate)
                .contains("__NACOS_MANAGED_MERCHANT_DEFAULT_LOGIN_PASSWORD__")
                .contains("__NACOS_MANAGED_MERCHANT_MFA_SECRET__")
                .doesNotContain("${MERCHANT_DEFAULT_LOGIN_PASSWORD")
                .doesNotContain("PAYMENT_MFA_SECRET");
    }

    /**
     * 验证 Nacos 管理脚本具备显式确认、完整备份和迁移后校验门禁。
     */
    @Test
    void shouldProvideGuardedNacosRuntimeMigrationCommand() throws IOException {
        String script = Files.readString(repositoryPath("scripts/manage-nacos-internal-auth.sh"));

        assertThat(script)
                .contains("migrate-local-dev-runtime-config")
                .contains("CONFIRM_MIGRATE_LOCAL_DEV_RUNTIME_CONFIG")
                .contains("backup_service_configs")
                .contains("verify_loaded_runtime_configs")
                .contains("Admin and Merchant must not share the same MFA master secret");
    }

    /**
     * 验证本地引导脚本只生成 Admin 前端登录辅助配置，重复执行不会覆盖已有文件。
     */
    @Test
    void shouldOnlyBootstrapIdempotentAdminFrontendLoginAssist() throws IOException, InterruptedException {
        Path frontendEnvironment = tempDirectory.resolve("admin-system/.env.development.local");
        Files.createDirectories(frontendEnvironment.getParent());

        assertThat(runBootstrap(frontendEnvironment)).isZero();
        byte[] frontendConfiguration = Files.readAllBytes(frontendEnvironment);
        assertThat(runBootstrap(frontendEnvironment)).isZero();

        assertThat(Files.readAllBytes(frontendEnvironment)).containsExactly(frontendConfiguration);
        try (Stream<Path> paths = Files.list(tempDirectory)) {
            assertThat(paths.noneMatch(path -> path.getFileName().toString().contains("secret"))).isTrue();
        }
    }

    /**
     * 验证后端仓库独立检出时可跳过不存在的前端目录，且不会回退生成本机后端密钥。
     */
    @Test
    void shouldSkipMissingFrontendWithoutCreatingBackendSecrets() throws IOException, InterruptedException {
        Path frontendEnvironment = tempDirectory.resolve("missing/frontend/.env.development.local");

        assertThat(runBootstrap(frontendEnvironment)).isZero();
        assertThat(frontendEnvironment).doesNotExist();
        try (Stream<Path> paths = Files.list(tempDirectory)) {
            assertThat(paths.findAny()).isEmpty();
        }
    }

    /**
     * 在隔离目录执行一次本地前端配置引导脚本。
     *
     * @param frontendEnvironment Admin 前端本地环境文件
     * @return 脚本退出码
     */
    private int runBootstrap(Path frontendEnvironment) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "sh",
                repositoryPath("scripts/bootstrap-local-dev-config.sh").toAbsolutePath().normalize().toString()
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.environment().put("ACQUIRING_ADMIN_DEV_ENV_FILE", frontendEnvironment.toString());
        processBuilder.environment().put("ADMIN_DEFAULT_LOGIN_PASSWORD", "AdminLocal@123456");

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        assertThat(output)
                .doesNotContain("本地开发配置初始化失败")
                .doesNotContain("AdminLocal@123456");
        return exitCode;
    }

    /**
     * 兼容从仓库根目录或 service-admin 模块目录运行测试。
     *
     * @param relativePath 仓库相对路径
     * @return 可读取的绝对或相对路径
     */
    private Path repositoryPath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("..", relativePath);
    }
}
