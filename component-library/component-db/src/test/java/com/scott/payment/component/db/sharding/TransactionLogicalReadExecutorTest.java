package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Transaction 逻辑只读执行器测试。
 */
class TransactionLogicalReadExecutorTest {

    @Test
    void shouldReturnLogicalReadResult() {
        TransactionLogicalReadExecutor executor = new TransactionLogicalReadExecutor();

        String result = executor.read(() -> "logical-result");

        assertThat(result).isEqualTo("logical-result");
    }

    @Test
    void shouldPropagateLogicalReadFailure() {
        TransactionLogicalReadExecutor executor = new TransactionLogicalReadExecutor();

        assertThatThrownBy(() -> executor.read(() -> {
            throw new IllegalStateException("query failed");
        })).isInstanceOf(IllegalStateException.class);
    }

}
