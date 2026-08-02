package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.info.Info;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingInfoContributorTest
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 验证五个直连服务仅暴露非敏感规则摘要，非直连服务不增加交易分片信息。
 * @status : create
 */
class TransactionShardingInfoContributorTest {

    @Test
    void shouldExposeLoadedRuleForDirectAccessService() {
        TransactionShardingProperties properties = publishedLegacyProperties();
        TransactionShardingRuntimeState runtimeState = new TransactionShardingRuntimeState();
        runtimeState.loadLegacy(properties);
        TransactionShardingInfoContributor contributor = new TransactionShardingInfoContributor(
                properties, runtimeState,
                new MockEnvironment().withProperty("spring.application.name", "service-payment"));

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);

        assertThat(builder.build().getDetails().get("transactionSharding"))
                .isEqualTo(Map.of(
                        "mode", "LEGACY",
                        "ruleVersion", "2026.08.02-001",
                        "ruleChecksumPrefix", properties.getRuleChecksum().substring(0, 19),
                        "compositeDataSourceActive", false));
    }

    @Test
    void shouldNotExposeRuleForNonDirectService() {
        TransactionShardingProperties properties = publishedLegacyProperties();
        TransactionShardingRuntimeState runtimeState = new TransactionShardingRuntimeState();
        runtimeState.loadLegacy(properties);
        TransactionShardingInfoContributor contributor = new TransactionShardingInfoContributor(
                properties, runtimeState,
                new MockEnvironment().withProperty("spring.application.name", "service-gateway"));

        Info.Builder builder = new Info.Builder();
        contributor.contribute(builder);

        assertThat(builder.build().getDetails()).doesNotContainKey("transactionSharding");
    }

    private TransactionShardingProperties publishedLegacyProperties() {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setMode(TransactionShardingMode.LEGACY);
        properties.setRuleVersion("2026.08.02-001");
        properties.setPhysicalNodes(List.of("202603", "202604"));
        properties.setRuleChecksum(TransactionShardingRuleChecksum.calculate(properties));
        return properties;
    }
}
