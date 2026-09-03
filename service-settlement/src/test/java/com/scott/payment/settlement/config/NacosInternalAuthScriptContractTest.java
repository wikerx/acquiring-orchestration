package com.scott.payment.settlement.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NacosInternalAuthScriptContractTest
 * @date : 2026-09-02 22:10
 * @email : scott_x@163.com
 * @description : 验证 Nacos 内部鉴权脚本兼容旧点号键，并统一生成保留注释的标准嵌套 YAML。
 * @status : create
 */
class NacosInternalAuthScriptContractTest {

    private static final String OLD_SECRET = "0123456789abcdef0123456789abcdef";
    private static final String NEW_SECRET = "fedcba9876543210fedcba9876543210";

    @TempDir
    Path tempDir;

    /** 旧配置必须可读取；同步输出只能保留一个 acquiring 根节点并使用标准嵌套结构。 */
    @Test
    void scriptShouldConvertLegacyDottedBundleToDocumentedNestedYaml() throws Exception {
        Path script = repositoryPath("scripts/manage-nacos-internal-auth.sh");
        Path current = tempDir.resolve("current.yaml");
        Path template = tempDir.resolve("template.yaml");
        Path fragment = tempDir.resolve("fragment.yaml");
        Path output = tempDir.resolve("output.yaml");
        Path headers = tempDir.resolve("headers.txt");
        Files.writeString(current, legacyConfiguration(), StandardCharsets.UTF_8);
        Files.writeString(template, businessTemplate(), StandardCharsets.UTF_8);
        Files.writeString(headers, "", StandardCharsets.UTF_8);

        String command = "source \"$1\"\n"
                + "work_dir=\"$2\"\n"
                + "[[ \"$(service_auth_bundle_format \"$3\")\" == legacy ]]\n"
                + "[[ \"$(service_edges service-admin | sort | uniq -d | wc -l | tr -d ' ')\" == 0 ]]\n"
                + "write_managed_auth_fragment_from_bundle service-payout \"$3\" \"$4\"\n"
                + "inject_managed_auth_fragment \"$5\" \"$4\" \"$6\"\n"
                + "verify_service_bundle_file service-payout \"$6\" \"$7\"\n"
                + "[[ \"$(service_auth_bundle_format \"$6\")\" == nested ]]\n"
                + "replace_bundle_edge \"$6\" openapi-payout \"$8\" \"\"\n"
                + "verify_service_bundle_file service-payout \"$6\" \"$7\"\n";
        Process process = new ProcessBuilder(
                "bash", "-c", command, "bash",
                script.toString(), tempDir.toString(), current.toString(), fragment.toString(),
                template.toString(), output.toString(), headers.toString(), NEW_SECRET)
                .redirectErrorStream(true)
                .start();
        String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertThat(exitCode).as(processOutput).isZero();
        String yaml = Files.readString(output);
        assertThat(yaml)
                .contains("# 保留业务配置注释。")
                .contains("acquiring:\n  mq:")
                .contains("  # --- managed internal-auth bundle; rotate secrets through this script ---")
                .contains("  internal-auth:\n    # 参数说明：按真实调用关系隔离的凭据集合")
                .contains("      openapi-payout:")
                .contains("        active-secret: " + NEW_SECRET)
                .contains("        previous-secret: \"\"")
                .doesNotContain("acquiring.internal-auth.edges:");
        assertThat(yaml.lines().filter("acquiring:"::equals).count()).isEqualTo(1);
    }

    /** 无 acquiring 业务根节点时允许新增一次；已有嵌套配置再次规范化必须保持内容稳定。 */
    @Test
    void scriptShouldAppendOneRootAndKeepNestedBundleStable() throws Exception {
        Path script = repositoryPath("scripts/manage-nacos-internal-auth.sh");
        Path legacy = tempDir.resolve("legacy.yaml");
        Path nested = tempDir.resolve("nested.yaml");
        Path normalizedAgain = tempDir.resolve("normalized-again.yaml");
        Files.writeString(legacy, legacyConfigurationWithoutAcquiringRoot(), StandardCharsets.UTF_8);

        String command = "source \"$1\"\n"
                + "work_dir=\"$2\"\n"
                + "normalize_service_bundle_to_nested service-payout \"$3\" \"$4\"\n"
                + "normalize_service_bundle_to_nested service-payout \"$4\" \"$5\"\n"
                + "cmp -s \"$4\" \"$5\"\n";
        Process process = new ProcessBuilder(
                "bash", "-c", command, "bash",
                script.toString(), tempDir.toString(), legacy.toString(), nested.toString(),
                normalizedAgain.toString())
                .redirectErrorStream(true)
                .start();
        String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertThat(exitCode).as(processOutput).isZero();
        String yaml = Files.readString(nested);
        assertThat(yaml)
                .contains("spring:\n  application:\n    name: service-payout")
                .contains("acquiring:\n  # --- managed internal-auth bundle")
                .doesNotContain("acquiring.internal-auth.edges:");
        assertThat(yaml.lines().filter("acquiring:"::equals).count()).isEqualTo(1);
    }

    /** 重复 acquiring 根节点会导致 YAML 覆盖语义不确定，脚本必须在发布前失败关闭。 */
    @Test
    void scriptShouldRejectDuplicateAcquiringRoots() throws Exception {
        Path script = repositoryPath("scripts/manage-nacos-internal-auth.sh");
        Path template = tempDir.resolve("duplicate-root-template.yaml");
        Path fragment = tempDir.resolve("fragment.yaml");
        Path output = tempDir.resolve("duplicate-root-output.yaml");
        Files.writeString(template, "acquiring:\n  one: true\nacquiring:\n  two: true\n",
                StandardCharsets.UTF_8);
        Files.writeString(fragment, "  internal-auth:\n    edges: {}\n", StandardCharsets.UTF_8);

        String command = "source \"$1\"\n"
                + "work_dir=\"$2\"\n"
                + "inject_managed_auth_fragment \"$3\" \"$4\" \"$5\"\n";
        Process process = new ProcessBuilder(
                "bash", "-c", command, "bash",
                script.toString(), tempDir.toString(), template.toString(), fragment.toString(),
                output.toString())
                .redirectErrorStream(true)
                .start();
        String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertThat(exitCode).isNotZero();
        assertThat(processOutput).contains("duplicate acquiring roots");
    }

    /** 模板已有未受控 internal-auth 节点时禁止再次插入同名 key，避免 YAML 后值覆盖前值。 */
    @Test
    void scriptShouldRejectUnmanagedInternalAuthNode() throws Exception {
        Path script = repositoryPath("scripts/manage-nacos-internal-auth.sh");
        Path template = tempDir.resolve("unmanaged-auth-template.yaml");
        Path fragment = tempDir.resolve("fragment.yaml");
        Path output = tempDir.resolve("unmanaged-auth-output.yaml");
        Files.writeString(template, "acquiring:\n  internal-auth:\n    audit-enabled: true\n",
                StandardCharsets.UTF_8);
        Files.writeString(fragment, "  internal-auth:\n    edges: {}\n", StandardCharsets.UTF_8);

        String command = "source \"$1\"\n"
                + "work_dir=\"$2\"\n"
                + "inject_managed_auth_fragment \"$3\" \"$4\" \"$5\"\n";
        Process process = new ProcessBuilder(
                "bash", "-c", command, "bash",
                script.toString(), tempDir.toString(), template.toString(), fragment.toString(),
                output.toString())
                .redirectErrorStream(true)
                .start();
        String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertThat(exitCode).isNotZero();
        assertThat(processOutput).contains("unmanaged internal-auth node");
    }

    /** 服务配置携带职责外调用边等同越权，校验必须在发布前拒绝。 */
    @Test
    void scriptShouldRejectUnexpectedServiceEdge() throws Exception {
        Path script = repositoryPath("scripts/manage-nacos-internal-auth.sh");
        Path bundle = tempDir.resolve("over-privileged-bundle.yaml");
        Path headers = tempDir.resolve("headers.txt");
        Files.writeString(bundle, nestedConfigurationWithUnexpectedEdge(), StandardCharsets.UTF_8);
        Files.writeString(headers, "", StandardCharsets.UTF_8);

        String command = "source \"$1\"\n"
                + "work_dir=\"$2\"\n"
                + "verify_service_bundle_file service-payout \"$3\" \"$4\"\n";
        Process process = new ProcessBuilder(
                "bash", "-c", command, "bash",
                script.toString(), tempDir.toString(), bundle.toString(), headers.toString())
                .redirectErrorStream(true)
                .start();
        String processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertThat(exitCode).isNotZero();
        assertThat(processOutput).contains("least-privilege edge validation failed");
    }

    private String legacyConfiguration() {
        return "acquiring:\n"
                + "  mq:\n"
                + "    enabled: true\n"
                + "\n"
                + "# --- managed internal-auth bundle; rotate secrets through this script ---\n"
                + "acquiring.internal-auth.edges:\n"
                + "  openapi-payout:\n"
                + "    active-secret: " + OLD_SECRET + "\n"
                + "    previous-secret: \"\"\n";
    }

    private String businessTemplate() {
        return "# 保留业务配置注释。\n"
                + "acquiring:\n"
                + "  mq:\n"
                + "    # 参数说明：测试业务参数。\n"
                + "    enabled: true\n"
                + "\n"
                + "internal-service:\n"
                + "  auth:\n"
                + "    # 参数说明：测试允许时间偏差。\n"
                + "    allowed-clock-skew: 5m\n";
    }

    private String legacyConfigurationWithoutAcquiringRoot() {
        return "spring:\n"
                + "  application:\n"
                + "    name: service-payout\n"
                + "\n"
                + "# --- managed internal-auth bundle; rotate secrets through this script ---\n"
                + "acquiring.internal-auth.edges:\n"
                + "  openapi-payout:\n"
                + "    active-secret: " + OLD_SECRET + "\n"
                + "    previous-secret: \"\"\n";
    }

    private String nestedConfigurationWithUnexpectedEdge() {
        return "acquiring:\n"
                + "  # --- managed internal-auth bundle; rotate secrets through this script ---\n"
                + "  internal-auth:\n"
                + "    edges:\n"
                + "      openapi-payout:\n"
                + "        active-secret: " + OLD_SECRET + "\n"
                + "        previous-secret: \"\"\n"
                + "      admin-settlement:\n"
                + "        active-secret: " + NEW_SECRET + "\n"
                + "        previous-secret: \"\"\n";
    }

    private Path repositoryPath(String relativePath) {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return direct;
        }
        return Path.of("..").resolve(relativePath);
    }
}
