package com.scott.payment.clearing.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingPersistenceMapperContractTest
 * @date : 2026-08-26 18:50
 * @email : scott_x@163.com
 * @description : 锁定清分 Mapper 的季度分片、状态 CAS、行锁、明细分表边界和定时 Outbox 持久化合同。
 * @status : create
 */
class ClearingPersistenceMapperContractTest {

    private static final List<Class<?>> MAPPER_TYPES = List.of(
            ClearingFeeTierAccumulatorMapper.class,
            ClearingFeeVersionSnapshotMapper.class,
            ClearingReserveMapper.class,
            ClearingTransactionContextMapper.class,
            ClearingTransactionDetailMapper.class,
            ClearingTransactionEventOutboxMapper.class,
            ClearingTransactionFinanceStateMapper.class,
            ClearingTransactionIdempotencyMapper.class,
            ClearingTransactionMerchantSnapshotMapper.class,
            ClearingTransactionOperationMapper.class,
            ClearingTransactionAbnormalEventMapper.class,
            ClearingSettlementCandidateMapper.class,
            ClearingTierPeriodReplayMapper.class,
            ClearingCompensationMapper.class);

    private static final Set<String> SHARDED_TABLES = Set.of(
            "transaction_operation",
            "transaction_merchant_snapshot",
            "transaction_payment_method_info",
            "transaction_flow_event",
            "transaction_order",
            "transaction_finance_state",
            "transaction_clearing_detail",
            "transaction_reserve_clearing_detail",
            "transaction_reserve_clearing_state",
            "transaction_abnormal_event",
            "transaction_event_outbox");

    @Test
    void allAnnotatedSqlShouldUseBoundParametersWithoutTextInterpolation() {
        MAPPER_TYPES.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(ClearingPersistenceMapperContractTest::sql)
                .filter(value -> !value.isBlank())
                .forEach(value -> assertThat(value).doesNotContain("${"));
    }

    @Test
    void everyShardedTableAccessShouldCarryTransactionDateTime() {
        MAPPER_TYPES.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .forEach(method -> {
                    String value = sql(method);
                    SHARDED_TABLES.stream()
                            .filter(value::contains)
                            .forEach(table -> assertThat(value)
                                    .as("%s.%s access to %s", method.getDeclaringClass().getSimpleName(),
                                            method.getName(), table)
                                    .contains("transaction_date_time")
                                    .doesNotContain("${"));
                });
    }

    @Test
    void financeStateMutationsShouldRequireStatusOwnerAndVersionCas() {
        String claim = sql(methodNamed(ClearingTransactionFinanceStateMapper.class, "claimProcessing"));
        assertThat(claim)
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("clearing_status IN ('NOT_CLEARED', 'PENDING', 'WAITING_SOURCE', 'FAILED')")
                .contains("processing_owner = #{processingOwner}")
                .contains("processing_deadline = #{processingDeadline}")
                .contains("version = #{expectedVersion}")
                .contains("version = version + 1");

        String complete = sql(methodNamed(ClearingTransactionFinanceStateMapper.class, "completeProcessing"));
        assertThat(complete)
                .contains("clearing_status = 'PROCESSING'")
                .contains("processing_owner = #{processingOwner}")
                .contains("processing_deadline >= #{now}")
                .contains("version = #{expectedVersion}")
                .contains("transaction_date_time = #{transactionDateTime}");

        String failure = sql(methodNamed(ClearingTransactionFinanceStateMapper.class, "recordFailure"));
        assertThat(failure)
                .contains("clearing_status = 'PROCESSING'")
                .contains("processing_owner = #{processingOwner}")
                .contains("version = #{expectedVersion}")
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("next_retry_time = #{nextRetryTime}");
    }

    @Test
    void financeReserveOrderAndTierReadsShouldUseSingleRowLocks() {
        assertLock(ClearingTransactionFinanceStateMapper.class, "selectForUpdate",
                "transaction_finance_state", "transaction_date_time = #{transactionDateTime}");
        assertLock(ClearingReserveMapper.class, "selectStateForUpdate",
                "transaction_reserve_clearing_state",
                "transaction_date_time = #{originalTransactionDateTime}");
        assertLock(ClearingTransactionContextMapper.class, "selectOrderForUpdate",
                "transaction_order", "transaction_date_time = #{transactionDateTime}");
        assertLock(ClearingFeeTierAccumulatorMapper.class, "selectForUpdateBatch",
                "fee_tier_accumulator", "period_key = #{periodKey}");
        assertThat(sql(methodNamed(ClearingFeeTierAccumulatorMapper.class, "selectForUpdateBatch")))
                .contains("fee_rule_id IN", "ORDER BY fee_rule_id ASC");
    }

    @Test
    void transactionAndReserveDetailsShouldRemainPhysicallySeparated() {
        String transactionInsert = sql(methodNamed(ClearingTransactionDetailMapper.class, "insertBatch"));
        assertThat(transactionInsert)
                .contains("INSERT INTO transaction_clearing_detail")
                .contains("payment_type", "payment_method", "#{row.paymentType}", "#{row.paymentMethod}")
                .contains("#{row.transactionDateTime}")
                .doesNotContain("transaction_reserve_clearing_detail");

        String reserveInsert = sql(methodNamed(ClearingReserveMapper.class, "insertDetail"));
        assertThat(reserveInsert)
                .contains("INSERT INTO transaction_reserve_clearing_detail")
                .contains("payment_type", "payment_method", "#{row.paymentType}", "#{row.paymentMethod}")
                .contains("#{row.transactionDateTime}")
                .doesNotContain("INSERT INTO transaction_clearing_detail");
    }

    @Test
    void reserveReturnShouldProtectAmountInvariantAndStatusTransition() {
        String applyReturn = sql(methodNamed(ClearingReserveMapper.class, "applyReturn"));

        assertThat(applyReturn)
                .contains("version = #{expectedVersion}")
                .contains("reserve_status = 'OPEN'")
                .contains("#{returnAmount} > 0")
                .contains("retained_amount + debit_adjustment_amount")
                .contains("returned_amount + released_amount + credit_adjustment_amount + remaining_amount")
                .contains("remaining_amount = #{remainingAmount} + #{returnAmount}")
                .contains("#{reserveStatus} = 'OPEN' AND #{remainingAmount} > 0")
                .contains("#{reserveStatus} = 'FULLY_RETURNED' AND #{remainingAmount} = 0");
    }

    @Test
    void outboxInsertShouldPersistScheduledDeliveryPayloadAndShardFields() {
        String outboxInsert = sql(methodNamed(ClearingTransactionEventOutboxMapper.class, "insertLogical"));
        assertThat(outboxInsert)
                .contains("delivery_mode", "deliver_at", "payload_json", "next_retry_time")
                .contains("transaction_date_time", "transaction_utc_time", "transaction_time_zone")
                .contains("#{row.deliveryMode}", "#{row.deliverAt}", "#{row.payloadJson}")
                .contains("#{row.nextRetryTime}", "#{row.transactionDateTime}")
                .doesNotContain("${");
    }

    @Test
    void idempotentOutboxInsertShouldNotSuppressNonDuplicateDatabaseErrors() {
        String outboxInsert = sql(methodNamed(ClearingTransactionEventOutboxMapper.class, "insertLogical"));
        String identityLock = sql(methodNamed(
                ClearingTransactionEventOutboxMapper.class, "selectByEventNoForUpdate"));

        assertThat(outboxInsert)
                .contains("INSERT INTO transaction_event_outbox")
                .doesNotContain("INSERT IGNORE");
        assertThat(identityLock)
                .contains("event_no = #{eventNo}")
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("FOR UPDATE");
    }

    @Test
    void idempotentInitializersShouldOnlyAbsorbUniqueKeyDuplicates() {
        assertDuplicateOnlyInsert(ClearingTransactionFinanceStateMapper.class, "insertIfAbsent");
        assertDuplicateOnlyInsert(ClearingFeeTierAccumulatorMapper.class, "insertIfAbsentBatch");
        assertDuplicateOnlyInsert(ClearingSettlementCandidateMapper.class, "insertIdempotent");
    }

    @Test
    void tierAccumulatorDeltaShouldBatchRuleUpdatesWithIndependentVersionCas() {
        String applyDelta = sql(methodNamed(ClearingFeeTierAccumulatorMapper.class, "applyDeltas"));

        assertThat(applyDelta)
                .contains("accumulated_count = accumulated_count + 1")
                .contains("CASE fee_rule_id")
                .contains("fee_rule_id = #{delta.feeRuleId}")
                .contains("version = #{delta.expectedVersion}")
                .contains("#{delta.amountDelta} &gt;= 0");
    }

    @Test
    void tierPeriodReplayShouldUseStableShardQueriesAndRuleScopedReset() {
        String periodItems = sql(methodNamed(ClearingTierPeriodReplayMapper.class, "selectPeriodItems"));
        assertThat(periodItems)
                .contains("op.operation_id = fs.operation_id")
                .contains("fs.transaction_date_time >= #{periodStart}")
                .contains("fs.transaction_date_time < #{periodEnd}")
                .contains("ORDER BY op.clearing_complete_time ASC, fs.transaction_id ASC")
                .contains("FOR UPDATE")
                .doesNotContain("(SELECT COUNT(1) FROM transaction_reserve_clearing_detail");

        String reserveGate = sql(methodNamed(
                ClearingTierPeriodReplayMapper.class, "countActiveReserveDetails"));
        assertThat(reserveGate)
                .contains("JOIN transaction_reserve_clearing_detail rd")
                .contains("rd.transaction_date_time = fs.transaction_date_time")
                .contains("rd.clearing_revision = fs.clearing_revision")
                .contains("fs.transaction_date_time >= #{periodStart}")
                .contains("fs.transaction_date_time < #{periodEnd}")
                .contains("rd.record_status = 'ACTIVE'")
                .doesNotContain("${");

        String reset = sql(methodNamed(ClearingFeeTierAccumulatorMapper.class, "resetPeriod"));
        assertThat(reset)
                .contains("fee_rule_id IN")
                .contains("<foreach collection=\"feeRuleIds\"")
                .contains("period_key = #{periodKey}")
                .doesNotContain("${");
    }

    @Test
    void operationAndOrderProjectionUpdatesShouldUseShardAndVersionCas() {
        String operationUpdate = sql(methodNamed(
                ClearingTransactionOperationMapper.class, "updateClearingProjection"));
        assertThat(operationUpdate)
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("version = #{expectedVersion}")
                .contains("clearing_status IN ('NOT_CLEARED', 'PENDING', 'FAILED')")
                .contains("version = version + 1");

        String orderUpdate = sql(methodNamed(
                ClearingTransactionContextMapper.class, "updateOrderClearingProjection"));
        assertThat(orderUpdate)
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("version = #{expectedVersion}")
                .contains("version = version + 1")
                .contains("deleted = 0");
    }

    @Test
    void anomalyAndSettlementCandidateMutationsShouldKeepTheirScopeAndCasGuards() {
        String anomalyUpsert = sql(methodNamed(
                ClearingTransactionAbnormalEventMapper.class, "upsertOccurrence"));
        assertThat(anomalyUpsert)
                .contains("INSERT INTO transaction_abnormal_event")
                .contains("#{row.transactionDateTime}")
                .contains("ON DUPLICATE KEY UPDATE")
                .doesNotContain("${");

        String anomalyResolve = sql(methodNamed(
                ClearingTransactionAbnormalEventMapper.class, "resolveActiveClearingCases"));
        assertThat(anomalyResolve)
                .contains("transaction_date_time = #{transactionDateTime}")
                .contains("abnormal_type IN")
                .contains("event_status IN ('OPEN', 'PROCESSING')")
                .contains("deleted = 0");

        String candidateSupersede = sql(methodNamed(
                ClearingSettlementCandidateMapper.class, "supersedeReady"));
        assertThat(candidateSupersede)
                .contains("candidate_status = 'READY'")
                .contains("settlement_batch_no IS NULL")
                .contains("version = #{expectedVersion}")
                .contains("version = version + 1");
    }

    private static void assertLock(Class<?> type, String methodName, String table, String routeCondition) {
        String value = sql(methodNamed(type, methodName));
        assertThat(value)
                .contains("FROM " + table)
                .contains(routeCondition)
                .contains("FOR UPDATE")
                .doesNotContain("${");
    }

    private static void assertDuplicateOnlyInsert(Class<?> type, String methodName) {
        assertThat(sql(methodNamed(type, methodName)))
                .contains("INSERT INTO")
                .contains("ON DUPLICATE KEY UPDATE")
                .doesNotContain("INSERT IGNORE");
    }

    private static Method methodNamed(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static String sql(Method method) {
        Select select = method.getAnnotation(Select.class);
        if (select != null) {
            return String.join("\n", select.value());
        }
        Insert insert = method.getAnnotation(Insert.class);
        if (insert != null) {
            return String.join("\n", insert.value());
        }
        Update update = method.getAnnotation(Update.class);
        if (update != null) {
            return String.join("\n", update.value());
        }
        Delete delete = method.getAnnotation(Delete.class);
        return delete == null ? "" : String.join("\n", delete.value());
    }
}
