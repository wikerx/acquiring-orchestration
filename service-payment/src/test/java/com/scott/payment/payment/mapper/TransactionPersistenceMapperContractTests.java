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
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void legacyShardingConfigShouldKeepTemplateCreationAndDisableAutomaticAlter() throws IOException {
        Map<String, Object> root = parseYaml(readProjectFile("docs/deployment/nacos/sharding-dev.yaml"));
        Map<String, Object> globalPayment = childMap(root, "global-payment");
        Map<String, Object> sharding = childMap(globalPayment, "sharding");
        Map<String, Object> maintenance = childMap(sharding, "table-maintenance");
        Map<String, Object> tables = childMap(sharding, "tables");

        assertThat(maintenance).containsEntry("allow-create-from-template-table", true)
                .containsEntry("allow-alter-existing-table", false);
        assertLegacyTable(tables, "transaction-order", "transaction_order");
        assertLegacyTable(tables, "transaction-operation", "transaction_operation");
        assertLegacyTable(tables, "transaction-channel-request", "transaction_channel_request");
        assertLegacyTable(tables, "transaction-event-outbox", "transaction_event_outbox");
        assertLegacyTable(tables, "transaction-status-history", "transaction_status_history");
        assertLegacyTable(tables, "transaction-merchant-api-interaction-log",
                "transaction_merchant_api_interaction_log");
    }

    @Test
    void transactionShardingDraftShouldDeclareOnlyVerifiedProductionTopology() throws IOException {
        Map<String, Object> root = parseYaml(
                readProjectFile("docs/deployment/nacos/transaction-sharding-dev-draft.yaml"));
        Map<String, Object> sharding = childMap(root, "transaction-sharding");

        assertThat(sharding).doesNotContainKey("mode");
        assertThat(sharding.get("rule-version")).asString()
                .matches("\\d{4}\\.\\d{2}\\.\\d{2}-\\d{3}");
        assertThat(sharding.get("rule-checksum")).asString()
                .matches("sha256:[0-9a-f]{64}");
        assertThat(sharding).containsEntry("database-zone-id", "Asia/Shanghai")
                .containsEntry("sharding-column", "transaction_date_time");
        assertThat(childList(sharding, "physical-nodes"))
                .containsExactly("202603", "202604");
        assertThat(childList(sharding, "logic-tables"))
                .containsExactlyInAnyOrderElementsOf(PRODUCTION_LOGIC_TABLES)
                .noneMatch(table -> table.startsWith("test_"));
        assertThat(childList(sharding, "direct-access-services"))
                .containsExactlyInAnyOrder(
                        "service-payment",
                        "service-admin",
                        "service-merchant",
                        "service-risk",
                        "service-data");
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
    void directAccessServicesShouldOwnTheirMigrationMode() throws IOException {
        assertServiceMode("service-payment-dev.yaml", "${PAYMENT_TRANSACTION_SHARDING_MODE:LEGACY}");
        assertServiceMode("service-data-dev.yaml", "${DATA_TRANSACTION_SHARDING_MODE:LEGACY}");
        assertServiceMode("service-admin-dev.yaml", "${ADMIN_TRANSACTION_SHARDING_MODE:LEGACY}");
        assertServiceMode("service-merchant-dev.yaml", "${MERCHANT_TRANSACTION_SHARDING_MODE:LEGACY}");
        assertServiceMode("service-risk-dev.yaml", "${RISK_TRANSACTION_SHARDING_MODE:LEGACY}");
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
            DS annotation = type.getAnnotation(DS.class);
            assertThat(annotation)
                    .as("%s must select transaction before @Transactional opens", type.getSimpleName())
                    .isNotNull();
            assertThat(annotation.value()).isEqualTo(DataSourceName.TRANSACTION);
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
                TransactionEventOutboxMapper.class,
                TransactionFlowEventMapper.class,
                TransactionMerchantApiInteractionLogMapper.class,
                TransactionMerchantNotificationLogMapper.class,
                TransactionMerchantNotificationMapper.class,
                TransactionOperationMapper.class,
                TransactionOrderMapper.class,
                TransactionPaymentMethodInfoMapper.class,
                TransactionStatusHistoryMapper.class);

        transactionMappers.stream()
                .flatMap(type -> Arrays.stream(type.getMethods()))
                .forEach(method -> {
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
                    if (selectSql.contains("FROM transaction_") && selectSql.contains("FOR UPDATE")) {
                        assertThat(selectSql)
                                .as("%s.%s lock SQL", method.getDeclaringClass().getSimpleName(), method.getName())
                                .contains("transaction_date_time")
                                .doesNotContain("${");
                    }
                });
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
                java.time.LocalDateTime.class,
                java.time.LocalDateTime.class), Update.class);

        assertLogicalPointSql(orderSelectSql, "transaction_order");
        assertLogicalPointSql(operationSelectSql, "transaction_operation");
        assertLogicalPointSql(orderLockSql, "transaction_order");
        assertThat(orderLockSql).contains("FOR UPDATE");
        assertThat(notificationUpdateSql)
                .contains("UPDATE transaction_merchant_notification")
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("version = #{expectedVersion}")
                .contains("version = version + 1")
                .contains("notify_status = 'INIT'")
                .contains("deleted = 0")
                .doesNotContain("${");
    }

    @Test
    void operationLogicalQueriesShouldUseBindingTimeAndCurrencySafeAggregation() {
        String pageSql = annotationValue(methodNamed(TransactionOperationMapper.class, "selectPageLogical"), Select.class);
        String countSql = annotationValue(methodNamed(TransactionOperationMapper.class, "countPageLogical"), Select.class);
        String amountSql = annotationValue(
                methodNamed(TransactionOperationMapper.class, "selectAmountSummaryLogical"), Select.class);
        String paymentSql = annotationValue(
                methodNamed(TransactionOperationMapper.class, "selectPaymentMethodSummaryLogical"), Select.class);

        assertLogicalRangeSql(pageSql);
        assertLogicalRangeSql(countSql);
        assertLogicalRangeSql(amountSql);
        assertLogicalRangeSql(paymentSql);
        assertThat(pageSql)
                .contains("ORDER BY o.transaction_date_time DESC, o.id DESC")
                .contains("FROM transaction_payment_method_info p");
        assertThat(amountSql)
                .contains("GROUP BY o.transaction_status, COALESCE(o.transaction_currency, 'UNKNOWN')")
                .contains("AS currency");
        assertThat(paymentSql)
                .contains("LEFT JOIN transaction_payment_method_info p")
                .contains("p.transaction_date_time = o.transaction_date_time")
                .contains("COALESCE(o.transaction_currency, 'UNKNOWN') AS currency")
                .contains("GROUP BY COALESCE(p.payment_method, 'UNKNOWN'), p.payment_brand,")
                .contains("COALESCE(o.transaction_currency, 'UNKNOWN'), o.currency_exponent");
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

    private static final Set<String> PRODUCTION_LOGIC_TABLES = Set.of(
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
            "transaction_channel_callback",
            "transaction_channel_callback_log",
            "transaction_flow_event",
            "transaction_status_history",
            "transaction_amount_change_log",
            "transaction_finance_state",
            "transaction_currency_conversion",
            "transaction_merchant_notification",
            "transaction_merchant_notification_log",
            "transaction_merchant_api_interaction_log",
            "transaction_event_outbox",
            "transaction_abnormal_event");

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

    private static void assertLegacyTable(Map<String, Object> tables, String key, String logicalTable) {
        Map<String, Object> table = childMap(tables, key);
        assertThat(table).containsEntry("logical-table", logicalTable)
                .containsEntry("template-table", logicalTable)
                .containsEntry("table-name-format", "%s_%d%02d");
    }

    private static void assertLogicalPointSql(String sql, String logicalTable) {
        assertThat(sql)
                .contains("FROM " + logicalTable)
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("deleted = 0")
                .doesNotContain("${");
    }

    private static void assertLogicalRangeSql(String sql) {
        assertThat(sql)
                .contains("transaction_operation")
                .contains("o.transaction_date_time &gt;= #{beginTime}")
                .contains("o.transaction_date_time &lt; #{endTimeExclusive}")
                .doesNotContain("${");
    }

    private static void assertServiceMode(String fileName, String expectedMode) throws IOException {
        Map<String, Object> root = parseYaml(readProjectFile("docs/deployment/nacos/" + fileName));
        assertThat(childMap(root, "transaction-sharding")).containsEntry("mode", expectedMode);
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
