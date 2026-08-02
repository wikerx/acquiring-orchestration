package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Transaction 逻辑只读执行器作用域测试。
 */
class TransactionLogicalReadExecutorTest {

    @Test
    void shouldExposeLogicalRouteOnlyInsideReadCallback() {
        TransactionLogicalReadExecutor executor = new TransactionLogicalReadExecutor();

        boolean activeInside = executor.read(executor::isLogicalRouteActive);

        assertThat(activeInside).isTrue();
        assertThat(executor.isLogicalRouteActive()).isFalse();
    }

    @Test
    void shouldClearLogicalRouteAfterQueryFailure() {
        TransactionLogicalReadExecutor executor = new TransactionLogicalReadExecutor();

        assertThatThrownBy(() -> executor.read(() -> {
            throw new IllegalStateException("query failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(executor.isLogicalRouteActive()).isFalse();
    }

}
