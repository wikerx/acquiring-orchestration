package com.scott.payment.component.db.sharding;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验风控审计季度分表迁移包的只读前后检、非破坏性迁移和回退边界，不连接数据库。
 */
class RiskAuditShardingMigrationBundleContractTest {

    /**
     * 前检只能读取源表和元数据，不得修改任何风控事实。
     */
    @Test
    void precheckShouldRemainReadOnlyAndCoverBothSourceTables() throws IOException {
        String sql = executableSql(readSql("20261108_01_risk_audit_sharding_precheck.sql"));

        assertThat(sql).contains(
                "RISK_EVALUATION_RECORD",
                "RISK_EVALUATION_HIT_DETAIL",
                "RISK_RECORD_NO_UNIQUE",
                "DETAIL_HAS_PARENT_RECORD",
                "SUPPORTED_QUARTER_RANGE");
        assertThat(sql).doesNotContain(
                "ALTER TABLE", "CREATE TABLE", "INSERT ", "UPDATE ", "DELETE ",
                "DROP ", "TRUNCATE ", "RENAME ");
    }

    /**
     * 迁移脚本必须同时建立定位表、明细路由字段和两个季度的双表，并保留原始 ID。
     */
    @Test
    void migrationShouldCreateLocatorAndBothQuarterTablePairsWithoutDestructiveDdl() throws IOException {
        String sql = readSql("20261108_02_risk_audit_sharding_migration.sql").toUpperCase();

        assertThat(sql)
                .contains("ADD COLUMN EVALUATION_TIME DATETIME(3)")
                .contains("MODIFY COLUMN EVALUATION_TIME DATETIME(3) NOT NULL")
                .contains("KEY IDX_RISK_HIT_TIME_RECORD (EVALUATION_TIME, RISK_RECORD_NO, ID)")
                .contains("CREATE TABLE IF NOT EXISTS RISK_EVALUATION_LOCATOR")
                .contains("UNIQUE KEY UK_RISK_EVALUATION_LOCATOR_RECORD (RISK_RECORD_NO)")
                .contains("INSERT INTO RISK_EVALUATION_LOCATOR (RISK_RECORD_NO, EVALUATION_TIME)");
        for (String suffix : List.of("202603", "202604")) {
            assertThat(sql)
                    .contains("CREATE TABLE IF NOT EXISTS RISK_EVALUATION_RECORD_" + suffix)
                    .contains("CREATE TABLE IF NOT EXISTS RISK_EVALUATION_HIT_DETAIL_" + suffix)
                    .contains("INSERT INTO RISK_EVALUATION_RECORD_" + suffix + " (")
                    .contains("INSERT INTO RISK_EVALUATION_HIT_DETAIL_" + suffix + " (")
                    .contains("ALTER TABLE RISK_EVALUATION_RECORD_" + suffix + " AUTO_INCREMENT")
                    .contains("ALTER TABLE RISK_EVALUATION_HIT_DETAIL_" + suffix + " AUTO_INCREMENT");
        }
        assertThat(sql)
                .contains("ID, RISK_RECORD_NO")
                .doesNotContain("DROP TABLE", "TRUNCATE TABLE", "RENAME TABLE", "DELETE FROM");
    }

    /**
     * 后检必须覆盖表完整性、路由字段、定位一致性、双季度数量和号段门禁。
     */
    @Test
    void postcheckShouldCoverAllRiskAuditPublicationGates() throws IOException {
        String sql = readSql("20261108_03_risk_audit_sharding_postcheck.sql").toUpperCase();

        assertThat(sql).contains(
                "REQUIRED_TABLES_EXIST",
                "COUNT(*) = 7 AS PASSED",
                "DETAIL_ROUTING_COLUMN",
                "LOCATOR_COVERS_SOURCE",
                "LOCATOR_TIME_MATCHES_SOURCE",
                "Q3_RECORD_COUNT_MATCH",
                "Q4_RECORD_COUNT_MATCH",
                "Q3_DETAIL_COUNT_MATCH",
                "Q4_DETAIL_COUNT_MATCH",
                "AUTO_INCREMENT",
                "UK_RISK_EVALUATION_LOCATOR_RECORD",
                "IDX_RISK_HIT_TIME_RECORD");
    }

    /**
     * 回退脚本只允许检查切换后的差异事实，不得自动覆盖、删除或合并数据。
     */
    @Test
    void rollbackShouldRemainReadOnlyAndCheckEveryPhysicalTable() throws IOException {
        String sql = executableSql(readSql("20261108_04_risk_audit_sharding_rollback.sql"));

        assertThat(sql).contains(
                "Q3_RECORD_ONLY_IN_PHYSICAL",
                "Q4_RECORD_ONLY_IN_PHYSICAL",
                "Q3_DETAIL_ONLY_IN_PHYSICAL",
                "Q4_DETAIL_ONLY_IN_PHYSICAL");
        assertThat(sql).doesNotContain(
                "ALTER TABLE", "CREATE TABLE", "INSERT ", "UPDATE ", "DELETE ",
                "DROP ", "TRUNCATE ", "RENAME ");
    }

    /**
     * 去除空行和 SQL 行注释，避免操作指引中的禁止词影响只读断言。
     */
    private String executableSql(String sql) {
        return sql.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("--"))
                .reduce("", (left, right) -> left + right + "\n")
                .toUpperCase();
    }

    /**
     * 从当前模块向上查找仓库级 SQL 脚本，兼容 IDE 和 Maven Reactor 工作目录。
     */
    private String readSql(String fileName) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("docs/sql").resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IllegalStateException(fileName + " is missing");
    }
}
