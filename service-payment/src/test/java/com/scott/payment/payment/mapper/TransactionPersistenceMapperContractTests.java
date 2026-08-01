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
    void shardingConfigsShouldKeepTemplateCreationAndDisableAutomaticAlter() throws IOException {
        String devYaml = readProjectFile("docs/deployment/nacos/sharding-dev.yaml");
        String draftYaml = readProjectFile("docs/deployment/nacos/transaction-sharding-dev-draft.yaml");

        assertShardingConfigAligned(devYaml);
        assertShardingConfigAligned(draftYaml);
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
}
