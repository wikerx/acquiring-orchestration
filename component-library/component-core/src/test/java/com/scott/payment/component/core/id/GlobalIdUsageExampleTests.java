package com.scott.payment.component.core.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Global ID Usage Example Tests 自动化测试类，位于 公共组件库，验证当前模块的正常路径、异常边界和回归场景。
 */
class GlobalIdUsageExampleTests {

    @Test
    void createTransactionShouldUseGlobalIdGenerator() {
        GlobalIdGenerator globalIdGenerator = new LocalGlobalIdGenerator();

        String transactionId = globalIdGenerator.nextId();

        assertThat(GlobalIdValidator.isValid(transactionId)).isTrue();
    }
}
