package com.scott.payment.admin.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PendingBalanceSchemaContractTests
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 在途余额数据库脚本契约测试，防止重新创建旧明细表或移除空表保护与交易聚合索引。
 * @status : create
 */
class PendingBalanceSchemaContractTests {

    /** 基础结构不得再创建旧在途表，迁移必须先校验空表并补齐交易聚合索引。 */
    @Test
    void shouldKeepPendingBalanceDerivedFromTransactionOperations() throws IOException {
        String foundation = Files.readString(Path.of(
                "src/main/resources/sql/fee-account-foundation-schema.sql"));
        String migration = Files.readString(Path.of(
                "src/main/resources/sql/transaction-pending-balance-migration.sql"));

        assertThat(foundation)
                .doesNotContain("CREATE TABLE IF NOT EXISTS merchant_pending_fund_item")
                .contains("transaction_operation 成功未结算资金动作实时聚合");
        assertThat(migration).contains(
                "SELECT COUNT(1) INTO legacy_row_count FROM merchant_pending_fund_item",
                "SIGNAL SQLSTATE '45000'",
                "idx_pending_fund_balance",
                "merchant_id`,`transaction_status`,`settlement_status`,`transaction_type`,`transaction_date_time");
    }
}
