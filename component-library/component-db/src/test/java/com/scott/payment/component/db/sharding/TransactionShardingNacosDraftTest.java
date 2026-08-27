package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingNacosDraftTest
 * @date : 2026-08-02 23:59
 * @email : scott_x@163.com
 * @description : 直接加载 Nacos 分片发布草案，验证版本、正式表、物理节点和 SHA-256 激活契约一致
 * @status : create
 */
class TransactionShardingNacosDraftTest {

    /** 当前 dev 已发布基线必须保持正式 28 表、清分直连权限和精确 checksum。 */
    @Test
    void shouldValidateVersionedDevNacosDraft() throws Exception {
        TransactionShardingProperties properties = bind(
                "sharding-dev.yaml", "transaction-sharding", TransactionShardingProperties.class);

        properties.validateForActivation();
        assertThat(properties.usesFormalLogicTableTopology()).isTrue();
        assertThat(properties.getLogicTables())
                .containsExactlyElementsOf(TransactionShardingProperties.defaultLogicTables());
        assertThat(properties.getPhysicalNodes()).containsExactly("202603", "202604");
        assertThat(properties.getDirectAccessServices()).contains("service-clearing");
        assertThat(properties.getRuleChecksum())
                .isEqualTo(TransactionShardingRuleChecksum.calculate(properties));
    }

    /** 28 表候选必须使用正式清单和与候选版本绑定的精确 checksum。 */
    @Test
    void shouldValidateClearingTwentyEightTableCandidate() throws Exception {
        TransactionShardingProperties properties = bind(
                "transaction-sharding-dev-draft.yaml",
                "transaction-sharding",
                TransactionShardingProperties.class);

        properties.validateForActivation();
        assertThat(properties.usesFormalLogicTableTopology()).isTrue();
        assertThat(properties.getLogicTables())
                .containsExactlyElementsOf(TransactionShardingProperties.defaultLogicTables());
        assertThat(properties.getDirectAccessServices()).contains("service-clearing");
        assertThat(properties.getRuleChecksum())
                .isEqualTo(TransactionShardingRuleChecksum.calculate(properties));
    }

    /** Job 治理候选必须完整覆盖 28 表，不能继续使用旧 25 表预建清单。 */
    @Test
    void shouldBindCompleteClearingGovernanceCandidate() throws Exception {
        TransactionShardingGovernanceProperties properties = bind(
                "transaction-sharding-governance-dev-draft.yaml",
                "transaction-sharding.governance",
                TransactionShardingGovernanceProperties.class);

        assertThat(properties.getTables()).hasSize(TransactionShardingProperties.FORMAL_LOGIC_TABLE_COUNT);
        assertThat(properties.getTables().values())
                .extracting(TransactionShardingGovernanceProperties.TableRule::getLogicalTable)
                .containsExactlyInAnyOrderElementsOf(TransactionShardingProperties.defaultLogicTables());
    }

    /** 使用 Spring Boot 正式 Binder 加载候选，避免测试自行解释 YAML 产生口径差异。 */
    private <T> T bind(String fileName, String prefix, Class<T> type) throws Exception {
        FileSystemResource resource = new FileSystemResource(findDraftPath(fileName));
        List<PropertySource<?>> yamlSources = new YamlPropertySourceLoader().load(fileName, resource);
        MutablePropertySources propertySources = new MutablePropertySources();
        yamlSources.forEach(propertySources::addLast);
        return new Binder(ConfigurationPropertySources.from(propertySources))
                .bind(prefix, Bindable.of(type))
                .orElseThrow(() -> new IllegalStateException(prefix + " draft is missing"));
    }

    /** 从当前模块向上查找仓库级 Nacos 草案，兼容 IDE 和 Maven reactor 工作目录。 */
    private Path findDraftPath(String fileName) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("docs/deployment/nacos").resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(fileName + " draft is missing");
    }
}
