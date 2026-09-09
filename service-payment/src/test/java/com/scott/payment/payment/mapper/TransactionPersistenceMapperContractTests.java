package com.scott.payment.payment.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.db.sharding.TransactionShardingRuleChecksum;
import com.scott.payment.payment.service.impl.DefaultCaptureChannelResultTransactionService;
import com.scott.payment.payment.service.impl.DefaultCaptureTransactionPreparationService;
import com.scott.payment.payment.service.impl.DefaultIncrementalAuthorizationChannelResultTransactionService;
import com.scott.payment.payment.service.impl.DefaultIncrementalAuthorizationTransactionPreparationService;
import com.scott.payment.payment.service.impl.DefaultPaymentChannelResultTransactionService;
import com.scott.payment.payment.service.impl.DefaultPaymentTransactionPreparationService;
import com.scott.payment.payment.service.impl.DefaultRefundChannelResultTransactionService;
import com.scott.payment.payment.service.impl.DefaultRefundTransactionPreparationService;
import com.scott.payment.payment.service.impl.DefaultTransactionCallbackService;
import com.scott.payment.payment.service.impl.DefaultTransactionChannelMatchResultTransactionService;
import com.scott.payment.payment.service.impl.DefaultVoidChannelResultTransactionService;
import com.scott.payment.payment.service.impl.DefaultVoidTransactionPreparationService;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionPersistenceMapperContractTests
 * @date : 2026-07-23 13:26
 * @email : scott_x@163.com
 * @description : 校验交易 Mapper 的关键查询、CAS 更新 SQL，以及当前 Nacos 分表配置的静态契约
 * @status : create
 */
class TransactionPersistenceMapperContractTests {

    @Test
    void shardingConfigShouldPublishOnlyVersionedTopologyAndGovernance() throws IOException {
        Map<String, Object> root = parseYaml(readProjectFile("docs/deployment/nacos/sharding-dev.yaml"));
        Map<String, Object> sharding = childMap(root, "transaction-sharding");
        Map<String, Object> governance = childMap(sharding, "governance");
        Map<String, Object> maintenance = childMap(governance, "table-maintenance");
        Map<String, Object> tables = childMap(governance, "tables");

        assertThat(root).doesNotContainKey("global-payment");
        assertThat(sharding).doesNotContainKey("mode");
        assertThat(childList(sharding, "logic-tables"))
                .containsExactlyInAnyOrderElementsOf(TransactionShardingProperties.defaultLogicTables());
        assertThat(sharding.get("rule-checksum"))
                .isEqualTo(TransactionShardingRuleChecksum.calculate(toShardingProperties(sharding)));
        assertThat(maintenance).containsEntry("allow-create-from-template-table", true)
                .containsEntry("allow-alter-existing-table", false);
        assertThat(tables).hasSize(TransactionShardingProperties.FORMAL_LOGIC_TABLE_COUNT);
        assertThat(tables.values()).allSatisfy(value -> {
            assertThat(value).isInstanceOf(Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> rule = (Map<String, Object>) value;
            assertThat(rule.get("logical-table"))
                    .isIn(TransactionShardingProperties.defaultLogicTables().toArray());
            assertThat(rule).containsEntry("template-table", rule.get("logical-table"))
                    .containsEntry("end-year", 2099);
        });
    }

    @Test
    void transactionShardingDraftShouldDeclareCompleteClearingCandidateTopology() throws IOException {
        Map<String, Object> root = parseYaml(
                readProjectFile("docs/deployment/nacos/transaction-sharding-dev-draft.yaml"));
        Map<String, Object> sharding = childMap(root, "transaction-sharding");

        assertThat(sharding).doesNotContainKey("mode");
        assertThat(sharding.get("rule-version")).asString()
                .matches("\\d{4}\\.\\d{2}\\.\\d{2}-\\d{3}-candidate-clearing-28");
        assertThat(sharding.get("rule-checksum")).asString()
                .matches("sha256:[0-9a-f]{64}");
        assertThat(sharding).containsEntry("database-zone-id", "Asia/Shanghai")
                .containsEntry("sharding-column", "transaction_date_time");
        assertThat(childList(sharding, "physical-nodes"))
                .containsExactly("202603", "202604");
        assertThat(childList(sharding, "logic-tables"))
                .hasSize(TransactionShardingProperties.FORMAL_LOGIC_TABLE_COUNT)
                .containsExactlyInAnyOrderElementsOf(TransactionShardingProperties.defaultLogicTables())
                .noneMatch(table -> table.startsWith("test_"));
        assertThat(childList(sharding, "direct-access-services"))
                .containsExactlyInAnyOrder(
                        "service-payment",
                        "service-admin",
                        "service-merchant",
                        "service-risk",
                        "service-data",
                        "service-clearing",
                        "service-settlement");
        assertThat(sharding.get("rule-checksum"))
                .isEqualTo(TransactionShardingRuleChecksum.calculate(toShardingProperties(sharding)));
        assertThat(root).doesNotContainKey("global-payment");
    }

    private TransactionShardingProperties toShardingProperties(Map<String, Object> sharding) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setRuleVersion(String.valueOf(sharding.get("rule-version")));
        properties.setDatabaseZoneId(String.valueOf(sharding.get("database-zone-id")));
        properties.setShardingColumn(String.valueOf(sharding.get("sharding-column")));
        properties.setPrimaryDataSource(String.valueOf(sharding.get("primary-data-source")));
        properties.setReplicaDataSources(childList(sharding, "replica-data-sources"));
        properties.setPhysicalNodes(childList(sharding, "physical-nodes"));
        properties.setLogicTables(childList(sharding, "logic-tables"));
        Map<String, Object> budgetConfig = childMap(sharding, "query-budget");
        TransactionShardingProperties.QueryBudget budget = new TransactionShardingProperties.QueryBudget();
        budget.setSynchronousTimeoutMillis(((Number) budgetConfig.get("synchronous-timeout-millis")).longValue());
        budget.setMaxResultRows(((Number) budgetConfig.get("max-result-rows")).intValue());
        budget.setMaxConcurrentExportsPerUser(
                ((Number) budgetConfig.get("max-concurrent-exports-per-user")).intValue());
        properties.setQueryBudget(budget);
        return properties;
    }

    @Test
    void directAccessServiceConfigsShouldNotDeclareLegacyMigrationModes() throws IOException {
        assertServiceHasNoShardingMode("service-payment-dev.yaml");
        assertServiceHasNoShardingMode("service-data-dev.yaml");
        assertServiceHasNoShardingMode("service-admin-dev.yaml");
        assertServiceHasNoShardingMode("service-merchant-dev.yaml");
        assertServiceHasNoShardingMode("service-risk-dev.yaml");

        Map<String, Object> dataConfig = parseYaml(readProjectFile("docs/deployment/nacos/service-data-dev.yaml"));
        Map<String, Object> data = childMap(dataConfig, "data");
        assertThat(childMap(data, "merchant-notification"))
                .containsEntry("recovery-batch-limit", "${DATA_MERCHANT_NOTIFICATION_RECOVERY_BATCH_LIMIT:100}");
    }

    @Test
    void callbackUrlsShouldUsePlaintextColumnsAcrossRuntimeAndShardTemplates() throws IOException {
        String schema = readProjectFile("docs/sql/payment_acquiring_表结构.sql");
        String migration = readProjectFile("service-admin/src/main/resources/sql/callback-url-plaintext-migration.sql");

        assertThat(tableDefinition(schema, "transaction_order"))
                .contains("`callback_url` varchar(512)", "`redirect_url` varchar(512)", "`language` varchar(20)")
                .doesNotContain("callback_url_ciphertext", "callback_url_encryption_key_version", "callback_url_hash",
                        "redirect_url_ciphertext", "redirect_url_encryption_key_version", "redirect_url_hash");
        assertThat(tableDefinition(schema, "payment_checkout_session"))
                .contains("`merchant_notify_url` varchar(512)", "`redirect_url` varchar(512)")
                .doesNotContain("merchant_notify_url_ciphertext", "merchant_notify_url_hash",
                        "redirect_url_ciphertext", "redirect_url_encryption_key_version", "redirect_url_hash");
        assertThat(tableDefinition(schema, "transaction_merchant_notification"))
                .contains("`callback_url` varchar(512)", "`payload_json` mediumtext")
                .doesNotContain("notify_config_snapshot_json");
        assertThat(migration)
                .contains("transaction_order", "transaction_order_202603", "transaction_order_202604",
                        "payment_checkout_session", "transaction_merchant_notification",
                        "transaction_merchant_notification_202603", "transaction_merchant_notification_202604")
                .contains("callback_url", "redirect_url", "merchant_notify_url", "payload_json");
    }

    @Test
    void operationDescriptionShouldExistInTemplateAndEveryPublishedShardMigration() throws IOException {
        String schema = readProjectFile("docs/sql/payment_acquiring_表结构.sql");
        String migration = readProjectFile(
                "service-admin/src/main/resources/sql/transaction-operation-description-migration.sql");

        assertThat(tableDefinition(schema, "transaction_operation"))
                .contains("`description` varchar(128)");
        assertThat(migration)
                .contains("transaction_operation", "transaction_operation_202603", "transaction_operation_202604")
                .contains("`description` varchar(128)");
    }

    @Test
    void transactionOutboxInsertShouldPersistExplicitDeliveryContract() {
        String insertSql = annotationValue(
                methodNamed(TransactionEventOutboxMapper.class, "insertLogical"), Insert.class);
        int columnStart = insertSql.indexOf('(');
        int columnEnd = insertSql.indexOf(')', columnStart);
        int valuesStart = insertSql.indexOf('(', insertSql.indexOf("VALUES"));
        int valuesEnd = insertSql.indexOf(')', valuesStart);

        assertThat(insertSql)
                .contains("delivery_mode", "deliver_at")
                .contains("#{eventDO.deliveryMode}", "#{eventDO.deliverAt}")
                .doesNotContain("${");
        assertThat(commaSeparatedItemCount(insertSql.substring(columnStart + 1, columnEnd)))
                .isEqualTo(commaSeparatedItemCount(insertSql.substring(valuesStart + 1, valuesEnd)));
    }

    @Test
    void clearingDraftShouldDefineSameOutboxDeliveryColumnsForTemplateAndPublishedShards() throws IOException {
        String draft = readProjectFile("docs/sql/20260825_01_transaction_clearing_schema_draft.sql");

        assertThat(List.of(
                alterTableDefinition(draft, "transaction_event_outbox"),
                alterTableDefinition(draft, "transaction_event_outbox_202603"),
                alterTableDefinition(draft, "transaction_event_outbox_202604")))
                .allSatisfy(definition -> assertThat(definition)
                        .contains("ADD COLUMN delivery_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO'")
                        .contains("ADD COLUMN deliver_at DATETIME(3) NULL")
                        .contains("delivery_mode IN ('AUTO', 'NORMAL', 'ORDERLY', 'SCHEDULED')")
                        .contains("delivery_mode = 'SCHEDULED' OR deliver_at IS NULL")
                        .doesNotContain("deliver_at DATETIME NULL"));
    }

    @Test
    void merchantSnapshotShouldExposeStructuredFeeVersionAndDatabaseIdempotencyContract() throws IOException {
        String selectSql = annotationValue(
                methodNamed(TransactionMerchantSnapshotMapper.class, "selectByTransaction"), Select.class);
        String draft = readProjectFile("docs/sql/20260825_01_transaction_clearing_schema_draft.sql");

        assertThat(selectSql)
                .contains("fee_config_snapshot_json", "fee_plan_id", "fee_plan_version_id",
                        "fee_plan_version_no", "fee_snapshot_hash", "fee_snapshot_time")
                .contains("transaction_id = #{transactionId}",
                        "transaction_date_time = #{transactionDateTime}")
                .doesNotContain("${");
        assertThat(List.of(
                alterTableDefinition(draft, "transaction_merchant_snapshot"),
                alterTableDefinition(draft, "transaction_merchant_snapshot_202603"),
                alterTableDefinition(draft, "transaction_merchant_snapshot_202604")))
                .allSatisfy(definition -> assertThat(definition)
                        .contains("fee_plan_version_id BIGINT NULL")
                        .contains("fee_snapshot_time DATETIME(3) NULL")
                        .contains("ADD UNIQUE KEY uk_merchant_snapshot_transaction "
                                + "(transaction_id, transaction_date_time)"));
    }

    @Test
    void activeMerchantFeePointerShouldRequirePlanAndVersionNumbersToMatch() {
        String selectSql = annotationValue(
                methodNamed(MerchantFeeVersionSnapshotMapper.class, "selectActivePointer"), Select.class);

        assertThat(selectSql)
                .contains("fp.current_version_id", "fp.current_version_no = fpv.version_no")
                .contains("fpv.version_status = 'ACTIVE'", "fp.plan_type = 'MERCHANT'")
                .doesNotContain("${");
    }

    @Test
    void clearingDraftShouldPreserveExistingMerchantFeeConfigurationContract() throws IOException {
        String draft = readProjectFile("docs/sql/20260825_01_transaction_clearing_schema_draft.sql");

        assertThat(draft)
                .contains("fp.plan_type = 'MERCHANT'")
                .contains("fpv.version_status <> 'ACTIVE'")
                .contains("fr.fixed_amount_usd", "fr.minimum_amount_usd", "fr.maximum_amount_usd")
                .contains("accumulated_amount_usd", "tier_amount_usd_before", "tier_amount_usd_delta")
                .doesNotContain("ALTER TABLE fee_plan_version")
                .doesNotContain("ALTER TABLE fee_rule\n")
                .doesNotContain("ALTER TABLE fee_rule_tier")
                .doesNotContain("fixed_amount_native", "minimum_amount_native", "maximum_amount_native")
                .doesNotContain("fee_component_currency_mode", "tier_pricing_method");
    }

    @Test
    void transactionLocatorShouldHaveAnExecutableFixedTableMigration() throws IOException {
        String schema = readProjectFile("docs/sql/payment_acquiring_表结构.sql");
        String migration = readProjectFile(
                "service-admin/src/main/resources/sql/transaction-locator-migration.sql");

        assertThat(tableDefinition(schema, "transaction_locator"))
                .contains("`transaction_id` varchar(64)", "`transaction_date_time` datetime(3)",
                        "UNIQUE KEY `uk_transaction_id`", "KEY `idx_merchant_order_root`");
        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS `transaction_locator`", "`root_transaction_id` varchar(64)",
                        "`root_transaction_date_time` datetime(3)", "UNIQUE KEY `uk_transaction_id`")
                .doesNotContain("transaction_locator_202603", "transaction_locator_202604");
    }

    @Test
    void transactionalPaymentCoordinatorsShouldSelectLogicalDataSourceBeforeTransactionBegins() {
        List<Class<?>> transactionTypes = List.of(
                DefaultPaymentTransactionPreparationService.class,
                DefaultCaptureTransactionPreparationService.class,
                DefaultRefundTransactionPreparationService.class,
                DefaultVoidTransactionPreparationService.class,
                DefaultIncrementalAuthorizationTransactionPreparationService.class,
                DefaultPaymentChannelResultTransactionService.class,
                DefaultCaptureChannelResultTransactionService.class,
                DefaultRefundChannelResultTransactionService.class,
                DefaultVoidChannelResultTransactionService.class,
                DefaultIncrementalAuthorizationChannelResultTransactionService.class,
                DefaultTransactionChannelMatchResultTransactionService.class,
                DefaultTransactionCallbackService.class);

        assertThat(transactionTypes).allSatisfy(type -> {
            assertThat(type.getAnnotation(DS.class)).isNull();
            List<Method> transactionMethods = Arrays.stream(type.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Transactional.class))
                    .toList();
            assertThat(transactionMethods)
                    .as("%s must expose a transactional entry", type.getSimpleName())
                    .isNotEmpty()
                    .allSatisfy(method -> {
                        DS annotation = method.getAnnotation(DS.class);
                        assertThat(annotation)
                                .as("%s.%s must select transaction before @Transactional opens",
                                        type.getSimpleName(), method.getName())
                                .isNotNull();
                        assertThat(annotation.value()).isEqualTo(DataSourceName.TRANSACTION);
                    });
        });
    }

    @Test
    void allLogicalWritesAndLocksShouldCarryTransactionShardingTime() {
        List<Class<?>> transactionMappers = List.of(
                TransactionAmountChangeLogMapper.class,
                TransactionChannelCallbackLogMapper.class,
                TransactionChannelCallbackMapper.class,
                TransactionChannelInteractionLogMapper.class,
                TransactionChannelRequestMapper.class,
                TransactionAuthenticationInfoMapper.class,
                TransactionMerchantSnapshotMapper.class,
                TransactionPayerInfoMapper.class,
                TransactionBillingInfoMapper.class,
                TransactionShippingInfoMapper.class,
                TransactionProductItemMapper.class,
                TransactionEventOutboxMapper.class,
                TransactionFinanceStateMapper.class,
                TransactionFlowEventMapper.class,
                TransactionMerchantApiInteractionLogMapper.class,
                TransactionMerchantNotificationMapper.class,
                TransactionAbnormalEventMapper.class,
                TransactionOperationMapper.class,
                TransactionOrderMapper.class,
                TransactionPaymentMethodInfoMapper.class,
                TransactionStatusHistoryMapper.class);

        transactionMappers.stream()
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .forEach(method -> {
                    Insert insert = method.getAnnotation(Insert.class);
                    if (insert != null) {
                        String insertSql = String.join("\n", insert.value());
                        if (insertSql.contains("INSERT INTO transaction_")) {
                            assertThat(insertSql)
                                    .as("%s.%s logical insert", method.getDeclaringClass().getSimpleName(), method.getName())
                                    .contains("transaction_date_time")
                                    .doesNotContain("${");
                        }
                    }
                    Update update = method.getAnnotation(Update.class);
                    if (update != null) {
                        assertSafeLogicalMutation(method, String.join("\n", update.value()), "UPDATE");
                    }
                    Delete delete = method.getAnnotation(Delete.class);
                    if (delete != null) {
                        assertSafeLogicalMutation(method, String.join("\n", delete.value()), "DELETE FROM");
                    }
                    Select select = method.getAnnotation(Select.class);
                    String selectSql = select == null ? "" : String.join("\n", select.value());
                    if (selectSql.contains("FROM transaction_")) {
                        assertThat(selectSql)
                                .as("%s.%s logical select", method.getDeclaringClass().getSimpleName(), method.getName())
                                .contains("transaction_date_time")
                                .doesNotContain("${");
                    }
                    if (selectSql.contains("FROM transaction_") && selectSql.contains("FOR UPDATE")) {
                        assertThat(selectSql)
                                .as("%s.%s lock SQL", method.getDeclaringClass().getSimpleName(), method.getName())
                                .contains("transaction_date_time")
                                .doesNotContain("${");
                    }
                });
    }

    @Test
    void authenticationMapperShouldUseLogicalTableAndNeverPersistCavv() {
        String upsertSql = annotationValue(methodNamed(
                TransactionAuthenticationInfoMapper.class, "upsertPhase"), Insert.class);
        String selectSql = annotationValue(methodNamed(
                TransactionAuthenticationInfoMapper.class, "selectByAuthenticationInfoId"), Select.class);

        assertThat(upsertSql)
                .contains("INSERT INTO transaction_authentication_info")
                .contains("ON DUPLICATE KEY UPDATE")
                .contains("cavv = NULL")
                .contains("authentication_status IN ('AUTHENTICATED', 'FAILED')")
                .contains("#{row.transactionDateTime}")
                .doesNotContain("#{row.cavv}")
                .doesNotContain("${");
        assertThat(selectSql)
                .contains("authentication_info_id = #{authenticationInfoId}")
                .contains("transaction_date_time = #{transactionDateTime}")
                .doesNotContain("${");
    }

    @Test
    void pocLogicalMappersShouldRoutePointReadsLocksAndNotificationUpdatesByTransactionTime()
            throws NoSuchMethodException {
        String orderSelectSql = annotationValue(TransactionOrderMapper.class.getMethod(
                "selectByOperationId",
                String.class,
                java.time.LocalDateTime.class), Select.class);
        String orderLockSql = annotationValue(TransactionOrderMapper.class.getMethod(
                "selectByOperationIdForUpdate",
                String.class,
                java.time.LocalDateTime.class), Select.class);
        String operationSelectSql = annotationValue(TransactionOperationMapper.class.getMethod(
                "selectByTransactionId",
                String.class,
                java.time.LocalDateTime.class), Select.class);
        String notificationUpdateSql = annotationValue(TransactionMerchantNotificationMapper.class.getMethod(
                "activateByTransactionId",
                String.class,
                java.time.LocalDateTime.class,
                Integer.class,
                String.class,
                String.class,
                java.time.LocalDateTime.class,
                java.time.LocalDateTime.class), Update.class);

        assertLogicalPointSql(orderSelectSql, "transaction_order");
        assertLogicalPointSql(operationSelectSql, "transaction_operation");
        assertLogicalPointSql(orderLockSql, "transaction_order");
        assertThat(orderSelectSql)
                .contains("settlement_currency", "settlement_amount", "settlement_rate",
                        "settlement_date", "settlement_batch_no", "settlement_transaction_id",
                        "settlement_transaction_date_time")
                .doesNotContain("SELECT *", "SELECT o.*");
        assertThat(orderLockSql)
                .contains("settlement_currency", "settlement_amount", "settlement_rate",
                        "settlement_date", "settlement_batch_no", "settlement_transaction_id",
                        "settlement_transaction_date_time")
                .doesNotContain("SELECT *", "SELECT o.*");
        assertThat(orderLockSql).contains("FOR UPDATE");
        assertThat(notificationUpdateSql)
                .contains("UPDATE transaction_merchant_notification")
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("version = #{expectedVersion}")
                .contains("version = version + 1")
                .contains("#{callbackPayloadJson}")
                .contains("notify_status = 'INIT'")
                .contains("deleted = 0")
                .doesNotContain("${");
    }

    @Test
    void callbackLogicalUpdateShouldRequireShardTimeVersionAndCurrentStatus() {
        String sql = annotationValue(
                methodNamed(TransactionChannelCallbackMapper.class, "updateProcessResultLogical"), Update.class);

        assertThat(sql)
                .contains("UPDATE transaction_channel_callback")
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("version = #{expectedVersion}")
                .contains("callback_status IN")
                .contains("version = version + 1")
                .contains("deleted = 0")
                .doesNotContain("${");
    }

    @Test
    void transactionMappersShouldNotExposeDynamicPhysicalTableSql() {
        List<Class<?>> transactionMappers = List.of(
                TransactionAmountChangeLogMapper.class,
                TransactionChannelCallbackLogMapper.class,
                TransactionChannelCallbackMapper.class,
                TransactionChannelInteractionLogMapper.class,
                TransactionChannelRequestMapper.class,
                TransactionEventOutboxMapper.class,
                TransactionFlowEventMapper.class,
                TransactionMerchantApiInteractionLogMapper.class,
                TransactionMerchantNotificationMapper.class,
                TransactionOperationMapper.class,
                TransactionOrderMapper.class,
                TransactionPaymentMethodInfoMapper.class,
                TransactionStatusHistoryMapper.class);

        transactionMappers.stream()
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .forEach(method -> {
                    assertThat(Arrays.toString(method.getAnnotations()))
                            .as("%s.%s must use a fixed logical table", method.getDeclaringClass().getSimpleName(), method.getName())
                            .doesNotContain("${");
                    assertThat(Arrays.stream(method.getParameters()).map(java.lang.reflect.Parameter::getName))
                            .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("physicaltable"));
                });
    }

    @Test
    void channelRequestMapperShouldExposeStableLookupAndCasUpdateSql() throws NoSuchMethodException {
        String requestIdSql = annotationValue(methodNamed(
                TransactionChannelRequestMapper.class, "selectByRequestId"), Select.class);
        String originalRequestSql = annotationValue(methodNamed(
                TransactionChannelRequestMapper.class, "selectOriginalByTransaction"), Select.class);
        String channelTransactionSql = annotationValue(methodNamed(
                TransactionChannelRequestMapper.class, "selectByChannelTransaction"), Select.class);
        String updateStatusSql = annotationValue(methodNamed(
                TransactionChannelRequestMapper.class, "updateStatusLogical"), Update.class);

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
        assertThat(updateStatusSql).contains("transaction_date_time = #{transactionDateTime}");
    }

    /**
     * 3DS 认证结果只能写入认证专用字段，不能覆盖支付或授权已准备好的渠道交易号。
     */
    @Test
    void checkoutAuthenticationResultShouldNotOverwriteFundsTransactionIdentity() {
        Method method = methodNamed(PaymentCheckoutAttemptMapper.class, "markAuthenticationResultCas");
        String sql = annotationValue(method, Update.class);

        assertThat(sql)
                .contains("three_ds_transaction_id = #{threeDsTransactionId}")
                .doesNotContain("channel_transaction_id = #{channelTransactionId}")
                .doesNotContain("channel_request_id = #{channelRequestId}");
        assertThat(Arrays.stream(method.getParameters())
                .map(java.lang.reflect.Parameter::getName))
                .doesNotContain("channelTransactionId", "channelRequestId");
    }

    /**
     * 渠道交互结果只能回填本地准备阶段已经创建的空结果行，重复或迟到结果不得覆盖首个审计事实。
     */
    @Test
    void channelInteractionMapperShouldExposeStableLookupAndOneShotResultCas() {
        String selectSql = annotationValue(methodNamed(
                TransactionChannelInteractionLogMapper.class, "selectByRequestId"), Select.class);
        String updateSql = annotationValue(methodNamed(
                TransactionChannelInteractionLogMapper.class, "updateByRequestIdLogical"), Update.class);

        assertThat(selectSql).contains("request_id = #{requestId}")
                .contains("transaction_date_time = #{transactionDateTime}");
        assertThat(updateSql).contains("request_id = #{requestId}")
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("http_status IS NULL")
                .contains("response_header_json_masked IS NULL")
                .contains("response_body_json_masked IS NULL")
                .contains("exception_type IS NULL")
                .contains("exception_message IS NULL")
                .contains("duration_millis IS NULL");
    }

    @Test
    void operationMapperShouldCasUpdateNonTerminalChannelResultForRecovery() throws NoSuchMethodException {
        String sql = annotationValue(methodNamed(
                TransactionOperationMapper.class, "updateNonTerminalChannelResult"), Update.class);

        assertThat(sql).contains("version = #{expectedVersion}");
        assertThat(sql).contains("transaction_status NOT IN ('SUCCESS', 'FAILED')");
        assertThat(sql).contains("channel_match_status = 'PENDING'");
        assertThat(sql).contains("last_channel_match_request_id = #{requestId}");
        assertThat(sql).contains("next_channel_match_time = COALESCE(next_channel_match_time, #{matchTime})");
        assertThat(sql).contains("transaction_date_time = #{transactionDateTime}");
        assertThat(sql).doesNotContain("complete_time");
    }

    @Test
    void operationMapperShouldExposeNonTerminalIncrementalAuthorizationLookup() throws NoSuchMethodException {
        String sql = annotationValue(methodNamed(
                TransactionOperationMapper.class, "selectNonTerminalIncrementalAuthorizations"), Select.class);

        assertThat(sql).contains("operation_id = #{operationId}");
        assertThat(sql).contains("transaction_type = 'INCREMENTAL_AUTHORIZATION'");
        assertThat(sql).contains("transaction_status IN ('PROCESSING', 'PENDING')");
        assertThat(sql).contains("transaction_date_time >= #{beginTime}");
        assertThat(sql).contains("deleted = 0");
    }

    @Test
    void operationMapperShouldReturnInitCandidatesForServiceGracePeriodCheckAndProtectTerminalUpdates()
            throws NoSuchMethodException {
        String selectSql = annotationValue(methodNamed(
                TransactionOperationMapper.class, "selectPendingChannelMatch"), Select.class);
        String updateSql = annotationValue(methodNamed(
                TransactionOperationMapper.class, "updateChannelMatch"), Update.class);
        String terminalUpdateSql = annotationValue(methodNamed(
                TransactionOperationMapper.class, "updateTerminalChannelMatch"), Update.class);
        String orderUpdateSql = annotationValue(methodNamed(
                TransactionOrderMapper.class, "updateLatestChannelMatch"), Update.class);

        assertThat(selectSql).contains("channel_code IS NOT NULL");
        assertThat(selectSql).doesNotContain("NOT EXISTS");
        assertThat(selectSql).doesNotContain("request_status = 'INIT'");
        assertThat(selectSql).doesNotContain("channel_order_no IS NOT NULL");
        assertThat(selectSql).doesNotContain("channel_transaction_id IS NOT NULL");
        assertThat(updateSql).contains("version = #{expectedVersion}");
        assertThat(updateSql).contains("transaction_date_time = #{transactionDateTime}");
        assertThat(updateSql).contains(
                "channel_match_status IN ('PENDING', 'REVIEW_REQUIRED', 'MISMATCHED', 'FAILED')");
        assertThat(updateSql).contains("transaction_status NOT IN ('SUCCESS', 'FAILED')");
        assertThat(terminalUpdateSql).contains("id = #{id}");
        assertThat(terminalUpdateSql).contains("transaction_date_time = #{transactionDateTime}");
        assertThat(terminalUpdateSql).contains("version = #{expectedVersion}");
        assertThat(terminalUpdateSql).contains(
                "channel_match_status IN ('PENDING', 'REVIEW_REQUIRED', 'MISMATCHED', 'FAILED')");
        assertThat(terminalUpdateSql).contains("transaction_status IN ('SUCCESS', 'FAILED')");
        assertThat(terminalUpdateSql).doesNotContain("SET transaction_status", "complete_time");
        assertThat(orderUpdateSql).contains("latest_transaction_id = #{latestTransactionId}");
        assertThat(orderUpdateSql).contains(
                "channel_match_status IN ('PENDING', 'REVIEW_REQUIRED', 'MISMATCHED', 'FAILED')");
        assertThat(orderUpdateSql).contains("channel_match_status = #{matchStatus}");
        assertThat(orderUpdateSql).doesNotContain("transaction_status =");
    }

    /**
     * ShardingSphere 会缓存逻辑表元数据；季度表在线扩列后，SELECT * 可能按旧列序读取成错误类型。
     */
    @Test
    void operationChannelMatchReadsShouldUseExplicitProjection() {
        String pointReadSql = annotationValue(methodNamed(
                TransactionOperationMapper.class, "selectByTransactionId"), Select.class);
        String pendingReadSql = annotationValue(methodNamed(
                TransactionOperationMapper.class, "selectPendingChannelMatch"), Select.class);

        assertThat(pointReadSql)
                .contains("next_channel_match_time")
                .doesNotContain("SELECT *", "SELECT o.*");
        assertThat(pendingReadSql)
                .contains("next_channel_match_time")
                .doesNotContain("SELECT *", "SELECT o.*");
    }

    private static <A extends java.lang.annotation.Annotation> String annotationValue(Method method,
                                                                                       Class<A> annotationType) {
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

    private static Method methodNamed(Class<?> mapperType, String methodName) {
        return Arrays.stream(mapperType.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .reduce((left, right) -> {
                    throw new AssertionError("mapper method is overloaded: " + mapperType.getSimpleName() + "." + methodName);
                })
                .orElseThrow(() -> new AssertionError(
                        "mapper method not found: " + mapperType.getSimpleName() + "." + methodName));
    }

    private static void assertSafeLogicalMutation(Method method, String sql, String statementPrefix) {
        if (!sql.contains(statementPrefix + " transaction_")) {
            return;
        }
        assertThat(sql)
                .as("%s.%s logical mutation", method.getDeclaringClass().getSimpleName(), method.getName())
                .contains("transaction_date_time")
                .doesNotContain("${");
    }

    private static void assertLogicalPointSql(String sql, String logicalTable) {
        assertThat(sql)
                .contains("FROM " + logicalTable)
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("deleted = 0")
                .doesNotContain("${");
    }

    private static void assertServiceHasNoShardingMode(String fileName) throws IOException {
        Map<String, Object> root = parseYaml(readProjectFile("docs/deployment/nacos/" + fileName));
        assertThat(root).doesNotContainKey("transaction-sharding");
    }

    private static Map<String, Object> parseYaml(String yaml) {
        return new Yaml().load(yaml);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> childMap(Map<String, Object> parent, String key) {
        Object child = parent.get(key);
        assertThat(child).as("YAML map '%s'", key).isInstanceOf(Map.class);
        return (Map<String, Object>) child;
    }

    @SuppressWarnings("unchecked")
    private static List<String> childList(Map<String, Object> parent, String key) {
        Object child = parent.get(key);
        assertThat(child).as("YAML list '%s'", key).isInstanceOf(List.class);
        return (List<String>) child;
    }

    private static String tableDefinition(String schema, String tableName) {
        String marker = "CREATE TABLE `" + tableName + "`";
        int begin = schema.indexOf(marker);
        assertThat(begin).as("schema table %s", tableName).isGreaterThanOrEqualTo(0);
        int end = schema.indexOf(";", begin);
        assertThat(end).as("schema terminator for %s", tableName).isGreaterThan(begin);
        return schema.substring(begin, end);
    }

    private static String alterTableDefinition(String migration, String tableName) {
        String marker = "ALTER TABLE " + tableName;
        int begin = migration.indexOf(marker);
        assertThat(begin).as("migration alter table %s", tableName).isGreaterThanOrEqualTo(0);
        int end = migration.indexOf(";", begin);
        assertThat(end).as("migration terminator for %s", tableName).isGreaterThan(begin);
        return migration.substring(begin, end);
    }

    private static long commaSeparatedItemCount(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .count();
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
}
