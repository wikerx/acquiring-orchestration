package com.scott.payment.data.config;

import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证卡资料消费开关与交易分片拓扑必须同步启用。 */
class DataCardVaultShardingActivationGuardTests {

    /** 缺少卡资料表的兼容基线不能承载卡资料消费。 */
    @Test
    void shouldRejectCardVaultActivationOnPreviousTopology() {
        TransactionShardingProperties sharding = sharding(TransactionShardingProperties.previousLogicTables());

        assertThrows(IllegalStateException.class,
                () -> new DataCardVaultShardingActivationGuard(sharding).afterPropertiesSet());
    }

    /** 包含卡资料表和收货快照表的 25 表完整目标拓扑允许消费服务激活。 */
    @Test
    void shouldAllowCardVaultActivationOnTargetTopology() {
        TransactionShardingProperties sharding = sharding(TransactionShardingProperties.defaultLogicTables());

        assertDoesNotThrow(() -> new DataCardVaultShardingActivationGuard(sharding).afterPropertiesSet());
    }

    private TransactionShardingProperties sharding(List<String> logicTables) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setLogicTables(logicTables);
        return properties;
    }
}
