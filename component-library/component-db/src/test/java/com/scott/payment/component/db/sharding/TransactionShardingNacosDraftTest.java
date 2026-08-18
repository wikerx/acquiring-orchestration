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

    /** 仓库中的 dev 草案必须能通过生产代码使用的同一激活校验。 */
    @Test
    void shouldValidateVersionedDevNacosDraft() throws Exception {
        Path draftPath = findDraftPath();
        FileSystemResource resource = new FileSystemResource(draftPath);
        List<PropertySource<?>> yamlSources = new YamlPropertySourceLoader().load("sharding-dev", resource);
        MutablePropertySources propertySources = new MutablePropertySources();
        yamlSources.forEach(propertySources::addLast);

        TransactionShardingProperties properties = new Binder(ConfigurationPropertySources.from(propertySources))
                .bind("transaction-sharding", Bindable.of(TransactionShardingProperties.class))
                .orElseThrow(() -> new IllegalStateException("transaction-sharding draft is missing"));

        properties.validateForActivation();
        assertThat(properties.getLogicTables())
                .containsExactlyElementsOf(TransactionShardingProperties.defaultLogicTables());
        assertThat(properties.getPhysicalNodes()).containsExactly("202603", "202604");
        assertThat(properties.getRuleChecksum()).startsWith("sha256:");
    }

    /** 从当前模块向上查找仓库级 Nacos 草案，兼容 IDE 和 Maven reactor 工作目录。 */
    private Path findDraftPath() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("docs/deployment/nacos/sharding-dev.yaml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("sharding-dev.yaml draft is missing");
    }
}
