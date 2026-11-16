package com.scott.payment.risk.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.mq.message.RiskAuditHitMessage;
import com.scott.payment.component.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.domain.FrequencyRuleEvaluation;
import com.scott.payment.risk.domain.MerchantLimitEvaluation;
import com.scott.payment.risk.domain.MerchantLimitReadEvaluation;
import com.scott.payment.risk.domain.MerchantLimitReservation;
import com.scott.payment.risk.domain.RiskListFunction;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.RiskRuntimeLookupValue;
import com.scott.payment.risk.domain.SourceUrlRuleEvaluation;
import com.scott.payment.risk.domain.state.RiskDecisionEnum;
import com.scott.payment.risk.domain.state.RiskReasonCodeEnum;
import com.scott.payment.risk.repository.RiskAuditRecordPublisher;
import com.scott.payment.risk.repository.RiskListRuntimeRepository;
import com.scott.payment.risk.service.RiskRuntimeValueNormalizer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证实时风控阶段顺序、短路决策、只读并发边界、预占回滚和脱敏审计行为。
 */
class DefaultRiskEvaluationServiceTests {

    /**
     * 验证 Spring 在存在测试构造器时仍选择标注的生产构造器，并解析命名只读执行器。
     */
    @Test
    void shouldSelectProductionConstructorWhenCreatedBySpring() {
        RiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository();
        RiskRuntimeValueNormalizer normalizer = new RiskRuntimeValueNormalizer();
        RiskEvaluationProperties properties = runtimeProperties();
        Executor executor = Runnable::run;
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RiskListRuntimeRepository.class, () -> repository);
            context.registerBean(RiskRuntimeValueNormalizer.class, () -> normalizer);
            context.registerBean(RiskEvaluationProperties.class, () -> properties);
            context.registerBean("riskReadOnlyEvaluationExecutor", Executor.class, () -> executor);
            context.register(DefaultRiskEvaluationService.class);

            context.refresh();

            assertThat(context.getBean(DefaultRiskEvaluationService.class)).isNotNull();
        }
    }

    /**
     * 验证三个相互独立的只读风控组并发执行。
     */
    @Test
    void shouldExecuteIndependentReadOnlyRiskGroupsConcurrently() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            ParallelProbeRiskListRuntimeRepository repository = new ParallelProbeRiskListRuntimeRepository();
            RiskEvaluationProperties properties = runtimeProperties();
            properties.setReadOnlyParallelEnabled(true);
            DefaultRiskEvaluationService runtimeService = new DefaultRiskEvaluationService(
                    repository,
                    new RecordingRiskAuditRecordPublisher(),
                    properties,
                    executor);

            RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(fullyPopulatedRequest());

            assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
            assertThat(repository.concurrentGroupsReached()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证并发查询完成后仍按既定优先级选择 AML 决策。
     */
    @Test
    void shouldKeepAmlDecisionPriorityAfterParallelQueriesComplete() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                    .withListHit(RiskListFunction.AML_CARD,
                            match("AML", "card", "CRITICAL", "REJECT"))
                    .withListHit(RiskListFunction.WHITE_CARD_NO,
                            match("WHITE", "cardNo", "CRITICAL", "PASS"))
                    .withMerchantLimitRule(match("RULE", "merchantLimit", "HIGH", "REJECT"));
            RiskEvaluationProperties properties = runtimeProperties();
            properties.setReadOnlyParallelEnabled(true);
            RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
            DefaultRiskEvaluationService runtimeService = new DefaultRiskEvaluationService(
                    repository,
                    publisher,
                    properties,
                    executor);

            RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(fullyPopulatedRequest());

            assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
            assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.AML_HIT.getCode());
            assertThat(publisher.messages).singleElement().satisfies(message ->
                    assertThat(message.getHits())
                            .extracting(RiskAuditHitMessage::getStageCode)
                            .containsOnly("AML"));
            assertThat(repository.merchantLimitQueryCount).isEqualTo(1);
            assertThat(repository.cumulativeReservationCount).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证只读风控组超时时失败关闭且不进入有副作用阶段。
     */
    @Test
    void shouldFailClosedWhenReadOnlyRiskGroupTimesOut() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            FakeRiskListRuntimeRepository repository =
                    new SlowMerchantLimitRiskListRuntimeRepository();
            RiskEvaluationProperties properties = runtimeProperties();
            properties.setReadOnlyParallelEnabled(true);
            properties.setReadOnlyTimeoutMillis(100);
            RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
            DefaultRiskEvaluationService runtimeService = new DefaultRiskEvaluationService(
                    repository,
                    publisher,
                    properties,
                    executor);

            assertThatThrownBy(() -> runtimeService.evaluatePayment(fullyPopulatedRequest()))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("risk read-only evaluation is unavailable")
                    .hasRootCauseInstanceOf(TimeoutException.class);
            assertThat(repository.cumulativeReservationCount).isZero();
            assertThat(repository.frequencyEvaluationCount).isZero();
            assertThat(publisher.messages).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证只读风控组异常时失败关闭且不创建任何预占。
     */
    @Test
    void shouldFailClosedWhenReadOnlyRiskGroupThrows() {
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            FakeRiskListRuntimeRepository repository =
                    new ThrowingMerchantLimitRiskListRuntimeRepository();
            RiskEvaluationProperties properties = runtimeProperties();
            properties.setReadOnlyParallelEnabled(true);
            RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
            DefaultRiskEvaluationService runtimeService = new DefaultRiskEvaluationService(
                    repository,
                    publisher,
                    properties,
                    executor);

            assertThatThrownBy(() -> runtimeService.evaluatePayment(fullyPopulatedRequest()))
                    .isInstanceOf(ServiceException.class)
                    .hasMessage("risk read-only evaluation is unavailable")
                    .hasRootCauseMessage("simulated merchant limit query failure");
            assertThat(repository.cumulativeReservationCount).isZero();
            assertThat(repository.frequencyEvaluationCount).isZero();
            assertThat(publisher.messages).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证累计限额、频控和 3DS 等有状态阶段留在请求线程执行。
     */
    @Test
    void shouldKeepStatefulRiskStagesOnRequestThread() {
        ExecutorService executor = Executors.newFixedThreadPool(
                3,
                runnable -> new Thread(runnable, "risk-read-only-test"));
        try {
            FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository();
            RiskEvaluationProperties properties = runtimeProperties();
            properties.setReadOnlyParallelEnabled(true);
            DefaultRiskEvaluationService runtimeService = new DefaultRiskEvaluationService(
                    repository,
                    new RecordingRiskAuditRecordPublisher(),
                    properties,
                    executor);
            String requestThreadName = Thread.currentThread().getName();

            RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(fullyPopulatedRequest());

            assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
            assertThat(repository.readOnlyThreadNames)
                    .isNotEmpty()
                    .allMatch(name -> name.startsWith("risk-read-only-test"));
            assertThat(repository.cumulativeLimitThreadName).isEqualTo(requestThreadName);
            assertThat(repository.frequencyThreadName).isEqualTo(requestThreadName);
            assertThat(repository.threeDsThreadName).isEqualTo(requestThreadName);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 验证关闭并发后恢复惰性串行短路且不调用只读执行器。
     */
    @Test
    void shouldPreserveLazySerialShortCircuitWhenParallelEvaluationIsDisabled() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.AML_CARD, match("AML", "card", "CRITICAL", "REJECT"));
        RiskEvaluationProperties properties = runtimeProperties();
        properties.setReadOnlyParallelEnabled(false);
        DefaultRiskEvaluationService runtimeService = new DefaultRiskEvaluationService(
                repository,
                new RecordingRiskAuditRecordPublisher(),
                properties,
                runnable -> {
                    throw new AssertionError("disabled parallel executor must not be used");
                });
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardNo("4111111111111234");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.AML_HIT.getCode());
        assertThat(repository.merchantLimitQueryCount).isZero();
    }

    /**
     * 验证开启查询合并后使用组合仓储契约，并在一次评估内只读取一次相同名单启用状态。
     */
    @Test
    void shouldConsolidateRepeatedRuleReadsWithinSingleEvaluation() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withActiveListRule(RiskListFunction.BLACK_EMAIL)
                .withActiveFrequencyRule();
        RiskEvaluationProperties properties = runtimeProperties();
        properties.setReadQueryConsolidationEnabled(true);
        DefaultRiskEvaluationService runtimeService = new DefaultRiskEvaluationService(
                repository,
                new RecordingRiskAuditRecordPublisher(),
                properties);

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(fullyPopulatedRequest());

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(repository.sourceUrlEvaluationCount).isEqualTo(1);
        assertThat(repository.merchantLimitReadEvaluationCount).isEqualTo(1);
        assertThat(repository.frequencyEvaluationWithStatusCount).isEqualTo(1);
        assertThat(repository.activeListRuleQueryCounts.get(RiskListFunction.BLACK_EMAIL)).isEqualTo(1);
    }

    /**
     * 验证查询合并开关开启前后保持决策、原因码和审计节点顺序一致。
     */
    @Test
    void shouldKeepEvaluationOutcomeStableWhenQueryConsolidationIsEnabled() {
        FakeRiskListRuntimeRepository legacyRepository = new FakeRiskListRuntimeRepository()
                .withSourceUrlRule(match("RULE", "sourceUrl", "LOW", "PASS"))
                .withActiveListRule(RiskListFunction.BLACK_EMAIL)
                .withActiveMerchantLimitRule()
                .withActiveFrequencyRule();
        FakeRiskListRuntimeRepository consolidatedRepository = new FakeRiskListRuntimeRepository()
                .withSourceUrlRule(match("RULE", "sourceUrl", "LOW", "PASS"))
                .withActiveListRule(RiskListFunction.BLACK_EMAIL)
                .withActiveMerchantLimitRule()
                .withActiveFrequencyRule();
        RecordingRiskAuditRecordPublisher legacyPublisher = new RecordingRiskAuditRecordPublisher();
        RecordingRiskAuditRecordPublisher consolidatedPublisher = new RecordingRiskAuditRecordPublisher();
        RiskEvaluationProperties legacyProperties = runtimeProperties();
        RiskEvaluationProperties consolidatedProperties = runtimeProperties();
        consolidatedProperties.setReadQueryConsolidationEnabled(true);

        RiskPaymentEvaluateResultDTO legacyResult = new DefaultRiskEvaluationService(
                legacyRepository, legacyPublisher, legacyProperties)
                .evaluatePayment(fullyPopulatedRequest());
        RiskPaymentEvaluateResultDTO consolidatedResult = new DefaultRiskEvaluationService(
                consolidatedRepository, consolidatedPublisher, consolidatedProperties)
                .evaluatePayment(fullyPopulatedRequest());

        assertThat(consolidatedResult.getDecision()).isEqualTo(legacyResult.getDecision());
        assertThat(consolidatedResult.getReasonCode()).isEqualTo(legacyResult.getReasonCode());
        assertThat(consolidatedResult.isMerchantLimitReserved())
                .isEqualTo(legacyResult.isMerchantLimitReserved());
        List<String> legacyDetails = legacyPublisher.messages.get(0).getHits().stream()
                .map(detail -> detail.getStageCode() + ":" + detail.getFunctionCode()
                        + ":" + detail.getMatchResult() + ":" + detail.getDecisionEffect())
                .toList();
        List<String> consolidatedDetails = consolidatedPublisher.messages.get(0).getHits().stream()
                .map(detail -> detail.getStageCode() + ":" + detail.getFunctionCode()
                        + ":" + detail.getMatchResult() + ":" + detail.getDecisionEffect())
                .toList();
        assertThat(consolidatedDetails).containsExactlyElementsOf(legacyDetails);
    }

    /**
     * 验证商户未启用任何风控功能时返回明确 SKIP。
     */
    @Test
    void shouldSkipWhenNoMerchantRiskFunctionIsEnabled() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository();
        RiskEvaluationProperties properties = runtimeProperties();
        DefaultRiskEvaluationService runtimeService = new DefaultRiskEvaluationService(
                repository,
                new RecordingRiskAuditRecordPublisher(),
                properties);

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(
                baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo("NOT_ENABLED");
    }

    /**
     * 验证已配置黑名单未命中时返回 NO_MATCH。
     */
    @Test
    void shouldSkipWhenConfiguredBlacklistDoesNotMatch() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withActiveListRule(RiskListFunction.BLACK_CARD_BIN);
        DefaultRiskEvaluationService runtimeService = runtimeService(
                repository,
                new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardBin("411111");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo("NO_MATCH");
    }

    /**
     * 验证规则存在但商户未启用对应功能时跳过该规则。
     */
    @Test
    void shouldSkipWhenMerchantRiskFunctionIsNotEnabled() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository();
        DefaultRiskEvaluationService runtimeService = runtimeService(
                repository,
                new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardBin("411111");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo("NOT_ENABLED");
    }

    /**
     * 验证商户启用功能但无生效规则时返回 NOT_CONFIGURED。
     */
    @Test
    void shouldSkipWhenMerchantRiskFunctionIsEnabledButNotConfigured() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withEnabledMerchantFunction(RiskListFunction.BLACK_CARD_BIN);
        DefaultRiskEvaluationService runtimeService = runtimeService(
                repository,
                new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardBin("411111");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo("NOT_CONFIGURED");
    }

    /**
     * 验证普通支付在未配置任何风控规则时保持可继续的跳过结论。
     */
    @Test
    void shouldSkipNormalPaymentWhenNoRiskFunctionIsConfigured() {
        DefaultRiskEvaluationService runtimeService = runtimeService(
                new FakeRiskListRuntimeRepository(),
                new RecordingRiskAuditRecordPublisher());

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.NOT_ENABLED.getCode());
        assertThat(resultDTO.getRiskRecordNo()).startsWith("RK");
    }

    /**
     * 验证来源地址关键字不会触发代码内硬编码阻断。
     */
    @Test
    void shouldNotUseSourceKeywordAsHardcodedBlockRule() {
        DefaultRiskEvaluationService runtimeService = runtimeService(
                new FakeRiskListRuntimeRepository(),
                new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setSourceUrl("https://blocked.example.test/pay");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.NOT_ENABLED.getCode());
    }

    /**
     * 验证未配置规则时不使用代码内硬编码金额触发 3DS。
     */
    @Test
    void shouldNotApplyHardcodedThreeDsAmountThreshold() {
        DefaultRiskEvaluationService runtimeService = runtimeService(
                new FakeRiskListRuntimeRepository(),
                new RecordingRiskAuditRecordPublisher());

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("1000.00")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.NOT_ENABLED.getCode());
    }

    /**
     * 验证未配置规则时不使用代码内硬编码金额触发人工复核。
     */
    @Test
    void shouldNotApplyHardcodedManualReviewAmountThreshold() {
        DefaultRiskEvaluationService runtimeService = runtimeService(
                new FakeRiskListRuntimeRepository(),
                new RecordingRiskAuditRecordPublisher());

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("5000.00")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.NOT_ENABLED.getCode());
    }

    /**
     * 验证商户强白名单命中后大额交易按规则允许通过。
     */
    @Test
    void shouldPassWhenWhitelistMerchantMatchesForLargeAmount() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.WHITE_MERCHANT, match("WHITE", "merchant", "CRITICAL", "PASS"));
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("9999.00")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.NONE.getCode());
        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.messages.get(0).getDecisionResult()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(publisher.messages.get(0).getHits())
                .extracting(RiskAuditHitMessage::getModuleType, RiskAuditHitMessage::getFunctionCode)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("WHITE", "merchant"));
        assertThat(publisher.messages.get(0).getHitCount()).isEqualTo(1);
    }

    /**
     * 验证付款人邮箱命中黑名单时拒绝交易。
     */
    @Test
    void shouldRejectWhenBlacklistEmailHit() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.BLACK_EMAIL, match("BLACK", "email", "HIGH", "REJECT"));
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setBillingEmail("blocked@example.test");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.BLACKLIST_HIT.getCode());
        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.messages.get(0).getDecisionResult()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(publisher.messages.get(0).getDecisionReasonCode())
                .isEqualTo(RiskReasonCodeEnum.BLACKLIST_HIT.getCode());
        assertThat(publisher.messages.get(0).getRiskLevel()).isEqualTo("HIGH");
        assertThat(publisher.messages.get(0).getHits())
                .filteredOn(hit -> "BLACK".equals(hit.getModuleType())
                        && "email".equals(hit.getFunctionCode())
                        && "HIT".equals(hit.getMatchResult()))
                .singleElement()
                .extracting(RiskAuditHitMessage::getReasonCode)
                .isEqualTo(RiskReasonCodeEnum.BLACKLIST_HIT.getCode());
    }

    /**
     * 验证启用商户 IP 白名单后请求 IP 未命中时拒绝交易。
     */
    @Test
    void shouldRejectWhenMerchantHasActiveIpWhitelistButRequestIpMissed() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withMerchantIpWhitelistMiss(match("SYSTEM", "merchantIpWhitelist", "HIGH", "REJECT"));
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setIngressClientIp("198.51.100.24");
        requestDTO.setPayerIp("203.0.113.9");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.IP_WHITELIST_MISSED.getCode());
        assertThat(repository.merchantIpWhitelistLookup.getRawValue()).isEqualTo("198.51.100.24");
        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.messages.get(0).getHitCount()).isZero();
        assertThat(publisher.messages.get(0).getHits())
                .filteredOn(detail -> !"NONE".equals(detail.getDecisionEffect()))
                .extracting(RiskAuditHitMessage::getStageCode, RiskAuditHitMessage::getMatchResult, RiskAuditHitMessage::getDecisionEffect)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "MERCHANT_IP_WHITELIST", "MISS", "BLOCK"));
        assertThat(publisher.messages.get(0).getHits().get(0).getDecisionReason())
                .contains("不在", "单节点拦截")
                .doesNotContain("放行");
    }

    /**
     * 验证配置来源限制后请求主机未命中时按规则拒绝。
     */
    @Test
    void shouldRejectWhenSourceUrlRestrictionConfiguredButSourceMissed() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withSourceUrlMiss(match("SYSTEM", "sourceUrl", "HIGH", "REJECT"));
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setSourceUrl("https://evil.example.test/pay");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.SOURCE_URL_NOT_ALLOWED.getCode());
        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.messages.get(0).getHitCount()).isZero();
        assertThat(publisher.messages.get(0).getHits().get(0).getDecisionReason())
                .contains("不在", "单节点拦截")
                .doesNotContain("放行");
    }

    /**
     * 验证托管收银台只豁免来源地址限制，不扩大其他风控豁免。
     */
    @Test
    void shouldBypassOnlySourceUrlRestrictionForHostedCheckout() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withSourceUrlMiss(match("SYSTEM", "sourceUrl", "HIGH", "REJECT"))
                .withSourceUrlRule(match("RULE", "sourceUrl", "MEDIUM", "REVIEW"));
        DefaultRiskEvaluationService runtimeService = runtimeService(
                repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setRequestSource("HOSTED_CHECKOUT");
        requestDTO.setSourceUrl("https://not-allowed.example.test/pay");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.NOT_ENABLED.getCode());
    }

    /**
     * 验证托管收银台交易命中 AML 时仍然拒绝。
     */
    @Test
    void shouldStillRejectAmlHitForHostedCheckout() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withSourceUrlMiss(match("SYSTEM", "sourceUrl", "HIGH", "REJECT"))
                .withListHit(RiskListFunction.AML_CARD,
                        match("AML", "card", "CRITICAL", "REJECT"));
        DefaultRiskEvaluationService runtimeService = runtimeService(
                repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setRequestSource("HOSTED_CHECKOUT");
        requestDTO.setSourceUrl("https://not-allowed.example.test/pay");
        requestDTO.setCardNo("4111111111111234");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.AML_HIT.getCode());
    }

    /**
     * 验证托管收银台跳过商户入口白名单后仍执行付款人 IP 黑名单。
     */
    @Test
    void shouldBypassMerchantWhitelistButStillRejectPayerIpBlacklistForHostedCheckout() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withMerchantIpWhitelistMiss(match("SYSTEM", "merchantIpWhitelist", "HIGH", "REJECT"))
                .withListHit(RiskListFunction.BLACK_IP, match("BLACK", "ip", "HIGH", "REJECT"));
        DefaultRiskEvaluationService runtimeService = runtimeService(
                repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setRequestSource("HOSTED_CHECKOUT");
        requestDTO.setIngressClientIp("198.51.100.11");
        requestDTO.setPayerIp("203.0.113.9");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.BLACKLIST_HIT.getCode());
        assertThat(repository.merchantIpWhitelistLookupCount).isZero();
        assertThat(repository.lookups.get(RiskListFunction.BLACK_IP).getRawValue())
                .isEqualTo("203.0.113.9");
    }

    /**
     * 验证卡 BIN 命中 AML 且动作配置为 REVIEW 时进入人工复核。
     */
    @Test
    void shouldReviewWhenAmlCardBinHitWithReviewAction() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.AML_CARD_BIN, match("AML", "cardBin", "CRITICAL", "REVIEW"));
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardBin("411111");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REVIEW.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.AML_HIT.getCode());
        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.messages.get(0).getHits())
                .extracting(RiskAuditHitMessage::getModuleType, RiskAuditHitMessage::getFunctionCode)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("AML", "cardBin"));
        assertThat(publisher.messages.get(0).getHits().get(0).getStageCode()).isEqualTo("AML");
        assertThat(publisher.messages.get(0).getHits().get(0).getMatchResult()).isEqualTo("HIT");
    }

    /**
     * 验证 AML 阶段优先于强白名单执行。
     */
    @Test
    void shouldApplyAmlBeforeStrongWhitelist() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.AML_CARD, match("AML", "card", "CRITICAL", "REJECT"))
                .withListHit(RiskListFunction.WHITE_CARD_NO, match("WHITE", "cardNo", "CRITICAL", "PASS"));
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardNo("4111111111111234");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.AML_HIT.getCode());
    }

    /**
     * 验证 AML 拒绝前已执行节点均写入脱敏决策审计。
     */
    @Test
    void shouldAuditDecisionProcessBeforeAmlReject() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withMerchantIpWhitelistHit(match("SYSTEM", "merchantIpWhitelist", "LOW", "PASS"))
                .withSourceUrlRule(match("RULE", "sourceUrl", "LOW", "PASS"))
                .withListHit(RiskListFunction.AML_CARD, match("AML", "card", "CRITICAL", "REJECT"));
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardNo("4111111111111234");
        requestDTO.setPayerIp("203.0.113.9");
        requestDTO.setSourceUrl("https://checkout.example.test/pay");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.messages.get(0).getHitCount()).isZero();
        assertThat(publisher.messages.get(0).getHits())
                .filteredOn(detail -> !"NONE".equals(detail.getDecisionEffect()))
                .extracting(RiskAuditHitMessage::getStageCode, RiskAuditHitMessage::getMatchResult, RiskAuditHitMessage::getDecisionEffect)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("MERCHANT_IP_WHITELIST", "HIT", "ALLOW"),
                        org.assertj.core.groups.Tuple.tuple("SOURCE_URL_RESTRICTION", "HIT", "ALLOW"),
                        org.assertj.core.groups.Tuple.tuple("AML", "HIT", "BLOCK")
                );
    }

    /**
     * 验证强白名单阶段优先于 A 级黑名单。
     */
    @Test
    void shouldUseStrongWhitelistBeforeBlackA() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.WHITE_CARD_NO, match("WHITE", "cardNo", "CRITICAL", "PASS"))
                .withListHit(RiskListFunction.BLACK_EMAIL, match("BLACK", "email", "CRITICAL", "REJECT"));
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardNo("4111111111111234");
        requestDTO.setBillingEmail("blocked@example.test");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.NONE.getCode());
        assertThat(publisher.messages.get(0).getHits())
                .filteredOn(detail -> !"NONE".equals(detail.getDecisionEffect()))
                .extracting(RiskAuditHitMessage::getModuleType, RiskAuditHitMessage::getFunctionCode, RiskAuditHitMessage::getDecisionEffect)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("WHITE", "cardNo", "ALLOW"),
                        org.assertj.core.groups.Tuple.tuple("BLACK", "email", "BLOCK")
                );
    }

    /**
     * 验证 A 级黑名单优先于优先白名单。
     */
    @Test
    void shouldUseBlackABeforePriorityWhitelist() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.BLACK_CARD_NO, match("BLACK", "cardNo", "CRITICAL", "REJECT"))
                .withListHit(RiskListFunction.WHITE_EMAIL, match("WHITE", "email", "HIGH", "PASS"));
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardNo("4111111111111234");
        requestDTO.setBillingEmail("vip@example.test");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.BLACKLIST_HIT.getCode());
    }

    /**
     * 验证优先白名单优先于 B 级黑名单。
     */
    @Test
    void shouldUsePriorityWhitelistBeforeBlackB() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.WHITE_EMAIL, match("WHITE", "email", "HIGH", "PASS"))
                .withListHit(RiskListFunction.BLACK_CARD_BIN, match("BLACK", "cardBin", "HIGH", "REJECT"));
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardBin("411111");
        requestDTO.setBillingEmail("vip@example.test");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
    }

    /**
     * 验证 B 级黑名单优先于弱白名单。
     */
    @Test
    void shouldUseBlackBBeforeWeakWhitelist() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.BLACK_CARD_BIN, match("BLACK", "cardBin", "HIGH", "REJECT"))
                .withListHit(RiskListFunction.WHITE_IP, match("WHITE", "ip", "LOW", "PASS"));
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardBin("411111");
        requestDTO.setPayerIp("203.0.113.9");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
    }

    /**
     * 验证弱白名单优先于 C 级黑名单。
     */
    @Test
    void shouldUseWeakWhitelistBeforeBlackC() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.WHITE_IP, match("WHITE", "ip", "LOW", "PASS"))
                .withListHit(RiskListFunction.BLACK_EMAIL, match("BLACK", "email", "LOW", "REJECT"));
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setPayerIp("203.0.113.9");
        requestDTO.setBillingEmail("maybe@example.test");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
    }

    /**
     * 验证卡号名单使用完整 PAN 指纹匹配且不保留明文卡号。
     */
    @Test
    void shouldUseFullPanHashForCardNoListMatch() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.BLACK_CARD_NO, match("BLACK", "cardNo", "CRITICAL", "REJECT"));
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardNo("4111 1111 1111 1234");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(repository.lookups.get(RiskListFunction.BLACK_CARD_NO).getRawValue()).isEqualTo("4111111111111234");
        assertThat(repository.lookups.get(RiskListFunction.BLACK_CARD_NO).getMatchValueMasked()).isEqualTo("411111******1234");
        assertThat(repository.lookups.get(RiskListFunction.BLACK_CARD_NO).getMatchValueHash()).hasSize(64);
    }

    /**
     * 验证发卡国家名单使用卡 BIN 解析结果进行匹配。
     */
    @Test
    void shouldMatchIssuerCountryResolvedByCardBin() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withIssuerCountry("USA")
                .withListHit(RiskListFunction.BLACK_ISSUER_COUNTRY, match("BLACK", "issuerCountry", "CRITICAL", "REJECT"));
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardBin("411111");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(repository.lookups.get(RiskListFunction.BLACK_ISSUER_COUNTRY).getCountryAlpha3()).isEqualTo("USA");
    }

    /**
     * 验证商户限额在名单仲裁完成后执行。
     */
    @Test
    void shouldApplyMerchantLimitAfterListArbitration() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.WHITE_IP, match("WHITE", "ip", "LOW", "PASS"))
                .withMerchantLimitRule(match("RULE", "merchantLimit", "HIGH", "REJECT"));
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setPayerIp("203.0.113.9");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.RULE_HIT.getCode());
    }

    /**
     * 最终决策日志必须记录真正导致拒绝的月限额规则，不能记录此前仅产生 ALLOW 效果的来源网址命中。
     */
    @Test
    void shouldLogBlockingRuleInsteadOfEarlierAllowHit() {
        RiskListMatch sourceAllow = match("RULE", "sourceUrl", "LOW", "PASS");
        sourceAllow.setRuleId(4L);
        RiskListMatch monthlyReject = cumulativeLimitMatch("MONTHLY", "HIT", "REJECT");
        monthlyReject.setRuleId(7L);
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withSourceUrlRule(sourceAllow)
                .withCumulativeLimitEvaluation(new MerchantLimitEvaluation(
                        List.of(monthlyReject),
                        List.of()
                ));
        DefaultRiskEvaluationService runtimeService = runtimeService(
                repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("10.00"));
        requestDTO.setSourceUrl("https://merchant.example.test/payment");
        Logger logger = (Logger) LoggerFactory.getLogger(DefaultRiskEvaluationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

            assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .filteredOn(message -> message.contains("event: RISK_EVALUATION_END"))
                    .singleElement()
                    .satisfies(message -> assertThat(message)
                            .contains("hitRuleId: 7", "hitRuleType: RULE:merchantLimit")
                            .doesNotContain("hitRuleId: 4"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    /**
     * 评估开始日志必须保持关键字段可追踪，并且只记录来源 URL 的不可逆摘要。
     */
    @Test
    void shouldLogEvaluationContextWithoutRawSourceUrl() {
        DefaultRiskEvaluationService runtimeService = runtimeService(
                new FakeRiskListRuntimeRepository(), new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("10.00"));
        requestDTO.setTransactionId("TX202611010001");
        requestDTO.setTransactionType("AUTHORIZATION");
        requestDTO.setPaymentMethod("BANK_CARD");
        requestDTO.setIngressClientIp("198.51.100.10");
        requestDTO.setPayerIp("203.0.113.9");
        requestDTO.setSourceUrl("https://merchant.example.test/payment?token=secret");
        Logger logger = (Logger) LoggerFactory.getLogger(DefaultRiskEvaluationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            runtimeService.evaluatePayment(requestDTO);

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .filteredOn(message -> message.contains("event: RISK_EVALUATION_START"))
                    .singleElement()
                    .satisfies(message -> assertThat(message)
                            .contains(
                                    "transactionType: AUTHORIZATION",
                                    "paymentMethod: BANK_CARD",
                                    "amount: 10.00",
                                    "currency: USD",
                                    "ruleCount: 5",
                                    "ingressClientIpHash: " + RedisKeyDigest.sha256("198.51.100.10"),
                                    "payerIpHash: " + RedisKeyDigest.sha256("203.0.113.9"),
                                    "sourceUrlHash: " + RedisKeyDigest.sha256(
                                            "https://merchant.example.test/payment?token=secret"))
                            .doesNotContain("merchant.example.test", "token=secret"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    /**
     * 验证已配置商户限额未命中时仍记录通过检查点。
     */
    @Test
    void shouldAuditPassCheckpointWhenConfiguredMerchantLimitMissed() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withActiveMerchantLimitRule();
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(publisher.messages.get(0).getHitCount()).isZero();
        assertThat(publisher.messages.get(0).getHits())
                .filteredOn(detail -> "MERCHANT_LIMIT".equals(detail.getStageCode()))
                .extracting(RiskAuditHitMessage::getStageCode, RiskAuditHitMessage::getMatchResult, RiskAuditHitMessage::getDecisionAction)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("MERCHANT_LIMIT", "PASS", "PASS"));
    }

    /**
     * 验证频控阶段在商户限额阶段之后执行。
     */
    @Test
    void shouldApplyFrequencyAfterMerchantLimit() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withFrequencyRule(match("RULE", "frequency", "HIGH", "REVIEW"));
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, new RecordingRiskAuditRecordPublisher());

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REVIEW.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.FREQUENCY_LIMIT_HIT.getCode());
    }

    /**
     * 验证最终决策前审计每条已配置频控规则的结果。
     */
    @Test
    void shouldAuditEveryConfiguredFrequencyRuleBeforeFinalDecision() {
        RiskListMatch passed = match("RULE", "frequency", "LOW", "PASS");
        passed.setRuleId(1001L);
        passed.setMatchResult("PASS");
        RiskListMatch rejected = match("RULE", "frequency", "HIGH", "REJECT");
        rejected.setRuleId(1002L);
        rejected.setMatchResult("HIT");
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withFrequencyRules(List.of(passed, rejected));
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.messages.get(0).getHitCount()).isZero();
        assertThat(publisher.messages.get(0).getHits())
                .filteredOn(detail -> "FREQUENCY_LIMIT".equals(detail.getStageCode()))
                .extracting(RiskAuditHitMessage::getRuleId,
                        RiskAuditHitMessage::getStageOrder,
                        RiskAuditHitMessage::getMatchResult,
                        RiskAuditHitMessage::getDecisionEffect)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1001L, 60, "PASS", "ALLOW"),
                        org.assertj.core.groups.Tuple.tuple(1002L, 60, "HIT", "BLOCK")
                );
    }

    /**
     * 验证黑白名单审计明细按配置的仲裁优先级排序。
     */
    @Test
    void shouldOrderBlackAndWhiteDetailsByConfiguredArbitrationPriority() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.WHITE_CARD_NO,
                        match("WHITE", "cardNo", "HIGH", "PASS"))
                .withListHit(RiskListFunction.WHITE_IP,
                        match("WHITE", "ip", "LOW", "PASS"))
                .withListHit(RiskListFunction.BLACK_CARD_NO,
                        match("BLACK", "cardNo", "CRITICAL", "REJECT"))
                .withListHit(RiskListFunction.BLACK_IP,
                        match("BLACK", "ip", "HIGH", "REJECT"))
                .withListHit(RiskListFunction.BLACK_EMAIL,
                        match("BLACK", "email", "LOW", "REJECT"));
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardNo("4111111111111234");
        requestDTO.setPayerIp("203.0.113.9");
        requestDTO.setBillingEmail("buyer@example.test");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(publisher.messages.get(0).getHits())
                .filteredOn(detail -> "BLACK_WHITE_ARBITRATION".equals(detail.getStageCode())
                        && "HIT".equals(detail.getMatchResult()))
                .extracting(RiskAuditHitMessage::getModuleType,
                        RiskAuditHitMessage::getRiskLevel,
                        RiskAuditHitMessage::getStageOrder)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("BLACK", "CRITICAL", 42),
                        org.assertj.core.groups.Tuple.tuple("WHITE", "HIGH", 43),
                        org.assertj.core.groups.Tuple.tuple("BLACK", "HIGH", 44),
                        org.assertj.core.groups.Tuple.tuple("WHITE", "LOW", 45),
                        org.assertj.core.groups.Tuple.tuple("BLACK", "LOW", 46)
                );
    }

    /**
     * 验证每条累计商户限额规则均产生独立审计检查点。
     */
    @Test
    void shouldAuditEveryCumulativeMerchantLimitCheckpoint() {
        RiskListMatch daily = cumulativeLimitMatch("DAILY", "PASS", "PASS");
        RiskListMatch weekly = cumulativeLimitMatch("WEEKLY", "PASS", "PASS");
        RiskListMatch monthly = cumulativeLimitMatch("MONTHLY", "PASS", "PASS");
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withCumulativeLimitEvaluation(new MerchantLimitEvaluation(
                        List.of(daily, weekly, monthly),
                        List.of(new MerchantLimitReservation("aggregate", "reservation"))
                ));
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(repository.merchantLimitRollbackCount).isZero();
        assertThat(publisher.messages.get(0).getHits())
                .filteredOn(detail -> "MERCHANT_LIMIT".equals(detail.getStageCode()))
                .extracting(RiskAuditHitMessage::getStageCode, RiskAuditHitMessage::getHitElement, RiskAuditHitMessage::getMatchResult)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("MERCHANT_LIMIT", "DAILY", "PASS"),
                        org.assertj.core.groups.Tuple.tuple("MERCHANT_LIMIT", "WEEKLY", "PASS"),
                        org.assertj.core.groups.Tuple.tuple("MERCHANT_LIMIT", "MONTHLY", "PASS")
                );
    }

    /**
     * 验证累计商户限额超限时拒绝交易。
     */
    @Test
    void shouldRejectWhenCumulativeMerchantLimitIsExceeded() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withCumulativeLimitEvaluation(new MerchantLimitEvaluation(
                        List.of(cumulativeLimitMatch("MONTHLY", "HIT", "REJECT")),
                        List.of()
                ));
        DefaultRiskEvaluationService runtimeService = runtimeService(
                repository, new RecordingRiskAuditRecordPublisher());

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.RULE_HIT.getCode());
    }

    /**
     * 验证累计限额运行时不可用时进入 REVIEW 而不放行。
     */
    @Test
    void shouldReviewWhenCumulativeMerchantLimitRuntimeFails() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withCumulativeLimitEvaluation(new MerchantLimitEvaluation(
                        List.of(cumulativeLimitMatch("DAILY", "ERROR", "REVIEW")),
                        List.of()
                ));
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REVIEW.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.RULE_HIT.getCode());
        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.messages.get(0).getHitCount()).isZero();
        assertThat(publisher.messages.get(0).getHits())
                .filteredOn(detail -> "MERCHANT_LIMIT".equals(detail.getStageCode()))
                .extracting(RiskAuditHitMessage::getStageCode,
                        RiskAuditHitMessage::getMatchResult,
                        RiskAuditHitMessage::getDecisionEffect)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "MERCHANT_LIMIT", "ERROR", "REVIEW"));
    }

    /**
     * 验证后续频控拒绝时回滚此前创建的累计限额预占。
     */
    @Test
    void shouldRollbackCumulativeReservationWhenFrequencyRejects() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withCumulativeLimitEvaluation(new MerchantLimitEvaluation(
                        List.of(cumulativeLimitMatch("DAILY", "PASS", "PASS")),
                        List.of(new MerchantLimitReservation("aggregate", "reservation"))
                ))
                .withFrequencyRule(match("RULE", "frequency", "HIGH", "REJECT"));
        DefaultRiskEvaluationService runtimeService = runtimeService(
                repository, new RecordingRiskAuditRecordPublisher());

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(repository.merchantLimitRollbackCount).isEqualTo(1);
        assertThat(repository.frequencySuccessReleaseCount).isEqualTo(1);
    }

    /**
     * 验证已配置频控未命中时仍记录通过检查点。
     */
    @Test
    void shouldAuditPassCheckpointWhenConfiguredFrequencyMissed() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withActiveFrequencyRule();
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(publisher.messages.get(0).getHitCount()).isZero();
        assertThat(publisher.messages.get(0).getHits())
                .filteredOn(detail -> "FREQUENCY_LIMIT".equals(detail.getStageCode()))
                .extracting(RiskAuditHitMessage::getStageCode, RiskAuditHitMessage::getMatchResult, RiskAuditHitMessage::getDecisionAction)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FREQUENCY_LIMIT", "PASS", "PASS"));
    }

    /**
     * 验证已配置但未命中的名单检查仍保留审计明细。
     */
    @Test
    void shouldAuditConfiguredListChecksThatDoNotMatch() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withActiveListRule(RiskListFunction.AML_CARD)
                .withActiveListRule(RiskListFunction.WHITE_CARD_NO)
                .withActiveListRule(RiskListFunction.BLACK_CARD_NO);
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardNo("4111111111111234");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.messages.get(0).getHitCount()).isZero();
        assertThat(publisher.messages.get(0).getHits())
                .filteredOn(detail -> "MISS".equals(detail.getMatchResult()))
                .extracting(RiskAuditHitMessage::getModuleType,
                        RiskAuditHitMessage::getFunctionCode,
                        RiskAuditHitMessage::getMatchResult,
                        RiskAuditHitMessage::getDecisionAction)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AML", "card", "MISS", "SKIP"),
                        org.assertj.core.groups.Tuple.tuple("WHITE", "cardNo", "MISS", "SKIP"),
                        org.assertj.core.groups.Tuple.tuple("BLACK", "cardNo", "MISS", "SKIP")
                );
    }

    /**
     * 验证来源地址规则命中后执行规则配置的决策动作。
     */
    @Test
    void shouldApplySourceUrlRuleAction() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withSourceUrlRule(match("RULE", "sourceUrl", "MEDIUM", "REVIEW"));
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setSourceUrl("https://checkout.example.test/pay?token=secret");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REVIEW.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.RULE_HIT.getCode());
    }

    /**
     * 验证运行时规则要求 3DS 且请求无认证证明时返回 REQUIRE_3DS。
     */
    @Test
    void shouldRequireThreeDsWhenRuntimeRuleForcesThreeDsWithoutProof() {
        RiskListMatch frequencyPass = match("RULE", "frequency", "LOW", "PASS");
        frequencyPass.setMatchResult("PASS");
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withFrequencyRule(frequencyPass)
                .withThreeDsRule(match("RULE", "threeDs", "MEDIUM", "REQUIRE_3DS"));
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, new RecordingRiskAuditRecordPublisher());
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setPaymentMethod("BANK_CARD");
        requestDTO.setCardBrand("VISA");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REQUIRE_3DS.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.THREE_DS_REQUIRED.getCode());
        assertThat(repository.frequencySuccessReleaseCount).isEqualTo(1);
    }

    /**
     * 验证每个已配置名单维度恰好记录一次评估明细。
     */
    @Test
    void shouldAuditEveryConfiguredListEvaluationDimensionExactlyOnce() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withAllActiveListRules()
                .withIssuerCountry("CAN");
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = fullyPopulatedRequest();

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
        assertThat(publisher.messages).singleElement().satisfies(message -> {
            assertThat(message.getHitCount()).isZero();
            assertThat(message.getHits().stream()
                    .map(detail -> detail.getModuleType() + ":" + detail.getFunctionCode() + ":" + detail.getHitElement())
                    .distinct()
                    .count()).isEqualTo(message.getHits().size());
            assertThat(EnumSet.allOf(RiskListFunction.class)).allSatisfy(function ->
                    assertThat(message.getHits()).anySatisfy(detail -> {
                        assertThat(detail.getModuleType()).isEqualTo(function.getModuleType().getCode());
                        assertThat(detail.getFunctionCode()).isEqualTo(function.getFunctionCode());
                    }));
            assertThat(message.getHits())
                    .filteredOn(detail -> "MISS".equals(detail.getMatchResult())
                            || "SKIPPED".equals(detail.getMatchResult()))
                    .allSatisfy(detail -> assertThat(detail.getDecisionAction())
                            .isEqualTo(RiskDecisionEnum.SKIP.getCode()));
        });
    }

    /**
     * 验证 AML 国家维度审计相互独立且不修改缓存规则对象。
     */
    @Test
    void shouldKeepAmlCountryAuditDimensionsSeparateWithoutMutatingCachedRule() {
        RiskListMatch cachedRule = match("AML", "country", "CRITICAL", "REJECT");
        cachedRule.setHitElement("country");
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withListHit(RiskListFunction.AML_COUNTRY, cachedRule)
                .withIssuerCountry("CAN");
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setBillingCountry("USA");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(publisher.messages.get(0).getHitCount()).isZero();
        assertThat(publisher.messages.get(0).getHits())
                .filteredOn(detail -> "HIT".equals(detail.getMatchResult()))
                .extracting(RiskAuditHitMessage::getHitElement, RiskAuditHitMessage::getHitValueMasked)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("issuerCountry", "CAN"),
                        org.assertj.core.groups.Tuple.tuple("billingCountry", "USA")
                );
        assertThat(cachedRule.getHitElement()).isEqualTo("country");
        assertThat(cachedRule.getStageCode()).isNull();
    }

    /**
     * 验证账单、付款人和收货地区作为独立风险维度评估。
     */
    @Test
    void shouldEvaluateBillingPayerAndShippingAsIndependentRiskDimensions() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withAllActiveListRules()
                .withIssuerCountry("CAN");
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = fullyPopulatedRequest();
        requestDTO.setPayerId("PAYER-001");
        requestDTO.setPayerName("Payer Alice");
        requestDTO.setPayerEmail("payer@example.test");
        requestDTO.setPayerPhone("+15550101");
        requestDTO.setPayerCountry("GBR");
        requestDTO.setPayerAddress("3 Payer Street");
        requestDTO.setPayerZip("SW1A1AA");
        requestDTO.setPayerRegion("London");
        requestDTO.setPayerCity("London");
        requestDTO.setShippingName("Receiver Bob");
        requestDTO.setShippingEmail("receiver@example.test");
        requestDTO.setShippingPhone("+15550102");
        requestDTO.setShippingRegion("CA");
        requestDTO.setShippingCity("Los Angeles");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.SKIP.getCode());
        assertThat(publisher.messages).singleElement().satisfies(message ->
                assertThat(message.getHits())
                        .extracting(RiskAuditHitMessage::getHitElement)
                        .contains(
                                "billingEmail", "payerEmail", "shippingEmail",
                                "billingPhone", "payerPhone", "shippingPhone",
                                "billingName", "payerName", "shippingName",
                                "billingAddress", "payerAddress", "shippingAddress",
                                "billingCountry", "payerCountry", "shippingCountry"));
    }

    /**
     * 构造覆盖名单、来源、地区和 3DS 字段的完整风控请求。
     */
    private RiskPaymentEvaluateRequestDTO fullyPopulatedRequest() {
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setCardNo("4111111111111234");
        requestDTO.setCardBin("411111");
        requestDTO.setPayerIp("203.0.113.9");
        requestDTO.setSourceUrl("https://checkout.example.test/pay");
        requestDTO.setBillingEmail("buyer@example.test");
        requestDTO.setBillingPhone("+15550100");
        requestDTO.setBillingCountry("USA");
        requestDTO.setBillingAddress("1 Billing Street");
        requestDTO.setBillingZip("10001");
        requestDTO.setBillingRegion("NY");
        requestDTO.setCardholderName("John Smith");
        requestDTO.setLegalPerson("Jane Owner");
        requestDTO.setEnterprise("Example Trading Limited");
        requestDTO.setMerchantBillingAddress("100 Merchant Street");
        requestDTO.setShippingAddress("2 Shipping Street");
        requestDTO.setShippingZip("10003");
        requestDTO.setShippingCountry("USA");
        requestDTO.setCustomerId("CUSTOMER-001");
        requestDTO.setDeviceFingerprint("DEVICE-FP-001");
        return requestDTO;
    }

    /**
     * 构造包含商户、订单、金额和币种的最小风控请求。
     */
    private RiskPaymentEvaluateRequestDTO baseRequest(BigDecimal amount) {
        RiskPaymentEvaluateRequestDTO requestDTO = new RiskPaymentEvaluateRequestDTO();
        requestDTO.setMerchantId("200001");
        requestDTO.setMerchantOrderNo("M202607120001");
        requestDTO.setAmount(amount);
        requestDTO.setCurrency("USD");
        return requestDTO;
    }

    /**
     * 使用同步执行器和测试审计发布器构造实时风控服务。
     */
    private DefaultRiskEvaluationService runtimeService(FakeRiskListRuntimeRepository repository,
                                                        RecordingRiskAuditRecordPublisher publisher) {
        return new DefaultRiskEvaluationService(repository, publisher, runtimeProperties());
    }

    /**
     * 构造启用运行时规则且使用安全默认值的风控配置。
     */
    private RiskEvaluationProperties runtimeProperties() {
        RiskEvaluationProperties properties = new RiskEvaluationProperties();
        properties.setAuditMqEnabled(true);
        properties.setReadOnlyParallelEnabled(false);
        return properties;
    }

    /**
     * 构造具有指定阶段、字段、风险等级和动作的规则命中结果。
     */
    private RiskListMatch match(String moduleType, String functionCode, String riskLevel, String decisionAction) {
        RiskListMatch match = new RiskListMatch();
        match.setRuleId(1001L);
        match.setModuleType(moduleType);
        match.setFunctionCode(functionCode);
        match.setFunctionName(functionCode);
        match.setHitElement(functionCode);
        match.setHitValueMasked("masked");
        match.setRiskLevel(riskLevel);
        match.setDecisionAction(decisionAction);
        match.setDecisionReason(functionCode + " hit");
        return match;
    }

    /**
     * 构造具有周期阈值的累计限额规则命中结果。
     */
    private RiskListMatch cumulativeLimitMatch(String limitType,
                                               String matchResult,
                                               String decisionAction) {
        RiskListMatch match = match("RULE", "merchantLimit", "HIGH", decisionAction);
        match.setHitElement(limitType);
        match.setMatchResult(matchResult);
        match.setDecisionReason(limitType + " cumulative amount evaluated");
        return match;
    }

    /**
     * 提供可编排规则命中、预占和审计路径的内存风控仓储。
     */
    private static class FakeRiskListRuntimeRepository implements RiskListRuntimeRepository {

        /**
         * 按名单功能预置的测试命中结果。
         */
        private final Map<RiskListFunction, RiskListMatch> listHits = new EnumMap<>(RiskListFunction.class);

        /**
         * 记录服务传给仓储的规范化查询值，供脱敏和路由断言使用。
         */
        private final Map<RiskListFunction, RiskRuntimeLookupValue> lookups = new ConcurrentHashMap<>();

        /**
         * 记录只读规则查询实际使用的线程，验证并发边界不扩展到有副作用阶段。
         */
        private final java.util.Set<String> readOnlyThreadNames = ConcurrentHashMap.newKeySet();

        /**
         * 在测试场景中声明已启用的名单节点。
         */
        private final EnumSet<RiskListFunction> activeListRules = EnumSet.noneOf(RiskListFunction.class);

        /**
         * 记录每个名单功能启用状态的实际读取次数。
         */
        private final Map<RiskListFunction, Integer> activeListRuleQueryCounts =
                new ConcurrentHashMap<>();

        /**
         * 在测试场景中声明商户已开通但可以尚未配置规则的名单功能。
         */
        private final EnumSet<RiskListFunction> enabledMerchantFunctions = EnumSet.noneOf(RiskListFunction.class);

        /**
         * 预置的商户 IP 白名单命中结果。
         */
        private RiskListMatch merchantIpWhitelistHit;

        /**
         * 预置的商户 IP 白名单未命中结果。
         */
        private RiskListMatch merchantIpWhitelistMiss;

        /**
         * 记录商户接入白名单实际使用的查询值。
         */
        private RiskRuntimeLookupValue merchantIpWhitelistLookup;

        /**
         * 记录商户接入白名单查询次数，Hosted Checkout 必须保持为零。
         */
        private int merchantIpWhitelistLookupCount;

        /**
         * 预置的来源网址限制未命中结果。
         */
        private RiskListMatch sourceUrlMiss;

        /**
         * 预置的来源网址允许规则命中结果。
         */
        private RiskListMatch sourceUrlRule;

        /**
         * 记录来源网址组合查询调用次数。
         */
        private int sourceUrlEvaluationCount;

        /**
         * 预置的商户单笔限额命中结果。
         */
        private RiskListMatch merchantLimitRule;

        /**
         * 测试场景是否声明存在启用金额限额规则。
         */
        private boolean activeMerchantLimitRule;

        /**
         * 记录单笔限额只读查询次数。
         */
        private int merchantLimitQueryCount;

        /**
         * 记录商户限额组合只读查询调用次数。
         */
        private int merchantLimitReadEvaluationCount;

        /**
         * 记录累计限额预占调用次数。
         */
        private int cumulativeReservationCount;

        /**
         * 记录累计限额预占所在的线程。
         */
        private String cumulativeLimitThreadName;

        /**
         * 预置的累计限额明细和 Redis 预占结果。
         */
        private MerchantLimitEvaluation cumulativeLimitEvaluation = MerchantLimitEvaluation.empty();

        /**
         * 记录服务触发累计限额回滚的次数。
         */
        private int merchantLimitRollbackCount;

        /**
         * 按仓储执行顺序预置的频率规则明细。
         */
        private List<RiskListMatch> frequencyRules = List.of();

        /**
         * 测试场景是否声明存在启用频率规则。
         */
        private boolean activeFrequencyRule;

        /**
         * 记录后续节点阻断时释放频控成功名额的次数。
         */
        private int frequencySuccessReleaseCount;

        /**
         * 记录频控评估调用次数。
         */
        private int frequencyEvaluationCount;

        /**
         * 记录携带启用状态的频控组合执行调用次数。
         */
        private int frequencyEvaluationWithStatusCount;

        /**
         * 记录频控评估所在的线程。
         */
        private String frequencyThreadName;

        /**
         * 预置的 3DS 规则命中结果。
         */
        private RiskListMatch threeDsRule;

        /**
         * 按卡 BIN 预置的发卡行国家代码。
         */
        private String issuerCountry;

        /**
         * 记录 3DS 查询所在的线程。
         */
        private String threeDsThreadName;

        /**
         * 为指定名单功能登记测试命中结果。
         */
        private FakeRiskListRuntimeRepository withListHit(RiskListFunction function, RiskListMatch match) {
            listHits.put(function, match);
            activeListRules.add(function);
            return this;
        }

        /**
         * 登记已生效但默认不命中的名单规则。
         */
        private FakeRiskListRuntimeRepository withActiveListRule(RiskListFunction function) {
            activeListRules.add(function);
            return this;
        }

        /**
         * 登记全部名单功能为已生效状态。
         */
        private FakeRiskListRuntimeRepository withAllActiveListRules() {
            activeListRules.addAll(EnumSet.allOf(RiskListFunction.class));
            return this;
        }

        /**
         * 登记商户已启用的风控功能。
         */
        private FakeRiskListRuntimeRepository withEnabledMerchantFunction(RiskListFunction function) {
            enabledMerchantFunctions.add(function);
            return this;
        }

        /**
         * 登记来源地址规则的命中结果。
         */
        private FakeRiskListRuntimeRepository withSourceUrlRule(RiskListMatch match) {
            sourceUrlRule = match;
            return this;
        }

        /**
         * 登记来源地址规则未命中且返回指定决策。
         */
        private FakeRiskListRuntimeRepository withSourceUrlMiss(RiskListMatch match) {
            sourceUrlMiss = match;
            return this;
        }

        /**
         * 登记商户 IP 白名单未命中结果。
         */
        private FakeRiskListRuntimeRepository withMerchantIpWhitelistMiss(RiskListMatch match) {
            merchantIpWhitelistMiss = match;
            return this;
        }

        /**
         * 登记商户 IP 白名单命中结果。
         */
        @SuppressWarnings("unused")
        private FakeRiskListRuntimeRepository withMerchantIpWhitelistHit(RiskListMatch match) {
            merchantIpWhitelistHit = match;
            return this;
        }

        /**
         * 登记商户单笔限额规则命中结果。
         */
        private FakeRiskListRuntimeRepository withMerchantLimitRule(RiskListMatch match) {
            merchantLimitRule = match;
            activeMerchantLimitRule = true;
            return this;
        }

        /**
         * 登记已生效但默认不命中的商户限额规则。
         */
        private FakeRiskListRuntimeRepository withActiveMerchantLimitRule() {
            activeMerchantLimitRule = true;
            return this;
        }

        /**
         * 登记累计限额评估结果及其预占信息。
         */
        private FakeRiskListRuntimeRepository withCumulativeLimitEvaluation(MerchantLimitEvaluation evaluation) {
            cumulativeLimitEvaluation = evaluation;
            activeMerchantLimitRule = evaluation != null && !evaluation.details().isEmpty();
            return this;
        }

        /**
         * 登记单条频控规则评估结果。
         */
        private FakeRiskListRuntimeRepository withFrequencyRule(RiskListMatch match) {
            frequencyRules = match == null ? List.of() : List.of(match);
            activeFrequencyRule = true;
            return this;
        }

        /**
         * 登记一组按顺序评估的频控规则结果。
         */
        private FakeRiskListRuntimeRepository withFrequencyRules(List<RiskListMatch> matches) {
            frequencyRules = matches == null ? List.of() : List.copyOf(matches);
            activeFrequencyRule = !frequencyRules.isEmpty();
            return this;
        }

        /**
         * 登记已生效但默认不命中的频控规则。
         */
        private FakeRiskListRuntimeRepository withActiveFrequencyRule() {
            activeFrequencyRule = true;
            return this;
        }

        /**
         * 登记 3DS 规则命中结果。
         */
        private FakeRiskListRuntimeRepository withThreeDsRule(RiskListMatch match) {
            threeDsRule = match;
            return this;
        }

        /**
         * 登记卡 BIN 对应的发卡国家解析结果。
         */
        private FakeRiskListRuntimeRepository withIssuerCountry(String country) {
            issuerCountry = country;
            return this;
        }

        /**
         * 按名单功能返回预先登记的测试命中结果。
         */
        @Override
        public Optional<RiskListMatch> findListMatch(RiskListFunction function,
                                                     String merchantId,
                                                     RiskRuntimeLookupValue lookupValue) {
            readOnlyThreadNames.add(Thread.currentThread().getName());
            if (lookupValue != null) {
                lookups.put(function, lookupValue);
            }
            return Optional.ofNullable(listHits.get(function));
        }

        /**
         * 返回测试场景是否将该名单功能标记为启用。
         */
        @Override
        public boolean hasActiveListRule(RiskListFunction function, String merchantId) {
            activeListRuleQueryCounts.merge(function, 1, Integer::sum);
            return activeListRules.contains(function);
        }

        /**
         * 判断测试商户是否启用指定风控功能。
         */
        @Override
        public boolean isMerchantFunctionEnabled(RiskListFunction function, String merchantId) {
            return enabledMerchantFunctions.contains(function);
        }

        /**
         * 返回测试商户已登记的全部风控功能。
         */
        @Override
        public java.util.Set<RiskListFunction> findEnabledMerchantFunctions(String merchantId) {
            return java.util.Set.copyOf(enabledMerchantFunctions);
        }

        /**
         * 返回预置的来源网址允许规则。
         */
        @Override
        public Optional<RiskListMatch> findSourceUrlRule(String merchantId, RiskRuntimeLookupValue lookupValue) {
            return Optional.ofNullable(sourceUrlRule);
        }

        /**
         * 返回预置的来源网址限制未命中结果。
         */
        @Override
        public Optional<RiskListMatch> findSourceUrlRestrictionMiss(String merchantId, RiskRuntimeLookupValue lookupValue) {
            return Optional.ofNullable(sourceUrlMiss);
        }

        /**
         * 记录并返回来源网址允许与限制未命中的组合结果。
         */
        @Override
        public SourceUrlRuleEvaluation evaluateSourceUrlRules(String merchantId,
                                                              RiskRuntimeLookupValue lookupValue) {
            sourceUrlEvaluationCount++;
            return RiskListRuntimeRepository.super.evaluateSourceUrlRules(merchantId, lookupValue);
        }

        /**
         * 返回预置的商户 IP 白名单命中结果。
         */
        @Override
        public Optional<RiskListMatch> findMerchantIpWhitelistHit(String merchantId, RiskRuntimeLookupValue lookupValue) {
            merchantIpWhitelistLookup = lookupValue;
            merchantIpWhitelistLookupCount++;
            return Optional.ofNullable(merchantIpWhitelistHit);
        }

        /**
         * 返回预置的商户 IP 白名单未命中结果。
         */
        @Override
        public Optional<RiskListMatch> findMerchantIpWhitelistMiss(String merchantId, RiskRuntimeLookupValue lookupValue) {
            merchantIpWhitelistLookup = lookupValue;
            merchantIpWhitelistLookupCount++;
            return Optional.ofNullable(merchantIpWhitelistMiss);
        }

        /**
         * 返回预置的商户单笔限额规则。
         */
        @Override
        public Optional<RiskListMatch> findMerchantLimitRule(String merchantId, BigDecimal amount, String currency) {
            merchantLimitQueryCount++;
            readOnlyThreadNames.add(Thread.currentThread().getName());
            return Optional.ofNullable(merchantLimitRule);
        }

        /**
         * 记录并返回单笔限额与规则启用状态的组合结果。
         */
        @Override
        public MerchantLimitReadEvaluation evaluateMerchantLimitRules(String merchantId,
                                                                      BigDecimal amount,
                                                                      String currency) {
            merchantLimitReadEvaluationCount++;
            return RiskListRuntimeRepository.super.evaluateMerchantLimitRules(merchantId, amount, currency);
        }

        /**
         * 返回测试场景预置的累计限额评估。
         */
        @Override
        public MerchantLimitEvaluation reserveCumulativeMerchantLimits(RiskPaymentEvaluateRequestDTO requestDTO) {
            cumulativeReservationCount++;
            cumulativeLimitThreadName = Thread.currentThread().getName();
            return cumulativeLimitEvaluation;
        }

        /**
         * 记录服务是否在后续节点阻断时请求回滚累计限额。
         */
        @Override
        public void rollbackMerchantLimitReservations(MerchantLimitEvaluation evaluation) {
            merchantLimitRollbackCount++;
        }

        /**
         * 返回测试场景是否声明启用金额限额规则。
         */
        @Override
        public boolean hasActiveMerchantLimitRule(String merchantId, String currency) {
            return activeMerchantLimitRule;
        }

        /**
         * 从预置频率明细中返回首条 HIT、ERROR 或旧格式无结果明细。
         */
        @Override
        public Optional<RiskListMatch> findFrequencyRuleHit(String merchantId,
                                                            RiskPaymentEvaluateRequestDTO requestDTO,
                                                            RiskRuntimeLookupValue cardNoLookup,
                                                            RiskRuntimeLookupValue cardFingerprintLookup,
                                                            RiskRuntimeLookupValue ipLookup,
                                                            RiskRuntimeLookupValue emailLookup,
                                                            RiskRuntimeLookupValue phoneLookup,
                                                            RiskRuntimeLookupValue customerIdLookup,
                                                            RiskRuntimeLookupValue deviceFingerprintLookup) {
            return frequencyRules.stream()
                    .filter(match -> "HIT".equalsIgnoreCase(match.getMatchResult())
                            || "ERROR".equalsIgnoreCase(match.getMatchResult())
                            || match.getMatchResult() == null)
                    .findFirst();
        }

        /**
         * 按预置顺序返回全部频率规则明细。
         */
        @Override
        public List<RiskListMatch> evaluateFrequencyRules(String merchantId,
                                                         RiskPaymentEvaluateRequestDTO requestDTO,
                                                         RiskRuntimeLookupValue cardNoLookup,
                                                         RiskRuntimeLookupValue cardFingerprintLookup,
                                                         RiskRuntimeLookupValue ipLookup,
                                                         RiskRuntimeLookupValue emailLookup,
                                                         RiskRuntimeLookupValue phoneLookup,
                                                         RiskRuntimeLookupValue customerIdLookup,
                                                         RiskRuntimeLookupValue deviceFingerprintLookup) {
            frequencyEvaluationCount++;
            frequencyThreadName = Thread.currentThread().getName();
            return frequencyRules;
        }

        /**
         * 记录并返回频控执行明细与规则启用状态的组合结果。
         */
        @Override
        public FrequencyRuleEvaluation evaluateFrequencyRulesWithStatus(
                String merchantId,
                RiskPaymentEvaluateRequestDTO requestDTO,
                RiskRuntimeLookupValue cardNoLookup,
                RiskRuntimeLookupValue cardFingerprintLookup,
                RiskRuntimeLookupValue ipLookup,
                RiskRuntimeLookupValue emailLookup,
                RiskRuntimeLookupValue phoneLookup,
                RiskRuntimeLookupValue customerIdLookup,
                RiskRuntimeLookupValue deviceFingerprintLookup) {
            frequencyEvaluationWithStatusCount++;
            return RiskListRuntimeRepository.super.evaluateFrequencyRulesWithStatus(
                    merchantId,
                    requestDTO,
                    cardNoLookup,
                    cardFingerprintLookup,
                    ipLookup,
                    emailLookup,
                    phoneLookup,
                    customerIdLookup,
                    deviceFingerprintLookup);
        }

        /**
         * 记录服务是否在当前评估未放行时释放成功次数预占。
         */
        @Override
        public void releaseFrequencySuccessReservations(String merchantId, String transactionId) {
            frequencySuccessReleaseCount++;
        }

        /**
         * 返回测试场景是否声明启用频率规则。
         */
        @Override
        public boolean hasActiveFrequencyRule(String merchantId) {
            return activeFrequencyRule;
        }

        /**
         * 返回按卡 BIN 预置的发卡行国家代码。
         */
        @Override
        public Optional<String> findIssuerCountryByCardBin(RiskRuntimeLookupValue cardBinLookup) {
            return Optional.ofNullable(issuerCountry);
        }

        /**
         * 返回预置的 3DS 规则。
         */
        @Override
        public Optional<RiskListMatch> findThreeDsRule(String merchantId,
                                                       String channelCode,
                                                       String paymentMethod,
                                                       String cardBrand,
                                                       BigDecimal amount,
                                                       String currency,
                                                       String currentRiskLevel) {
            threeDsThreadName = Thread.currentThread().getName();
            return Optional.ofNullable(threeDsRule);
        }
    }

    /**
     * 模拟商户限额只读查询超时的风控仓储。
     */
    private static final class SlowMerchantLimitRiskListRuntimeRepository
            extends FakeRiskListRuntimeRepository {

        /**
         * 模拟单笔限额查询超过只读阶段共享超时。
         */
        @Override
        public Optional<RiskListMatch> findMerchantLimitRule(String merchantId,
                                                             BigDecimal amount,
                                                             String currency) {
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    /**
     * 模拟商户限额只读查询抛出异常的风控仓储。
     */
    private static final class ThrowingMerchantLimitRiskListRuntimeRepository
            extends FakeRiskListRuntimeRepository {

        /**
         * 模拟单笔限额查询发生基础设施异常。
         */
        @Override
        public Optional<RiskListMatch> findMerchantLimitRule(String merchantId,
                                                             BigDecimal amount,
                                                             String currency) {
            throw new IllegalStateException("simulated merchant limit query failure");
        }
    }

    /**
     * 使用并发屏障验证三个只读规则组同时执行的风控仓储。
     */
    private static final class ParallelProbeRiskListRuntimeRepository extends FakeRiskListRuntimeRepository {

        /**
         * 三个只读规则组必须在超时前同时到达的测试屏障。
         */
        private final CyclicBarrier readOnlyGroupBarrier = new CyclicBarrier(3);

        /**
         * 记录三个规则组是否完成并发会合。
         */
        private volatile boolean concurrentGroupsReached;

        /**
         * 在 AML 和白名单组的首个查询处等待其他只读组。
         */
        @Override
        public Optional<RiskListMatch> findListMatch(RiskListFunction function,
                                                     String merchantId,
                                                     RiskRuntimeLookupValue lookupValue) {
            if (function == RiskListFunction.AML_CARD || function == RiskListFunction.WHITE_MERCHANT) {
                awaitReadOnlyGroups();
            }
            return super.findListMatch(function, merchantId, lookupValue);
        }

        /**
         * 在单笔限额组首个查询处等待 AML 和黑白名单组。
         */
        @Override
        public Optional<RiskListMatch> findMerchantLimitRule(String merchantId,
                                                             BigDecimal amount,
                                                             String currency) {
            awaitReadOnlyGroups();
            return super.findMerchantLimitRule(merchantId, amount, currency);
        }

        /**
         * 等待三个只读规则组到达并发屏障，并记录超时或中断。
         */
        private void awaitReadOnlyGroups() {
            try {
                readOnlyGroupBarrier.await(500, TimeUnit.MILLISECONDS);
                concurrentGroupsReached = true;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("read-only risk group probe was interrupted", exception);
            } catch (BrokenBarrierException | TimeoutException exception) {
                throw new IllegalStateException("read-only risk groups did not execute concurrently", exception);
            }
        }

        /**
         * 返回三个只读规则组是否在超时前同时到达屏障。
         */
        private boolean concurrentGroupsReached() {
            return concurrentGroupsReached;
        }
    }

    /**
     * 在内存中收集风控审计消息供顺序和脱敏断言。
     */
    private static final class RecordingRiskAuditRecordPublisher implements RiskAuditRecordPublisher {

        /**
         * 收集服务发布的审计消息，供字段和顺序断言使用。
         */
        private final List<RiskEvaluationAuditMessage> messages = new ArrayList<>();

        /**
         * 将审计消息追加到内存列表，不执行外部 MQ 或数据库写入。
         */
        @Override
        public void publish(RiskEvaluationAuditMessage message) {
            messages.add(message);
        }
    }
}
