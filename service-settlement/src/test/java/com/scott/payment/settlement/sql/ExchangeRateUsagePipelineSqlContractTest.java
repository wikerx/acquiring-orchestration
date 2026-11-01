package com.scott.payment.settlement.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 汇率实际使用消息链路数据库迁移合同。
 *
 * <p>锁定快照最终幂等键、结算 Outbox 双来源字段、迁移后事实检查和有数据时拒绝破坏性回滚，
 * 防止支付或结算已经产生汇率使用事实后脚本语义发生漂移。</p>
 */
class ExchangeRateUsagePipelineSqlContractTest {

    private static final String PRECHECK =
            "docs/sql/20261101_01_exchange_rate_usage_pipeline_precheck.sql";

    private static final String MIGRATION =
            "docs/sql/20261101_02_exchange_rate_usage_pipeline_migration.sql";

    private static final String POSTCHECK =
            "docs/sql/20261101_03_exchange_rate_usage_pipeline_postcheck.sql";

    private static final String ROLLBACK =
            "docs/sql/20261101_04_exchange_rate_usage_pipeline_rollback.sql";

    private static final String LOCAL_HISTORY_BACKFILL =
            "docs/sql/20261102_01_local_exchange_rate_usage_history_backfill.sql";

    /**
     * 迁移前检查必须拒绝缺表、非法历史快照和占用新事件号前缀的存量数据。
     */
    @Test
    void precheckShouldRejectMissingTablesInvalidFactsAndReservedEventPrefixes() throws IOException {
        String precheck = readRepositoryFile(PRECHECK);

        assertThat(precheck).contains(
                "missing_required_table_count",
                "invalid_existing_snapshot_count",
                "reserved_usage_event_prefix_collision_count",
                "invalid_existing_settlement_outbox_count",
                "event_no LIKE 'FXTR:%'",
                "event_no LIKE 'FXST:%'");
    }

    /**
     * 正向迁移必须建立快照最终幂等键，并允许结算 Outbox 分别承载交易候选或批次汇率来源。
     */
    @Test
    void migrationShouldAddDurableUsageIdentityAndSettlementRateSource() throws IOException {
        String migration = readRepositoryFile(MIGRATION);

        assertThat(migration).contains(
                "ADD COLUMN usage_event_id VARCHAR(96) NULL",
                "SET usage_event_id = CONCAT('LEGACY:'",
                "MODIFY COLUMN usage_event_id VARCHAR(96) NOT NULL",
                "ADD UNIQUE KEY uk_exchange_snapshot_usage_event (usage_event_id)",
                "ADD COLUMN rate_usage_id BIGINT NULL",
                "MODIFY COLUMN candidate_id BIGINT NULL",
                "ADD UNIQUE KEY uk_settlement_event_rate_usage",
                "(settlement_batch_no, rate_usage_id, tag)");
    }

    /**
     * 迁移后检查必须覆盖列和唯一键形态、重复事件、非法跨币种事实以及两类 Outbox 来源互斥。
     */
    @Test
    void postcheckShouldVerifySchemaIdempotencyAndImmutableUsageFacts() throws IOException {
        String postcheck = readRepositoryFile(POSTCHECK);

        assertThat(postcheck).contains(
                "missing_usage_pipeline_column_count",
                "nullable_usage_event_id_column_count",
                "invalid_settlement_outbox_source_column_shape_count",
                "missing_usage_pipeline_unique_index_count",
                "duplicate_usage_event_id_count",
                "invalid_new_usage_snapshot_count",
                "business_rate_id IS NULL",
                "base_currency = quote_currency",
                "applied_time < rate_effective_time",
                "invalid_settlement_rate_usage_outbox_count",
                "invalid_settlement_projection_outbox_count");
    }

    /**
     * 回滚必须在任何新快照或结算汇率 Outbox 已存在时失败关闭，只允许空链路恢复旧列形态。
     */
    @Test
    void rollbackShouldRefuseToDeleteCommittedUsageFacts() throws IOException {
        String rollback = readRepositoryFile(ROLLBACK);

        assertThat(rollback).contains(
                "usage_event_id NOT LIKE 'LEGACY:%'",
                "rate_usage_id IS NOT NULL",
                "SIGNAL SQLSTATE '45000'",
                "use forward repair instead of destructive rollback",
                "MODIFY COLUMN candidate_id BIGINT NOT NULL");
    }

    /**
     * 本机历史回填必须限制目标库、保留执行前备份、按真实类型修复且不伪造清分汇率。
     */
    @Test
    void localHistoryBackfillShouldBeGuardedIdempotentAndSourceBacked() throws IOException {
        String backfill = readRepositoryFile(LOCAL_HISTORY_BACKFILL);

        assertThat(backfill).contains(
                "DATABASE() <> 'payment_acquiring'",
                "exchange_rate_usage_snapshot_backup_20261102",
                "START TRANSACTION",
                "ROLLBACK",
                "INSERT IGNORE INTO exchange_rate_usage_snapshot",
                "snapshot.business_type = fact.business_type",
                "orders.dcc_enabled = 1 OR orders.edc_enabled = 1",
                "operations.dcc_enabled = 1 OR operations.edc_enabled = 1",
                "batch.batch_status = 'POSTED'",
                "settlement_result_item",
                "transaction exchange-rate history backfill verification failed",
                "settlement exchange-rate history backfill verification failed");
        assertThat(backfill).doesNotContain("CLEARING_RATE");
    }

    /**
     * 从仓库根目录或 Maven 子模块目录读取受版本控制的 SQL 文件。
     *
     * @param relativePath 相对后端仓库根目录的 SQL 路径
     * @return SQL 文件完整文本
     */
    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        Path path = Files.exists(direct) ? direct : Path.of("..").resolve(relativePath).normalize();
        assertThat(path).as("SQL contract file %s must exist", relativePath).exists();
        return Files.readString(path);
    }
}
