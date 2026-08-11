package com.scott.payment.risk.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.mq.message.RiskAuditHitMessage;
import com.scott.payment.component.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.domain.MerchantLimitEvaluation;
import com.scott.payment.risk.domain.MerchantLimitReservation;
import com.scott.payment.risk.domain.RiskListFunction;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.RiskRuntimeLookupValue;
import com.scott.payment.risk.domain.state.RiskDecisionEnum;
import com.scott.payment.risk.domain.state.RiskReasonCodeEnum;
import com.scott.payment.risk.repository.RiskAuditRecordPublisher;
import com.scott.payment.risk.repository.RiskListRuntimeRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRiskEvaluationServiceTests
 * @date : 2026-07-12 22:43
 * @email : scott_x@163.com
 * @description : Default Risk Evaluation Service Tests 自动化测试类，位于 风控服务，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
class DefaultRiskEvaluationServiceTests {

    /**
     * service 依赖，用于 Default Risk Evaluation Service Tests 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final DefaultRiskEvaluationService service = new DefaultRiskEvaluationService();

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

            assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
            assertThat(repository.concurrentGroupsReached()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

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

            assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
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

    @Test
    void shouldPassNormalPaymentRiskEvaluation() {
        RiskPaymentEvaluateResultDTO resultDTO = service.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.NONE.getCode());
        assertThat(resultDTO.getRiskRecordNo()).startsWith("RK");
    }

    @Test
    void shouldRejectBlockedSource() {
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setSourceUrl("https://blocked.example.test/pay");

        RiskPaymentEvaluateResultDTO resultDTO = service.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.BLOCKED_SOURCE.getCode());
    }

    @Test
    void shouldRequireThreeDsForLargePaymentWithoutThreeDsProof() {
        RiskPaymentEvaluateResultDTO resultDTO = service.evaluatePayment(baseRequest(new BigDecimal("1000.00")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REQUIRE_3DS.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.THREE_DS_REQUIRED.getCode());
    }

    @Test
    void shouldReviewVeryLargePayment() {
        RiskPaymentEvaluateResultDTO resultDTO = service.evaluatePayment(baseRequest(new BigDecimal("5000.00")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REVIEW.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.MANUAL_REVIEW_REQUIRED.getCode());
    }

    @Test
    void shouldPassWhenWhitelistMerchantHitBeforeSkeletonAmountRejection() {
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
        assertThat(publisher.messages.get(0).getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    void shouldRejectWhenMerchantIpWhitelistEnabledButRequestIpMissed() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withMerchantIpWhitelistMiss(match("SYSTEM", "merchantIpWhitelist", "HIGH", "REJECT"));
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = baseRequest(new BigDecimal("12.34"));
        requestDTO.setPayerIp("203.0.113.9");

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REJECT.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.IP_WHITELIST_MISSED.getCode());
        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.messages.get(0).getHitCount()).isZero();
        assertThat(publisher.messages.get(0).getHits())
                .extracting(RiskAuditHitMessage::getStageCode, RiskAuditHitMessage::getMatchResult, RiskAuditHitMessage::getDecisionEffect)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "MERCHANT_IP_WHITELIST", "MISS", "BLOCK"));
        assertThat(publisher.messages.get(0).getHits().get(0).getDecisionReason())
                .contains("不在", "单节点拦截")
                .doesNotContain("放行");
    }

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

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.NONE.getCode());
    }

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
                .extracting(RiskAuditHitMessage::getStageCode, RiskAuditHitMessage::getMatchResult, RiskAuditHitMessage::getDecisionEffect)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("MERCHANT_IP_WHITELIST", "HIT", "ALLOW"),
                        org.assertj.core.groups.Tuple.tuple("SOURCE_URL_RESTRICTION", "HIT", "ALLOW"),
                        org.assertj.core.groups.Tuple.tuple("AML", "HIT", "BLOCK")
                );
    }

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
                .extracting(RiskAuditHitMessage::getModuleType, RiskAuditHitMessage::getFunctionCode, RiskAuditHitMessage::getDecisionEffect)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("WHITE", "cardNo", "ALLOW"),
                        org.assertj.core.groups.Tuple.tuple("BLACK", "email", "BLOCK")
                );
    }

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
                .extracting(RiskAuditHitMessage::getStageCode, RiskAuditHitMessage::getMatchResult, RiskAuditHitMessage::getDecisionAction)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("MERCHANT_LIMIT", "PASS", "PASS"));
    }

    @Test
    void shouldApplyFrequencyAfterMerchantLimit() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withFrequencyRule(match("RULE", "frequency", "HIGH", "REVIEW"));
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, new RecordingRiskAuditRecordPublisher());

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(baseRequest(new BigDecimal("12.34")));

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.REVIEW.getCode());
        assertThat(resultDTO.getReasonCode()).isEqualTo(RiskReasonCodeEnum.FREQUENCY_LIMIT_HIT.getCode());
    }

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
                .extracting(RiskAuditHitMessage::getRuleId,
                        RiskAuditHitMessage::getStageOrder,
                        RiskAuditHitMessage::getMatchResult,
                        RiskAuditHitMessage::getDecisionEffect)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1001L, 60, "PASS", "ALLOW"),
                        org.assertj.core.groups.Tuple.tuple(1002L, 60, "HIT", "BLOCK")
                );
    }

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
                .filteredOn(detail -> "BLACK_WHITE_ARBITRATION".equals(detail.getStageCode()))
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
                .extracting(RiskAuditHitMessage::getStageCode, RiskAuditHitMessage::getHitElement, RiskAuditHitMessage::getMatchResult)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("MERCHANT_LIMIT", "DAILY", "PASS"),
                        org.assertj.core.groups.Tuple.tuple("MERCHANT_LIMIT", "WEEKLY", "PASS"),
                        org.assertj.core.groups.Tuple.tuple("MERCHANT_LIMIT", "MONTHLY", "PASS")
                );
    }

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
                .extracting(RiskAuditHitMessage::getStageCode,
                        RiskAuditHitMessage::getMatchResult,
                        RiskAuditHitMessage::getDecisionEffect)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        "MERCHANT_LIMIT", "ERROR", "REVIEW"));
    }

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
                .extracting(RiskAuditHitMessage::getStageCode, RiskAuditHitMessage::getMatchResult, RiskAuditHitMessage::getDecisionAction)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("FREQUENCY_LIMIT", "PASS", "PASS"));
    }

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

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(publisher.messages).hasSize(1);
        assertThat(publisher.messages.get(0).getHitCount()).isZero();
        assertThat(publisher.messages.get(0).getHits())
                .extracting(RiskAuditHitMessage::getModuleType,
                        RiskAuditHitMessage::getFunctionCode,
                        RiskAuditHitMessage::getMatchResult,
                        RiskAuditHitMessage::getDecisionAction)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AML", "card", "MISS", "PASS"),
                        org.assertj.core.groups.Tuple.tuple("WHITE", "cardNo", "MISS", "PASS"),
                        org.assertj.core.groups.Tuple.tuple("BLACK", "cardNo", "MISS", "PASS")
                );
    }

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

    @Test
    void shouldAuditEveryConfiguredListEvaluationDimensionExactlyOnce() {
        FakeRiskListRuntimeRepository repository = new FakeRiskListRuntimeRepository()
                .withAllActiveListRules()
                .withIssuerCountry("CAN");
        RecordingRiskAuditRecordPublisher publisher = new RecordingRiskAuditRecordPublisher();
        DefaultRiskEvaluationService runtimeService = runtimeService(repository, publisher);
        RiskPaymentEvaluateRequestDTO requestDTO = fullyPopulatedRequest();

        RiskPaymentEvaluateResultDTO resultDTO = runtimeService.evaluatePayment(requestDTO);

        assertThat(resultDTO.getDecision()).isEqualTo(RiskDecisionEnum.PASS.getCode());
        assertThat(publisher.messages).singleElement().satisfies(message -> {
            int expectedEvaluationDimensions = RiskListFunction.values().length + 2;
            assertThat(message.getHitCount()).isZero();
            assertThat(message.getHits()).hasSize(expectedEvaluationDimensions);
            assertThat(message.getHits().stream()
                    .map(detail -> detail.getModuleType() + ":" + detail.getFunctionCode() + ":" + detail.getHitElement())
                    .distinct()
                    .count()).isEqualTo(expectedEvaluationDimensions);
        });
    }

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
                .extracting(RiskAuditHitMessage::getHitElement, RiskAuditHitMessage::getHitValueMasked)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("issuerCountry", "CAN"),
                        org.assertj.core.groups.Tuple.tuple("tradeCountry", "USA")
                );
        assertThat(cachedRule.getHitElement()).isEqualTo("country");
        assertThat(cachedRule.getStageCode()).isNull();
    }

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

    private RiskPaymentEvaluateRequestDTO baseRequest(BigDecimal amount) {
        RiskPaymentEvaluateRequestDTO requestDTO = new RiskPaymentEvaluateRequestDTO();
        requestDTO.setMerchantId("200001");
        requestDTO.setMerchantOrderNo("M202607120001");
        requestDTO.setAmount(amount);
        requestDTO.setCurrency("USD");
        return requestDTO;
    }

    private DefaultRiskEvaluationService runtimeService(FakeRiskListRuntimeRepository repository,
                                                        RecordingRiskAuditRecordPublisher publisher) {
        return new DefaultRiskEvaluationService(repository, publisher, runtimeProperties());
    }

    private RiskEvaluationProperties runtimeProperties() {
        RiskEvaluationProperties properties = new RiskEvaluationProperties();
        properties.setSkeletonFallbackEnabled(true);
        properties.setRuntimeEnabled(true);
        properties.setAuditMqEnabled(true);
        properties.setReadOnlyParallelEnabled(false);
        return properties;
    }

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

    private RiskListMatch cumulativeLimitMatch(String limitType,
                                               String matchResult,
                                               String decisionAction) {
        RiskListMatch match = match("RULE", "merchantLimit", "HIGH", decisionAction);
        match.setHitElement(limitType);
        match.setMatchResult(matchResult);
        match.setDecisionReason(limitType + " cumulative amount evaluated");
        return match;
    }

    private static class FakeRiskListRuntimeRepository implements RiskListRuntimeRepository {

        /** 按名单功能预置的测试命中结果。 */
        private final Map<RiskListFunction, RiskListMatch> listHits = new EnumMap<>(RiskListFunction.class);

        /** 记录服务传给仓储的规范化查询值，供脱敏和路由断言使用。 */
        private final Map<RiskListFunction, RiskRuntimeLookupValue> lookups = new ConcurrentHashMap<>();

        /** 记录只读规则查询实际使用的线程，验证并发边界不扩展到有副作用阶段。 */
        private final java.util.Set<String> readOnlyThreadNames = ConcurrentHashMap.newKeySet();

        /** 在测试场景中声明已启用的名单节点。 */
        private final EnumSet<RiskListFunction> activeListRules = EnumSet.noneOf(RiskListFunction.class);

        /** 预置的商户 IP 白名单命中结果。 */
        private RiskListMatch merchantIpWhitelistHit;

        /** 预置的商户 IP 白名单未命中结果。 */
        private RiskListMatch merchantIpWhitelistMiss;

        /** 预置的来源网址限制未命中结果。 */
        private RiskListMatch sourceUrlMiss;

        /** 预置的来源网址允许规则命中结果。 */
        private RiskListMatch sourceUrlRule;

        /** 预置的商户单笔限额命中结果。 */
        private RiskListMatch merchantLimitRule;

        /** 测试场景是否声明存在启用金额限额规则。 */
        private boolean activeMerchantLimitRule;

        /** 记录单笔限额只读查询次数。 */
        private int merchantLimitQueryCount;

        /** 记录累计限额预占调用次数。 */
        private int cumulativeReservationCount;

        /** 记录累计限额预占所在的线程。 */
        private String cumulativeLimitThreadName;

        /** 预置的累计限额明细和 Redis 预占结果。 */
        private MerchantLimitEvaluation cumulativeLimitEvaluation = MerchantLimitEvaluation.empty();

        /** 记录服务触发累计限额回滚的次数。 */
        private int merchantLimitRollbackCount;

        /** 按仓储执行顺序预置的频率规则明细。 */
        private List<RiskListMatch> frequencyRules = List.of();

        /** 测试场景是否声明存在启用频率规则。 */
        private boolean activeFrequencyRule;

        /** 记录后续节点阻断时释放频控成功名额的次数。 */
        private int frequencySuccessReleaseCount;

        /** 记录频控评估调用次数。 */
        private int frequencyEvaluationCount;

        /** 记录频控评估所在的线程。 */
        private String frequencyThreadName;

        /** 预置的 3DS 规则命中结果。 */
        private RiskListMatch threeDsRule;

        /** 按卡 BIN 预置的发卡行国家代码。 */
        private String issuerCountry;

        /** 记录 3DS 查询所在的线程。 */
        private String threeDsThreadName;

        private FakeRiskListRuntimeRepository withListHit(RiskListFunction function, RiskListMatch match) {
            listHits.put(function, match);
            activeListRules.add(function);
            return this;
        }

        private FakeRiskListRuntimeRepository withActiveListRule(RiskListFunction function) {
            activeListRules.add(function);
            return this;
        }

        private FakeRiskListRuntimeRepository withAllActiveListRules() {
            activeListRules.addAll(EnumSet.allOf(RiskListFunction.class));
            return this;
        }

        private FakeRiskListRuntimeRepository withSourceUrlRule(RiskListMatch match) {
            sourceUrlRule = match;
            return this;
        }

        private FakeRiskListRuntimeRepository withSourceUrlMiss(RiskListMatch match) {
            sourceUrlMiss = match;
            return this;
        }

        private FakeRiskListRuntimeRepository withMerchantIpWhitelistMiss(RiskListMatch match) {
            merchantIpWhitelistMiss = match;
            return this;
        }

        @SuppressWarnings("unused")
        private FakeRiskListRuntimeRepository withMerchantIpWhitelistHit(RiskListMatch match) {
            merchantIpWhitelistHit = match;
            return this;
        }

        private FakeRiskListRuntimeRepository withMerchantLimitRule(RiskListMatch match) {
            merchantLimitRule = match;
            activeMerchantLimitRule = true;
            return this;
        }

        private FakeRiskListRuntimeRepository withActiveMerchantLimitRule() {
            activeMerchantLimitRule = true;
            return this;
        }

        private FakeRiskListRuntimeRepository withCumulativeLimitEvaluation(MerchantLimitEvaluation evaluation) {
            cumulativeLimitEvaluation = evaluation;
            activeMerchantLimitRule = evaluation != null && !evaluation.details().isEmpty();
            return this;
        }

        private FakeRiskListRuntimeRepository withFrequencyRule(RiskListMatch match) {
            frequencyRules = match == null ? List.of() : List.of(match);
            activeFrequencyRule = true;
            return this;
        }

        private FakeRiskListRuntimeRepository withFrequencyRules(List<RiskListMatch> matches) {
            frequencyRules = matches == null ? List.of() : List.copyOf(matches);
            activeFrequencyRule = !frequencyRules.isEmpty();
            return this;
        }

        private FakeRiskListRuntimeRepository withActiveFrequencyRule() {
            activeFrequencyRule = true;
            return this;
        }

        private FakeRiskListRuntimeRepository withThreeDsRule(RiskListMatch match) {
            threeDsRule = match;
            return this;
        }

        private FakeRiskListRuntimeRepository withIssuerCountry(String country) {
            issuerCountry = country;
            return this;
        }

        /**
         * 记录规范化查询值并返回对应名单功能的预置命中。
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
            return activeListRules.contains(function);
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
         * 返回预置的商户 IP 白名单命中结果。
         */
        @Override
        public Optional<RiskListMatch> findMerchantIpWhitelistHit(String merchantId, RiskRuntimeLookupValue lookupValue) {
            return Optional.ofNullable(merchantIpWhitelistHit);
        }

        /**
         * 返回预置的商户 IP 白名单未命中结果。
         */
        @Override
        public Optional<RiskListMatch> findMerchantIpWhitelistMiss(String merchantId, RiskRuntimeLookupValue lookupValue) {
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
                                                       String paymentMethod,
                                                       String cardBrand,
                                                       BigDecimal amount,
                                                       String currency,
                                                       String currentRiskLevel) {
            threeDsThreadName = Thread.currentThread().getName();
            return Optional.ofNullable(threeDsRule);
        }
    }

    private static final class SlowMerchantLimitRiskListRuntimeRepository
            extends FakeRiskListRuntimeRepository {

        /** 模拟单笔限额查询超过只读阶段共享超时。 */
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

    private static final class ThrowingMerchantLimitRiskListRuntimeRepository
            extends FakeRiskListRuntimeRepository {

        /** 模拟单笔限额查询发生基础设施异常。 */
        @Override
        public Optional<RiskListMatch> findMerchantLimitRule(String merchantId,
                                                             BigDecimal amount,
                                                             String currency) {
            throw new IllegalStateException("simulated merchant limit query failure");
        }
    }

    private static final class ParallelProbeRiskListRuntimeRepository extends FakeRiskListRuntimeRepository {

        /** 三个只读规则组必须在超时前同时到达的测试屏障。 */
        private final CyclicBarrier readOnlyGroupBarrier = new CyclicBarrier(3);

        /** 记录三个规则组是否完成并发会合。 */
        private volatile boolean concurrentGroupsReached;

        /** 在 AML 和白名单组的首个查询处等待其他只读组。 */
        @Override
        public Optional<RiskListMatch> findListMatch(RiskListFunction function,
                                                     String merchantId,
                                                     RiskRuntimeLookupValue lookupValue) {
            if (function == RiskListFunction.AML_CARD || function == RiskListFunction.WHITE_MERCHANT) {
                awaitReadOnlyGroups();
            }
            return super.findListMatch(function, merchantId, lookupValue);
        }

        /** 在单笔限额组首个查询处等待 AML 和黑白名单组。 */
        @Override
        public Optional<RiskListMatch> findMerchantLimitRule(String merchantId,
                                                             BigDecimal amount,
                                                             String currency) {
            awaitReadOnlyGroups();
            return super.findMerchantLimitRule(merchantId, amount, currency);
        }

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

        private boolean concurrentGroupsReached() {
            return concurrentGroupsReached;
        }
    }

    private static final class RecordingRiskAuditRecordPublisher implements RiskAuditRecordPublisher {

        /** 收集服务发布的审计消息，供字段和顺序断言使用。 */
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
