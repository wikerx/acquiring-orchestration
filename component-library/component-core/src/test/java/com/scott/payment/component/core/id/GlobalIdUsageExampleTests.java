package com.scott.payment.component.core.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalIdUsageExampleTests
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : GlobalIdUsageExampleTests 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
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
