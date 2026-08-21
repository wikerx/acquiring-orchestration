package com.scott.payment.admin.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeAccountSchemaOptimizationContractTests
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 费用和资金账户结构收敛脚本契约测试，防止冗余字段、关键索引或资金约束被初始化脚本重新引入。
 * @status : create
 */
class FeeAccountSchemaOptimizationContractTests {

    /** 最终基础结构必须只保留单一事实字段，并声明风控试算快照及资金一致性约束。 */
    @Test
    void shouldKeepFoundationSchemaAlignedWithOptimizedModel() throws IOException {
        String foundation = Files.readString(Path.of(
                "src/main/resources/sql/fee-account-foundation-schema.sql"));

        assertThat(foundation)
                .doesNotContain("regular_delay_unit CHAR", "reverse_restricted TINYINT", "balance_type VARCHAR")
                .contains("risk_service_type VARCHAR(16) NOT NULL DEFAULT 'NONE'",
                        "idx_fee_plan_type_list",
                        "idx_fund_ledger_account_time",
                        "uk_reserve_merchant_source_business",
                        "chk_fund_ledger_balance",
                        "chk_fee_tier_range");
    }

    /** 优化脚本必须先阻断不兼容存量数据，且提供三个删列字段的显式回滚定义。 */
    @Test
    void shouldKeepDestructiveMigrationGuardedAndReversible() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/sql/fee-account-structure-optimization.sql"));
        String rollback = Files.readString(Path.of(
                "src/main/resources/sql/fee-account-structure-optimization-rollback.sql"));

        assertThat(migration).contains(
                "validate_fee_account_optimization",
                "fee_plan_version contains inconsistent settlement delay units",
                "merchant_fund_ledger contains unsupported balance types",
                "merchant_fund_account contains inconsistent reverse restriction flags",
                "CALL `drop_fee_account_column`('fee_plan_version', 'regular_delay_unit')",
                "CALL `drop_fee_account_column`('merchant_fund_ledger', 'balance_type')",
                "CALL `drop_fee_account_column`('merchant_fund_account', 'reverse_restricted')");
        assertThat(rollback).contains(
                "CALL `rollback_add_column`('fee_plan_version', 'regular_delay_unit'",
                "CALL `rollback_add_column`('merchant_fund_ledger', 'balance_type'",
                "CALL `rollback_add_column`('merchant_fund_account', 'reverse_restricted'");
    }
}
