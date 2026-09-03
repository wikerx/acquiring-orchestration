package com.scott.payment.risk.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.mq.message.RiskAuditHitMessage;
import com.scott.payment.component.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.domain.MerchantLimitEvaluation;
import com.scott.payment.risk.domain.RiskEvaluationOutcome;
import com.scott.payment.risk.domain.RiskListFunction;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.RiskRuntimeLookupValue;
import com.scott.payment.risk.domain.state.RiskDecisionEnum;
import com.scott.payment.risk.domain.state.RiskReasonCodeEnum;
import com.scott.payment.risk.repository.RiskAuditRecordPublisher;
import com.scott.payment.risk.repository.RiskListRuntimeRepository;
import com.scott.payment.risk.service.RiskEvaluationService;
import com.scott.payment.risk.service.RiskRuntimeValueNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRiskEvaluationService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 默认实时风控评估实现，按固定阶段编排名单、AML、累计限额、频率和 3DS 规则，
 * 对每个已执行节点生成脱敏审计明细，并在后续节点阻断或异常时回滚未确认的累计限额预占。
 * @status : create
 */
@Service
@Slf4j
public class DefaultRiskEvaluationService implements RiskEvaluationService {

    /**
     * 风控流水号前缀。
     */
    private static final String RISK_RECORD_PREFIX = "RK";

    /** 支付核心内部生成的 Hosted Checkout 来源，商户 OpenAPI 不允许控制该值。 */
    private static final String REQUEST_SOURCE_HOSTED_CHECKOUT = "HOSTED_CHECKOUT";

    /**
     * 来源阻断关键词，当前仅用于骨架验证，正式名单应来自配置或数据库。
     */
    private static final String BLOCKED_SOURCE_KEYWORD = "blocked";

    /**
     * 骨架阶段的 IP 阻断名单，正式名单应迁移到名单服务或风控规则库。
     */
    private static final Set<String> BLOCKED_PAYER_IPS = Set.of("127.0.0.2");

    /**
     * 要求 3DS 的最小金额阈值，单位为主币种单位；正式规则应按商户、币种和卡 BIN 配置。
     */
    private static final BigDecimal REQUIRE_3DS_AMOUNT = new BigDecimal("1000.00");

    /**
     * 人工复核金额阈值，单位为主币种单位；正式规则应按商户、币种和风险等级配置。
     */
    private static final BigDecimal MANUAL_REVIEW_AMOUNT = new BigDecimal("5000.00");

    /**
     * 当前内置风控规则数量，用于日志展示评估覆盖范围。
     */
    private static final int BUILT_IN_RULE_COUNT = 5;

    /** 不要求额外处置的低风险等级。 */
    private static final String RISK_LEVEL_LOW = "LOW";

    /** 通常进入复核或 3DS 挑战的中风险等级。 */
    private static final String RISK_LEVEL_MEDIUM = "MEDIUM";

    /** 通常触发阻断或人工复核的高风险等级。 */
    private static final String RISK_LEVEL_HIGH = "HIGH";

    /** 黑白名单仲裁中优先级最高的严重风险等级。 */
    private static final String RISK_LEVEL_CRITICAL = "CRITICAL";

    /** 规则查询值与启用规则实际匹配。 */
    private static final String MATCH_HIT = "HIT";

    /** 启用规则存在但当前查询值未匹配。 */
    private static final String MATCH_MISS = "MISS";

    /** 规则已执行且计数或金额仍在允许范围内。 */
    private static final String MATCH_PASS = "PASS";

    /** 规则明细允许评估继续。 */
    private static final String EFFECT_ALLOW = "ALLOW";

    /** 规则明细要求终止评估并拒绝交易。 */
    private static final String EFFECT_BLOCK = "BLOCK";

    /** 规则明细要求转人工复核。 */
    private static final String EFFECT_REVIEW = "REVIEW";

    /** 规则明细要求执行 3DS 挑战。 */
    private static final String EFFECT_CHALLENGE = "CHALLENGE";

    /** 未知动作对评估流程不产生已声明影响。 */
    private static final String EFFECT_NONE = "NONE";

    private static final Stage STAGE_MERCHANT_IP_WHITELIST = new Stage(
            "MERCHANT_IP_WHITELIST", "商户IP白名单", 10);

    private static final Stage STAGE_SOURCE_URL_RESTRICTION = new Stage(
            "SOURCE_URL_RESTRICTION", "商户来源网址限定", 20);

    private static final Stage STAGE_AML = new Stage("AML", "AML拦截", 30);

    private static final Stage STAGE_BLACK_WHITE = new Stage("BLACK_WHITE_ARBITRATION", "黑白名单优先级仲裁", 40);

    private static final Stage STAGE_MERCHANT_LIMIT = new Stage("MERCHANT_LIMIT", "商户交易限额", 50);

    private static final Stage STAGE_FREQUENCY = new Stage("FREQUENCY_LIMIT", "交易频率限定", 60);

    private static final Stage STAGE_THREE_DS = new Stage("THREE_DS", "3DS规则", 70);

    private static final Stage STAGE_SKELETON = new Stage("SKELETON_FALLBACK", "本地骨架规则", 90);

    /** 提供名单、金额限额、频率和 3DS 运行时规则。 */
    private final RiskListRuntimeRepository riskListRuntimeRepository;

    /** 将敏感输入转换为脱敏、哈希或区间查询值。 */
    private final RiskRuntimeValueNormalizer valueNormalizer;

    /** 在决策完成后发布评估主记录和逐节点脱敏明细。 */
    private final RiskAuditRecordPublisher auditRecordPublisher;

    /** 控制运行时规则和仅限兼容测试的骨架降级开关。 */
    private final RiskEvaluationProperties properties;

    /** 执行无状态只读规则组的专用执行器；累计限额和频控预占不得提交到该执行器。 */
    private final Executor readOnlyEvaluationExecutor;

    /**
     * 创建由 Spring 管理的实时风控评估服务。
     *
     * <p>运行时仓储和审计发布器允许在兼容测试环境缺省；生产决策是否启用运行时规则
     * 由 {@link RiskEvaluationProperties} 显式控制，不能因依赖缺失自动开启骨架放行。</p>
     *
     * @param riskListRuntimeRepositoryProvider 名单、限额、频控和 3DS 运行时仓储提供器
     * @param valueNormalizerProvider 敏感风控值归一化组件提供器
     * @param auditRecordPublisherProvider 脱敏评估审计发布器提供器
     * @param properties 风控运行模式及兼容降级配置
     */
    @Autowired
    public DefaultRiskEvaluationService(ObjectProvider<RiskListRuntimeRepository> riskListRuntimeRepositoryProvider,
                                        ObjectProvider<RiskRuntimeValueNormalizer> valueNormalizerProvider,
                                        ObjectProvider<RiskAuditRecordPublisher> auditRecordPublisherProvider,
                                        RiskEvaluationProperties properties,
                                        @Qualifier("riskReadOnlyEvaluationExecutor") Executor readOnlyEvaluationExecutor) {
        this.riskListRuntimeRepository = riskListRuntimeRepositoryProvider.getIfAvailable();
        this.valueNormalizer = valueNormalizerProvider.getIfAvailable(RiskRuntimeValueNormalizer::new);
        this.auditRecordPublisher = auditRecordPublisherProvider.getIfAvailable();
        this.properties = properties;
        this.readOnlyEvaluationExecutor = readOnlyEvaluationExecutor;
    }

    /**
     * 创建仅用于兼容测试和无 Spring 场景的默认实例。
     *
     * <p>该构造器不装配运行时仓储和审计发布器，是否执行骨架规则仍受默认配置约束，
     * 不应作为生产依赖注入入口。</p>
     */
    public DefaultRiskEvaluationService() {
        this.riskListRuntimeRepository = null;
        this.valueNormalizer = new RiskRuntimeValueNormalizer();
        this.auditRecordPublisher = null;
        this.properties = new RiskEvaluationProperties();
        this.readOnlyEvaluationExecutor = Runnable::run;
    }

    DefaultRiskEvaluationService(RiskListRuntimeRepository riskListRuntimeRepository,
                                 RiskAuditRecordPublisher auditRecordPublisher,
                                 RiskEvaluationProperties properties) {
        this(riskListRuntimeRepository, auditRecordPublisher, properties, Runnable::run);
    }

    DefaultRiskEvaluationService(RiskListRuntimeRepository riskListRuntimeRepository,
                                 RiskAuditRecordPublisher auditRecordPublisher,
                                 RiskEvaluationProperties properties,
                                 Executor readOnlyEvaluationExecutor) {
        this.riskListRuntimeRepository = riskListRuntimeRepository;
        this.valueNormalizer = new RiskRuntimeValueNormalizer();
        this.auditRecordPublisher = auditRecordPublisher;
        this.properties = properties == null ? new RiskEvaluationProperties() : properties;
        this.readOnlyEvaluationExecutor = readOnlyEvaluationExecutor == null ? Runnable::run : readOnlyEvaluationExecutor;
    }

    /**
     * 执行收单支付路由前风控评估。
     *
     * @param requestDTO 支付风控评估请求
     * @return 风控决策结果
     */
    @Override
    public RiskPaymentEvaluateResultDTO evaluatePayment(RiskPaymentEvaluateRequestDTO requestDTO) {
        long startNanos = System.nanoTime();
        String riskRecordNo = PaymentOrderNoGenerator.nextOrderNo(RISK_RECORD_PREFIX);
        log.info("event: RISK_EVALUATION_START stage=ACCEPT traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} paymentMethod: {} amount: {} currency: {} ruleCount: {} payerIp: {} sourceUrl: {}",
                TraceContext.getTraceId(),
                requestDTO == null ? null : requestDTO.getMerchantId(),
                requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                requestDTO == null ? null : requestDTO.getTransactionId(),
                requestDTO == null ? null : requestDTO.getTransactionType(),
                requestDTO == null ? null : requestDTO.getPaymentMethod(),
                requestDTO == null ? null : requestDTO.getAmount(),
                requestDTO == null ? null : requestDTO.getCurrency(),
                BUILT_IN_RULE_COUNT,
                requestDTO == null ? null : requestDTO.getPayerIp(),
                maskUrl(requestDTO == null ? null : requestDTO.getSourceUrl()));
        RiskEvaluationOutcome outcome = evaluate(requestDTO, riskRecordNo);
        RiskPaymentEvaluateResultDTO resultDTO = outcome.getResult();
        publishAudit(requestDTO, outcome);
        RiskListMatch firstHit = outcome.getHits().stream()
                .filter(RiskListMatch::actualHit)
                .findFirst()
                .orElse(null);
        // 非 PASS 决策必须归因到真正终止评估的规则；PASS 场景仍保留首条允许命中，便于审计规则覆盖情况。
        RiskListMatch decisiveHit = firstBlockingDetail(outcome.getHits()).orElse(firstHit);
        String hitRuleId = decisiveHit == null || decisiveHit.getRuleId() == null
                ? "NONE"
                : String.valueOf(decisiveHit.getRuleId());
        String hitRuleType = decisiveHit == null
                ? "NO_RULE_HIT"
                : decisiveHit.getModuleType() + ":" + decisiveHit.getFunctionCode();
        log.info("event: RISK_EVALUATION_END stage=DECISION traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} amount: {} currency: {} ruleCount: {} hitRuleId: {} hitRuleType: {} decision: {} rejectReasonCode: {} durationMs: {}",
                TraceContext.getTraceId(),
                requestDTO == null ? null : requestDTO.getMerchantId(),
                requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                requestDTO == null ? null : requestDTO.getTransactionId(),
                requestDTO == null ? null : requestDTO.getTransactionType(),
                requestDTO == null ? null : requestDTO.getAmount(),
                requestDTO == null ? null : requestDTO.getCurrency(),
                BUILT_IN_RULE_COUNT,
                hitRuleId,
                hitRuleType,
                resultDTO.getDecision(),
                resultDTO.getReasonCode(),
                elapsedMillis(startNanos));
        return resultDTO;
    }

    /**
     * 按商户 IP、来源网址、AML、黑白名单、限额、频控和 3DS 顺序执行一次评估。
     *
     * <p>任一阶段明确阻断时立即短路；累计限额预占完成后，如后续频控、3DS 或异常
     * 终止评估，必须先回滚本次未确认预占，防止失败交易占用商户额度。</p>
     *
     * @param requestDTO 支付交易及脱敏所需风控上下文
     * @param riskRecordNo 本次评估的稳定风控流水号
     * @return 最终决策及按阶段排序的脱敏审计明细
     */
    private RiskEvaluationOutcome evaluate(RiskPaymentEvaluateRequestDTO requestDTO, String riskRecordNo) {
        if (isInvalid(requestDTO)) {
            RiskListMatch hit = RiskListMatch.system("paramValidation", "参数校验", "request", "INVALID",
                    RISK_LEVEL_HIGH, RiskDecisionEnum.REJECT.getCode(), RiskReasonCodeEnum.PARAM_INVALID.getMessage())
                    .markStage("PARAM_VALIDATION", "参数校验", 0, MATCH_HIT, EFFECT_BLOCK);
            return RiskEvaluationOutcome.of(
                    buildResult(RiskDecisionEnum.REJECT, RiskReasonCodeEnum.PARAM_INVALID, riskRecordNo),
                    List.of(hit));
        }
        LookupContext context = lookupContext(requestDTO);
        List<RiskListMatch> details = new ArrayList<>();
        findMerchantIpWhitelistHit(requestDTO, context).ifPresent(match -> details.add(stage(match, STAGE_MERCHANT_IP_WHITELIST, MATCH_HIT)));
        Optional<RiskListMatch> merchantIpWhitelistMiss = findMerchantIpWhitelistMiss(requestDTO, context);
        merchantIpWhitelistMiss.ifPresent(match -> details.add(stage(match, STAGE_MERCHANT_IP_WHITELIST, MATCH_MISS)));
        if (merchantIpWhitelistMiss.isPresent()) {
            return RiskEvaluationOutcome.of(
                    buildResult(RiskDecisionEnum.REJECT, RiskReasonCodeEnum.IP_WHITELIST_MISSED, riskRecordNo),
                    details);
        }
        if (!isHostedCheckout(requestDTO)) {
            Optional<RiskListMatch> sourceUrlMiss = findSourceUrlRestrictionMiss(requestDTO, context);
            sourceUrlMiss.ifPresent(match -> details.add(stage(match, STAGE_SOURCE_URL_RESTRICTION, MATCH_MISS)));
            if (sourceUrlMiss.isPresent()) {
                return RiskEvaluationOutcome.of(
                        buildResult(RiskDecisionEnum.REJECT, RiskReasonCodeEnum.SOURCE_URL_NOT_ALLOWED, riskRecordNo),
                        details);
            }
            Optional<RiskListMatch> sourceUrlRule = findSourceUrlRule(requestDTO, context);
            sourceUrlRule.ifPresent(match -> details.add(stage(match, STAGE_SOURCE_URL_RESTRICTION, MATCH_HIT)));
            if (sourceUrlRule.isPresent() && !RiskDecisionEnum.PASS.getCode().equalsIgnoreCase(sourceUrlRule.get().getDecisionAction())) {
                return RiskEvaluationOutcome.of(
                        buildResult(resolveDecision(sourceUrlRule.get(), RiskDecisionEnum.REVIEW),
                                RiskReasonCodeEnum.RULE_HIT, riskRecordNo),
                        details);
            }
        }
        ReadOnlyEvaluation readOnlyEvaluation = properties.isReadOnlyParallelEnabled()
                ? evaluateReadOnlyGroups(requestDTO, context)
                : null;
        List<RiskListMatch> amlDetails = readOnlyEvaluation == null
                ? evaluateAmlChecks(requestDTO, context)
                : readOnlyEvaluation.amlDetails();
        details.addAll(amlDetails);
        Optional<RiskListMatch> amlBlockingDetail = firstBlockingDetail(amlDetails);
        if (amlBlockingDetail.isPresent()) {
            return RiskEvaluationOutcome.of(
                    buildResult(resolveDecision(amlBlockingDetail.get(), RiskDecisionEnum.REVIEW),
                            RiskReasonCodeEnum.AML_HIT, riskRecordNo),
                    details);
        }
        BlackWhiteEvaluation blackWhiteEvaluation = readOnlyEvaluation == null
                ? evaluateBlackWhiteChecks(requestDTO, context)
                : readOnlyEvaluation.blackWhiteEvaluation();
        Optional<RiskListMatch> listBlockHit = evaluateBlackWhiteArbitration(blackWhiteEvaluation, details);
        if (listBlockHit.isPresent()) {
            return RiskEvaluationOutcome.of(
                    buildResult(resolveDecision(listBlockHit.get(), RiskDecisionEnum.REJECT),
                            RiskReasonCodeEnum.BLACKLIST_HIT, riskRecordNo),
                    details);
        }
        MerchantLimitReadOnlyEvaluation merchantLimitEvaluation = readOnlyEvaluation == null
                ? evaluateMerchantLimitReadOnly(requestDTO)
                : readOnlyEvaluation.merchantLimitEvaluation();
        Optional<RiskListMatch> limitHit = merchantLimitEvaluation.limitHit();
        limitHit.ifPresent(match -> details.add(stage(match, STAGE_MERCHANT_LIMIT, MATCH_HIT)));
        if (limitHit.isPresent() && !RiskDecisionEnum.PASS.getCode().equalsIgnoreCase(limitHit.get().getDecisionAction())) {
            return RiskEvaluationOutcome.of(
                    buildResult(resolveDecision(limitHit.get(), RiskDecisionEnum.REVIEW),
                            RiskReasonCodeEnum.RULE_HIT, riskRecordNo),
                    details);
        }
        MerchantLimitEvaluation cumulativeLimitEvaluation =
                reserveCumulativeMerchantLimits(requestDTO, riskRecordNo);
        List<RiskListMatch> cumulativeLimitDetails = cumulativeLimitEvaluation.details().stream()
                .map(this::stageCumulativeLimit)
                .toList();
        details.addAll(cumulativeLimitDetails);
        Optional<RiskListMatch> cumulativeLimitBlock = firstBlockingDetail(cumulativeLimitDetails);
        if (cumulativeLimitBlock.isPresent()) {
            rollbackMerchantLimitReservations(cumulativeLimitEvaluation);
            return RiskEvaluationOutcome.of(
                    buildResult(resolveDecision(cumulativeLimitBlock.get(), RiskDecisionEnum.REVIEW),
                            RiskReasonCodeEnum.RULE_HIT, riskRecordNo),
                    details);
        }
        if (limitHit.isEmpty() && cumulativeLimitDetails.isEmpty()
                && merchantLimitEvaluation.activeMerchantLimitRule()) {
            details.add(checkpoint(STAGE_MERCHANT_LIMIT, MATCH_PASS, RiskDecisionEnum.PASS, "本笔交易未触发商户交易限额，放行"));
        }
        boolean retainFrequencySuccessReservations = false;
        try {
            List<RiskListMatch> frequencyDetails = evaluateFrequencyRules(requestDTO, context).stream()
                    .map(match -> stage(
                            match,
                            STAGE_FREQUENCY,
                            StringUtils.hasText(match.getMatchResult()) ? match.getMatchResult() : MATCH_HIT
                    ))
                    .toList();
            details.addAll(frequencyDetails);
            Optional<RiskListMatch> frequencyHit = firstBlockingDetail(frequencyDetails);
            if (frequencyHit.isPresent()
                    && !RiskDecisionEnum.PASS.getCode().equalsIgnoreCase(frequencyHit.get().getDecisionAction())) {
                rollbackMerchantLimitReservations(cumulativeLimitEvaluation);
                return RiskEvaluationOutcome.of(
                        buildResult(resolveDecision(frequencyHit.get(), RiskDecisionEnum.REVIEW),
                                RiskReasonCodeEnum.FREQUENCY_LIMIT_HIT, riskRecordNo),
                        details);
            }
            if (frequencyDetails.isEmpty() && hasActiveFrequencyRule(requestDTO)) {
                details.add(checkpoint(STAGE_FREQUENCY, MATCH_PASS, RiskDecisionEnum.PASS, "本笔交易未触发交易频率限定，放行"));
            }
            Optional<RiskListMatch> threeDsRule = findThreeDsRule(requestDTO, highestRiskLevel(details));
            threeDsRule.ifPresent(match -> details.add(stage(match, STAGE_THREE_DS, MATCH_HIT)));
            if (threeDsRule.isPresent()
                    && RiskDecisionEnum.REQUIRE_3DS.getCode().equalsIgnoreCase(threeDsRule.get().getDecisionAction())
                    && !hasThreeDsProof(requestDTO)) {
                rollbackMerchantLimitReservations(cumulativeLimitEvaluation);
                return RiskEvaluationOutcome.of(
                        buildResult(RiskDecisionEnum.REQUIRE_3DS, RiskReasonCodeEnum.THREE_DS_REQUIRED, riskRecordNo),
                        details);
            }
            retainFrequencySuccessReservations = true;
        } catch (RuntimeException exception) {
            rollbackMerchantLimitReservations(cumulativeLimitEvaluation);
            throw exception;
        } finally {
            if (!retainFrequencySuccessReservations) {
                releaseFrequencySuccessReservations(requestDTO);
            }
        }
        if (details.isEmpty()) {
            Optional<RiskEvaluationOutcome> skeletonOutcome = evaluateSkeletonFallback(requestDTO, riskRecordNo);
            if (skeletonOutcome.isPresent()) {
                skeletonOutcome.get().getHits().forEach(match -> details.add(stage(match, STAGE_SKELETON, MATCH_HIT)));
                skeletonOutcome.get().setHits(details);
                return skeletonOutcome.get();
            }
        }
        RiskPaymentEvaluateResultDTO resultDTO =
                buildResult(RiskDecisionEnum.PASS, RiskReasonCodeEnum.NONE, riskRecordNo);
        resultDTO.setMerchantLimitReserved(
                cumulativeLimitEvaluation.lifecycleManaged()
                        && !cumulativeLimitEvaluation.reservations().isEmpty());
        return RiskEvaluationOutcome.of(resultDTO, details);
    }

    /**
     * 释放本次评估已预占但不再可能进入支付成功终态的频控成功名额。
     *
     * @param requestDTO 当前风控请求；身份不完整时仓储实现安全忽略
     */
    private void releaseFrequencySuccessReservations(RiskPaymentEvaluateRequestDTO requestDTO) {
        if (requestDTO == null) {
            return;
        }
        riskListRuntimeRepository.releaseFrequencySuccessReservations(
                requestDTO.getMerchantId(),
                requestDTO.getTransactionId());
    }

    /**
     * 一次性归一化本次交易的全部名单查询值。
     *
     * <p>敏感输入只在内存中转换为哈希或脱敏值，后续规则编排不直接使用完整卡号、
     * 邮箱、电话或地址原文。</p>
     *
     * @return 本次评估专用的不可变查询上下文
     */
    private LookupContext lookupContext(RiskPaymentEvaluateRequestDTO requestDTO) {
        RiskRuntimeLookupValue cardNoLookup = valueNormalizer.cardNo(requestDTO.getCardNo());
        RiskRuntimeLookupValue cardFingerprintLookup = valueNormalizer.cardFingerprint(requestDTO.getCardNo());
        RiskRuntimeLookupValue cardBinLookup = valueNormalizer.cardBin(StringUtils.hasText(requestDTO.getCardBin())
                ? requestDTO.getCardBin()
                : requestDTO.getCardNo());
        RiskRuntimeLookupValue ipLookup = valueNormalizer.ip(requestDTO.getPayerIp());
        RiskRuntimeLookupValue billingEmailLookup = valueNormalizer.email(requestDTO.getBillingEmail());
        RiskRuntimeLookupValue billingEmailDomainLookup = valueNormalizer.emailDomain(requestDTO.getBillingEmail());
        RiskRuntimeLookupValue billingEmailUsernameLookup = valueNormalizer.emailUsername(requestDTO.getBillingEmail());
        RiskRuntimeLookupValue billingPhoneLookup = valueNormalizer.phone(requestDTO.getBillingPhone());
        RiskRuntimeLookupValue tradeCountryLookup = valueNormalizer.country(primaryCountry(requestDTO));
        RiskRuntimeLookupValue billingCountryLookup = valueNormalizer.country(requestDTO.getBillingCountry());
        RiskRuntimeLookupValue issuerCountryLookup = resolveIssuerCountry(cardBinLookup);
        RiskRuntimeLookupValue sourceHostLookup = valueNormalizer.sourceHost(requestDTO.getSourceUrl());
        RiskRuntimeLookupValue cardholderNameLookup = valueNormalizer.text(requestDTO.getCardholderName(), true);
        RiskRuntimeLookupValue legalPersonLookup = valueNormalizer.text(requestDTO.getLegalPerson(), true);
        RiskRuntimeLookupValue enterpriseLookup = valueNormalizer.text(requestDTO.getEnterprise(), true);
        RiskRuntimeLookupValue merchantBillingAddressLookup = valueNormalizer.text(
                requestDTO.getMerchantBillingAddress(), true);
        RiskRuntimeLookupValue billingAddressLookup = valueNormalizer.text(requestDTO.getBillingAddress(), true);
        RiskRuntimeLookupValue billingZipLookup = valueNormalizer.postalCode(requestDTO.getBillingZip());
        RiskRuntimeLookupValue billingRegionLookup = valueNormalizer.region(
                requestDTO.getBillingCountry(),
                requestDTO.getBillingRegion(),
                requestDTO.getBillingCity());
        RiskRuntimeLookupValue payerEmailLookup = hasText(requestDTO.getPayerEmail())
                ? valueNormalizer.email(requestDTO.getPayerEmail()) : null;
        RiskRuntimeLookupValue payerEmailDomainLookup = hasText(requestDTO.getPayerEmail())
                ? valueNormalizer.emailDomain(requestDTO.getPayerEmail()) : null;
        RiskRuntimeLookupValue payerEmailUsernameLookup = hasText(requestDTO.getPayerEmail())
                ? valueNormalizer.emailUsername(requestDTO.getPayerEmail()) : null;
        RiskRuntimeLookupValue payerPhoneLookup = hasText(requestDTO.getPayerPhone())
                ? valueNormalizer.phone(requestDTO.getPayerPhone()) : null;
        RiskRuntimeLookupValue payerCountryLookup = hasText(requestDTO.getPayerCountry())
                ? valueNormalizer.country(requestDTO.getPayerCountry()) : null;
        RiskRuntimeLookupValue payerNameLookup = hasText(requestDTO.getPayerName())
                ? valueNormalizer.text(requestDTO.getPayerName(), true) : null;
        RiskRuntimeLookupValue payerAddressLookup = hasText(requestDTO.getPayerAddress())
                ? valueNormalizer.text(requestDTO.getPayerAddress(), true) : null;
        RiskRuntimeLookupValue payerZipLookup = hasText(requestDTO.getPayerZip())
                ? valueNormalizer.postalCode(requestDTO.getPayerZip()) : null;
        RiskRuntimeLookupValue payerRegionLookup = hasAnyText(
                requestDTO.getPayerCountry(), requestDTO.getPayerRegion(), requestDTO.getPayerCity())
                ? valueNormalizer.region(
                        requestDTO.getPayerCountry(),
                        requestDTO.getPayerRegion(),
                        requestDTO.getPayerCity())
                : null;
        RiskRuntimeLookupValue payerIdLookup = hasText(requestDTO.getPayerId())
                ? valueNormalizer.text(requestDTO.getPayerId(), true) : null;
        RiskRuntimeLookupValue shippingAddressLookup = valueNormalizer.text(requestDTO.getShippingAddress(), true);
        RiskRuntimeLookupValue shippingZipLookup = valueNormalizer.postalCode(requestDTO.getShippingZip());
        RiskRuntimeLookupValue shippingCountryLookup = valueNormalizer.country(requestDTO.getShippingCountry());
        RiskRuntimeLookupValue shippingNameLookup = hasText(requestDTO.getShippingName())
                ? valueNormalizer.text(requestDTO.getShippingName(), true) : null;
        RiskRuntimeLookupValue shippingEmailLookup = hasText(requestDTO.getShippingEmail())
                ? valueNormalizer.email(requestDTO.getShippingEmail()) : null;
        RiskRuntimeLookupValue shippingEmailDomainLookup = hasText(requestDTO.getShippingEmail())
                ? valueNormalizer.emailDomain(requestDTO.getShippingEmail()) : null;
        RiskRuntimeLookupValue shippingEmailUsernameLookup = hasText(requestDTO.getShippingEmail())
                ? valueNormalizer.emailUsername(requestDTO.getShippingEmail()) : null;
        RiskRuntimeLookupValue shippingPhoneLookup = hasText(requestDTO.getShippingPhone())
                ? valueNormalizer.phone(requestDTO.getShippingPhone()) : null;
        RiskRuntimeLookupValue shippingRegionLookup = hasAnyText(
                requestDTO.getShippingCountry(), requestDTO.getShippingRegion(), requestDTO.getShippingCity())
                ? valueNormalizer.region(
                        requestDTO.getShippingCountry(),
                        requestDTO.getShippingRegion(),
                        requestDTO.getShippingCity())
                : null;
        RiskRuntimeLookupValue customerIdLookup = valueNormalizer.text(requestDTO.getCustomerId(), true);
        RiskRuntimeLookupValue deviceFingerprintLookup = valueNormalizer.text(
                requestDTO.getDeviceFingerprint(), true);
        return new LookupContext(cardNoLookup, cardFingerprintLookup, cardBinLookup, ipLookup, billingEmailLookup,
                billingEmailDomainLookup, billingEmailUsernameLookup, billingPhoneLookup,
                tradeCountryLookup, billingCountryLookup,
                issuerCountryLookup, sourceHostLookup, cardholderNameLookup, legalPersonLookup, enterpriseLookup,
                merchantBillingAddressLookup, billingAddressLookup, billingZipLookup, billingRegionLookup,
                payerEmailLookup, payerEmailDomainLookup, payerEmailUsernameLookup, payerPhoneLookup,
                payerCountryLookup, payerNameLookup, payerAddressLookup, payerZipLookup, payerRegionLookup,
                payerIdLookup, shippingAddressLookup, shippingZipLookup, shippingCountryLookup,
                shippingNameLookup, shippingEmailLookup, shippingEmailDomainLookup,
                shippingEmailUsernameLookup, shippingPhoneLookup, shippingRegionLookup, customerIdLookup,
                deviceFingerprintLookup);
    }

    /**
     * 查询商户 IP 白名单启用但付款方 IP 未命中的拒绝明细。
     */
    private Optional<RiskListMatch> findMerchantIpWhitelistMiss(RiskPaymentEvaluateRequestDTO requestDTO,
                                                                LookupContext context) {
        if (riskListRuntimeRepository == null) {
            return Optional.empty();
        }
        return riskListRuntimeRepository.findMerchantIpWhitelistMiss(requestDTO.getMerchantId(), context.ipLookup());
    }

    /**
     * 查询付款方 IP 命中商户 IP 白名单的放行明细。
     */
    private Optional<RiskListMatch> findMerchantIpWhitelistHit(RiskPaymentEvaluateRequestDTO requestDTO,
                                                               LookupContext context) {
        if (riskListRuntimeRepository == null) {
            return Optional.empty();
        }
        return riskListRuntimeRepository.findMerchantIpWhitelistHit(requestDTO.getMerchantId(), context.ipLookup());
    }

    /**
     * 查询来源网址限制已配置但当前主机未命中的拒绝明细。
     */
    private Optional<RiskListMatch> findSourceUrlRestrictionMiss(RiskPaymentEvaluateRequestDTO requestDTO,
                                                                 LookupContext context) {
        if (riskListRuntimeRepository == null) {
            return Optional.empty();
        }
        return riskListRuntimeRepository.findSourceUrlRestrictionMiss(requestDTO.getMerchantId(), context.sourceHostLookup());
    }

    /**
     * 判断当前请求是否由平台 Hosted Checkout 内部链路发起。
     *
     * @param requestDTO 支付风控请求
     * @return 可信来源标识为 HOSTED_CHECKOUT 时返回 {@code true}
     */
    private boolean isHostedCheckout(RiskPaymentEvaluateRequestDTO requestDTO) {
        return requestDTO != null
                && REQUEST_SOURCE_HOSTED_CHECKOUT.equals(requestDTO.getRequestSource());
    }

    /**
     * 执行商户、卡、IP、国家、邮箱、电话和设备等全部白名单节点。
     *
     * @return 每个存在启用规则节点的 HIT 或 MISS 明细
     */
    private List<RiskListMatch> evaluateWhitelistChecks(RiskPaymentEvaluateRequestDTO requestDTO,
                                                        LookupContext context) {
        return evaluateListChecks(requestDTO.getMerchantId(), STAGE_BLACK_WHITE, List.of(
                new ListCheck(RiskListFunction.WHITE_MERCHANT, valueNormalizer.merchant(requestDTO.getMerchantId())),
                new ListCheck(RiskListFunction.WHITE_CARD_NO, context.cardNoLookup()),
                new ListCheck(RiskListFunction.WHITE_CARD_FINGERPRINT, context.cardFingerprintLookup()),
                new ListCheck(RiskListFunction.WHITE_CARD_BIN, context.cardBinLookup()),
                new ListCheck(RiskListFunction.WHITE_TRADE_COUNTRY, context.tradeCountryLookup(),
                        tradeCountryAuditElement(requestDTO)),
                new ListCheck(RiskListFunction.WHITE_ISSUER_COUNTRY, context.issuerCountryLookup()),
                new ListCheck(RiskListFunction.WHITE_IP, context.ipLookup(), "payerIp"),
                new ListCheck(RiskListFunction.WHITE_EMAIL, context.billingEmailLookup(), "billingEmail"),
                new ListCheck(RiskListFunction.WHITE_EMAIL_DOMAIN, context.billingEmailDomainLookup(), "billingEmailDomain"),
                new ListCheck(RiskListFunction.WHITE_PHONE, context.billingPhoneLookup(), "billingPhone"),
                new ListCheck(RiskListFunction.WHITE_EMAIL, context.payerEmailLookup(), "payerEmail"),
                new ListCheck(RiskListFunction.WHITE_EMAIL_DOMAIN, context.payerEmailDomainLookup(), "payerEmailDomain"),
                new ListCheck(RiskListFunction.WHITE_PHONE, context.payerPhoneLookup(), "payerPhone"),
                new ListCheck(RiskListFunction.WHITE_TRADE_COUNTRY, context.payerCountryLookup(), "payerCountry"),
                new ListCheck(RiskListFunction.WHITE_CUSTOMER_ID, context.payerIdLookup(), "payerId"),
                new ListCheck(RiskListFunction.WHITE_EMAIL, context.shippingEmailLookup(), "shippingEmail"),
                new ListCheck(RiskListFunction.WHITE_EMAIL_DOMAIN, context.shippingEmailDomainLookup(), "shippingEmailDomain"),
                new ListCheck(RiskListFunction.WHITE_PHONE, context.shippingPhoneLookup(), "shippingPhone"),
                new ListCheck(RiskListFunction.WHITE_TRADE_COUNTRY, context.shippingCountryLookup(), "shippingCountry"),
                new ListCheck(RiskListFunction.WHITE_CUSTOMER_ID, context.customerIdLookup(), "customerId"),
                new ListCheck(RiskListFunction.WHITE_DEVICE_FINGERPRINT, context.deviceFingerprintLookup())
        ));
    }

    /**
     * 执行卡、IP、持卡人、地址、地域和设备等全部黑名单节点。
     *
     * @return 每个存在启用规则节点的 HIT 或 MISS 明细
     */
    private List<RiskListMatch> evaluateBlacklistChecks(RiskPaymentEvaluateRequestDTO requestDTO,
                                                        LookupContext context) {
        return evaluateListChecks(requestDTO.getMerchantId(), STAGE_BLACK_WHITE, List.of(
                new ListCheck(RiskListFunction.BLACK_CARD_NO, context.cardNoLookup()),
                new ListCheck(RiskListFunction.BLACK_CARD_FINGERPRINT, context.cardFingerprintLookup()),
                new ListCheck(RiskListFunction.BLACK_IP, context.ipLookup(), "payerIp"),
                new ListCheck(RiskListFunction.BLACK_CARD_BIN, context.cardBinLookup()),
                new ListCheck(RiskListFunction.BLACK_CARDHOLDER_NAME, context.cardholderNameLookup(), "billingName"),
                new ListCheck(RiskListFunction.BLACK_EMAIL, context.billingEmailLookup(), "billingEmail"),
                new ListCheck(RiskListFunction.BLACK_EMAIL_DOMAIN, context.billingEmailDomainLookup(), "billingEmailDomain"),
                new ListCheck(RiskListFunction.BLACK_EMAIL_USERNAME, context.billingEmailUsernameLookup(), "billingEmailUsername"),
                new ListCheck(RiskListFunction.BLACK_PHONE, context.billingPhoneLookup(), "billingPhone"),
                new ListCheck(RiskListFunction.BLACK_REGION, context.billingRegionLookup(), "billingRegion"),
                new ListCheck(RiskListFunction.BLACK_BILLING_ADDRESS, context.billingAddressLookup(), "billingAddress"),
                new ListCheck(RiskListFunction.BLACK_BILLING_ZIP, context.billingZipLookup(), "billingZip"),
                new ListCheck(RiskListFunction.BLACK_BILLING_COUNTRY, context.billingCountryLookup(), "billingCountry"),
                new ListCheck(RiskListFunction.BLACK_CARDHOLDER_NAME, context.payerNameLookup(), "payerName"),
                new ListCheck(RiskListFunction.BLACK_EMAIL, context.payerEmailLookup(), "payerEmail"),
                new ListCheck(RiskListFunction.BLACK_EMAIL_DOMAIN, context.payerEmailDomainLookup(), "payerEmailDomain"),
                new ListCheck(RiskListFunction.BLACK_EMAIL_USERNAME, context.payerEmailUsernameLookup(), "payerEmailUsername"),
                new ListCheck(RiskListFunction.BLACK_PHONE, context.payerPhoneLookup(), "payerPhone"),
                new ListCheck(RiskListFunction.BLACK_REGION, context.payerRegionLookup(), "payerRegion"),
                new ListCheck(RiskListFunction.BLACK_BILLING_ADDRESS, context.payerAddressLookup(), "payerAddress"),
                new ListCheck(RiskListFunction.BLACK_BILLING_ZIP, context.payerZipLookup(), "payerZip"),
                new ListCheck(RiskListFunction.BLACK_BILLING_COUNTRY, context.payerCountryLookup(), "payerCountry"),
                new ListCheck(RiskListFunction.BLACK_CARDHOLDER_NAME, context.shippingNameLookup(), "shippingName"),
                new ListCheck(RiskListFunction.BLACK_EMAIL, context.shippingEmailLookup(), "shippingEmail"),
                new ListCheck(RiskListFunction.BLACK_EMAIL_DOMAIN, context.shippingEmailDomainLookup(), "shippingEmailDomain"),
                new ListCheck(RiskListFunction.BLACK_EMAIL_USERNAME, context.shippingEmailUsernameLookup(), "shippingEmailUsername"),
                new ListCheck(RiskListFunction.BLACK_PHONE, context.shippingPhoneLookup(), "shippingPhone"),
                new ListCheck(RiskListFunction.BLACK_REGION, context.shippingRegionLookup(), "shippingRegion"),
                new ListCheck(RiskListFunction.BLACK_SHIPPING_ADDRESS, context.shippingAddressLookup(), "shippingAddress"),
                new ListCheck(RiskListFunction.BLACK_SHIPPING_ZIP, context.shippingZipLookup(), "shippingZip"),
                new ListCheck(RiskListFunction.BLACK_SHIPPING_COUNTRY, context.shippingCountryLookup(), "shippingCountry"),
                new ListCheck(RiskListFunction.BLACK_ISSUER_COUNTRY, context.issuerCountryLookup()),
                new ListCheck(RiskListFunction.BLACK_DEVICE_FINGERPRINT, context.deviceFingerprintLookup())
        ));
    }

    /**
     * 查询完整黑白名单明细，但不在工作线程内执行最终仲裁或修改主线程审计集合。
     *
     * @param requestDTO 当前支付风控请求
     * @param context 已归一化的只读查询上下文
     * @return 可由请求线程按固定阶段顺序仲裁的不可变查询结果
     */
    private BlackWhiteEvaluation evaluateBlackWhiteChecks(RiskPaymentEvaluateRequestDTO requestDTO,
                                                           LookupContext context) {
        return new BlackWhiteEvaluation(
                List.copyOf(evaluateWhitelistChecks(requestDTO, context)),
                List.copyOf(evaluateBlacklistChecks(requestDTO, context)));
    }

    /**
     * 按风险等级在白名单放行与黑名单阻断之间进行确定性仲裁。
     *
     * <p>每个等级均先判断白名单，再判断同等级黑名单；强等级先于优先和弱等级。</p>
     *
     * @param evaluation 已完成查询的黑白名单只读结果
     * @param details 接收全部黑白名单节点审计明细
     * @return 仲裁后的首条阻断黑名单；白名单优先或均未阻断时返回空
     */
    private Optional<RiskListMatch> evaluateBlackWhiteArbitration(BlackWhiteEvaluation evaluation,
                                                                  List<RiskListMatch> details) {
        List<RiskListMatch> whitelistDetails = evaluation.whitelistDetails();
        List<RiskListMatch> blacklistDetails = evaluation.blacklistDetails();
        details.addAll(whitelistDetails);
        details.addAll(blacklistDetails);
        List<RiskListMatch> whitelistMatches = whitelistDetails.stream()
                .filter(RiskListMatch::actualHit)
                .toList();
        List<RiskListMatch> blacklistMatches = blacklistDetails.stream()
                .filter(RiskListMatch::actualHit)
                .toList();
        List<RiskListMatch> strongWhitelists = byBand(whitelistMatches, RiskBand.STRONG);
        List<RiskListMatch> blacklistA = byBand(blacklistMatches, RiskBand.STRONG);
        List<RiskListMatch> priorityWhitelists = byBand(whitelistMatches, RiskBand.PRIORITY);
        List<RiskListMatch> blacklistB = byBand(blacklistMatches, RiskBand.PRIORITY);
        List<RiskListMatch> weakWhitelists = byBand(whitelistMatches, RiskBand.WEAK);
        List<RiskListMatch> blacklistC = byBand(blacklistMatches, RiskBand.WEAK);
        if (!strongWhitelists.isEmpty()) {
            return Optional.empty();
        }
        if (!blacklistA.isEmpty()) {
            return Optional.of(blacklistA.get(0));
        }
        if (!priorityWhitelists.isEmpty()) {
            return Optional.empty();
        }
        if (!blacklistB.isEmpty()) {
            return Optional.of(blacklistB.get(0));
        }
        if (!weakWhitelists.isEmpty()) {
            return Optional.empty();
        }
        return blacklistC.isEmpty() ? Optional.empty() : Optional.of(blacklistC.get(0));
    }

    /**
     * 执行卡、国家、IP、主体和来源网址等 AML 名单节点。
     *
     * @return 每个存在启用规则节点的 HIT 或 MISS 明细
     */
    private List<RiskListMatch> evaluateAmlChecks(RiskPaymentEvaluateRequestDTO requestDTO,
                                                 LookupContext context) {
        return evaluateListChecks(requestDTO.getMerchantId(), STAGE_AML, List.of(
                new ListCheck(RiskListFunction.AML_CARD, context.cardNoLookup()),
                new ListCheck(RiskListFunction.AML_CARD_BIN, context.cardBinLookup()),
                new ListCheck(RiskListFunction.AML_COUNTRY, context.issuerCountryLookup(), "issuerCountry"),
                new ListCheck(RiskListFunction.AML_IP, context.ipLookup(), "payerIp"),
                new ListCheck(RiskListFunction.AML_COUNTRY, context.tradeCountryLookup(),
                        tradeCountryAuditElement(requestDTO)),
                new ListCheck(RiskListFunction.AML_EMAIL, context.billingEmailLookup(), "billingEmail"),
                new ListCheck(RiskListFunction.AML_EMAIL, context.billingEmailDomainLookup(), "billingEmailDomain"),
                new ListCheck(RiskListFunction.AML_PHONE, context.billingPhoneLookup(), "billingPhone"),
                new ListCheck(RiskListFunction.AML_CARDHOLDER_NAME, context.cardholderNameLookup(), "billingName"),
                new ListCheck(RiskListFunction.AML_COUNTRY, context.payerCountryLookup(), "payerCountry"),
                new ListCheck(RiskListFunction.AML_EMAIL, context.payerEmailLookup(), "payerEmail"),
                new ListCheck(RiskListFunction.AML_EMAIL, context.payerEmailDomainLookup(), "payerEmailDomain"),
                new ListCheck(RiskListFunction.AML_PHONE, context.payerPhoneLookup(), "payerPhone"),
                new ListCheck(RiskListFunction.AML_CARDHOLDER_NAME, context.payerNameLookup(), "payerName"),
                new ListCheck(RiskListFunction.AML_COUNTRY, context.shippingCountryLookup(), "shippingCountry"),
                new ListCheck(RiskListFunction.AML_EMAIL, context.shippingEmailLookup(), "shippingEmail"),
                new ListCheck(RiskListFunction.AML_EMAIL, context.shippingEmailDomainLookup(), "shippingEmailDomain"),
                new ListCheck(RiskListFunction.AML_PHONE, context.shippingPhoneLookup(), "shippingPhone"),
                new ListCheck(RiskListFunction.AML_CARDHOLDER_NAME, context.shippingNameLookup(), "shippingName"),
                new ListCheck(RiskListFunction.AML_LEGAL_PERSON, context.legalPersonLookup()),
                new ListCheck(RiskListFunction.AML_ENTERPRISE, context.enterpriseLookup()),
                new ListCheck(RiskListFunction.AML_MERCHANT_BILLING_ADDRESS,
                        context.merchantBillingAddressLookup()),
                new ListCheck(RiskListFunction.AML_SOURCE_URL, context.sourceHostLookup())
        ));
    }

    /**
     * 查询当前来源主机命中的商户来源网址允许规则。
     */
    private Optional<RiskListMatch> findSourceUrlRule(RiskPaymentEvaluateRequestDTO requestDTO,
                                                      LookupContext context) {
        if (riskListRuntimeRepository == null) {
            return Optional.empty();
        }
        return riskListRuntimeRepository.findSourceUrlRule(requestDTO.getMerchantId(), context.sourceHostLookup());
    }

    /**
     * 查询本次金额触发的商户单笔最低或最高限额。
     */
    private Optional<RiskListMatch> findMerchantLimitRule(RiskPaymentEvaluateRequestDTO requestDTO) {
        if (riskListRuntimeRepository == null) {
            return Optional.empty();
        }
        return riskListRuntimeRepository.findMerchantLimitRule(requestDTO.getMerchantId(), requestDTO.getAmount(), requestDTO.getCurrency());
    }

    /**
     * 查询单笔限额命中和启用状态，不执行累计限额预占。
     *
     * @param requestDTO 当前支付风控请求
     * @return 单笔规则命中及启用状态快照
     */
    private MerchantLimitReadOnlyEvaluation evaluateMerchantLimitReadOnly(
            RiskPaymentEvaluateRequestDTO requestDTO) {
        return new MerchantLimitReadOnlyEvaluation(
                findMerchantLimitRule(requestDTO),
                hasActiveMerchantLimitRule(requestDTO));
    }

    /**
     * 并发执行三个相互独立且无业务副作用的只读风控组。
     *
     * <p>三个任务共享同一超时边界；任一任务超时、异常或提交失败均取消其余结果并抛出
     * 受控服务异常，由支付调用方按风控不可用拒绝交易。最终风险优先级仍在请求线程仲裁。</p>
     *
     * @param requestDTO 当前支付风控请求
     * @param context 已归一化的只读查询上下文
     * @return 三个只读规则组的不可变结果快照
     */
    private ReadOnlyEvaluation evaluateReadOnlyGroups(RiskPaymentEvaluateRequestDTO requestDTO,
                                                       LookupContext context) {
        List<CompletableFuture<?>> futures = new ArrayList<>(3);
        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(properties.getReadOnlyTimeoutMillis());
        try {
            CompletableFuture<List<RiskListMatch>> amlFuture = CompletableFuture.supplyAsync(
                    () -> List.copyOf(evaluateAmlChecks(requestDTO, context)),
                    readOnlyEvaluationExecutor);
            futures.add(amlFuture);
            CompletableFuture<BlackWhiteEvaluation> blackWhiteFuture = CompletableFuture.supplyAsync(
                    () -> evaluateBlackWhiteChecks(requestDTO, context),
                    readOnlyEvaluationExecutor);
            futures.add(blackWhiteFuture);
            CompletableFuture<MerchantLimitReadOnlyEvaluation> merchantLimitFuture = CompletableFuture.supplyAsync(
                    () -> evaluateMerchantLimitReadOnly(requestDTO),
                    readOnlyEvaluationExecutor);
            futures.add(merchantLimitFuture);

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new TimeoutException("read-only risk task submission exceeded the shared timeout");
            }
            CompletableFuture.allOf(amlFuture, blackWhiteFuture, merchantLimitFuture)
                    .get(remainingNanos, TimeUnit.NANOSECONDS);
            return new ReadOnlyEvaluation(
                    amlFuture.get(),
                    blackWhiteFuture.get(),
                    merchantLimitFuture.get());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cancelReadOnlyFutures(futures);
            throw readOnlyEvaluationUnavailable("INTERRUPTED", exception);
        } catch (TimeoutException exception) {
            cancelReadOnlyFutures(futures);
            throw readOnlyEvaluationUnavailable("TIMEOUT", exception);
        } catch (ExecutionException exception) {
            cancelReadOnlyFutures(futures);
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw readOnlyEvaluationUnavailable("EXECUTION_ERROR", cause);
        } catch (RuntimeException exception) {
            cancelReadOnlyFutures(futures);
            throw readOnlyEvaluationUnavailable("SUBMISSION_ERROR", exception);
        }
    }

    /**
     * 尽力取消尚未完成的只读任务；任务只允许读缓存和数据库，不包含预占或状态更新。
     *
     * @param futures 本次评估已成功提交的任务
     */
    private void cancelReadOnlyFutures(List<CompletableFuture<?>> futures) {
        futures.forEach(future -> future.cancel(true));
    }

    /**
     * 记录不含敏感输入的失败摘要并构建统一服务异常。
     *
     * @param failureType 受控失败分类
     * @param cause 原始失败原因
     * @return 交给内部接口异常处理器的失败关闭异常
     */
    private ServiceException readOnlyEvaluationUnavailable(String failureType, Throwable cause) {
        log.error("event: RISK_READ_ONLY_EVALUATION_FAILED stage=READ_ONLY_PARALLEL traceId: {} failureType: {} exceptionType: {} timeoutMillis: {}",
                TraceContext.getTraceId(),
                failureType,
                cause == null ? "Unknown" : cause.getClass().getSimpleName(),
                properties.getReadOnlyTimeoutMillis());
        return new ServiceException(
                ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                "risk read-only evaluation is unavailable",
                cause);
    }

    /**
     * 使用稳定风控流水号执行日、周、月累计限额预占。
     *
     * @return 规则仓储执行结果；仓储未装配时返回无规则空结果
     */
    private MerchantLimitEvaluation reserveCumulativeMerchantLimits(RiskPaymentEvaluateRequestDTO requestDTO,
                                                                    String riskRecordNo) {
        if (riskListRuntimeRepository == null) {
            return MerchantLimitEvaluation.empty();
        }
        return riskListRuntimeRepository.reserveCumulativeMerchantLimits(requestDTO, riskRecordNo);
    }

    /**
     * 在后续规则阻断或异常时幂等回滚本次累计限额预占。
     */
    private void rollbackMerchantLimitReservations(MerchantLimitEvaluation evaluation) {
        if (riskListRuntimeRepository != null && evaluation != null && !evaluation.reservations().isEmpty()) {
            riskListRuntimeRepository.rollbackMerchantLimitReservations(evaluation);
        }
    }

    /**
     * 为累计限额明细补充固定评估阶段和决策影响。
     *
     * @return 原地补充后的明细；输入为空时返回 {@code null}
     */
    private RiskListMatch stageCumulativeLimit(RiskListMatch match) {
        if (match == null) {
            return null;
        }
        String matchResult = StringUtils.hasText(match.getMatchResult()) ? match.getMatchResult() : MATCH_HIT;
        return match.markStage(
                STAGE_MERCHANT_LIMIT.code(),
                STAGE_MERCHANT_LIMIT.name(),
                STAGE_MERCHANT_LIMIT.order(),
                matchResult,
                decisionEffect(match)
        );
    }

    /**
     * 判断本次商户和币种是否存在任何启用金额限额规则。
     */
    private boolean hasActiveMerchantLimitRule(RiskPaymentEvaluateRequestDTO requestDTO) {
        return riskListRuntimeRepository != null
                && riskListRuntimeRepository.hasActiveMerchantLimitRule(requestDTO.getMerchantId(), requestDTO.getCurrency());
    }

    /**
     * 查询适用于本次支付维度和当前最高风险等级的 3DS 规则。
     */
    private Optional<RiskListMatch> findThreeDsRule(RiskPaymentEvaluateRequestDTO requestDTO, String riskLevel) {
        if (riskListRuntimeRepository == null) {
            return Optional.empty();
        }
        return riskListRuntimeRepository.findThreeDsRule(
                requestDTO.getMerchantId(),
                requestDTO.getChannelCode(),
                requestDTO.getPaymentMethod(),
                requestDTO.getCardBrand(),
                requestDTO.getAmount(),
                requestDTO.getCurrency(),
                riskLevel);
    }

    /**
     * 执行本次交易适用的全部频率规则。
     *
     * @return PASS、HIT 或 ERROR 逐规则明细；仓储未装配时返回空集合
     */
    private List<RiskListMatch> evaluateFrequencyRules(RiskPaymentEvaluateRequestDTO requestDTO,
                                                       LookupContext context) {
        if (riskListRuntimeRepository == null) {
            return List.of();
        }
        return riskListRuntimeRepository.evaluateFrequencyRules(
                requestDTO.getMerchantId(),
                requestDTO,
                context.cardNoLookup(),
                context.cardFingerprintLookup(),
                context.ipLookup(),
                context.billingEmailLookup(),
                context.billingPhoneLookup(),
                context.customerIdLookup(),
                context.deviceFingerprintLookup()
        );
    }

    /**
     * 判断当前商户是否存在启用的交易频率规则。
     */
    private boolean hasActiveFrequencyRule(RiskPaymentEvaluateRequestDTO requestDTO) {
        return riskListRuntimeRepository != null
                && riskListRuntimeRepository.hasActiveFrequencyRule(requestDTO.getMerchantId());
    }

    /**
     * 查询单个名单功能，缺少仓储或规范查询值时按未执行处理。
     */
    private Optional<RiskListMatch> findMatch(RiskListFunction function,
                                              String merchantId,
                                              RiskRuntimeLookupValue lookupValue) {
        if (riskListRuntimeRepository == null || lookupValue == null) {
            return Optional.empty();
        }
        return riskListRuntimeRepository.findListMatch(function, merchantId, lookupValue);
    }

    /**
     * 执行同一阶段的名单检查，并为启用规则同时保留 HIT 与 MISS 审计证据。
     *
     * @return 按输入检查顺序排列的阶段明细
     */
    private List<RiskListMatch> evaluateListChecks(String merchantId,
                                                   Stage stage,
                                                   List<ListCheck> checks) {
        List<RiskListMatch> details = new ArrayList<>();
        for (ListCheck check : checks) {
            if (check.lookupValue() == null) {
                continue;
            }
            Optional<RiskListMatch> matched = findMatch(check.function(), merchantId, check.lookupValue());
            if (matched.isPresent()) {
                RiskListMatch detail = copyMatch(matched.get());
                detail.setHitElement(check.auditElement());
                detail.setHitValueMasked(maskedLookupValue(check.lookupValue()));
                details.add(stage(detail, stage, MATCH_HIT));
                continue;
            }
            if (hasActiveListRule(check.function(), merchantId)) {
                details.add(listMiss(check.function(), check.lookupValue(), check.auditElement(), stage));
            }
        }
        return details;
    }

    /**
     * 按风险等级仲裁带筛选实际命中的名单明细。
     */
    private List<RiskListMatch> byBand(List<RiskListMatch> matches, RiskBand band) {
        return matches.stream()
                .filter(match -> riskBand(match.getRiskLevel()) == band)
                .toList();
    }

    /**
     * 判断名单节点是否存在当前商户可用的启用规则。
     */
    private boolean hasActiveListRule(RiskListFunction function, String merchantId) {
        return riskListRuntimeRepository != null
                && riskListRuntimeRepository.hasActiveListRule(function, merchantId);
    }

    /**
     * 复制规则命中并补齐阶段顺序、匹配结果、决策影响和可读原因。
     *
     * @return 独立审计明细；输入为空时返回 {@code null}
     */
    private RiskListMatch stage(RiskListMatch match, Stage stage, String matchResult) {
        if (match == null) {
            return null;
        }
        RiskListMatch staged = copyMatch(match);
        int stageOrder = STAGE_BLACK_WHITE.code().equals(stage.code())
                ? blackWhiteStageOrder(staged)
                : stage.order();
        staged.markStage(stage.code(), stage.name(), stageOrder, matchResult, decisionEffect(staged));
        if (!"ERROR".equalsIgnoreCase(matchResult) || !StringUtils.hasText(staged.getDecisionReason())) {
            staged.setDecisionReason(detailDecisionReason(staged, matchResult));
        }
        return staged;
    }

    /**
     * 为已启用但未命中的名单节点构建不含敏感原文的 MISS 审计明细。
     */
    private RiskListMatch listMiss(RiskListFunction function,
                                   RiskRuntimeLookupValue lookupValue,
                                   String auditElement,
                                   Stage stage) {
        RiskListMatch detail = new RiskListMatch();
        detail.setModuleType(function.getModuleType().getCode());
        detail.setFunctionCode(function.getFunctionCode());
        detail.setFunctionName(function.getFunctionName());
        detail.setHitElement(auditElement);
        detail.setHitValueMasked(maskedLookupValue(lookupValue));
        detail.setRiskLevel(RISK_LEVEL_LOW);
        detail.setDecisionAction(RiskDecisionEnum.PASS.getCode());
        int stageOrder = STAGE_BLACK_WHITE.code().equals(stage.code())
                ? blackWhiteStageOrder(detail)
                : stage.order();
        detail.markStage(stage.code(), stage.name(), stageOrder, MATCH_MISS, EFFECT_ALLOW);
        detail.setDecisionReason(detailDecisionReason(detail, MATCH_MISS));
        return detail;
    }

    /**
     * 计算黑白名单仲裁顺序：同一风险带内白名单先于黑名单。
     */
    private int blackWhiteStageOrder(RiskListMatch detail) {
        boolean whitelist = detail != null && "WHITE".equalsIgnoreCase(detail.getModuleType());
        return switch (riskBand(detail == null ? null : detail.getRiskLevel())) {
            case STRONG -> whitelist ? 41 : 42;
            case PRIORITY -> whitelist ? 43 : 44;
            case WEAK -> whitelist ? 45 : 46;
        };
    }

    /**
     * 按阶段既定顺序返回首条需要终止评估且动作非 PASS 的明细。
     */
    private Optional<RiskListMatch> firstBlockingDetail(List<RiskListMatch> details) {
        return details.stream()
                .filter(this::requiresEvaluationStop)
                .filter(detail -> !RiskDecisionEnum.PASS.getCode().equalsIgnoreCase(detail.getDecisionAction()))
                .findFirst();
    }

    /**
     * 判断明细是否为实际命中或基础设施错误，二者均不得被静默忽略。
     */
    private boolean requiresEvaluationStop(RiskListMatch detail) {
        return detail != null
                && (detail.actualHit() || "ERROR".equalsIgnoreCase(detail.getMatchResult()));
    }

    /**
     * 使用脱敏命中值、功能名称和最终动作生成人工可读的节点原因。
     *
     * @return 不包含完整卡号、邮箱、电话或地址原文的原因文本
     */
    private String detailDecisionReason(RiskListMatch detail, String matchResult) {
        String element = displayElement(detail.getHitElement());
        String value = StringUtils.hasText(detail.getHitValueMasked()) ? detail.getHitValueMasked() : "EMPTY";
        String functionName = StringUtils.hasText(detail.getFunctionName())
                ? detail.getFunctionName()
                : detail.getFunctionCode();
        String prefix = element + "：" + value + " ";
        if (MATCH_MISS.equalsIgnoreCase(matchResult)) {
            RiskDecisionEnum missDecision = resolveDecision(detail, RiskDecisionEnum.PASS);
            if (missDecision == RiskDecisionEnum.REJECT) {
                return prefix + "不在" + functionName + "中，单节点拦截";
            }
            if (missDecision == RiskDecisionEnum.REVIEW) {
                return prefix + "不在" + functionName + "中，进入人工复核";
            }
            if ("WHITE".equalsIgnoreCase(detail.getModuleType())) {
                return prefix + "未命中" + functionName + "，继续执行后续风控";
            }
            return prefix + "不在" + functionName + "中，放行";
        }
        RiskDecisionEnum decision = resolveDecision(detail, RiskDecisionEnum.PASS);
        return switch (decision) {
            case REJECT -> prefix + "触发" + functionName + "，单节点拦截";
            case REVIEW -> prefix + "触发" + functionName + "，进入人工复核";
            case REQUIRE_3DS -> prefix + "触发" + functionName + "，进入3DS验证";
            default -> prefix + "命中" + functionName + "，放行";
        };
    }

    /**
     * 选择可进入审计明细的脱敏值，优先使用专用掩码。
     *
     * @return 脱敏文本、国家代码、来源主机或区间数值；无值时返回 {@code EMPTY}
     */
    private String maskedLookupValue(RiskRuntimeLookupValue lookupValue) {
        if (lookupValue == null) {
            return "EMPTY";
        }
        if (StringUtils.hasText(lookupValue.getMatchValueMasked())) {
            return lookupValue.getMatchValueMasked();
        }
        if (StringUtils.hasText(lookupValue.getCountryAlpha3())) {
            return lookupValue.getCountryAlpha3();
        }
        if (StringUtils.hasText(lookupValue.getSourceHost())) {
            return lookupValue.getSourceHost();
        }
        return lookupValue.getNumericValue() == null
                ? "EMPTY"
                : lookupValue.getNumericValue().toPlainString();
    }

    /**
     * 复制仓储规则，避免阶段信息和原因文本污染缓存对象。
     */
    private RiskListMatch copyMatch(RiskListMatch source) {
        RiskListMatch target = new RiskListMatch();
        target.setRuleId(source.getRuleId());
        target.setModuleType(source.getModuleType());
        target.setFunctionCode(source.getFunctionCode());
        target.setFunctionName(source.getFunctionName());
        target.setHitElement(source.getHitElement());
        target.setHitValueMasked(source.getHitValueMasked());
        target.setRiskLevel(source.getRiskLevel());
        target.setDecisionAction(source.getDecisionAction());
        target.setDecisionReason(source.getDecisionReason());
        target.setTimeWindowSeconds(source.getTimeWindowSeconds());
        target.setThresholdCount(source.getThresholdCount());
        target.setElementsJson(source.getElementsJson());
        target.setCurrentCount(source.getCurrentCount());
        target.setAmountLimit(source.getAmountLimit());
        target.setCurrentAmount(source.getCurrentAmount());
        target.setStageCode(source.getStageCode());
        target.setStageName(source.getStageName());
        target.setStageOrder(source.getStageOrder());
        target.setMatchResult(source.getMatchResult());
        target.setDecisionEffect(source.getDecisionEffect());
        return target;
    }

    /**
     * 将内部名单元素编码映射为稳定的审计展示名称。
     */
    private String displayElement(String element) {
        if (!StringUtils.hasText(element)) {
            return "RiskElement";
        }
        return switch (element) {
            case "card", "cardNo" -> "CardNo";
            case "cardBin" -> "CardBin";
            case "ip" -> "IP";
            case "email" -> "Email";
            case "emailDomain" -> "EmailDomain";
            case "emailUsername" -> "EmailUsername";
            case "phone" -> "Phone";
            case "cardFingerprint" -> "CardFingerprint";
            case "cardholderName" -> "CardholderName";
            case "legalPerson" -> "LegalPerson";
            case "enterprise" -> "Enterprise";
            case "merchantBillingAddress" -> "MerchantBillingAddress";
            case "billingAddress" -> "BillingAddress";
            case "billingZip" -> "BillingZip";
            case "region" -> "Region";
            case "shippingAddress" -> "ShippingAddress";
            case "shippingZip" -> "ShippingZip";
            case "shippingCountry" -> "ShippingCountry";
            case "customerId" -> "CustomerId";
            case "deviceFingerprint" -> "DeviceFingerprint";
            case "tradeCountry" -> "TradeCountry";
            case "billingCountry" -> "BillingCountry";
            case "issuerCountry" -> "IssuerCountry";
            case "country" -> "Country";
            case "sourceUrl" -> "SourceUrl";
            case "merchant" -> "MerchantId";
            default -> element;
        };
    }

    /**
     * 为已执行但无逐规则明细的阶段创建显式 PASS 检查点。
     */
    private RiskListMatch checkpoint(Stage stage,
                                     String matchResult,
                                     RiskDecisionEnum decisionEnum,
                                     String decisionReason) {
        return RiskListMatch.checkpoint(
                stage.code(),
                stage.name(),
                stage.order(),
                matchResult,
                decisionEnum.getCode(),
                decisionReason);
    }

    /**
     * 将规则动作映射为流程影响：放行、阻断、复核、挑战或未知。
     */
    private String decisionEffect(RiskListMatch match) {
        RiskDecisionEnum decisionEnum = resolveDecision(match, RiskDecisionEnum.PASS);
        if (RiskDecisionEnum.REJECT == decisionEnum) {
            return EFFECT_BLOCK;
        }
        if (RiskDecisionEnum.REVIEW == decisionEnum) {
            return EFFECT_REVIEW;
        }
        if (RiskDecisionEnum.REQUIRE_3DS == decisionEnum) {
            return EFFECT_CHALLENGE;
        }
        return RiskDecisionEnum.PASS == decisionEnum ? EFFECT_ALLOW : EFFECT_NONE;
    }

    /**
     * 通过卡 BIN 解析发卡行国家并再次规范化为国家查询值。
     *
     * @return 国家查询值；仓储未装配或 BIN 无记录时返回 {@code null}
     */
    private RiskRuntimeLookupValue resolveIssuerCountry(RiskRuntimeLookupValue cardBinLookup) {
        if (riskListRuntimeRepository == null) {
            return null;
        }
        return riskListRuntimeRepository.findIssuerCountryByCardBin(cardBinLookup)
                .map(valueNormalizer::country)
                .orElse(null);
    }

    /**
     * 按声明顺序短路执行候选名单查询。
     *
     * @return 首个非空命中；全部未命中时返回空
     */
    @SafeVarargs
    private Optional<RiskListMatch> firstPresent(SupplierWithOptional... suppliers) {
        for (SupplierWithOptional supplier : suppliers) {
            Optional<RiskListMatch> match = supplier.get();
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    /**
     * 在没有任何运行时规则明细时执行显式开启的兼容骨架规则。
     *
     * <p>该路径仅用于兼容测试和受控环境；关闭开关时不参与生产决策。</p>
     *
     * @return 骨架规则阻断、复核或 3DS 结果；未触发时返回空
     */
    private Optional<RiskEvaluationOutcome> evaluateSkeletonFallback(RiskPaymentEvaluateRequestDTO requestDTO,
                                                                     String riskRecordNo) {
        if (!properties.isSkeletonFallbackEnabled()) {
            return Optional.empty();
        }
        if (isBlockedSource(requestDTO.getSourceUrl())) {
            RiskListMatch hit = RiskListMatch.system("skeletonSource", "骨架来源阻断", "sourceUrl", maskUrl(requestDTO.getSourceUrl()),
                    RISK_LEVEL_HIGH, RiskDecisionEnum.REJECT.getCode(), RiskReasonCodeEnum.BLOCKED_SOURCE.getMessage());
            return Optional.of(RiskEvaluationOutcome.of(
                    buildResult(RiskDecisionEnum.REJECT, RiskReasonCodeEnum.BLOCKED_SOURCE, riskRecordNo),
                    List.of(hit)));
        }
        if (isBlockedPayerIp(requestDTO.getPayerIp())) {
            RiskListMatch hit = RiskListMatch.system("skeletonIp", "骨架IP阻断", "ip", requestDTO.getPayerIp(),
                    RISK_LEVEL_HIGH, RiskDecisionEnum.REJECT.getCode(), RiskReasonCodeEnum.BLOCKED_IP.getMessage());
            return Optional.of(RiskEvaluationOutcome.of(
                    buildResult(RiskDecisionEnum.REJECT, RiskReasonCodeEnum.BLOCKED_IP, riskRecordNo),
                    List.of(hit)));
        }
        if (requestDTO.getAmount().compareTo(MANUAL_REVIEW_AMOUNT) >= 0) {
            RiskListMatch hit = RiskListMatch.system("skeletonAmountReview", "骨架金额复核", "amount", requestDTO.getAmount().toPlainString(),
                    RISK_LEVEL_MEDIUM, RiskDecisionEnum.REVIEW.getCode(), RiskReasonCodeEnum.MANUAL_REVIEW_REQUIRED.getMessage());
            return Optional.of(RiskEvaluationOutcome.of(
                    buildResult(RiskDecisionEnum.REVIEW,
                            RiskReasonCodeEnum.MANUAL_REVIEW_REQUIRED, riskRecordNo),
                    List.of(hit)));
        }
        if (requestDTO.getAmount().compareTo(REQUIRE_3DS_AMOUNT) >= 0 && !hasThreeDsProof(requestDTO)) {
            RiskListMatch hit = RiskListMatch.system("skeletonAmount3ds", "骨架3DS要求", "amount", requestDTO.getAmount().toPlainString(),
                    RISK_LEVEL_MEDIUM, RiskDecisionEnum.REQUIRE_3DS.getCode(), RiskReasonCodeEnum.THREE_DS_REQUIRED.getMessage());
            return Optional.of(RiskEvaluationOutcome.of(
                    buildResult(RiskDecisionEnum.REQUIRE_3DS,
                            RiskReasonCodeEnum.THREE_DS_REQUIRED, riskRecordNo),
                    List.of(hit)));
        }
        return Optional.empty();
    }

    /**
     * 将规则动作解析为统一决策枚举，并兼容历史 {@code FORCE_3DS} 动作。
     *
     * @return 已知统一决策；动作缺失或未知时返回调用方指定默认值
     */
    private RiskDecisionEnum resolveDecision(RiskListMatch match, RiskDecisionEnum defaultDecision) {
        if (match == null || !StringUtils.hasText(match.getDecisionAction())) {
            return defaultDecision;
        }
        String action = match.getDecisionAction().trim().toUpperCase(Locale.ROOT);
        if (RiskDecisionEnum.REJECT.getCode().equals(action)) {
            return RiskDecisionEnum.REJECT;
        }
        if (RiskDecisionEnum.REVIEW.getCode().equals(action)) {
            return RiskDecisionEnum.REVIEW;
        }
        if (RiskDecisionEnum.REQUIRE_3DS.getCode().equals(action) || "FORCE_3DS".equals(action)) {
            return RiskDecisionEnum.REQUIRE_3DS;
        }
        return RiskDecisionEnum.PASS.getCode().equals(action) ? RiskDecisionEnum.PASS : defaultDecision;
    }

    /**
     * 选择交易国家规则的主国家，优先使用账单国家，其次使用子商户国家。
     */
    private String primaryCountry(RiskPaymentEvaluateRequestDTO requestDTO) {
        if (StringUtils.hasText(requestDTO.getBillingCountry())) {
            return requestDTO.getBillingCountry();
        }
        return requestDTO.getSubMerchantCountryCode();
    }

    /** Return an audit label that preserves whether the transaction country came from billing or sub-merchant data. */
    private String tradeCountryAuditElement(RiskPaymentEvaluateRequestDTO requestDTO) {
        return hasText(requestDTO.getBillingCountry()) ? "billingCountry" : "tradeCountry";
    }

    /** Check one optional risk input without converting missing personal data into an EMPTY lookup. */
    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    /** Check whether a composite address dimension has at least one merchant-provided component. */
    private boolean hasAnyText(String... values) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (hasText(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算已执行节点中的最高风险等级，供后续 3DS 规则判断。
     */
    private String highestRiskLevel(List<RiskListMatch> hits) {
        return hits.stream()
                .map(RiskListMatch::getRiskLevel)
                .filter(StringUtils::hasText)
                .max(Comparator.comparingInt(this::riskWeight))
                .orElse(RISK_LEVEL_LOW);
    }

    /**
     * 将风险等级映射为可比较权重。
     *
     * @return CRITICAL 为 4、HIGH 为 3、MEDIUM 为 2，其余为 1
     */
    private int riskWeight(String riskLevel) {
        if (!StringUtils.hasText(riskLevel)) {
            return 1;
        }
        return switch (riskLevel.trim().toUpperCase(Locale.ROOT)) {
            case RISK_LEVEL_CRITICAL -> 4;
            case RISK_LEVEL_HIGH -> 3;
            case RISK_LEVEL_MEDIUM -> 2;
            default -> 1;
        };
    }

    /**
     * 将风险等级归入黑白名单的强、优先或弱仲裁带。
     */
    private RiskBand riskBand(String riskLevel) {
        int weight = riskWeight(riskLevel);
        if (weight >= 4) {
            return RiskBand.STRONG;
        }
        if (weight >= 3) {
            return RiskBand.PRIORITY;
        }
        return RiskBand.WEAK;
    }

    /**
     * 将最终决策和全部脱敏节点明细发布到可靠审计仓储。
     *
     * <p>发布器未装配时兼容测试环境跳过；消息不包含完整卡号、CVV 或密钥。</p>
     */
    private void publishAudit(RiskPaymentEvaluateRequestDTO requestDTO, RiskEvaluationOutcome outcome) {
        if (auditRecordPublisher == null || requestDTO == null || outcome == null || outcome.getResult() == null) {
            return;
        }
        RiskEvaluationAuditMessage message = new RiskEvaluationAuditMessage();
        message.setRiskRecordNo(outcome.getResult().getRiskRecordNo());
        message.setMerchantId(requestDTO.getMerchantId());
        message.setMerchantOrderNo(requestDTO.getMerchantOrderNo());
        message.setPaymentOrderNo(requestDTO.getTransactionId());
        message.setTransactionAmount(requestDTO.getAmount());
        message.setTransactionCurrency(requestDTO.getCurrency());
        message.setRiskLevel(highestRiskLevel(outcome.getHits()));
        message.setDecisionResult(outcome.getResult().getDecision());
        message.setDecisionReason(outcome.getResult().getReasonMessage());
        message.setHitCount(hitCount(outcome.getHits(), outcome.getResult().getDecision()));
        message.setEvaluationTime(outcome.getResult().getDecisionTime());
        message.setHits(outcome.getHits().stream().map(this::toAuditHitMessage).toList());
        auditRecordPublisher.publish(message);
    }

    /**
     * 将 Risk 内部命中模型转换为跨服务 MQ DTO，阻止消费者依赖 Risk 领域包。
     *
     * @param match 已完成脱敏的风控执行明细
     * @return 仅包含审计字段的 MQ 命中明细
     */
    private RiskAuditHitMessage toAuditHitMessage(RiskListMatch match) {
        RiskAuditHitMessage message = new RiskAuditHitMessage();
        message.setRuleId(match.getRuleId());
        message.setModuleType(match.getModuleType());
        message.setFunctionCode(match.getFunctionCode());
        message.setFunctionName(match.getFunctionName());
        message.setHitElement(match.getHitElement());
        message.setHitValueMasked(match.getHitValueMasked());
        message.setRiskLevel(match.getRiskLevel());
        message.setDecisionAction(match.getDecisionAction());
        message.setDecisionReason(match.getDecisionReason());
        message.setTimeWindowSeconds(match.getTimeWindowSeconds());
        message.setThresholdCount(match.getThresholdCount());
        message.setElementsJson(match.getElementsJson());
        message.setCurrentCount(match.getCurrentCount());
        message.setAmountLimit(match.getAmountLimit());
        message.setCurrentAmount(match.getCurrentAmount());
        message.setStageCode(match.getStageCode());
        message.setStageName(match.getStageName());
        message.setStageOrder(match.getStageOrder());
        message.setMatchResult(match.getMatchResult());
        message.setDecisionEffect(match.getDecisionEffect());
        return message;
    }

    /**
     * 统计最终 PASS 决策中实际触发但产生放行效果的规则数量。
     *
     * @return 非 PASS 决策或无明细时返回零
     */
    private int hitCount(List<RiskListMatch> matches, String finalDecision) {
        if (!RiskDecisionEnum.PASS.getCode().equalsIgnoreCase(finalDecision)
                || matches == null || matches.isEmpty()) {
            return 0;
        }
        return (int) matches.stream()
                .filter(this::countsAsTriggeredRule)
                .count();
    }

    /**
     * 判断命中明细是否属于最终放行决策中的有效触发规则。
     */
    private boolean countsAsTriggeredRule(RiskListMatch match) {
        return match != null
                && match.actualHit()
                && EFFECT_ALLOW.equalsIgnoreCase(match.getDecisionEffect());
    }

    /**
     * 使用单调时钟计算本次评估耗时。
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 脱敏来源 URL 查询参数。
     *
     * @param sourceUrl 风控输入的来源 URL
     * @return 可写入日志的 URL 摘要
     */
    private String maskUrl(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            return null;
        }
        int queryIndex = sourceUrl.indexOf('?');
        if (queryIndex < 0) {
            return sourceUrl;
        }
        return sourceUrl.substring(0, queryIndex) + "?...";
    }

    private boolean isInvalid(RiskPaymentEvaluateRequestDTO requestDTO) {
        return requestDTO == null
                || !StringUtils.hasText(requestDTO.getMerchantId())
                || !StringUtils.hasText(requestDTO.getMerchantOrderNo())
                || !StringUtils.hasText(requestDTO.getCurrency())
                || requestDTO.getAmount() == null
                || requestDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * 检查兼容骨架规则中的来源 URL 阻断关键词。
     *
     * @param sourceUrl 商户请求来源页面 URL，仅在内存中比较且不得完整写入日志
     * @return 忽略大小写后包含受控阻断关键词时返回 {@code true}
     */
    private boolean isBlockedSource(String sourceUrl) {
        return StringUtils.hasText(sourceUrl)
                && sourceUrl.toLowerCase(Locale.ROOT).contains(BLOCKED_SOURCE_KEYWORD);
    }

    /**
     * 检查兼容骨架规则中的精确付款方 IP 阻断集合。
     *
     * @param payerIp 已由请求提供的付款方 IP
     * @return 去除首尾空白后命中固定阻断集合时返回 {@code true}
     */
    private boolean isBlockedPayerIp(String payerIp) {
        return StringUtils.hasText(payerIp) && BLOCKED_PAYER_IPS.contains(payerIp.trim());
    }

    /**
     * 判断请求是否携带至少一种 3DS 验证证明。
     *
     * @param requestDTO 当前支付风控请求
     * @return ECI、3DS 版本或 3DS 交易号任一存在时返回 {@code true}
     */
    private boolean hasThreeDsProof(RiskPaymentEvaluateRequestDTO requestDTO) {
        return StringUtils.hasText(requestDTO.getThreeDsEci())
                || StringUtils.hasText(requestDTO.getThreeDsVersion())
                || StringUtils.hasText(requestDTO.getThreeDsTransactionId());
    }

    /**
     * 构建包含稳定风控流水号、统一原因码和决策时间的响应。
     *
     * @param decisionEnum 最终统一风控决策
     * @param reasonCodeEnum 与决策对应的稳定原因码
     * @param riskRecordNo 本次评估流水号
     * @return 可返回支付服务的风控结果
     */
    private RiskPaymentEvaluateResultDTO buildResult(RiskDecisionEnum decisionEnum,
                                                     RiskReasonCodeEnum reasonCodeEnum,
                                                     String riskRecordNo) {
        RiskPaymentEvaluateResultDTO resultDTO = new RiskPaymentEvaluateResultDTO();
        resultDTO.setRiskRecordNo(riskRecordNo);
        resultDTO.setDecision(decisionEnum.getCode());
        resultDTO.setReasonCode(reasonCodeEnum.getCode());
        resultDTO.setReasonMessage(reasonCodeEnum.getMessage());
        resultDTO.setDecisionTime(LocalDateTime.now());
        return resultDTO;
    }

    @FunctionalInterface
    private interface SupplierWithOptional {
        /**
         * 延迟执行一次名单查询，供候选规则按声明顺序短路。
         *
         * @return 当前候选规则的命中明细；未命中时返回空
         */
        Optional<RiskListMatch> get();
    }

    private record LookupContext(RiskRuntimeLookupValue cardNoLookup,
                                 RiskRuntimeLookupValue cardFingerprintLookup,
                                 RiskRuntimeLookupValue cardBinLookup,
                                 RiskRuntimeLookupValue ipLookup,
                                 RiskRuntimeLookupValue billingEmailLookup,
                                 RiskRuntimeLookupValue billingEmailDomainLookup,
                                 RiskRuntimeLookupValue billingEmailUsernameLookup,
                                 RiskRuntimeLookupValue billingPhoneLookup,
                                 RiskRuntimeLookupValue tradeCountryLookup,
                                 RiskRuntimeLookupValue billingCountryLookup,
                                 RiskRuntimeLookupValue issuerCountryLookup,
                                 RiskRuntimeLookupValue sourceHostLookup,
                                 RiskRuntimeLookupValue cardholderNameLookup,
                                 RiskRuntimeLookupValue legalPersonLookup,
                                 RiskRuntimeLookupValue enterpriseLookup,
                                 RiskRuntimeLookupValue merchantBillingAddressLookup,
                                 RiskRuntimeLookupValue billingAddressLookup,
                                 RiskRuntimeLookupValue billingZipLookup,
                                 RiskRuntimeLookupValue billingRegionLookup,
                                 RiskRuntimeLookupValue payerEmailLookup,
                                 RiskRuntimeLookupValue payerEmailDomainLookup,
                                 RiskRuntimeLookupValue payerEmailUsernameLookup,
                                 RiskRuntimeLookupValue payerPhoneLookup,
                                 RiskRuntimeLookupValue payerCountryLookup,
                                 RiskRuntimeLookupValue payerNameLookup,
                                 RiskRuntimeLookupValue payerAddressLookup,
                                 RiskRuntimeLookupValue payerZipLookup,
                                 RiskRuntimeLookupValue payerRegionLookup,
                                 RiskRuntimeLookupValue payerIdLookup,
                                 RiskRuntimeLookupValue shippingAddressLookup,
                                 RiskRuntimeLookupValue shippingZipLookup,
                                 RiskRuntimeLookupValue shippingCountryLookup,
                                 RiskRuntimeLookupValue shippingNameLookup,
                                 RiskRuntimeLookupValue shippingEmailLookup,
                                 RiskRuntimeLookupValue shippingEmailDomainLookup,
                                 RiskRuntimeLookupValue shippingEmailUsernameLookup,
                                 RiskRuntimeLookupValue shippingPhoneLookup,
                                 RiskRuntimeLookupValue shippingRegionLookup,
                                 RiskRuntimeLookupValue customerIdLookup,
                                 RiskRuntimeLookupValue deviceFingerprintLookup) {
    }

    /** 三个独立只读规则组完成后交给请求线程确定性汇总的结果。 */
    private record ReadOnlyEvaluation(List<RiskListMatch> amlDetails,
                                      BlackWhiteEvaluation blackWhiteEvaluation,
                                      MerchantLimitReadOnlyEvaluation merchantLimitEvaluation) {
    }

    /** 白名单与黑名单查询明细，列表顺序与规则声明顺序一致。 */
    private record BlackWhiteEvaluation(List<RiskListMatch> whitelistDetails,
                                        List<RiskListMatch> blacklistDetails) {
    }

    /** 单笔限额命中和启用状态，不包含累计限额预占结果。 */
    private record MerchantLimitReadOnlyEvaluation(Optional<RiskListMatch> limitHit,
                                                   boolean activeMerchantLimitRule) {
    }

    private record Stage(String code, String name, int order) {
    }

    private record ListCheck(RiskListFunction function,
                             RiskRuntimeLookupValue lookupValue,
                             String auditElement) {

        private ListCheck(RiskListFunction function, RiskRuntimeLookupValue lookupValue) {
            this(function, lookupValue, function.getFunctionCode());
        }
    }

    private enum RiskBand {
        /** CRITICAL 风险带，在仲裁中最先处理。 */
        STRONG,

        /** HIGH 风险带，优先级低于强风险带、高于弱风险带。 */
        PRIORITY,

        /** MEDIUM、LOW 或未知等级使用的兜底风险带。 */
        WEAK
    }
}
