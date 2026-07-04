package com.scott.payment.component.core.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalIdUsageExampleTests
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Global Id Usage Example Tests，位于 component-library/component-core 的测试层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
class GlobalIdUsageExampleTests {

    @Test
    void createTransactionShouldUseGlobalIdGenerator() {
        GlobalIdGenerator globalIdGenerator = new LocalGlobalIdGenerator();

        String transactionId = globalIdGenerator.nextId();

        assertThat(GlobalIdValidator.isValid(transactionId)).isTrue();
    }
}
