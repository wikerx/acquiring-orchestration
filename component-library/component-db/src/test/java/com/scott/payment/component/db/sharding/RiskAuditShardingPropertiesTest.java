package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证风控审计分片规则只接受完整、版本化且 checksum 一致的正式配置。
 */
class RiskAuditShardingPropertiesTest {

    /**
     * 验证启用状态下完整规则可以通过激活校验。
     */
    @Test
    void shouldAcceptPublishedCompleteRule() {
        RiskAuditShardingProperties properties = publishedRule();

        properties.validateForActivation();

        assertThat(properties.getLogicTables())
                .containsExactlyElementsOf(RiskAuditShardingProperties.defaultLogicTables());
    }

    /**
     * 验证缺少任一风控审计逻辑表时拒绝启动，避免主记录和执行明细只迁移一半。
     */
    @Test
    void shouldRejectIncompleteTopology() {
        RiskAuditShardingProperties properties = publishedRule();
        properties.setLogicTables(List.of(RiskAuditShardingProperties.EVALUATION_RECORD_TABLE));
        properties.setRuleChecksum(RiskAuditShardingRuleChecksum.calculate(properties));

        assertThatThrownBy(properties::validateForActivation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both formal logic tables");
    }

    /**
     * 验证配置正文变化但未更新 checksum 时拒绝激活。
     */
    @Test
    void shouldRejectChecksumDrift() {
        RiskAuditShardingProperties properties = publishedRule();
        properties.setPhysicalNodes(List.of("202604"));

        assertThatThrownBy(properties::validateForActivation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum mismatch");
    }

    /**
     * 构造通过发布门禁的风控审计分片规则。
     */
    private RiskAuditShardingProperties publishedRule() {
        RiskAuditShardingProperties properties = new RiskAuditShardingProperties();
        properties.setEnabled(true);
        properties.setRuleVersion("2026.11.08-risk-audit-001");
        properties.setPhysicalNodes(List.of("202603", "202604"));
        properties.setRuleChecksum(RiskAuditShardingRuleChecksum.calculate(properties));
        return properties;
    }
}
