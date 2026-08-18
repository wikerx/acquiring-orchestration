package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingRuleChecksumTest
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 验证规则 checksum 的稳定性和启用前完整性门禁。
 * @status : create
 */
class TransactionShardingRuleChecksumTest {

    /** 当前 25 表规则缺少任一正式表时必须拒绝激活，避免服务启动后才暴露缺表。 */
    @Test
    void shouldRejectPreviousTwentyThreeTableBaselineAfterMigration() {
        TransactionShardingProperties properties = validProperties();
        List<String> previousBaseline = new ArrayList<>(TransactionShardingProperties.defaultLogicTables());
        previousBaseline.remove("transaction_card_vault");
        properties.setLogicTables(previousBaseline);
        properties.setRuleChecksum(TransactionShardingRuleChecksum.calculate(properties));

        assertThrows(IllegalStateException.class, properties::validateForActivation);
    }

    /** 兼容集合也不能把未知表误判为正式拓扑。 */
    @Test
    void shouldRejectUnknownTableInsideTwentyThreeTableTopology() {
        TransactionShardingProperties properties = validProperties();
        List<String> invalidTopology = new ArrayList<>(TransactionShardingProperties.previousLogicTables());
        invalidTopology.set(0, "transaction_unknown");
        properties.setLogicTables(invalidTopology);
        properties.setRuleChecksum(TransactionShardingRuleChecksum.calculate(properties));

        assertThrows(IllegalStateException.class, properties::validateForActivation);
    }

    @Test
    void shouldRemainStableWhenConfiguredCollectionsHaveDifferentOrder() {
        TransactionShardingProperties first = validProperties();
        TransactionShardingProperties second = validProperties();
        List<String> reversedTables = new ArrayList<>(second.getLogicTables());
        Collections.reverse(reversedTables);
        second.setLogicTables(reversedTables);
        second.setPhysicalNodes(List.of("202604", "202603"));

        assertEquals(TransactionShardingRuleChecksum.calculate(first),
                TransactionShardingRuleChecksum.calculate(second));
    }

    @Test
    void shouldRejectActivationWhenChecksumOrTopologyIsInvalid() {
        TransactionShardingProperties properties = validProperties();
        properties.setRuleChecksum("sha256:invalid");
        assertThrows(IllegalStateException.class, properties::validateForActivation);

        properties = validProperties();
        properties.setLogicTables(List.of("transaction_order"));
        properties.setRuleChecksum(TransactionShardingRuleChecksum.calculate(properties));
        assertThrows(IllegalStateException.class, properties::validateForActivation);
    }

    @Test
    void shouldRejectNonPositiveQueryBudget() {
        TransactionShardingProperties properties = validProperties();
        properties.getQueryBudget().setMaxResultRows(0);
        properties.setRuleChecksum(TransactionShardingRuleChecksum.calculate(properties));

        assertThrows(IllegalStateException.class, properties::validateForActivation);
    }

    /** 候选规则校验失败后，上一版完整拓扑仍可通过激活门禁。 */
    @Test
    void shouldRestorePreviousPublishedRuleAfterCandidateChecksumFailure() {
        TransactionShardingProperties previous = validProperties();
        String previousChecksum = previous.getRuleChecksum();

        TransactionShardingProperties candidate = validProperties();
        candidate.setRuleVersion("2026.08.02-002");
        candidate.setRuleChecksum("sha256:invalid-candidate");

        assertThrows(IllegalStateException.class, candidate::validateForActivation);
        previous.validateForActivation();
        assertEquals("2026.08.02-001", previous.getRuleVersion());
        assertEquals(previousChecksum, previous.getRuleChecksum());
    }

    private TransactionShardingProperties validProperties() {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setRuleVersion("2026.08.02-001");
        properties.setPhysicalNodes(List.of("202603", "202604"));
        properties.setRuleChecksum(TransactionShardingRuleChecksum.calculate(properties));
        return properties;
    }
}
