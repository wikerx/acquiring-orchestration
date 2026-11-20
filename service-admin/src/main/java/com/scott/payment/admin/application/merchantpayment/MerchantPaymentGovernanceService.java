package com.scott.payment.admin.application.merchantpayment;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.*;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.scott.payment.admin.application.merchantpayment.MerchantPaymentRepository.*;

/** 支付方式申请与正式授权的唯一写入入口。 */
@Service
@DS(DataSourceName.MASTER)
public class MerchantPaymentGovernanceService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String PENDING = "PENDING_REVIEW";
    private static final String RETURNED = "RETURNED";
    private static final String APPROVED = "APPROVED";
    private static final String ACTIVE = "ACTIVE";
    private static final String SUSPENDED = "SUSPENDED";
    private static final String TERMINATED = "TERMINATED";
    private static final String LEGACY_SCOPE = "LEGACY_SCOPE";
    private static final String LEGACY_OBSERVED = "LEGACY_OBSERVED";
    private static final String PENDING_VERIFICATION = "PENDING_VERIFICATION";

    private final MerchantPaymentRepository repository;
    private final AdminMerchantDataScopeResolver scopes;
    private final GlobalIdGenerator ids;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    private final MerchantPaymentMethodReadiness readiness;

    MerchantPaymentGovernanceService(MerchantPaymentRepository repository, AdminMerchantDataScopeResolver scopes,
                                     GlobalIdGenerator ids, ObjectMapper json, ApplicationEventPublisher events,
                                     MerchantPaymentMethodReadiness readiness) {
        this.repository = repository;
        this.scopes = scopes;
        this.ids = ids;
        this.json = json;
        this.events = events;
        this.readiness = readiness;
    }

    public Scope adminScope() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !"ADMIN".equals(account.getAppCode())
                || account.getAccountId() == null || account.getUserId() == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return new Scope(scopes.resolve(account),
                new Actor("ADMIN", account.getAccountId(), account.getUserId(), account.getRealName()));
    }

    public Scope merchantScope(String merchant, Long account, Long user) {
        if (account == null || user == null || merchant == null || merchant.isBlank()
                || !repository.verifiedMerchantAccount(merchant, account, user)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return new Scope(AdminMerchantDataScope.limited(Set.of(merchant)), new Actor("MERCHANT", account, user, null));
    }

    public PageResult<MethodRow> methods(Scope scope, String merchant, Search query) {
        requireScope(scope, merchant);
        if (!repository.merchantExists(merchant)) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        Search input = query == null ? new Search() : query;
        var directory = enabledDirectory(false);
        var items = repository.items(merchant).stream().collect(Collectors.toMap(Item::itemKey, Function.identity()));
        var grants = repository.grants(merchant).stream().collect(Collectors.toMap(Grant::itemKey, Function.identity()));
        Set<String> frozen = repository.frozenMethods(merchant);
        var keys = new java.util.LinkedHashSet<>(directory.keySet());
        keys.addAll(frozen.stream().sorted().toList());
        keys.addAll(grants.keySet().stream().sorted().toList());
        keys.addAll(items.keySet().stream().sorted().toList());
        LocalDateTime now = now();
        var assessment = readiness.evaluate(merchant, now);
        var rows = keys.stream()
                .filter(key -> input.getItemKey() == null || input.getItemKey().equals(key))
                .map(key -> view(key, directory.get(key), items.get(key), grants.get(key), now,
                        assessment.forMethod(key), frozen.contains(key)))
                .filter(row -> input.getStatus() == null || input.getStatus().isBlank()
                        || input.getStatus().equals(row.authorizationStatus()))
                .toList();
        long page = input.safePageNo();
        long size = Math.min(input.safePageSize(), 100);
        return PageResult.of(rows.size(), page, size, rows.stream().skip((page - 1) * size).limit(size).toList());
    }

    public MethodRow method(Scope scope, String merchant, String itemKey) {
        Search query = new Search();
        query.setItemKey(normalizeKey(itemKey));
        return methods(scope, merchant, query).getRecords().stream().findFirst().orElseThrow(
                () -> new ServiceException(ApiResultEnum.ORDER_NOT_FOUND));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public FeeAuthorizationProjection feeAuthorizations(Scope scope, String merchant) {
        requireScope(scope, merchant);
        LegacyMerchant mode = merchantMode(merchant);
        if (historyPending(mode)) {
            return new FeeAuthorizationProjection("HISTORY_PENDING", List.of());
        }
        if (!knownMode(mode)) {
            return new FeeAuthorizationProjection("UNAVAILABLE", List.of());
        }
        var facts = authorizationFacts(merchant, now());
        return new FeeAuthorizationProjection("EVALUATED", facts.coverage());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccessSummary accessSummary(Scope scope, String merchant) {
        requireScope(scope, merchant);
        LegacyMerchant mode = merchantMode(merchant);
        if (historyPending(mode)) {
            return AccessSummary.unavailable("HISTORY_PENDING");
        }
        if (!knownMode(mode)) {
            return AccessSummary.unavailable("UNAVAILABLE");
        }
        LocalDateTime now = now();
        var facts = authorizationFacts(merchant, now);
        return readiness.evaluate(merchant, now).accessSummary(facts, "HOLD".equals(mode.mode()), now);
    }

    private LegacyMerchant merchantMode(String merchant) {
        return repository.legacyMerchant(merchant).orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_NOT_FOUND));
    }

    private static boolean historyPending(LegacyMerchant merchant) {
        return merchant.mode() == null || "LEGACY_OBSERVE".equals(merchant.mode()) && merchant.batch() == null;
    }

    private static boolean knownMode(LegacyMerchant merchant) {
        return Set.of("ENFORCED", "LEGACY_OBSERVE", "HOLD").contains(merchant.mode());
    }

    private MerchantPaymentMethodReadiness.AuthorityFacts authorizationFacts(String merchant, LocalDateTime now) {
        Map<String, String> directory = enabledDirectory(false);
        Map<String, Grant> grants = repository.grants(merchant).stream()
                .collect(Collectors.toMap(Grant::itemKey, Function.identity()));
        Set<String> frozen = repository.frozenMethods(merchant);
        Set<String> keys = new java.util.TreeSet<>(directory.keySet());
        keys.addAll(grants.keySet());
        keys.addAll(frozen);
        var coverage = keys.stream().map(key -> {
            Grant grant = grants.get(key);
            String status = grant == null ? (frozen.contains(key) ? LEGACY_OBSERVED : "NOT_AUTHORIZED")
                    : effectiveStatus(grant.status(), grant.effective(), grant.expires(), now);
            if (grant != null && ACTIVE.equals(status) && "HISTORICAL_BASELINE".equals(grant.source())
                    && frozen.contains(key)) {
                status = LEGACY_OBSERVED;
            }
            String name = directory.getOrDefault(key, key.substring(key.indexOf(':') + 1));
            return new AuthorizationCoverage(key, name, status);
        }).toList();
        return new MerchantPaymentMethodReadiness.AuthorityFacts(coverage, Set.copyOf(directory.keySet()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<ApplicationRow> applications(Scope scope, Search search) {
        Search query = search == null ? new Search() : search;
        if (query.getMerchantId() != null && !query.getMerchantId().isBlank()) {
            requireScope(scope, query.getMerchantId());
        }
        PageResult<Batch> page = repository.batches(scope.merchants(), query);
        Map<String, List<Item>> grouped = repository.listBatches(page.getRecords()).stream()
                .collect(Collectors.groupingBy(Item::application));
        return PageResult.of(page.getTotal(), page.getPageNo(), page.getPageSize(), page.getRecords().stream()
                .map(batch -> applicationView(batch, grouped.getOrDefault(batch.application(), List.of()), Map.of()))
                .toList());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ApplicationRow application(Scope scope, String application) {
        String merchant = repository.batchMerchant(scope.merchants(), application)
                .orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_NOT_FOUND));
        Batch batch = new Batch(merchant, application);
        Map<Long, List<HistoryRow>> history = repository.itemReviews(merchant, application).stream()
                .collect(Collectors.groupingBy(ItemReview::itemId,
                        Collectors.mapping(ItemReview::history, Collectors.toList())));
        return applicationView(batch, repository.listBatches(List.of(batch)), history);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public BatchReceipt apply(Scope scope, String merchant, Apply input) {
        requireScope(scope, merchant);
        String reason = reason(input.reason(), "ADMIN".equals(scope.actor().app()));
        List<String> keys = input.items().stream().map(MerchantPaymentGovernanceService::normalizeKey)
                .distinct().sorted().toList();
        byte[] hash = fingerprint(scope.actor(), "SUBMIT", merchant, reason, keys);
        long revision = lockMerchant(merchant);
        BatchReceipt previous = replay(merchant, input.requestId(), hash, BatchReceipt.class).orElse(null);
        if (previous != null) {
            return previous;
        }

        Map<String, Grant> originalGrants = new LinkedHashMap<>();
        Map<String, String> failures = new LinkedHashMap<>();
        for (String key : keys) {
            Grant grant = repository.lockGrant(merchant, key).orElse(null);
            if (grant != null && !TERMINATED.equals(grant.status())) {
                failures.put(key, "已有授权");
            } else if (repository.hasActiveItem(merchant, key)) {
                failures.put(key, "已有进行中的申请");
            }
            originalGrants.put(key, grant);
        }
        Map<String, String> directory = enabledDirectory(true);
        for (String key : keys) {
            if (!directory.containsKey(key)) {
                failures.putIfAbsent(key, "当前未启用");
            }
        }
        if (!failures.isEmpty()) {
            throw invalid(failures.entrySet().stream().map(entry -> entry.getKey() + " " + entry.getValue())
                    .collect(Collectors.joining("、")));
        }

        String application = ids.nextId();
        LocalDateTime now = now();
        BatchReceipt receipt = new BatchReceipt(application, keys.size());
        repository.review(new Audit(merchant, application, "SUBMIT", "NOT_CREATED", PENDING, reason,
                "APPLICATION_BATCH", application, null, null, input.requestId(), hash, boundedJson(receipt),
                boundedJson(new BatchFact(1, keys)), now), scope.actor());
        String source = "ADMIN".equals(scope.actor().app()) ? "ADMIN_PROXY" : "MERCHANT";
        for (Map.Entry<String, Grant> original : originalGrants.entrySet()) {
            String key = original.getKey();
            Grant grant = original.getValue();
            String operation = grant == null ? "APPLY" : "REAPPLY";
            long grantVersion = grant == null ? 0L : grant.version();
            long item = repository.insertItem(merchant, key, application, input.requestId(), reason,
                    source, operation, grantVersion, scope.actor(), now);
            long audit = repository.review(new Audit(merchant, application, "SUBMIT", "NOT_CREATED", PENDING, reason,
                    "APPLICATION_ITEM", Long.toString(item), item, 1, null, null, null,
                    boundedJson(new ApplicationFact(1, key, operation, grantVersion, 1, 1)), now), scope.actor());
            publish(merchant, key, item, 1, audit, revision, "SUBMIT", now);
        }
        return receipt;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ItemReceipt decide(Scope scope, long id, Decision input) {
        requireApplicationRole(scope, "ADMIN");
        String merchant = findMerchant(scope, id);
        String reason = reason(input.reason(), !"APPROVE".equals(input.decision()));
        long expected = version(input.expectedVersion());
        byte[] hash = fingerprint(scope.actor(), input.decision(), Long.toString(id), expected, reason);
        long revision = lockMerchant(merchant);
        ItemReceipt replay = itemReplay(merchant, input.requestId(), hash);
        if (replay != null) {
            return replay;
        }
        Item item = lockItem(merchant, id);
        requireVersion(item.version(), expected);
        if (!PENDING.equals(item.status())) {
            throw invalid("申请状态已变化，请刷新");
        }
        if (repository.submittedThisRound(item, scope.actor().user())) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "不能复核本人提交的申请");
        }
        Grant grant = repository.lockGrant(merchant, item.itemKey()).orElse(null);
        requireExpectedGrant(item, grant);
        requireEnabled(enabledDirectory(true), item.itemKey());
        LocalDateTime now = now();
        String status = switch (input.decision()) {
            case "APPROVE" -> APPROVED;
            case "RETURN" -> RETURNED;
            case "REJECT" -> "REJECTED";
            default -> throw invalid("申请决策无效");
        };
        Long grantVersion = APPROVED.equals(status) ? (grant == null ? 1L : Math.incrementExact(grant.version())) : null;
        ItemReceipt receipt = itemReceipt(item, status, Math.incrementExact(item.version()), grantVersion);
        Object facts = new ActionFact(1, item.itemKey(), item.round(), item.version(), item.version() + 1,
                grant == null ? null : grant.version(), grantVersion, grant == null ? null : grant.status(),
                APPROVED.equals(status) ? ACTIVE : null, grant == null ? null : grant.effective(),
                grant == null ? null : grant.expires(), APPROVED.equals(status) ? now : null, null);
        long reviewId = reviewItem(item, scope.actor(), input.decision(), status, reason, item.round(),
                input.requestId(), hash, receipt, facts, now);
        if (APPROVED.equals(status)) {
            repository.approve(item, grant, scope.actor(), reviewId, now);
            revision = repository.advanceRevision(merchant, revision);
        }
        requireChanged(repository.transitionItem(item, status, now));
        publish(merchant, item.itemKey(), item.id(), item.round(), reviewId, revision, input.decision(), now);
        return withReview(receipt, reviewId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ItemReceipt changeApplication(Scope scope, long id, Change input, boolean resubmit) {
        requireApplicationRole(scope, "MERCHANT");
        String merchant = findMerchant(scope, id);
        String action = resubmit ? "RESUBMIT" : "WITHDRAW";
        String reason = reason(input.reason(), resubmit);
        rejectUnexpectedTimes(input);
        long expected = version(input.expectedVersion());
        byte[] hash = fingerprint(scope.actor(), action, Long.toString(id), expected, reason);
        long revision = lockMerchant(merchant);
        ItemReceipt replay = itemReplay(merchant, input.requestId(), hash);
        if (replay != null) {
            return replay;
        }
        Item item = lockItem(merchant, id);
        requireVersion(item.version(), expected);
        if (!RETURNED.equals(item.status()) && (resubmit || !PENDING.equals(item.status()))) {
            throw invalid("申请状态已变化，请刷新");
        }
        int round = resubmit ? Math.incrementExact(item.round()) : item.round();
        String status = resubmit ? PENDING : "WITHDRAWN";
        LocalDateTime now = now();
        if (resubmit) {
            Grant grant = repository.lockGrant(merchant, item.itemKey()).orElse(null);
            requireExpectedGrant(item, grant);
            requireEnabled(enabledDirectory(true), item.itemKey());
            requireChanged(repository.resubmit(item, scope.actor(), reason, now));
        } else {
            requireChanged(repository.transitionItem(item, status, now));
        }
        ItemReceipt receipt = itemReceipt(item, status, Math.incrementExact(item.version()), null);
        long reviewId = reviewItem(item, scope.actor(), action, status, reason, round, input.requestId(), hash,
                receipt, new ApplicationFact(1, item.itemKey(), item.operation(), item.expectedGrantVersion(),
                        round, item.version() + 1), now);
        publish(merchant, item.itemKey(), item.id(), round, reviewId, revision, action, now);
        return withReview(receipt, reviewId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ItemReceipt changeGrant(Scope scope, String merchant, String rawKey, String action, Change input) {
        requireApplicationRole(scope, "ADMIN");
        requireScope(scope, merchant);
        String key = normalizeKey(rawKey);
        String reason = reason(input.reason(), true);
        long expected = version(input.expectedVersion());
        LocalDateTime requestedEffective = milliseconds(input.effectiveTime());
        LocalDateTime requestedExpiry = milliseconds(input.expireTime());
        if (!"VALIDITY".equals(action)) {
            rejectUnexpectedTimes(input);
        }
        byte[] hash = fingerprint(scope.actor(), action, key, expected, reason, requestedEffective, requestedExpiry);
        long revision = lockMerchant(merchant);
        ItemReceipt replay = itemReplay(merchant, input.requestId(), hash);
        if (replay != null) {
            return replay;
        }
        Grant grant = repository.lockGrant(merchant, key).orElse(null);
        if (grant == null) {
            return restrictHistoricalGrant(scope.actor(), merchant, key, action, input, expected, reason, hash, revision);
        }
        requireVersion(grant.version(), expected);
        if ("RESTORE".equals(action) && "HISTORICAL_BASELINE".equals(grant.source())
                && !repository.frozenMethods(merchant).contains(key)) {
            throw invalid("历史兼容范围已失效，不允许恢复");
        }
        String status = nextGrantStatus(grant.status(), action);
        LocalDateTime effective = requestedEffective == null ? grant.effective() : requestedEffective;
        LocalDateTime expires = "VALIDITY".equals(action) ? requestedExpiry : grant.expires();
        if (expires != null && !expires.isAfter(effective)) {
            throw invalid("到期时间必须晚于生效时间");
        }
        LocalDateTime now = now();
        long nextVersion = Math.incrementExact(grant.version());
        ItemReceipt receipt = new ItemReceipt(null, null, key, effectiveStatus(status, effective, expires, now),
                Long.toString(nextVersion), Long.toString(nextVersion), null);
        var facts = new ActionFact(1, key, null, null, null, grant.version(), nextVersion, grant.status(), status,
                grant.effective(), grant.expires(), effective, expires);
        long reviewId = repository.review(new Audit(merchant, null, action, grant.status(), status, reason,
                "AUTHORIZATION", key, null, null, input.requestId(), hash, boundedJson(receipt),
                boundedJson(facts), now), scope.actor());
        repository.revise(merchant, grant, status, effective, expires, reviewId, now);
        revision = repository.advanceRevision(merchant, revision);
        publish(merchant, key, null, null, reviewId, revision, action, now);
        return withReview(receipt, reviewId);
    }

    private ItemReceipt restrictHistoricalGrant(Actor actor, String merchant, String key, String action, Change input,
                                               long expected, String reason, byte[] hash, long revision) {
        if (expected != 0L || !Set.of("SUSPEND", "TERMINATE").contains(action)
                || !repository.frozenMethods(merchant).contains(key)) {
            throw invalid("尚未开通该支付方式");
        }
        LegacyMerchant legacy = repository.legacyMerchant(merchant).orElseThrow(
                () -> new ServiceException(ApiResultEnum.ORDER_NOT_FOUND));
        LocalDateTime now = now();
        String status = "SUSPEND".equals(action) ? SUSPENDED : TERMINATED;
        ItemReceipt receipt = new ItemReceipt(null, null, key, status, "1", "1", null);
        var facts = new ActionFact(1, key, null, null, null, 0L, 1L, LEGACY_OBSERVED, status,
                null, null, now, null);
        long reviewId = repository.review(new Audit(merchant, legacy.batch(), action, LEGACY_OBSERVED, status, reason,
                "AUTHORIZATION", key, null, null, input.requestId(), hash, boundedJson(receipt),
                boundedJson(facts), now), actor);
        repository.restrictLegacy(merchant, key, legacy.batch(), status, reviewId, now);
        long changedRevision = repository.advanceRevision(merchant, revision);
        publish(merchant, key, null, null, reviewId, changedRevision, action, now);
        return withReview(receipt, reviewId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public BaselineReceipt submitBaseline(Scope scope, String merchant, BaselineSubmit request) {
        requireApplicationRole(scope, "ADMIN");
        requireScope(scope, merchant);
        String note = reason(request.reason(), true);
        LocalDateTime cutoff = milliseconds(request.cutoffTime());
        List<Evidence> evidence = request.items().stream().map(item -> new Evidence(normalizeKey(item.itemKey()),
                item.evidenceType(), item.evidenceRef(), milliseconds(item.evidenceTime())))
                .sorted(java.util.Comparator.comparing(Evidence::itemKey)).toList();
        if (evidence.stream().map(Evidence::itemKey).distinct().count() != evidence.size()) {
            throw invalid("历史兼容核验项目重复");
        }
        byte[] hash = fingerprint(scope.actor(), "BASELINE_SUBMIT", merchant, cutoff, note, evidence);
        lockMerchant(merchant);
        BaselineReceipt replay = baselineReplay(merchant, request.requestId(), hash);
        if (replay != null) {
            return replay;
        }
        LegacyMerchant legacy = allowFirstBaseline(merchant);
        LocalDateTime now = now();
        if (cutoff == null || cutoff.isAfter(now) || cutoff.isBefore(legacy.createdAt())
                || evidence.stream().anyMatch(item -> item.evidenceTime() == null || item.evidenceTime().isAfter(cutoff)
                || item.evidenceTime().isBefore(legacy.createdAt()))) {
            throw invalid("历史依据时间必须在商户创建后，且不晚于冻结截止时间");
        }
        String batch = ids.nextId();
        var result = new BaselineReceipt(batch, evidence.size(), evidence.size(), PENDING_VERIFICATION);
        repository.review(LEGACY_SCOPE, new Audit(merchant, batch, "BASELINE_SUBMIT", "NOT_CREATED",
                PENDING_VERIFICATION, note, "BASELINE_BATCH", batch, null, null, request.requestId(), hash,
                boundedJson(result), boundedJson(new BaselineFact(1, cutoff)), now), scope.actor());
        for (Evidence item : evidence) {
            repository.review(LEGACY_SCOPE, new Audit(merchant, batch, "BASELINE_CANDIDATE", "NOT_CREATED",
                    PENDING_VERIFICATION, null, LEGACY_SCOPE, item.itemKey(), null, null, null,
                    null, null, boundedJson(item), now), scope.actor());
        }
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BaselineDetail baseline(Scope scope, String merchant, String batch) {
        requireApplicationRole(scope, "ADMIN");
        requireScope(scope, merchant);
        var header = repository.pendingBaseline(merchant, batch)
                .orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_NOT_FOUND));
        BaselineFact fact = readJson(header.fact(), BaselineFact.class);
        List<String> approved = repository.approvedBaselineKeys(merchant, batch);
        return new BaselineDetail(batch, fact.cutoffTime(), baselineEvidence(merchant, batch), approved,
                approved.isEmpty() ? PENDING_VERIFICATION : APPROVED);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public BaselineReceipt approveBaseline(Scope scope, String merchant, String batch, BaselineApprove request) {
        requireApplicationRole(scope, "ADMIN");
        requireScope(scope, merchant);
        String note = reason(request.reason(), true);
        List<String> keys = request.approvedItemKeys().stream().map(MerchantPaymentGovernanceService::normalizeKey)
                .distinct().sorted().toList();
        byte[] hash = fingerprint(scope.actor(), "BASELINE_APPROVE", merchant, batch, keys, note);
        long revision = lockMerchant(merchant);
        BaselineReceipt replay = baselineReplay(merchant, request.requestId(), hash);
        if (replay != null) {
            return replay;
        }
        var header = repository.pendingBaseline(merchant, batch)
                .orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_NOT_FOUND));
        if (header.maker() == null || header.maker() == scope.actor().user()) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "不能复核本人提交的历史兼容基线");
        }
        allowFirstBaseline(merchant);
        List<Evidence> evidence = baselineEvidence(merchant, batch);
        Map<String, Evidence> candidates = evidence.stream().collect(Collectors.toMap(Evidence::itemKey, Function.identity()));
        if (keys.isEmpty() || !candidates.keySet().containsAll(keys)) {
            throw invalid("审批只能选取本批次中具有待核验依据的支付方式");
        }
        LocalDateTime now = now();
        long changedRevision = Math.incrementExact(revision);
        var result = new BaselineReceipt(batch, keys.size(), candidates.size() - keys.size(), APPROVED);
        repository.review(LEGACY_SCOPE, new Audit(merchant, batch, "BASELINE_REVIEW", PENDING_VERIFICATION,
                APPROVED, note, "BASELINE_BATCH", batch, null, null, request.requestId(), hash,
                boundedJson(result), boundedJson(new BatchFact(1, keys)), now), scope.actor());
        repository.bindLegacyBaseline(merchant, batch, revision, now);
        for (String key : keys) {
            long review = repository.review(LEGACY_SCOPE, new Audit(merchant, batch, "BASELINE_APPROVE",
                    PENDING_VERIFICATION, APPROVED, note, LEGACY_SCOPE, key, null, null, null,
                    null, null, boundedJson(candidates.get(key)), now), scope.actor());
            publish(merchant, key, null, null, review, changedRevision, "CONFIGURATION_CHANGED", now);
        }
        return result;
    }

    private List<Evidence> baselineEvidence(String merchant, String batch) {
        return repository.candidateEvidence(merchant, batch).stream().map(value -> readJson(value, Evidence.class)).toList();
    }

    private LegacyMerchant allowFirstBaseline(String merchant) {
        LegacyMerchant legacy = repository.legacyMerchant(merchant).orElseThrow(
                () -> new ServiceException(ApiResultEnum.ORDER_NOT_FOUND));
        if (legacy.batch() != null || legacy.mode() != null && !"LEGACY_OBSERVE".equals(legacy.mode())) {
            throw invalid("该商户不能从此入口登记新的历史兼容基线");
        }
        return legacy;
    }

    private BaselineReceipt baselineReplay(String merchant, String command, byte[] hash) {
        return repository.replay(LEGACY_SCOPE, merchant, command)
                .map(prior -> receipt(prior, hash, BaselineReceipt.class)).orElse(null);
    }

    public PageResult<HistoryRow> history(Scope scope, String merchant, String itemKey, Search query) {
        requireApplicationRole(scope, "ADMIN");
        requireScope(scope, merchant);
        return repository.history(merchant, normalizeKey(itemKey), query == null ? new Search() : query);
    }

    private String findMerchant(Scope scope, long itemId) {
        Item item = repository.findItem(itemId).orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_NOT_FOUND));
        requireScope(scope, item.merchant());
        return item.merchant();
    }

    private long lockMerchant(String merchant) {
        return repository.lockMerchant(merchant).orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_NOT_FOUND));
    }

    private Item lockItem(String merchant, long id) {
        return repository.lockItem(merchant, id).orElseThrow(() -> new ServiceException(ApiResultEnum.ORDER_NOT_FOUND));
    }

    private static void requireScope(Scope scope, String merchant) {
        if (merchant == null || merchant.isBlank() || merchant.length() > 32
                || !(scope.merchants().allMerchants() || scope.merchants().merchantIds().contains(merchant))) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
    }

    private static void requireApplicationRole(Scope scope, String app) {
        if (!app.equals(scope.actor().app())) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
    }

    private static void requireExpectedGrant(Item item, Grant grant) {
        if (item.expectedGrantVersion() == 0L ? grant != null
                : grant == null || grant.version() != item.expectedGrantVersion() || !TERMINATED.equals(grant.status())) {
            throw invalid("当前授权已变化，请刷新");
        }
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) {
            throw invalid("记录版本已变化，请刷新");
        }
    }

    private static ApplicationRow applicationView(Batch batch, List<Item> items, Map<Long, List<HistoryRow>> history) {
        return new ApplicationRow(batch.application(), batch.merchant(), items.stream().map(item -> {
            List<HistoryRow> changes = history.getOrDefault(item.id(), List.of());
            String reviewReason = changes.stream()
                    .filter(entry -> Integer.valueOf(item.round()).equals(entry.roundNo())
                            && Set.of("APPROVE", "RETURN", "REJECT").contains(entry.action()))
                    .findFirst().map(HistoryRow::reason).orElse(null);
            return new ItemRow(Long.toString(item.id()), item.itemKey(), item.status(), item.round(),
                    Long.toString(item.version()), item.source(), item.operation(), item.reason(),
                    item.submittedAt(), reviewReason, changes);
        }).toList());
    }

    private static MethodRow view(String key, String name, Item item, Grant grant, LocalDateTime now,
                                  MerchantPaymentMethodReadiness.Projection readiness, boolean legacy) {
        boolean catalogEnabled = name != null;
        boolean activeApplication = item != null && Set.of(PENDING, RETURNED).contains(item.status());
        boolean requestable = catalogEnabled && !activeApplication
                && (grant == null || TERMINATED.equals(grant.status()));
        return new MethodRow(key, name == null ? key.substring(key.indexOf(':') + 1) : name,
                grant == null ? (legacy ? LEGACY_OBSERVED : "NOT_AUTHORIZED")
                        : effectiveStatus(grant.status(), grant.effective(), grant.expires(), now),
                grant == null ? (legacy ? "0" : null) : Long.toString(grant.version()),
                grant == null ? null : grant.effective(),
                grant == null ? null : grant.expires(), item == null ? null : item.status(),
                item == null ? null : Long.toString(item.id()), item == null ? null : Long.toString(item.version()),
                readiness.status(), readiness.reasons(), readiness.feeReference(), catalogEnabled, requestable);
    }

    static String effectiveStatus(String status, LocalDateTime effective, LocalDateTime expires, LocalDateTime now) {
        if (!ACTIVE.equals(status)) {
            return status;
        }
        if (now.isBefore(effective)) {
            return "NOT_YET_EFFECTIVE";
        }
        return expires != null && !now.isBefore(expires) ? "EXPIRED" : ACTIVE;
    }

    static String nextGrantStatus(String status, String action) {
        return switch (action) {
            case "SUSPEND" -> ACTIVE.equals(status) ? SUSPENDED : illegalLifecycle();
            case "RESTORE" -> SUSPENDED.equals(status) ? ACTIVE : illegalLifecycle();
            case "TERMINATE" -> ACTIVE.equals(status) || SUSPENDED.equals(status) ? TERMINATED : illegalLifecycle();
            case "VALIDITY" -> ACTIVE.equals(status) || SUSPENDED.equals(status) ? status : illegalLifecycle();
            default -> illegalLifecycle();
        };
    }

    private static String illegalLifecycle() {
        throw invalid("授权状态不允许此操作");
    }

    static String normalizeKey(String value) {
        if (value == null) {
            throw invalid("支付方式无效");
        }
        String key = value.trim().toUpperCase(Locale.ROOT);
        if (key.length() > 64 || !key.matches("(CARD_BRAND|METHOD):[A-Z0-9_][A-Z0-9_.-]*")
                || Set.of("ALL", "NONE", "UNKNOWN", "BANK_CARD").contains(key.substring(key.indexOf(':') + 1))) {
            throw invalid("支付方式无效");
        }
        return key;
    }

    private Map<String, String> enabledDirectory(boolean lock) {
        Map<String, List<DirectoryEntry>> grouped = new LinkedHashMap<>();
        for (DirectoryEntry row : repository.directory(lock)) {
            String value = row.value().toUpperCase(Locale.ROOT);
            String key = ("card_brand".equals(row.type()) ? "CARD_BRAND:" : "METHOD:") + value;
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        boolean cardEnabled = allEnabled(grouped.get("METHOD:BANK_CARD"));
        String language = LocaleContextHolder.getLocale().toLanguageTag();
        Map<String, String> options = new LinkedHashMap<>();
        grouped.forEach((key, rows) -> {
            if (!allEnabled(rows) || key.startsWith("CARD_BRAND:") && !cardEnabled) {
                return;
            }
            String code = key.substring(key.indexOf(':') + 1);
            if (Set.of("BANK_CARD", "ALL", "NONE", "UNKNOWN").contains(code) || key.length() > 64
                    || !key.matches("(CARD_BRAND|METHOD):[A-Z0-9_][A-Z0-9_.-]*")) {
                return;
            }
            String label = rows.stream().filter(row -> language.equalsIgnoreCase(row.locale())).findFirst()
                    .orElse(rows.get(0)).label();
            options.put(key, label);
        });
        return options;
    }

    private static boolean allEnabled(List<DirectoryEntry> rows) {
        return rows != null && !rows.isEmpty() && rows.stream().allMatch(DirectoryEntry::enabled);
    }

    private static void requireEnabled(Map<String, String> directory, String key) {
        if (!directory.containsKey(key)) {
            throw invalid("该支付方式当前未启用");
        }
    }

    private long reviewItem(Item item, Actor actor, String action, String status, String reason, int round,
                            String requestId, byte[] hash, ItemReceipt receipt, Object facts, LocalDateTime now) {
        return repository.review(new Audit(item.merchant(), item.application(), action, item.status(), status, reason,
                "APPLICATION_ITEM", Long.toString(item.id()), item.id(), round, requestId,
                hash, boundedJson(receipt), boundedJson(facts), now), actor);
    }

    private void publish(String merchant, String key, Long item, Integer round, long review, long revision,
                         String action, LocalDateTime now) {
        events.publishEvent(new MerchantPaymentGovernanceEvent(merchant, key, item, round, review, revision, action, now));
    }

    private ItemReceipt itemReplay(String merchant, String command, byte[] hash) {
        Review prior = repository.replay(merchant, command).orElse(null);
        return prior == null ? null : withReview(receipt(prior, hash, ItemReceipt.class), prior.id());
    }

    private <T> Optional<T> replay(String merchant, String command, byte[] hash, Class<T> type) {
        return repository.replay(merchant, command).map(prior -> receipt(prior, hash, type));
    }

    private <T> T receipt(Review prior, byte[] hash, Class<T> type) {
        if (prior.hash() == null || !MessageDigest.isEqual(prior.hash(), hash)) {
            throw new ServiceException(ApiResultEnum.IDEMPOTENCY_REQUEST_CONFLICT);
        }
        return readJson(prior.result(), type);
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("merchant payment review receipt is unreadable", exception);
        }
    }

    private static ItemReceipt itemReceipt(Item item, String status, long version, Long grantVersion) {
        return new ItemReceipt(item.application(), Long.toString(item.id()), item.itemKey(), status,
                Long.toString(version), grantVersion == null ? null : grantVersion.toString(), null);
    }

    private static ItemReceipt withReview(ItemReceipt receipt, long reviewId) {
        return new ItemReceipt(receipt.applicationNo(), receipt.itemId(), receipt.itemKey(), receipt.status(),
                receipt.version(), receipt.authorizationVersion(), Long.toString(reviewId));
    }

    private byte[] fingerprint(Actor actor, String action, Object... payload) {
        var fields = new ArrayList<>();
        fields.addAll(List.of("MERCHANT_PAYMENT_METHOD_V1", actor.app(), actor.account(), actor.user(), action));
        fields.addAll(Arrays.asList(payload));
        try {
            return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(fields));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String boundedJson(Object value) {
        try {
            String encoded = json.writeValueAsString(value);
            if (encoded.getBytes(StandardCharsets.UTF_8).length > 8192) {
                throw invalid("申请数据超出长度限制");
            }
            return encoded;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("merchant payment review data cannot be encoded", exception);
        }
    }

    private static String reason(String value, boolean required) {
        String reason = value == null || value.isBlank() ? null : value.trim();
        if ((required && reason == null) || (reason != null && reason.length() > 1000)) {
            throw invalid("请填写有效操作原因");
        }
        return reason;
    }

    private static void rejectUnexpectedTimes(Change input) {
        if (input.effectiveTime() != null || input.expireTime() != null) {
            throw invalid("此操作不接受有效期");
        }
    }

    private static long version(String value) {
        try {
            return Long.parseLong(Objects.requireNonNull(value));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw invalid("记录版本无效");
        }
    }

    private static LocalDateTime now() {
        return milliseconds(LocalDateTime.now(BUSINESS_ZONE));
    }

    private static LocalDateTime milliseconds(LocalDateTime value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MILLIS);
    }

    private static ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    public record Scope(AdminMerchantDataScope merchants, Actor actor) {}
    private record BatchFact(int schemaVersion, List<String> itemKeys) {}
    private record BaselineFact(int schemaVersion, LocalDateTime cutoffTime) {}
    private record ApplicationFact(int schemaVersion, String itemKey, String operation, long expectedGrantVersion,
                                   int round, long version) {}
    private record ActionFact(int schemaVersion, String itemKey, Integer round, Long beforeItemVersion,
                              Long afterItemVersion, Long beforeGrantVersion, Long afterGrantVersion,
                              String beforeGrantStatus, String afterGrantStatus, LocalDateTime beforeEffective,
                              LocalDateTime beforeExpiry, LocalDateTime afterEffective, LocalDateTime afterExpiry) {}
}
