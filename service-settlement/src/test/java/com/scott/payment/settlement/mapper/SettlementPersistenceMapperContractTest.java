package com.scott.payment.settlement.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementPersistenceMapperContractTest
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 锁定结算 Mapper 的参数绑定、数据库发号、批次租约、真实候选 CAS、汇率结果和审计关系边界。
 * @status : create
 */
class SettlementPersistenceMapperContractTest {

    private static final List<Class<?>> MAPPER_TYPES = List.of(
            SettlementBatchDailySequenceMapper.class,
            SettlementBatchMapper.class,
            SettlementCandidateMapper.class,
            SettlementBatchCandidateMapper.class,
            SettlementClearingFactMapper.class,
            SettlementBatchRateMapper.class,
            SettlementRateQuoteMapper.class,
            SettlementResultMapper.class);

    /** 所有注解 SQL 必须使用绑定参数，禁止字符串插值。 */
    @Test
    void allSqlShouldUseBoundParameters() {
        MAPPER_TYPES.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(SettlementPersistenceMapperContractTest::sql)
                .filter(value -> !value.isBlank())
                .forEach(value -> assertThat(value).doesNotContain("${"));
    }

    /** 日序列必须锁行并同时使用当前序号和版本 CAS，禁止 JVM 或 Redis 单独发号。 */
    @Test
    void dailySequenceShouldUseRowLockAndVersionCas() {
        String lock = sql(methodNamed(SettlementBatchDailySequenceMapper.class, "selectForUpdate"));
        String increment = sql(methodNamed(SettlementBatchDailySequenceMapper.class, "increment"));

        assertThat(lock).contains("business_date = #{businessDate}", "FOR UPDATE");
        assertThat(increment).contains(
                "current_sequence = current_sequence + 1",
                "current_sequence = #{expectedSequence}",
                "current_sequence < 99999999",
                "version = #{expectedVersion}",
                "version = version + 1");
    }

    /** 批次请求键必须唯一回读，候选计数只能在认领状态用版本 CAS 增加。 */
    @Test
    void batchShouldKeepCreateIdempotencyAndCountCas() {
        String insert = sql(methodNamed(SettlementBatchMapper.class, "insertIdempotent"));
        String requestLock = sql(methodNamed(SettlementBatchMapper.class, "selectByCreateRequestKeyForUpdate"));
        String count = sql(methodNamed(SettlementBatchMapper.class, "incrementCandidateCount"));

        assertThat(insert).contains("create_request_key", "ON DUPLICATE KEY UPDATE id = id");
        assertThat(requestLock).contains("create_request_key = #{createRequestKey}", "FOR UPDATE");
        assertThat(count).contains(
                "batch_status IN ('CREATED', 'CLAIMING')",
                "candidate_count = candidate_count + 1",
                "version = #{expectedVersion}",
                "version = version + 1");
    }

    /** 候选认领 SQL 必须同时保护状态、批次空值、真实模式、冻结配置和版本。 */
    @Test
    void candidateClaimShouldUseAllFinancialCasGuards() {
        String claim = sql(methodNamed(SettlementCandidateMapper.class, "claim"));
        String dependencies = sql(methodNamed(SettlementCandidateMapper.class, "countUnresolvedDependencies"));

        assertThat(claim).contains(
                "candidate_status = 'READY'",
                "settlement_batch_no IS NULL",
                "shadow_mode = 0",
                "settlement_profile_id = #{settlementProfileId}",
                "version = #{expectedVersion}",
                "version = version + 1");
        assertThat(dependencies).contains(
                "settlement_candidate_dependency",
                "required_candidate.candidate_status = 'POSTED'",
                "required_candidate.settlement_batch_no = #{settlementBatchNo}");
    }

    /** 关系 Mapper 禁止删除；Phase B 只允许带 CLAIMED 条件迁移到人工复核。 */
    @Test
    void relationMapperShouldRemainAppendOnly() {
        assertThat(SettlementBatchCandidateMapper.class.getDeclaredMethods())
                .allMatch(method -> method.getAnnotation(Delete.class) == null);
        assertThat(sql(methodNamed(SettlementBatchCandidateMapper.class, "insertIdempotent")))
                .contains("ON DUPLICATE KEY UPDATE id = id", "settlement_batch_no", "candidate_id");
        assertThat(sql(methodNamed(SettlementBatchCandidateMapper.class,
                "selectByBatchAndCandidateForUpdate"))).contains("FOR UPDATE");
        assertThat(sql(methodNamed(SettlementBatchCandidateMapper.class, "markBatchManualReview")))
                .contains("relation_status = 'CLAIMED'", "relation_status = 'MANUAL_REVIEW'",
                        "settlement_batch_no = #{settlementBatchNo}");
    }

    /** 每个批量清分 locator 必须同时携带交易号、精确分片时间和清分修订号。 */
    @Test
    void clearingFactReadsShouldUseExactRevisionLocatorsWithoutNPlusOne() {
        for (String method : List.of("selectTransactionDetails", "selectReserveDetails")) {
            String query = sql(methodNamed(SettlementClearingFactMapper.class, method));
            assertThat(query).contains(
                    "transaction_id = #{locator.transactionId}",
                    "transaction_date_time = #{locator.transactionDateTime}",
                    "clearing_revision = #{locator.clearingRevision}",
                    "<foreach collection=\"locators\"");
        }
    }

    /** 汇率和结果 Mapper 只能追加或读取，重试必须通过唯一键冲突后回读身份。 */
    @Test
    void rateAndResultMappersShouldRemainImmutableAndIdempotent() {
        for (Class<?> type : List.of(SettlementBatchRateMapper.class, SettlementResultMapper.class)) {
            assertThat(type.getDeclaredMethods()).allMatch(method ->
                    method.getAnnotation(Update.class) == null
                            && method.getAnnotation(Delete.class) == null);
        }
        assertThat(sql(methodNamed(SettlementBatchRateMapper.class, "insertBatchIdempotent")))
                .contains("ON DUPLICATE KEY UPDATE id = id", "settlement_batch_rate");
        assertThat(sql(methodNamed(SettlementResultMapper.class, "insertItemsIdempotent")))
                .contains("ON DUPLICATE KEY UPDATE id = id", "settlement_result_item")
                .doesNotContain("LEDGER_POSTING");
    }

    /** 汇率锁定和计算终点必须同时受当前状态、租约所有者、截止时间和 version CAS 保护。 */
    @Test
    void phaseBTransitionsShouldUseLeaseAndVersionCas() {
        String rateLocked = sql(methodNamed(SettlementBatchMapper.class, "markRateLocked"));
        String calculating = sql(methodNamed(SettlementBatchMapper.class, "beginCalculating"));
        String calculated = sql(methodNamed(SettlementBatchMapper.class, "markCalculated"));

        assertThat(rateLocked).contains("batch_status IN ('CLAIMED', 'FAILED_RETRYABLE')",
                "processing_owner = #{owner}", "processing_deadline > #{lockedTime}",
                "version = #{expectedVersion}", "batch_status = 'RATE_LOCKED'");
        assertThat(calculating).contains("batch_status = 'RATE_LOCKED'",
                "processing_owner = #{owner}", "version = #{expectedVersion}");
        assertThat(calculated).contains("batch_status = 'CALCULATING'",
                "batch_status = 'CALCULATED'", "calculated_time = #{calculatedTime}",
                "processing_owner = NULL", "version = #{expectedVersion}");
    }

    private static Method methodNamed(Class<?> type, String methodName) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }

    private static String sql(Method method) {
        if (method.getAnnotation(Select.class) != null) {
            return String.join(" ", method.getAnnotation(Select.class).value());
        }
        if (method.getAnnotation(Insert.class) != null) {
            return String.join(" ", method.getAnnotation(Insert.class).value());
        }
        if (method.getAnnotation(Update.class) != null) {
            return String.join(" ", method.getAnnotation(Update.class).value());
        }
        if (method.getAnnotation(Delete.class) != null) {
            return String.join(" ", method.getAnnotation(Delete.class).value());
        }
        return "";
    }
}
