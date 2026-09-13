package com.scott.payment.settlement.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.Configuration;
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
            MerchantSettlementProfileMapper.class,
            SettlementBatchDailySequenceMapper.class,
            SettlementBatchMapper.class,
            SettlementCandidateMapper.class,
            SettlementBatchCandidateMapper.class,
            SettlementClearingFactMapper.class,
            SettlementBatchRateMapper.class,
            SettlementReviewDailySequenceMapper.class,
            SettlementReviewOrderMapper.class,
            SettlementReviewCandidateMapper.class,
            SettlementReviewRateMapper.class,
            SettlementReviewSummaryMapper.class,
            SettlementManualReviewTaskMapper.class,
            SettlementReviewSegmentMapper.class,
            SettlementReviewDecisionTaskMapper.class,
            SettlementRateQuoteMapper.class,
            SettlementResultMapper.class,
            SettlementFundMapper.class,
            SettlementReserveMapper.class);

    /** 自动处理只读取自动档案；自动预审候选页必须稳定且不碰已被占用的候选。 */
    @Test
    void automaticProfileQueriesShouldRespectProcessingModeAndCandidateOwnership() {
        String groups = sql(methodNamed(MerchantSettlementProfileMapper.class, "selectReadyBatchGroups"));
        String candidates = sql(methodNamed(
                MerchantSettlementProfileMapper.class, "selectReadyReviewCandidates"));
        String automaticPostAnchor = sql(methodNamed(
                SettlementCandidateMapper.class, "selectAutomaticPostAnchorForUpdate"));

        assertThat(groups).contains(
                "profile.processing_mode IN ('AUTO_POST', 'AUTO_REVIEW')",
                "candidate.candidate_status = 'READY'",
                "candidate.settlement_batch_no IS NULL",
                "candidate.review_order_no IS NULL")
                .doesNotContain("profile.processing_mode = 'MANUAL'");
        assertThat(candidates).contains(
                "candidate.candidate_status = 'READY'",
                "candidate.settlement_batch_no IS NULL",
                "candidate.review_order_no IS NULL",
                "candidate.settlement_eligible_date <= #{businessDate}",
                "candidate.create_time < #{cutoffEndTime}",
                "ORDER BY candidate.id ASC",
                "LIMIT #{limit}");
        assertThat(automaticPostAnchor).contains(
                "profile.processing_mode = 'AUTO_POST'",
                "account.account_status = 'NORMAL'",
                "candidate.candidate_status = 'READY'",
                "candidate.settlement_batch_no IS NULL",
                "candidate.review_order_no IS NULL",
                "candidate.settlement_eligible_date <= #{businessDate}",
                "candidate.create_time < #{cutoffEndTime}",
                "required_candidate.candidate_status != 'POSTED'",
                "ORDER BY candidate.id ASC",
                "LIMIT 1",
                "FOR UPDATE SKIP LOCKED");
        assertThat(automaticPostAnchor).contains(
                "candidate.source_type = 'CLEARING_REVISION'",
                "candidate.source_type = 'RESERVE_RELEASE'",
                "candidate.source_type = 'ADJUSTMENT'");
        assertThat(sql(methodNamed(SettlementCandidateMapper.class, "lockForReview"))).contains(
                "candidate_status = 'READY'", "review_order_no IS NULL", "version = #{row.version}");
    }

    /** 所有注解 SQL 必须使用绑定参数，禁止字符串插值。 */
    @Test
    void allSqlShouldUseBoundParameters() {
        MAPPER_TYPES.stream()
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .map(SettlementPersistenceMapperContractTest::sql)
                .filter(value -> !value.isBlank())
                .forEach(value -> assertThat(value).doesNotContain("${"));
    }

    /** 真实调用 MyBatis 注解解析器，避免动态 script 结构错误只在服务启动时暴露。 */
    @Test
    void allMapperAnnotationsShouldParseWithMyBatis() {
        Configuration configuration = new Configuration();
        MAPPER_TYPES.forEach(configuration::addMapper);
        assertThat(configuration.getMappedStatementNames()).isNotEmpty();
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

    /** 批次请求键必须唯一回读，候选总数和真实交易投影数必须在同一版本 CAS 中增加。 */
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
                "projectable_candidate_count = projectable_candidate_count + #{projectableDelta}",
                "#{projectableDelta} IN (0, 1)",
                "version = #{expectedVersion}",
                "version = version + 1");
        assertThat(sql(methodNamed(SettlementBatchMapper.class, "incrementCandidateCountBy"))).contains(
                "candidate_count = candidate_count + #{delta}",
                "projectable_candidate_count = projectable_candidate_count + #{projectableDelta}",
                "#{projectableDelta} BETWEEN 0 AND #{delta}",
                "version = #{expectedVersion}");
    }

    /** 正式批次取消必须由状态/version CAS 与只追加审计唯一键共同保护。 */
    @Test
    void cancellationShouldUseStateCasAndImmutableAudit() {
        assertThat(sql(methodNamed(SettlementBatchMapper.class, "cancelBeforePosting"))).contains(
                "batch_status IN ('CREATED', 'CLAIMING', 'CLAIMED', 'RATE_LOCKED'",
                "'FAILED_RETRYABLE', 'MANUAL_REVIEW'",
                "version = #{expectedVersion}",
                "version = version + 1");
        assertThat(sql(methodNamed(SettlementBatchMapper.class,
                "selectCancellationAuditByRequestKey"))).contains(
                "settlement_batch_cancellation_audit", "request_key = #{requestKey}");
        assertThat(sql(methodNamed(SettlementBatchMapper.class,
                "selectCancellationAuditByBatchNo"))).contains(
                "settlement_batch_cancellation_audit", "settlement_batch_no = #{settlementBatchNo}",
                "FOR UPDATE");
        assertThat(sql(methodNamed(SettlementBatchMapper.class, "insertCancellationAudit"))).contains(
                "INSERT INTO settlement_batch_cancellation_audit", "request_key", "expected_version",
                "operator_role_snapshot", "released_candidate_count");
    }

    /** 人工恢复只允许汇率锁定重试耗尽状态，并由 version CAS、请求唯一键和全量候选恢复保护。 */
    @Test
    void recoveryShouldUseNarrowStateCasAndImmutableAudit() {
        assertThat(sql(methodNamed(SettlementBatchMapper.class, "retryExhaustedRateLocking"))).contains(
                "batch_status = 'MANUAL_REVIEW'",
                "last_failure_stage = 'RATE_LOCKING'",
                "last_failure_code = 'SETTLEMENT_RETRY_EXHAUSTED'",
                "batch_status = 'FAILED_RETRYABLE'",
                "retry_count = 0",
                "processing_deadline = #{now}",
                "version = #{expectedVersion}",
                "version = version + 1");
        assertThat(sql(methodNamed(SettlementCandidateMapper.class, "restoreManualReviewBatch")))
                .contains("candidate_status = 'MANUAL_REVIEW'", "candidate_status = 'CLAIMED'",
                        "settlement_batch_no = #{settlementBatchNo}");
        assertThat(sql(methodNamed(SettlementBatchCandidateMapper.class, "restoreManualReviewBatch")))
                .contains("relation_status = 'MANUAL_REVIEW'", "relation_status = 'CLAIMED'",
                        "settlement_batch_no = #{settlementBatchNo}");
        assertThat(sql(methodNamed(SettlementCandidateMapper.class, "releaseCancelledBatch")))
                .contains("candidate_status IN ('CLAIMED', 'MANUAL_REVIEW')");
        assertThat(sql(methodNamed(SettlementBatchCandidateMapper.class, "releaseCancelledBatch")))
                .contains("relation_status IN ('CLAIMED', 'MANUAL_REVIEW')");
        assertThat(sql(methodNamed(SettlementBatchMapper.class,
                "selectRecoveryAuditByRequestKeyForUpdate")))
                .contains("settlement_batch_recovery_audit", "request_key = #{requestKey}", "FOR UPDATE");
        assertThat(sql(methodNamed(SettlementBatchMapper.class, "insertRecoveryAudit")))
                .contains("INSERT INTO settlement_batch_recovery_audit", "recovery_action",
                        "failure_stage_before", "restored_candidate_count", "operator_role_snapshot");
    }

    /** 分段决策任务恢复必须保留进度，并以失败状态、版本和只追加审计共同保护。 */
    @Test
    void reviewDecisionRecoveryShouldUseStateCasAndImmutableAudit() {
        assertThat(sql(methodNamed(SettlementReviewDecisionTaskMapper.class, "resumeFailed")))
                .contains("task_status = 'QUEUED'", "task_status = 'FAILED'",
                        "version = #{expectedVersion}", "version = version + 1")
                .doesNotContain("processed_segment_count = 0", "result_batch_count = 0");
        assertThat(sql(methodNamed(SettlementReviewDecisionTaskMapper.class,
                "selectRecoveryAuditByRequestKeyForUpdate")))
                .contains("settlement_review_decision_recovery_audit",
                        "request_key = #{requestKey}", "FOR UPDATE");
        assertThat(sql(methodNamed(SettlementReviewDecisionTaskMapper.class,
                "insertRecoveryAuditIdempotent")))
                .contains("INSERT INTO settlement_review_decision_recovery_audit",
                        "expected_version", "failure_code_before", "started_time_before",
                        "completed_time_before", "operator_role_snapshot",
                        "ON DUPLICATE KEY UPDATE id = id");
    }

    /** 预审创建、终态决策和候选占用必须由唯一键及当前状态/version CAS 共同保护。 */
    @Test
    void reviewMappersShouldProtectIdempotencyAndStateTransitions() {
        assertThat(sql(methodNamed(SettlementReviewOrderMapper.class, "insertIdempotent")))
                .contains("create_request_key", "selection_fingerprint", "ON DUPLICATE KEY UPDATE id = id");
        assertThat(sql(methodNamed(SettlementReviewOrderMapper.class, "selectByDecisionRequestKeyForUpdate")))
                .contains("decision_request_key = #{requestKey}", "FOR UPDATE");
        assertThat(sql(methodNamed(SettlementReviewOrderMapper.class, "approve")))
                .contains("review_status = 'PENDING_APPROVAL'", "version = #{expectedVersion}",
                        "decision_request_key", "settlement_batch_no");
        assertThat(sql(methodNamed(SettlementCandidateMapper.class, "lockForReview")))
                .contains("candidate_status = 'READY'", "review_order_no IS NULL",
                        "version = #{row.version}", "candidate_status = 'REVIEW_LOCKED'");
        assertThat(sql(methodNamed(SettlementCandidateMapper.class, "consumeReviewLock")))
                .contains("candidate_status = 'REVIEW_LOCKED'", "review_order_no = #{reviewOrderNo}",
                        "version = #{row.version}", "candidate_status = 'CLAIMED'");
        assertThat(sql(methodNamed(SettlementCandidateMapper.class, "releaseReviewLock")))
                .contains("candidate_status = 'REVIEW_LOCKED'", "review_order_no = #{reviewOrderNo}",
                        "version = #{row.version}", "candidate_status = 'READY'");
        assertThat(sql(methodNamed(SettlementBatchMapper.class, "bindApprovedReview")))
                .contains("batch_status = 'CREATED'", "candidate_count = 0",
                        "review_order_no IS NULL", "version = #{expectedVersion}",
                        "create_mode = 'MANUAL_REVIEW'");
        assertThat(sql(methodNamed(SettlementBatchRateMapper.class, "insertBatchIdempotent")))
                .contains("review_rate_id", "ON DUPLICATE KEY UPDATE id = id");
    }

    /** 大批量人工预审必须冻结高水位并按游标分段处理，决策任务同样按分段租约恢复。 */
    @Test
    void asynchronousReviewMappersShouldFreezeScopeAndResumeBySegment() {
        String profile = sql(methodNamed(SettlementManualReviewTaskMapper.class, "selectProfile"));
        String snapshotMaxCandidate = sql(methodNamed(
                SettlementManualReviewTaskMapper.class, "selectSnapshotMaxCandidateId"));
        String preview = sql(methodNamed(SettlementManualReviewTaskMapper.class, "selectPreviewLines"));
        String candidates = sql(methodNamed(
                SettlementManualReviewTaskMapper.class, "selectNextCandidatesForUpdate"));
        String taskLease = sql(methodNamed(
                SettlementManualReviewTaskMapper.class, "selectNextDueForUpdate"));
        String taskClaim = sql(methodNamed(
                SettlementManualReviewTaskMapper.class, "markProcessing"));
        String blockingBatch = sql(methodNamed(
                SettlementManualReviewTaskMapper.class, "selectBlockingManualReviewBatch"));
        String decisionInsert = sql(methodNamed(
                SettlementReviewDecisionTaskMapper.class, "insertIdempotent"));
        String decisionLease = sql(methodNamed(
                SettlementReviewDecisionTaskMapper.class, "selectNextDueForUpdate"));
        String decisionAdvance = sql(methodNamed(
                SettlementReviewDecisionTaskMapper.class, "advance"));
        String decisionComplete = sql(methodNamed(
                SettlementReviewDecisionTaskMapper.class, "markCompleted"));
        String segment = sql(methodNamed(SettlementReviewSegmentMapper.class, "selectNextLockedForUpdate"));
        String consume = sql(methodNamed(SettlementReviewSegmentMapper.class, "markConsumed"));

        assertThat(profile).contains(
                "account.account_status = 'NORMAL'",
                "version.version_status = 'ACTIVE'",
                "version.settlement_currency = profile.target_currency",
                "LIMIT 1 FOR UPDATE");
        assertThat(snapshotMaxCandidate)
                .contains("ORDER BY candidate.id DESC", "LIMIT 1",
                        "paymentType != null and paymentType != ''",
                        "paymentMethod != null and paymentMethod != ''")
                .doesNotContain("MAX(candidate.id)");
        assertThat(preview).contains(
                "candidate.candidate_status = 'READY'",
                "candidate.settlement_batch_no IS NULL",
                "candidate.review_order_no IS NULL",
                "candidate.id &lt;= #{maxCandidateId}",
                "paymentType != null and paymentType != ''",
                "paymentMethod != null and paymentMethod != ''",
                "GROUP BY detail.label_currency, detail.label_currency_exponent");
        assertThat(blockingBatch).contains(
                "settlement_profile_id = #{profileId}",
                "batch_type = #{reviewType}",
                "batch_status = 'MANUAL_REVIEW'",
                "candidate_count > 0",
                "ORDER BY business_date ASC, id ASC",
                "LIMIT 1");
        assertThat(candidates).contains(
                "candidate.id &gt; #{task.lastCandidateId}",
                "candidate.id &lt;= #{task.snapshotMaxCandidateId}",
                "ORDER BY candidate.id ASC LIMIT #{limit} FOR UPDATE");
        assertThat(sql(methodNamed(SettlementManualReviewTaskMapper.class, "start"))).contains(
                "task_status = 'PREVIEWED'", "version = #{expectedVersion}",
                "task_status = 'QUEUED'");
        assertThat(taskLease).contains(
                "task_status IN ('QUEUED', 'PROCESSING', 'CANCELLING')",
                "next_retry_time <= #{now}",
                "processing_deadline <= #{now}",
                "LIMIT 1 FOR UPDATE SKIP LOCKED");
        assertThat(taskLease).doesNotContain("&lt;");
        assertThat(taskClaim)
                .contains("processing_deadline <= #{now}")
                .doesNotContain("&lt;");
        assertThat(decisionInsert).contains(
                "request_key", "expected_review_version", "total_segment_count",
                "ON DUPLICATE KEY UPDATE id = id");
        assertThat(decisionLease).contains(
                "task_status IN ('QUEUED', 'PROCESSING')", "FOR UPDATE SKIP LOCKED");
        assertThat(decisionAdvance).contains(
                "processed_segment_count = processed_segment_count + 1",
                "result_batch_count = result_batch_count + #{resultBatchDelta}",
                "first_settlement_batch_no = COALESCE");
        assertThat(decisionComplete).contains(
                "task_status = 'COMPLETED'",
                "processed_segment_count = total_segment_count");
        assertThat(segment).contains(
                "segment_status = 'LOCKED'", "LIMIT 1 FOR UPDATE");
        assertThat(consume).contains(
                "segment_status = 'CONSUMED'", "settlement_batch_no = #{settlementBatchNo}",
                "segment_status = 'LOCKED'", "version = #{version}");
    }

    /** 保证金异步预审只允许已到期 RELEASE，并按原保证金币种汇总且不生成交易投影。 */
    @Test
    void reserveManualReviewShouldUseMaturedReleaseFactsAndOriginalCurrency() {
        String snapshotMaxCandidate = sql(methodNamed(
                SettlementManualReviewTaskMapper.class, "selectSnapshotMaxCandidateId"));
        String preview = sql(methodNamed(SettlementManualReviewTaskMapper.class, "selectPreviewLines"));
        String candidates = sql(methodNamed(
                SettlementManualReviewTaskMapper.class, "selectNextCandidatesForUpdate"));
        String currencies = sql(methodNamed(SettlementManualReviewTaskMapper.class, "selectCurrencies"));

        for (String query : List.of(snapshotMaxCandidate, preview, candidates, currencies)) {
            assertThat(query).contains(
                    "RESERVE_RELEASE",
                    "reserve_detail.reserve_action_type = 'RELEASE'",
                    "reserve_detail.expected_reserve_release_date IS NOT NULL");
            assertThat(query).doesNotContain("candidate.source_type = 'ADJUSTMENT'");
        }
        assertThat(snapshotMaxCandidate).contains(
                "reserve_detail.expected_reserve_release_date &lt;= #{businessDate}",
                "candidate.candidate_status = 'READY'",
                "candidate.shadow_mode = 0",
                "candidate.settlement_batch_no IS NULL",
                "candidate.review_order_no IS NULL");
        assertThat(preview).contains(
                "reserve_detail.reserve_currency AS source_currency",
                "reserve_detail.reserve_currency_exponent AS source_currency_exponent",
                "SUM(reserve_detail.released_amount)",
                "MIN(reserve_detail.reserve_delay_days)",
                "MAX(reserve_detail.reserve_delay_days)",
                "MIN(reserve_detail.expected_reserve_release_date)",
                "MAX(reserve_detail.expected_reserve_release_date)",
                "GROUP BY reserve_detail.reserve_currency, reserve_detail.reserve_currency_exponent");
        assertThat(candidates).contains(
                "reserve_detail.expected_reserve_release_date &lt;= #{task.businessDate}",
                "candidate.id &gt; #{task.lastCandidateId}",
                "candidate.id &lt;= #{task.snapshotMaxCandidateId}",
                "ORDER BY candidate.id ASC LIMIT #{limit} FOR UPDATE");
        assertThat(currencies).contains(
                "reserve_detail.reserve_currency, reserve_detail.reserve_currency_exponent",
                "reserve_detail.expected_reserve_release_date &lt;= #{task.businessDate}",
                "GROUP BY currency",
                "HAVING MIN(currency_exponent) = MAX(currency_exponent)");
        assertThat(snapshotMaxCandidate).containsOnlyOnce("<script>");
        assertThat(preview).containsOnlyOnce("<script>");
        assertThat(candidates).containsOnlyOnce("<script>");
        assertThat(currencies).containsOnlyOnce("<script>");
    }

    /** 候选认领 SQL 必须同时保护状态、批次空值、真实模式、冻结配置和版本。 */
    @Test
    void candidateClaimShouldUseAllFinancialCasGuards() {
        String claim = sql(methodNamed(SettlementCandidateMapper.class, "claim"));
        String dependencies = sql(methodNamed(SettlementCandidateMapper.class, "countUnresolvedDependencies"));

        assertThat(claim).contains(
                "candidate_status = 'READY'",
                "settlement_batch_no IS NULL",
                "review_order_no IS NULL",
                "shadow_mode = 0",
                "settlement_profile_id = #{settlementProfileId}",
                "version = #{expectedVersion}",
                "version = version + 1");
        assertThat(sql(methodNamed(SettlementCandidateMapper.class, "claimBatch"))).contains(
                "candidate_status = 'READY'",
                "settlement_batch_no IS NULL",
                "review_order_no IS NULL",
                "shadow_mode = 0",
                "settlement_profile_id = #{settlementProfileId}",
                "version = #{row.version}",
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
        assertThat(rateLocked).containsOnlyOnce("version = version + 1");
        assertThat(calculating).contains("batch_status = 'RATE_LOCKED'",
                "processing_owner = #{owner}", "version = #{expectedVersion}");
        assertThat(calculated).contains("batch_status = 'CALCULATING'",
                "batch_status = 'CALCULATED'", "calculated_time = #{calculatedTime}",
                "processing_owner = NULL", "version = #{expectedVersion}");
    }

    /** 保证金全部消费和冲正 CAS 必须使用包含调整累计的统一剩余责任公式。 */
    @Test
    void reserveMutationsShouldUseAdjustmentAwareResponsibilityCas() {
        for (String method : List.of("applyReturn", "applyRelease", "reverseHold", "applyCreditAdjustment",
                "reverseDebitAdjustment")) {
            assertThat(sql(methodNamed(SettlementReserveMapper.class, method))).contains(
                    "retained_amount + debit_adjustment_amount",
                    "returned_amount",
                    "released_amount",
                    "credit_adjustment_amount",
                    "reversed_amount",
                    "version = #{expectedVersion}");
        }
    }

    /** MySQL 单表 UPDATE 从左到右求值，终态必须在累计金额变更前基于旧值计算。 */
    @Test
    void reserveTerminalStatusShouldBeAssignedBeforeCumulativeAmount() {
        assertStatusBeforeAmount("applyReturn", "returned_amount = returned_amount + #{amount}");
        assertStatusBeforeAmount("applyRelease", "released_amount = released_amount + #{amount}");
        assertStatusBeforeAmount("reverseHold", "reversed_amount = reversed_amount + #{amount}");
    }

    /** 所有结算余额 CAS 都必须再次校验正常账户，防止冻结账户执行结算或主动冲正。 */
    @Test
    void fundBalanceCasShouldRequireNormalAccount() {
        assertThat(sql(methodNamed(SettlementFundMapper.class, "updateAccountBalance"))).contains(
                "available_balance = #{balanceBefore}",
                "account_version = #{expectedVersion}",
                "account_status = 'NORMAL'",
                "deleted = 0");
    }

    private static Method methodNamed(Class<?> type, String methodName) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }

    private static void assertStatusBeforeAmount(String methodName, String amountAssignment) {
        String statement = sql(methodNamed(SettlementReserveMapper.class, methodName));
        assertThat(statement.indexOf("reserve_status = CASE"))
                .isGreaterThanOrEqualTo(0)
                .isLessThan(statement.indexOf(amountAssignment));
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
