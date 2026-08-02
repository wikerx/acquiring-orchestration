package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionShardingModeTest
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 验证交易迁移模式严格保持 Legacy 单写、Compare 只读和 ShardingSphere 单写语义。
 * @status : create
 */
class TransactionShardingModeTest {

    @Test
    void shouldKeepLegacyAsFailClosedDefault() {
        TransactionShardingProperties properties = new TransactionShardingProperties();

        assertThat(properties.getMode()).isEqualTo(TransactionShardingMode.LEGACY);
        assertThat(properties.getMode().isCompositeDataSourceRequired()).isFalse();
        assertThat(properties.getMode().isShardingWriteAllowed()).isFalse();
    }

    @Test
    void shouldAllowComparisonReadsWithoutAllowingShardingWrites() {
        assertThat(TransactionShardingMode.COMPARE.isCompositeDataSourceRequired()).isTrue();
        assertThat(TransactionShardingMode.COMPARE.isReadComparisonEnabled()).isTrue();
        assertThat(TransactionShardingMode.COMPARE.isShardingWriteAllowed()).isFalse();
    }

    @Test
    void shouldAllowShardingWritesOnlyInShardingSphereMode() {
        assertThat(TransactionShardingMode.SHARDINGSPHERE.isCompositeDataSourceRequired()).isTrue();
        assertThat(TransactionShardingMode.SHARDINGSPHERE.isReadComparisonEnabled()).isFalse();
        assertThat(TransactionShardingMode.SHARDINGSPHERE.isShardingWriteAllowed()).isTrue();
    }
}
