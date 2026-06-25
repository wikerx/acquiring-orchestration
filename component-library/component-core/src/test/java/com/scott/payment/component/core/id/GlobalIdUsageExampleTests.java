package com.scott.payment.component.core.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalIdUsageExampleTests {

    @Test
    void createTransactionShouldUseGlobalIdGenerator() {
        GlobalIdGenerator globalIdGenerator = new LocalGlobalIdGenerator();

        String transactionId = globalIdGenerator.nextId();

        assertThat(GlobalIdValidator.isValid(transactionId)).isTrue();
    }
}
