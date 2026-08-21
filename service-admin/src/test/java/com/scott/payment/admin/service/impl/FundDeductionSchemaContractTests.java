package com.scott.payment.admin.service.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FundDeductionSchemaContractTests
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 账户扣减数据库、权限和回滚保护契约测试。
 * @status : create
 */
class FundDeductionSchemaContractTests {

    /** 迁移必须包含独立单据、资金幂等约束、七项权限及可回滚保护。 */
    @Test
    void shouldKeepDeductionMigrationCompleteAndReversible() throws IOException {
        String migration = Files.readString(Path.of(
                "src/main/resources/sql/fund-account-deduction-migration.sql"));
        String rollback = Files.readString(Path.of(
                "src/main/resources/sql/fund-account-deduction-rollback.sql"));

        assertThat(migration).contains(
                "CREATE TABLE IF NOT EXISTS merchant_fund_deduction",
                "uk_fund_deduction_request",
                "uk_fund_deduction_ledger",
                "chk_fund_deduction_amount",
                "chk_fund_deduction_category",
                "fund:deduction:list",
                "fund:deduction:detail",
                "fund:deduction:add",
                "fund:deduction:audit",
                "fund:deduction:recheck",
                "fund:deduction:reject",
                "fund:deduction:export",
                "BALANCE_DEDUCTION");
        assertThat(rollback).contains(
                "rollback_empty_fund_deduction",
                "merchant_fund_deduction is not empty; reconcile before rollback",
                "DROP TABLE IF EXISTS merchant_fund_deduction");
    }
}
