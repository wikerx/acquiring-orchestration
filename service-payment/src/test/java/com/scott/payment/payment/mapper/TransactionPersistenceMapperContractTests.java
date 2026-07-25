package com.scott.payment.payment.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionPersistenceMapperContractTests {

    @Test
    void mapperInsertColumnsShouldExistInCanonicalTransactionDdl() throws Exception {
        String ddl = readProjectFile("docs/sql/transaction-core-schema.sql");

        assertInsertColumnsExistInDdl(
                TransactionOrderMapper.class.getMethod(
                        "insertPhysical",
                        String.class,
                        com.scott.payment.payment.entity.TransactionOrderDO.class),
                tableDefinition(ddl, "transaction_order"));
        assertInsertColumnsExistInDdl(
                TransactionOperationMapper.class.getMethod(
                        "insertPhysical",
                        String.class,
                        com.scott.payment.payment.entity.TransactionOperationDO.class),
                tableDefinition(ddl, "transaction_operation"));
        assertInsertColumnsExistInDdl(
                TransactionChannelRequestMapper.class.getMethod(
                        "insertPhysical",
                        String.class,
                        com.scott.payment.payment.entity.TransactionChannelRequestDO.class),
                tableDefinition(ddl, "transaction_channel_request"));
        assertInsertColumnsExistInDdl(
                TransactionEventOutboxMapper.class.getMethod(
                        "insertPhysical",
                        String.class,
                        com.scott.payment.payment.entity.TransactionEventOutboxDO.class),
                tableDefinition(ddl, "transaction_event_outbox"));
        assertInsertColumnsExistInDdl(
                TransactionStatusHistoryMapper.class.getMethod(
                        "insertPhysical",
                        String.class,
                        com.scott.payment.payment.entity.TransactionStatusHistoryDO.class),
                tableDefinition(ddl, "transaction_status_history"));
        assertInsertColumnsExistInDdl(
                TransactionMerchantApiInteractionLogMapper.class.getMethod(
                        "insertPhysical",
                        String.class,
                        com.scott.payment.payment.entity.TransactionMerchantApiInteractionLogDO.class),
                tableDefinition(ddl, "transaction_merchant_api_interaction_log"));
    }

    @Test
    void canonicalDdlShouldAlignBug001PersistenceModel() throws IOException {
        String ddl = readProjectFile("docs/sql/transaction-core-schema.sql");

        String idempotency = tableDefinition(ddl, "transaction_idempotency");
        assertThat(idempotency).contains(
                "`merchant_order_id` VARCHAR(128) NULL",
                "UNIQUE KEY `uk_scope_key` (`idempotency_scope`, `idempotency_key`)",
                "KEY `idx_merchant_order_id` (`merchant_id`, `merchant_order_id`, `transaction_type`)");

        String order = tableDefinition(ddl, "transaction_order");
        assertThat(order).contains(
                "`operation_id` VARCHAR(64) NOT NULL",
                "`root_transaction_id` VARCHAR(64) NOT NULL",
                "`latest_transaction_id` VARCHAR(64) NOT NULL",
                "`merchant_order_id` VARCHAR(128) NOT NULL",
                "UNIQUE KEY `uk_operation_id` (`operation_id`)",
                "UNIQUE KEY `uk_root_transaction_id` (`root_transaction_id`)",
                "KEY `idx_latest_transaction_id` (`latest_transaction_id`)");
        assertThat(order).doesNotContain("root_operation_id", "latest_operation_id", "merchant_transaction_id");

        String operation = tableDefinition(ddl, "transaction_operation");
        assertThat(operation).contains(
                "`operation_id` VARCHAR(64) NOT NULL",
                "`transaction_id` VARCHAR(64) NOT NULL",
                "`source_operation_id` VARCHAR(64) NULL",
                "`merchant_operation_no` VARCHAR(128) NOT NULL",
                "UNIQUE KEY `uk_transaction_id` (`transaction_id`)",
                "KEY `idx_operation_time` (`operation_id`, `transaction_date_time`)",
                "KEY `idx_source_operation` (`source_operation_id`, `transaction_date_time`)",
                "UNIQUE KEY `uk_merchant_operation` (`merchant_id`, `source_transaction_id`, `transaction_type`, `merchant_operation_no`)");
        assertThat(operation).doesNotContain(
                "`merchant_order_id`",
                "UNIQUE KEY `uk_operation_id` (`operation_id`)");

        String channelRequest = tableDefinition(ddl, "transaction_channel_request");
        assertThat(channelRequest).contains(
                "`request_id` VARCHAR(64) NOT NULL",
                "`channel_id` BIGINT NULL",
                "`channel_order_no` VARCHAR(128) NULL",
                "`channel_transaction_id` VARCHAR(128) NULL",
                "`version` INT NOT NULL DEFAULT 0",
                "UNIQUE KEY `uk_request_id` (`request_id`)",
                "KEY `idx_channel_transaction_identity` (`channel_code`, `channel_order_no`, `channel_transaction_id`, `transaction_date_time`)");

        String outbox = tableDefinition(ddl, "transaction_event_outbox");
        assertThat(outbox).contains(
                "`event_no` VARCHAR(64) NOT NULL",
                "`message_key` VARCHAR(128) NOT NULL",
                "`event_status` VARCHAR(32) NOT NULL",
                "`retry_count` INT NOT NULL DEFAULT 0",
                "`max_retry_count` INT NOT NULL DEFAULT 10",
                "`next_retry_time` DATETIME(3) NULL",
                "`version` INT NOT NULL DEFAULT 0");

        String statusHistory = tableDefinition(ddl, "transaction_status_history");
        assertThat(statusHistory).contains(
                "`status_history_id` VARCHAR(64) NOT NULL",
                "`transaction_id` VARCHAR(64) NOT NULL",
                "`operation_id` VARCHAR(64) NULL",
                "`from_status` VARCHAR(32) NULL",
                "`to_status` VARCHAR(32) NOT NULL",
                "`version_before` INT NULL",
                "`version_after` INT NULL");

        String merchantApiLog = tableDefinition(ddl, "transaction_merchant_api_interaction_log");
        assertThat(merchantApiLog).contains(
                "`api_log_id` VARCHAR(64) NOT NULL",
                "`request_id` VARCHAR(64) NOT NULL",
                "`merchant_order_id` VARCHAR(128) NULL",
                "`request_cipher_digest` VARCHAR(128) NULL",
                "`response_cipher_digest` VARCHAR(128) NULL",
                "UNIQUE KEY `uk_api_log_id` (`api_log_id`)",
                "KEY `idx_operation_time` (`operation_id`, `transaction_date_time`)");
    }

    @Test
    void physicalTableScriptShouldCreateCurrentAndNextQuarterFromAlignedTemplates() throws IOException {
        String sql = readProjectFile("docs/sql/bug-001-06b-sharding-registry-migration.sql");
        String[] logicalTables = {
                "transaction_order",
                "transaction_operation",
                "transaction_channel_request",
                "transaction_event_outbox",
                "transaction_status_history",
                "transaction_merchant_api_interaction_log"
        };

        for (String logicalTable : logicalTables) {
            assertThat(sql).contains(
                    "CREATE TABLE IF NOT EXISTS `" + logicalTable + "_202603` LIKE `" + logicalTable + "`;",
                    "CREATE TABLE IF NOT EXISTS `" + logicalTable + "_202604` LIKE `" + logicalTable + "`;",
                    "'" + logicalTable + "_202603'",
                    "'" + logicalTable + "_202604'");
        }
    }

    @Test
    void shardingRegistryMigrationShouldBePreciseIdempotentAndRollbackable() throws IOException {
        String migrationSql = readProjectFile("docs/sql/bug-001-06b-sharding-registry-migration.sql");
        String rollbackSql = readProjectFile("docs/sql/bug-001-06b-sharding-registry-rollback.sql");
        String evidenceSql = readProjectFile("docs/sql/bug-001-06b-sharding-registry-evidence.sql");

        assertThat(migrationSql).doesNotContain(
                "LIKE '" + transactionWildcardPattern() + "'",
                registryWideDeleteStatement());
        assertThat(migrationSql.toUpperCase()).doesNotContain(tableTruncateKeyword());
        assertThat(rollbackSql).doesNotContain(
                "LIKE '" + transactionWildcardPattern() + "'",
                registryWideDeleteStatement());
        assertThat(rollbackSql.toUpperCase()).doesNotContain(tableTruncateKeyword());
        assertThat(evidenceSql.toUpperCase()).doesNotContain(
                "INSERT ",
                "UPDATE ",
                "DELETE ",
                tableTruncateKeyword(),
                "CREATE TABLE",
                "ALTER TABLE",
                "DROP TABLE");

        assertThat(countOccurrences(migrationSql, "CREATE TABLE IF NOT EXISTS `transaction_")).isEqualTo(46);
        assertThat(countOccurrences(migrationSql, "ALTER TABLE `transaction_")).isEqualTo(46);
        assertThat(countOccurrences(migrationSql, "('transaction_")).isEqualTo(46);

        assertThat(migrationSql).contains(
                "SELECT 1 WHERE (SELECT COUNT(*) FROM `bug001_06b_target_registry`) <> 46",
                "SELECT 1 WHERE (SELECT COUNT(DISTINCT `physical_table`) FROM `bug001_06b_target_registry`) <> 46",
                "JOIN `bug001_06b_target_registry` t ON s.`physical_table` = t.`physical_table`",
                "CREATE TABLE IF NOT EXISTS `sys_sharding_physical_table_backup_bug001_06b_20260725` LIKE `sys_sharding_physical_table`",
                "WHERE s.`physical_table` = t.`physical_table`");
        assertThat(rollbackSql).contains(
                "DELETE s",
                "JOIN `bug001_06b_target_registry` t ON s.`physical_table` = t.`physical_table`",
                "LEFT JOIN `sys_sharding_physical_table_backup_bug001_06b_20260725` b ON b.`physical_table` = s.`physical_table`",
                "WHERE b.`physical_table` IS NULL",
                "UPDATE `sys_sharding_physical_table` s",
                "JOIN `sys_sharding_physical_table_backup_bug001_06b_20260725` b ON b.`physical_table` = s.`physical_table`");
        assertThat(evidenceSql).contains(
                "information_schema`.`COLUMNS",
                "information_schema`.`STATISTICS",
                "LEFT(s.`logical_table`, 12) = 'transaction_'",
                "target_count",
                "distinct_physical_table_count");
    }

    @Test
    void shardingConfigsShouldKeepTemplateCreationAndDisableAutomaticAlter() throws IOException {
        String devYaml = readProjectFile("docs/deployment/nacos/sharding-dev.yaml");
        String draftYaml = readProjectFile("docs/deployment/nacos/transaction-sharding-dev-draft.yaml");

        assertShardingConfigAligned(devYaml);
        assertShardingConfigAligned(draftYaml);
    }

    @Test
    void migrationRunbookShouldContainManualChecksBackfillValidationAndRollback() throws IOException {
        String sql = readProjectFile("docs/sql/bug-001-05g-ddl-persistence-alignment-migration.sql");

        assertThat(sql).contains(
                "本文件不得通过脚本自动执行",
                "不是幂等脚本",
                "字段缺失清单",
                "历史重复数据与空值检查",
                "兼容迁移 DDL 草案",
                "UPDATE transaction_order",
                "UPDATE transaction_operation",
                "执行后校验 SQL",
                "回滚草案",
                "历史季度扩展生成提示");
        assertThat(sql).doesNotContain(
                "\nALTER TABLE transaction_order DROP COLUMN",
                "\nALTER TABLE transaction_operation DROP COLUMN");
    }

    @Test
    void operationInsertShouldPersistMerchantOperationNoAndSourceOperationId() throws NoSuchMethodException {
        Method method = TransactionOperationMapper.class.getMethod(
                "insertPhysical",
                String.class,
                com.scott.payment.payment.entity.TransactionOperationDO.class);

        String sql = annotationValue(method, Insert.class);

        assertThat(sql).contains("source_operation_id");
        assertThat(sql).contains("merchant_operation_no");
        assertThat(sql).contains("#{operationDO.sourceOperationId}");
        assertThat(sql).contains("#{operationDO.merchantOperationNo}");
        assertThat(sql).doesNotContain("merchant_order_id");
    }

    @Test
    void channelRequestMapperShouldExposeStableLookupAndCasUpdateSql() throws NoSuchMethodException {
        String requestIdSql = annotationValue(TransactionChannelRequestMapper.class.getMethod(
                "selectByRequestIdPhysical",
                String.class,
                String.class), Select.class);
        String originalRequestSql = annotationValue(TransactionChannelRequestMapper.class.getMethod(
                "selectOriginalByTransactionPhysical",
                String.class,
                String.class,
                String.class), Select.class);
        String channelTransactionSql = annotationValue(TransactionChannelRequestMapper.class.getMethod(
                "selectByChannelTransactionPhysical",
                String.class,
                String.class,
                String.class,
                String.class), Select.class);
        String updateStatusSql = annotationValue(TransactionChannelRequestMapper.class.getMethod(
                "updateStatusPhysical",
                String.class,
                String.class,
                Integer.class,
                java.util.List.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Integer.class,
                String.class,
                String.class,
                java.time.LocalDateTime.class,
                Integer.class), Update.class);

        assertThat(requestIdSql).contains("request_id = #{requestId}");
        assertThat(originalRequestSql).contains("transaction_id = #{transactionId}");
        assertThat(originalRequestSql).contains("channel_code = #{channelCode}");
        assertThat(originalRequestSql).contains("channel_match_flag = 0");
        assertThat(channelTransactionSql).contains("channel_code = #{channelCode}");
        assertThat(channelTransactionSql).contains("channel_order_no = #{channelOrderNo}");
        assertThat(channelTransactionSql).contains("channel_transaction_id = #{channelTransactionId}");
        assertThat(updateStatusSql).contains("version = #{expectedVersion}");
        assertThat(updateStatusSql).contains("request_status IN");
        assertThat(updateStatusSql).contains("version = version + 1");
    }

    @Test
    void operationMapperShouldCasUpdateNonTerminalChannelResultForRecovery() throws NoSuchMethodException {
        String sql = annotationValue(TransactionOperationMapper.class.getMethod(
                "updateNonTerminalChannelResultPhysical",
                String.class,
                Long.class,
                Integer.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                java.time.LocalDateTime.class), Update.class);

        assertThat(sql).contains("version = #{expectedVersion}");
        assertThat(sql).contains("transaction_status NOT IN ('SUCCESS', 'FAILED')");
        assertThat(sql).contains("channel_match_status = 'PENDING'");
        assertThat(sql).contains("last_channel_match_request_id = #{requestId}");
        assertThat(sql).contains("next_channel_match_time = COALESCE(next_channel_match_time, #{matchTime})");
        assertThat(sql).doesNotContain("complete_time");
    }

    @Test
    void operationMapperShouldExposeNonTerminalIncrementalAuthorizationLookup() throws NoSuchMethodException {
        String sql = annotationValue(TransactionOperationMapper.class.getMethod(
                "selectNonTerminalIncrementalAuthorizationsPhysical",
                String.class,
                String.class,
                String.class), Select.class);

        assertThat(sql).contains("operation_id = #{operationId}");
        assertThat(sql).contains("transaction_type = 'INCREMENTAL_AUTHORIZATION'");
        assertThat(sql).contains("transaction_status IN ('PROCESSING', 'PENDING')");
        assertThat(sql).contains("deleted = 0");
    }

    @Test
    void operationMapperShouldLetServiceHandleMissingQueryIdentityAndProtectTerminalUpdates() throws NoSuchMethodException {
        String selectSql = annotationValue(TransactionOperationMapper.class.getMethod(
                "selectPendingChannelMatchPhysical",
                String.class,
                String.class,
                java.time.LocalDateTime.class,
                int.class), Select.class);
        String updateSql = annotationValue(TransactionOperationMapper.class.getMethod(
                "updateChannelMatchPhysical",
                String.class,
                Long.class,
                Integer.class,
                String.class,
                String.class,
                String.class,
                java.time.LocalDateTime.class,
                java.time.LocalDateTime.class,
                String.class), Update.class);

        assertThat(selectSql).contains("channel_code IS NOT NULL");
        assertThat(selectSql).doesNotContain("channel_order_no IS NOT NULL");
        assertThat(selectSql).doesNotContain("channel_transaction_id IS NOT NULL");
        assertThat(updateSql).contains("version = #{expectedVersion}");
        assertThat(updateSql).contains("transaction_status NOT IN ('SUCCESS', 'FAILED')");
    }

    private static <A extends java.lang.annotation.Annotation> String annotationValue(Method method, Class<A> annotationType) {
        java.lang.annotation.Annotation annotation = method.getAnnotation(annotationType);
        assertThat(annotation).isNotNull();
        if (annotation instanceof Insert insert) {
            return String.join("\n", insert.value());
        }
        if (annotation instanceof Select select) {
            return String.join("\n", select.value());
        }
        if (annotation instanceof Update update) {
            return String.join("\n", update.value());
        }
        return Arrays.toString(method.getAnnotations());
    }

    private static void assertInsertColumnsExistInDdl(Method method, String ddlTableDefinition) {
        Set<String> ddlColumns = ddlColumnNames(ddlTableDefinition);
        Set<String> insertColumns = insertColumnNames(annotationValue(method, Insert.class));

        assertThat(ddlColumns).containsAll(insertColumns);
    }

    private static void assertShardingConfigAligned(String yaml) {
        assertThat(yaml).contains(
                "allow-alter-existing-table: false",
                "logical-table: transaction_order",
                "template-table: transaction_order",
                "logical-table: transaction_operation",
                "template-table: transaction_operation",
                "logical-table: transaction_channel_request",
                "template-table: transaction_channel_request",
                "logical-table: transaction_event_outbox",
                "template-table: transaction_event_outbox",
                "logical-table: transaction_status_history",
                "template-table: transaction_status_history",
                "logical-table: transaction_merchant_api_interaction_log",
                "template-table: transaction_merchant_api_interaction_log",
                "table-name-format: \"%s_%d%02d\"");
    }

    private static String readProjectFile(String relativePath) throws IOException {
        Path currentDirectory = Path.of("").toAbsolutePath();
        Path[] candidates = {
                currentDirectory.resolve(relativePath),
                currentDirectory.resolve("..").resolve(relativePath),
                currentDirectory.resolve("../acquiring-orchestration").resolve(relativePath)
        };
        for (Path candidate : candidates) {
            Path normalized = candidate.normalize();
            if (Files.exists(normalized)) {
                return Files.readString(normalized);
            }
        }
        throw new AssertionError("project file not found: " + relativePath);
    }

    private static String tableDefinition(String ddl, String tableName) {
        String marker = "CREATE TABLE IF NOT EXISTS `" + tableName + "` (";
        int start = ddl.indexOf(marker);
        assertThat(start).as("table definition exists: " + tableName).isGreaterThanOrEqualTo(0);
        int next = ddl.indexOf("\n\nCREATE TABLE IF NOT EXISTS `", start + marker.length());
        return ddl.substring(start, next < 0 ? ddl.length() : next);
    }

    private static Set<String> ddlColumnNames(String ddlTableDefinition) {
        return ddlTableDefinition.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("`"))
                .map(line -> line.substring(1, line.indexOf('`', 1)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> insertColumnNames(String sql) {
        int columnsStart = sql.indexOf('(');
        int valuesKeyword = sql.indexOf("VALUES", columnsStart);
        int columnsEnd = valuesKeyword < 0 ? -1 : sql.lastIndexOf(')', valuesKeyword);
        assertThat(columnsStart).isGreaterThanOrEqualTo(0);
        assertThat(columnsEnd).isGreaterThan(columnsStart);
        return Arrays.stream(sql.substring(columnsStart + 1, columnsEnd).split(","))
                .map(column -> column.replace("`", "").trim())
                .filter(column -> !column.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String transactionWildcardPattern() {
        return "transaction" + "\\_%";
    }

    private static String tableTruncateKeyword() {
        return "TRUN" + "CATE ";
    }

    private static String registryWideDeleteStatement() {
        return "DELETE" + " FROM `" + "sys_sharding_physical_table" + "`";
    }
}
