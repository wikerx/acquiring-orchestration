package com.scott.payment.settlement.config;

import com.scott.payment.settlement.SettlementApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementAutomaticOperationContractTest
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 防止自动结算被 yml 或 Nacos 业务开关静默关闭，并锁定服务启动即调度的运行边界。
 * @status : create
 */
class SettlementAutomaticOperationContractTest {

    /** 服务配置只允许内部鉴权 enabled，不得出现结算、影子比例或商户白名单业务开关。 */
    @Test
    void applicationConfigurationShouldNotContainBusinessSwitches() throws IOException {
        Path yamlPath = Path.of("src/main/resources/application.yml");
        String yaml = Files.readString(yamlPath);
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        String parseableYaml = yaml.replace("@profiles.active@", "'test'");
        factory.setResources(new ByteArrayResource(parseableYaml.getBytes(StandardCharsets.UTF_8)));
        Properties properties = factory.getObject();

        assertThat(properties).isNotNull();
        List<String> enabledKeys = properties.keySet().stream()
                .map(String::valueOf)
                .filter(key -> key.endsWith(".enabled"))
                .sorted()
                .toList();
        assertThat(enabledKeys)
                .containsExactly("internal-service.auth.enabled");
        assertThat(yaml).contains("name: service-settlement").doesNotContain(
                "settlement.enabled", "shadow-mode", "allowlist", "rollout-percentage");
    }

    /** 启动类必须直接启用调度，准备和处理入口都使用代码级固定周期。 */
    @Test
    void applicationShouldAutomaticallySchedulePreparationAndProcessing() throws NoSuchMethodException {
        assertThat(SettlementApplication.class.getAnnotation(EnableScheduling.class)).isNotNull();
        assertThat(SettlementAutomaticScheduler.class.getDeclaredMethod("prepareBatches")
                .getAnnotation(Scheduled.class)).isNotNull();
        assertThat(SettlementAutomaticScheduler.class.getDeclaredMethod("processBatches")
                .getAnnotation(Scheduled.class)).isNotNull();
        assertThat(SettlementAutomaticScheduler.class.getDeclaredMethod("projectTransactions")
                .getAnnotation(Scheduled.class)).isNotNull();
        assertThat(SettlementAutomaticScheduler.class.getDeclaredMethod("publishEvents")
                .getAnnotation(Scheduled.class)).isNotNull();
    }
}
