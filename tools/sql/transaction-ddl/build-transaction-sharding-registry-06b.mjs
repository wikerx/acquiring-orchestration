import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const toolDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(toolDir, "../../..");
const docsSqlDir = path.join(repoRoot, "docs/sql");
const canonicalDdlFile = path.join(docsSqlDir, "transaction-core-schema.sql");

const migrationFile = path.join(docsSqlDir, "bug-001-06b-sharding-registry-migration.sql");
const rollbackFile = path.join(docsSqlDir, "bug-001-06b-sharding-registry-rollback.sql");
const evidenceFile = path.join(docsSqlDir, "bug-001-06b-sharding-registry-evidence.sql");

const identifierPattern = /^[A-Za-z][A-Za-z0-9_]{0,127}$/;
const backupTable = "sys_sharding_physical_table_backup_bug001_06b_20260725";
const targetTempTable = "bug001_06b_target_registry";
const guardTempTable = "bug001_06b_guard";
const shardingColumn = "transaction_date_time";
const shardingStrategy = "quarter";
const dataSource = "master";
const generatedDate = "2026-07-25";

const logicalTables = [
  "transaction_order",
  "transaction_operation",
  "transaction_merchant_snapshot",
  "transaction_payment_method_info",
  "transaction_payer_info",
  "transaction_billing_info",
  "transaction_additional_info",
  "transaction_authentication_info",
  "transaction_product_item",
  "transaction_channel_request",
  "transaction_channel_interaction_log",
  "transaction_channel_callback_log",
  "transaction_channel_callback",
  "transaction_flow_event",
  "transaction_status_history",
  "transaction_amount_change_log",
  "transaction_finance_state",
  "transaction_currency_conversion",
  "transaction_merchant_notification",
  "transaction_merchant_notification_log",
  "transaction_merchant_api_interaction_log",
  "transaction_event_outbox",
  "transaction_abnormal_event"
];

const quarters = [
  { year: 2026, quarter: 3, suffix: "202603" },
  { year: 2026, quarter: 4, suffix: "202604" }
];

const registryColumns = [
  "logical_table",
  "template_table",
  "physical_table",
  "sharding_column",
  "strategy",
  "year",
  "quarter",
  "quarter_suffix",
  "data_source",
  "table_status",
  "auto_created",
  "auto_increment_start",
  "auto_increment_current",
  "auto_increment_max",
  "schema_check_status",
  "last_check_time",
  "created_time",
  "error_message",
  "create_time",
  "update_time"
];

function quoteIdentifier(identifier) {
  assertSafeIdentifier(identifier, "identifier");
  return `\`${identifier}\``;
}

function sqlString(value) {
  return `'${String(value).replaceAll("'", "''")}'`;
}

function assertSafeIdentifier(value, label) {
  if (!identifierPattern.test(value)) {
    throw new Error(`${label} is not a safe SQL identifier: ${value}`);
  }
}

function assertUnique(values, label) {
  const seen = new Set();
  const duplicates = [];
  for (const value of values) {
    if (seen.has(value)) {
      duplicates.push(value);
    }
    seen.add(value);
  }
  if (duplicates.length > 0) {
    throw new Error(`${label} contains duplicates: ${duplicates.join(", ")}`);
  }
}

function quarterSuffix(year, quarter) {
  return `${year}${String(quarter).padStart(2, "0")}`;
}

function autoIncrementStart(suffix) {
  return `${suffix}000000000001`;
}

function autoIncrementMax(suffix) {
  return `${suffix}999999999999`;
}

function targetRows() {
  const rows = [];
  for (const logicalTable of logicalTables) {
    for (const quarter of quarters) {
      const expectedSuffix = quarterSuffix(quarter.year, quarter.quarter);
      if (quarter.suffix !== expectedSuffix) {
        throw new Error(`quarter suffix mismatch for ${quarter.year}Q${quarter.quarter}: ${quarter.suffix}`);
      }
      const physicalTable = `${logicalTable}_${quarter.suffix}`;
      rows.push({
        logicalTable,
        templateTable: logicalTable,
        physicalTable,
        shardingColumn,
        strategy: shardingStrategy,
        year: quarter.year,
        quarter: quarter.quarter,
        quarterSuffix: quarter.suffix,
        dataSource,
        tableStatus: "CREATED",
        autoCreated: 1,
        autoIncrementStart: autoIncrementStart(quarter.suffix),
        autoIncrementCurrent: autoIncrementStart(quarter.suffix),
        autoIncrementMax: autoIncrementMax(quarter.suffix),
        schemaCheckStatus: "MATCHED"
      });
    }
  }
  return rows;
}

function rowValue(row) {
  return [
    sqlString(row.logicalTable),
    sqlString(row.templateTable),
    sqlString(row.physicalTable),
    sqlString(row.shardingColumn),
    sqlString(row.strategy),
    row.year,
    row.quarter,
    sqlString(row.quarterSuffix),
    sqlString(row.dataSource),
    sqlString(row.tableStatus),
    row.autoCreated,
    row.autoIncrementStart,
    row.autoIncrementCurrent,
    row.autoIncrementMax,
    sqlString(row.schemaCheckStatus)
  ].join(", ");
}

function targetTableDefinition() {
  return [
    `CREATE TEMPORARY TABLE ${quoteIdentifier(targetTempTable)} (`,
    "  `logical_table` VARCHAR(128) NOT NULL,",
    "  `template_table` VARCHAR(128) NOT NULL,",
    "  `physical_table` VARCHAR(160) NOT NULL,",
    "  `sharding_column` VARCHAR(128) NOT NULL,",
    "  `strategy` VARCHAR(32) NOT NULL,",
    "  `year` INT NOT NULL,",
    "  `quarter` INT NOT NULL,",
    "  `quarter_suffix` VARCHAR(8) NOT NULL,",
    "  `data_source` VARCHAR(64) NOT NULL,",
    "  `table_status` VARCHAR(32) NOT NULL,",
    "  `auto_created` TINYINT NOT NULL,",
    "  `auto_increment_start` BIGINT NOT NULL,",
    "  `auto_increment_current` BIGINT NOT NULL,",
    "  `auto_increment_max` BIGINT NOT NULL,",
    "  `schema_check_status` VARCHAR(32) NOT NULL,",
    "  PRIMARY KEY (`physical_table`)",
    ") ENGINE=MEMORY;"
  ];
}

function insertTargetRows(rows) {
  return [
    `INSERT INTO ${quoteIdentifier(targetTempTable)} (` +
      [
        "logical_table",
        "template_table",
        "physical_table",
        "sharding_column",
        "strategy",
        "year",
        "quarter",
        "quarter_suffix",
        "data_source",
        "table_status",
        "auto_created",
        "auto_increment_start",
        "auto_increment_current",
        "auto_increment_max",
        "schema_check_status"
      ].map(quoteIdentifier).join(", ") +
      ") VALUES",
    rows.map((row, index) => `  (${rowValue(row)})${index === rows.length - 1 ? ";" : ","}`).join("\n")
  ];
}

function guardStatements(expectedCount) {
  return [
    `CREATE TEMPORARY TABLE ${quoteIdentifier(guardTempTable)} (`,
    "  `guard_id` INT NOT NULL PRIMARY KEY",
    ") ENGINE=MEMORY;",
    "",
    `INSERT INTO ${quoteIdentifier(guardTempTable)} (${quoteIdentifier("guard_id")}) VALUES (1);`,
    "",
    "-- Guard 1: the generated target list must stay exactly 46 rows.",
    `INSERT INTO ${quoteIdentifier(guardTempTable)} (${quoteIdentifier("guard_id")})`,
    `SELECT 1 WHERE (SELECT COUNT(*) FROM ${quoteIdentifier(targetTempTable)}) <> ${expectedCount};`,
    "",
    "-- Guard 2: every target physical table must be distinct.",
    `INSERT INTO ${quoteIdentifier(guardTempTable)} (${quoteIdentifier("guard_id")})`,
    `SELECT 1 WHERE (SELECT COUNT(DISTINCT ${quoteIdentifier("physical_table")}) FROM ${quoteIdentifier(targetTempTable)}) <> ${expectedCount};`,
    "",
    "-- Guard 3: existing registry rows must not contain duplicate target physical_table values.",
    `INSERT INTO ${quoteIdentifier(guardTempTable)} (${quoteIdentifier("guard_id")})`,
    "SELECT 1",
    "FROM (",
    "  SELECT s.`physical_table`",
    "  FROM `sys_sharding_physical_table` s",
    `  JOIN ${quoteIdentifier(targetTempTable)} t ON s.${quoteIdentifier("physical_table")} = t.${quoteIdentifier("physical_table")}`,
    "  GROUP BY s.`physical_table`",
    "  HAVING COUNT(*) > 1",
    ") duplicated_target_physical_table",
    "LIMIT 1;"
  ];
}

function physicalTableDdl(rows) {
  const lines = [];
  for (const row of rows) {
    lines.push(`CREATE TABLE IF NOT EXISTS ${quoteIdentifier(row.physicalTable)} LIKE ${quoteIdentifier(row.templateTable)};`);
    lines.push(`ALTER TABLE ${quoteIdentifier(row.physicalTable)} AUTO_INCREMENT = ${row.autoIncrementStart};`);
  }
  return lines;
}

function registryColumnList(prefix = "") {
  return registryColumns.map((column) => `${prefix}${quoteIdentifier(column)}`).join(", ");
}

function createBackupTableStatement() {
  return [
    `CREATE TABLE IF NOT EXISTS ${quoteIdentifier(backupTable)} LIKE ${quoteIdentifier("sys_sharding_physical_table")};`
  ];
}

function backupInsertStatement() {
  return [
    `INSERT INTO ${quoteIdentifier(backupTable)} (${["id", ...registryColumns].map(quoteIdentifier).join(", ")})`,
    `SELECT ${["id", ...registryColumns].map((column) => `s.${quoteIdentifier(column)}`).join(", ")}`,
    "FROM `sys_sharding_physical_table` s",
    `JOIN ${quoteIdentifier(targetTempTable)} t ON s.${quoteIdentifier("physical_table")} = t.${quoteIdentifier("physical_table")}`,
    "WHERE NOT EXISTS (",
    `  SELECT 1 FROM ${quoteIdentifier(backupTable)} b`,
    `  WHERE b.${quoteIdentifier("physical_table")} = s.${quoteIdentifier("physical_table")}`,
    ");"
  ];
}

function updateExistingRegistryStatement() {
  return [
    "UPDATE `sys_sharding_physical_table` s",
    `JOIN ${quoteIdentifier(targetTempTable)} t ON s.${quoteIdentifier("physical_table")} = t.${quoteIdentifier("physical_table")}`,
    "SET",
    "  s.`logical_table` = t.`logical_table`,",
    "  s.`template_table` = t.`template_table`,",
    "  s.`sharding_column` = t.`sharding_column`,",
    "  s.`strategy` = t.`strategy`,",
    "  s.`year` = t.`year`,",
    "  s.`quarter` = t.`quarter`,",
    "  s.`quarter_suffix` = t.`quarter_suffix`,",
    "  s.`data_source` = t.`data_source`,",
    "  s.`table_status` = 'EXISTS',",
    "  s.`auto_increment_start` = t.`auto_increment_start`,",
    "  s.`auto_increment_current` = GREATEST(COALESCE(s.`auto_increment_current`, t.`auto_increment_current`), t.`auto_increment_start`),",
    "  s.`auto_increment_max` = t.`auto_increment_max`,",
    "  s.`schema_check_status` = t.`schema_check_status`,",
    "  s.`last_check_time` = NOW(),",
    "  s.`created_time` = COALESCE(s.`created_time`, NOW()),",
    "  s.`error_message` = NULL,",
    "  s.`create_time` = COALESCE(s.`create_time`, NOW()),",
    "  s.`update_time` = NOW()",
    "WHERE s.`physical_table` = t.`physical_table`;"
  ];
}

function insertMissingRegistryStatement() {
  return [
    `INSERT INTO ${quoteIdentifier("sys_sharding_physical_table")} (${registryColumnList()})`,
    "SELECT",
    "  t.`logical_table`,",
    "  t.`template_table`,",
    "  t.`physical_table`,",
    "  t.`sharding_column`,",
    "  t.`strategy`,",
    "  t.`year`,",
    "  t.`quarter`,",
    "  t.`quarter_suffix`,",
    "  t.`data_source`,",
    "  t.`table_status`,",
    "  t.`auto_created`,",
    "  t.`auto_increment_start`,",
    "  t.`auto_increment_current`,",
    "  t.`auto_increment_max`,",
    "  t.`schema_check_status`,",
    "  NOW(),",
    "  NOW(),",
    "  NULL,",
    "  NOW(),",
    "  NOW()",
    `FROM ${quoteIdentifier(targetTempTable)} t`,
    "WHERE NOT EXISTS (",
    "  SELECT 1",
    "  FROM `sys_sharding_physical_table` s",
    "  WHERE s.`physical_table` = t.`physical_table`",
    ");"
  ];
}

function postCheckStatements(expectedCount) {
  return [
    "-- Post-check 1: should return 46 registered target rows.",
    "SELECT COUNT(*) AS target_registered_rows",
    "FROM `sys_sharding_physical_table` s",
    `JOIN ${quoteIdentifier(targetTempTable)} t ON s.${quoteIdentifier("physical_table")} = t.${quoteIdentifier("physical_table")};`,
    "",
    "-- Post-check 2: should return zero missing target rows.",
    "SELECT t.`physical_table` AS missing_physical_table",
    `FROM ${quoteIdentifier(targetTempTable)} t`,
    "LEFT JOIN `sys_sharding_physical_table` s ON s.`physical_table` = t.`physical_table`",
    "WHERE s.`physical_table` IS NULL;",
    "",
    "-- Post-check 3: should return zero duplicate target registry rows.",
    "SELECT s.`physical_table`, COUNT(*) AS duplicate_count",
    "FROM `sys_sharding_physical_table` s",
    `JOIN ${quoteIdentifier(targetTempTable)} t ON s.${quoteIdentifier("physical_table")} = t.${quoteIdentifier("physical_table")}`,
    "GROUP BY s.`physical_table`",
    "HAVING COUNT(*) > 1;",
    "",
    `-- Expected target count: ${expectedCount}.`
  ];
}

function targetDerivedTable(rows) {
  return rows
    .map((row) => [
      "SELECT",
      `${sqlString(row.logicalTable)} AS logical_table,`,
      `${sqlString(row.templateTable)} AS template_table,`,
      `${sqlString(row.physicalTable)} AS physical_table,`,
      `${sqlString(row.quarterSuffix)} AS quarter_suffix`
    ].join(" "))
    .join("\nUNION ALL\n");
}

function evidenceSql(rows) {
  const target = targetDerivedTable(rows);
  return [
    header("BUG-VERIFY-001-001 06B sharding registry read-only evidence SQL"),
    "-- 本文件只包含 SELECT，不包含 DDL/DML；用于执行前后留存 SQL 证据。",
    "SET NAMES utf8mb4;",
    "",
    "SELECT DATABASE() AS current_schema, NOW() AS evidence_time;",
    "",
    "-- Evidence 1: sys_sharding_physical_table columns and index shape.",
    "SELECT `COLUMN_NAME`, `COLUMN_TYPE`, `IS_NULLABLE`, `COLUMN_DEFAULT`, `COLUMN_KEY`",
    "FROM `information_schema`.`COLUMNS`",
    "WHERE `TABLE_SCHEMA` = DATABASE()",
    "  AND `TABLE_NAME` = 'sys_sharding_physical_table'",
    "ORDER BY `ORDINAL_POSITION`;",
    "",
    "SELECT `INDEX_NAME`, `NON_UNIQUE`, GROUP_CONCAT(`COLUMN_NAME` ORDER BY `SEQ_IN_INDEX`) AS indexed_columns",
    "FROM `information_schema`.`STATISTICS`",
    "WHERE `TABLE_SCHEMA` = DATABASE()",
    "  AND `TABLE_NAME` = 'sys_sharding_physical_table'",
    "GROUP BY `INDEX_NAME`, `NON_UNIQUE`",
    "ORDER BY `INDEX_NAME`;",
    "",
    "-- Evidence 2: generated target list must be 46 rows with no duplicate physical_table.",
    "SELECT COUNT(*) AS target_count, COUNT(DISTINCT target.`physical_table`) AS distinct_physical_table_count",
    "FROM (",
    target,
    ") target;",
    "",
    "-- Evidence 3: existing duplicate rows for target physical tables must be zero before migration.",
    "SELECT s.`physical_table`, COUNT(*) AS duplicate_count",
    "FROM `sys_sharding_physical_table` s",
    "JOIN (",
    target,
    ") target ON s.`physical_table` = target.`physical_table`",
    "GROUP BY s.`physical_table`",
    "HAVING COUNT(*) > 1;",
    "",
    "-- Evidence 4: existing target rows before or after migration.",
    "SELECT s.*",
    "FROM `sys_sharding_physical_table` s",
    "JOIN (",
    target,
    ") target ON s.`physical_table` = target.`physical_table`",
    "ORDER BY s.`physical_table`, s.`id`;",
    "",
    "-- Evidence 5: transaction-prefix registry rows outside 06B target list; these rows must be preserved.",
    "SELECT s.*",
    "FROM `sys_sharding_physical_table` s",
    "LEFT JOIN (",
    target,
    ") target ON s.`physical_table` = target.`physical_table`",
    "WHERE LEFT(s.`logical_table`, 12) = 'transaction_'",
    "  AND target.`physical_table` IS NULL",
    "ORDER BY s.`logical_table`, s.`quarter_suffix`, s.`physical_table`, s.`id`;",
    "",
    "-- Evidence 6: physical tables and AUTO_INCREMENT values for 06B target tables.",
    "SELECT target.`physical_table`, tables.`TABLE_NAME`, tables.`AUTO_INCREMENT`, tables.`CREATE_TIME`, tables.`UPDATE_TIME`",
    "FROM (",
    target,
    ") target",
    "LEFT JOIN `information_schema`.`TABLES` tables",
    "  ON tables.`TABLE_SCHEMA` = DATABASE()",
    " AND tables.`TABLE_NAME` = target.`physical_table`",
    "ORDER BY target.`physical_table`;",
    ""
  ].join("\n");
}

function migrationSql(rows) {
  const expectedCount = rows.length;
  return [
    header("BUG-VERIFY-001-001 06B sharding registry migration"),
    "-- 本文件不得由 Codex 自动执行；执行前必须先运行 evidence SQL 并人工确认 Guard 结果。",
    "-- 安全边界：不包含按 transaction 表前缀的宽范围删除；历史季度和范围外登记不会被删除。",
    "-- 当前日期 2026-07-25 Asia/Shanghai 仍处于 2026Q3，目标登记为 2026Q3(202603) 与 2026Q4(202604)。",
    "SET NAMES utf8mb4;",
    "",
    "-- Phase 1: additive physical table provisioning. These statements are idempotent and target only the 46 generated table names.",
    ...physicalTableDdl(rows),
    "",
    "-- Phase 2: create the precise backup table before the DML transaction because regular MySQL DDL causes implicit commit.",
    ...createBackupTableStatement(),
    "",
    "-- Phase 3: precise registry migration. Run in one session so temporary tables are available for checks.",
    "START TRANSACTION;",
    "",
    ...targetTableDefinition(),
    "",
    ...insertTargetRows(rows),
    "",
    ...guardStatements(expectedCount),
    "",
    "-- Backup only target registry rows. Historical quarters and out-of-range transaction_* rows are not selected.",
    ...backupInsertStatement(),
    "",
    "-- Update existing target registrations by exact physical_table match.",
    ...updateExistingRegistryStatement(),
    "",
    "-- Insert missing target registrations by exact physical_table anti-join.",
    ...insertMissingRegistryStatement(),
    "",
    ...postCheckStatements(expectedCount),
    "",
    "COMMIT;",
    ""
  ].join("\n");
}

function rollbackSql(rows) {
  const expectedCount = rows.length;
  return [
    header("BUG-VERIFY-001-001 06B sharding registry rollback"),
    "-- 本文件不得由 Codex 自动执行；只回滚 sys_sharding_physical_table 的 06B 目标登记。",
    "-- 物理表 DROP 不纳入自动回滚，避免误删业务数据；如需清理，先用 evidence SQL 确认空表后人工处理。",
    "SET NAMES utf8mb4;",
    "",
    "START TRANSACTION;",
    "",
    ...targetTableDefinition(),
    "",
    ...insertTargetRows(rows),
    "",
    ...guardStatements(expectedCount),
    "",
    "-- Delete rows inserted by 06B when no pre-migration backup row exists.",
    "DELETE s",
    "FROM `sys_sharding_physical_table` s",
    `JOIN ${quoteIdentifier(targetTempTable)} t ON s.${quoteIdentifier("physical_table")} = t.${quoteIdentifier("physical_table")}`,
    `LEFT JOIN ${quoteIdentifier(backupTable)} b ON b.${quoteIdentifier("physical_table")} = s.${quoteIdentifier("physical_table")}`,
    "WHERE b.`physical_table` IS NULL;",
    "",
    "-- Restore rows that existed before 06B by exact physical_table match.",
    "UPDATE `sys_sharding_physical_table` s",
    `JOIN ${quoteIdentifier(backupTable)} b ON b.${quoteIdentifier("physical_table")} = s.${quoteIdentifier("physical_table")}`,
    `JOIN ${quoteIdentifier(targetTempTable)} t ON t.${quoteIdentifier("physical_table")} = s.${quoteIdentifier("physical_table")}`,
    "SET",
    "  s.`logical_table` = b.`logical_table`,",
    "  s.`template_table` = b.`template_table`,",
    "  s.`physical_table` = b.`physical_table`,",
    "  s.`sharding_column` = b.`sharding_column`,",
    "  s.`strategy` = b.`strategy`,",
    "  s.`year` = b.`year`,",
    "  s.`quarter` = b.`quarter`,",
    "  s.`quarter_suffix` = b.`quarter_suffix`,",
    "  s.`data_source` = b.`data_source`,",
    "  s.`table_status` = b.`table_status`,",
    "  s.`auto_created` = b.`auto_created`,",
    "  s.`auto_increment_start` = b.`auto_increment_start`,",
    "  s.`auto_increment_current` = b.`auto_increment_current`,",
    "  s.`auto_increment_max` = b.`auto_increment_max`,",
    "  s.`schema_check_status` = b.`schema_check_status`,",
    "  s.`last_check_time` = b.`last_check_time`,",
    "  s.`created_time` = b.`created_time`,",
    "  s.`error_message` = b.`error_message`,",
    "  s.`create_time` = b.`create_time`,",
    "  s.`update_time` = b.`update_time`",
    "WHERE s.`physical_table` = t.`physical_table`;",
    "",
    "-- Reinsert backup rows if a target row was removed after migration.",
    `INSERT INTO ${quoteIdentifier("sys_sharding_physical_table")} (${["id", ...registryColumns].map(quoteIdentifier).join(", ")})`,
    `SELECT ${["id", ...registryColumns].map((column) => `b.${quoteIdentifier(column)}`).join(", ")}`,
    `FROM ${quoteIdentifier(backupTable)} b`,
    `JOIN ${quoteIdentifier(targetTempTable)} t ON b.${quoteIdentifier("physical_table")} = t.${quoteIdentifier("physical_table")}`,
    "WHERE NOT EXISTS (",
    "  SELECT 1",
    "  FROM `sys_sharding_physical_table` s",
    "  WHERE s.`physical_table` = b.`physical_table`",
    ");",
    "",
    ...postCheckStatements(expectedCount),
    "",
    "COMMIT;",
    ""
  ].join("\n");
}

function header(title) {
  return [
    `-- ${title}`,
    `-- Generated by tools/sql/transaction-ddl/build-transaction-sharding-registry-06b.mjs on ${generatedDate}.`,
    "-- Do not execute from automation. Review and run manually in the intended database session only."
  ].join("\n");
}

async function validateCanonicalDdl(rows) {
  const ddl = await fs.readFile(canonicalDdlFile, "utf8");
  for (const logicalTable of logicalTables) {
    if (!ddl.includes(`CREATE TABLE IF NOT EXISTS \`${logicalTable}\``)) {
      throw new Error(`canonical DDL does not contain logical table: ${logicalTable}`);
    }
  }
  if (ddl.includes("CREATE TABLE IF NOT EXISTS `transaction_idempotency`") && rows.some((row) => row.logicalTable === "transaction_idempotency")) {
    throw new Error("transaction_idempotency must not be registered as a sharded physical table");
  }
}

async function main() {
  for (const logicalTable of logicalTables) {
    assertSafeIdentifier(logicalTable, "logical table");
  }
  assertUnique(logicalTables, "logical table list");

  const rows = targetRows();
  assertUnique(rows.map((row) => row.physicalTable), "physical table target list");
  for (const row of rows) {
    assertSafeIdentifier(row.physicalTable, "physical table");
    assertSafeIdentifier(row.templateTable, "template table");
  }
  if (rows.length !== 46) {
    throw new Error(`expected 46 target registry rows, got ${rows.length}`);
  }

  await validateCanonicalDdl(rows);
  await fs.mkdir(docsSqlDir, { recursive: true });
  await fs.writeFile(migrationFile, migrationSql(rows));
  await fs.writeFile(rollbackFile, rollbackSql(rows));
  await fs.writeFile(evidenceFile, evidenceSql(rows));
  console.log(JSON.stringify({
    migrationFile,
    rollbackFile,
    evidenceFile,
    logicalTables: logicalTables.length,
    targetRows: rows.length
  }, null, 2));
}

await main();
