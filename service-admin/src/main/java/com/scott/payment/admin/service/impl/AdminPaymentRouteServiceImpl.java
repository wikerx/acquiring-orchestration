package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteActionRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.MerchantRouteConflictRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.MerchantRouteConflictResponse;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.MerchantRouteRuleBrief;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteCandidateRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteCandidateResponse;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteChangeLogQuery;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteChangeLogResponse;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteCopyRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RoutePaymentScope;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteRuleQuery;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteRuleResponse;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteRuleSaveRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteStrategyRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteStrategyResponse;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteValidationResponse;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.ValidationIssue;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelMidConfigDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.MerchantChannelMidBindingDO;
import com.scott.payment.admin.entity.channel.PaymentRouteEntities.PaymentRouteCandidateDO;
import com.scott.payment.admin.entity.channel.PaymentRouteEntities.PaymentRouteChangeLogDO;
import com.scott.payment.admin.entity.channel.PaymentRouteEntities.PaymentRouteMethodDO;
import com.scott.payment.admin.entity.channel.PaymentRouteEntities.PaymentRouteRuleDO;
import com.scott.payment.admin.entity.channel.PaymentRouteEntities.PaymentRouteRuleMerchantDO;
import com.scott.payment.admin.entity.channel.PaymentRouteEntities.PaymentRouteStrategyDO;
import com.scott.payment.admin.entity.channel.PaymentRouteEntities.PaymentRouteTransactionTypeDO;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.admin.mapper.ChannelMidConfigMapper;
import com.scott.payment.admin.mapper.MerchantChannelMidBindingMapper;
import com.scott.payment.admin.mapper.PaymentRouteCandidateMapper;
import com.scott.payment.admin.mapper.PaymentRouteChangeLogMapper;
import com.scott.payment.admin.mapper.PaymentRouteMethodMapper;
import com.scott.payment.admin.mapper.PaymentRouteRuleMapper;
import com.scott.payment.admin.mapper.PaymentRouteRuleMerchantMapper;
import com.scott.payment.admin.mapper.PaymentRouteStrategyMapper;
import com.scott.payment.admin.mapper.PaymentRouteTransactionTypeMapper;
import com.scott.payment.admin.service.AdminPaymentRouteService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端支付路由配置服务实现。
 *
 * <p>该实现只维护和校验配置，不被支付服务加载，因此不会改变阶段 1A 的真实交易选路。</p>
 */
@Service
public class AdminPaymentRouteServiceImpl implements AdminPaymentRouteService {

    private static final Logger log = LoggerFactory.getLogger(AdminPaymentRouteServiceImpl.class);

    private static final long NOT_DELETED = 0L;
    private static final int ENABLED = 1;
    private static final String BUSINESS_ACQUIRING = "ACQUIRING";
    private static final String RULE_FIXED = "FIXED";
    private static final String RULE_PRIORITY = "PRIORITY";
    private static final String RULE_WEIGHT = "WEIGHT";
    private static final String RULE_HISTORY_AFFINITY = "HISTORY_AFFINITY";
    private static final String SCOPE_GLOBAL = "GLOBAL";
    private static final String SCOPE_MERCHANT = "MERCHANT";
    private static final String POLICY_FALLBACK = "FALLBACK";
    private static final String POLICY_REJECT = "REJECT";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final int MAX_HISTORY_LOOKBACK_MONTHS = 24;
    private static final BigDecimal FULL_WEIGHT = new BigDecimal("100.00");
    private static final String ALL = "ALL";
    private static final String NONE = "NONE";
    private static final String BANK_CARD = "BANK_CARD";
    private static final String RULE_CODE_PREFIX = "ROUTE";
    private static final ZoneId PAYMENT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter RULE_CODE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd", Locale.ROOT);

    private final PaymentRouteRuleMapper ruleMapper;
    private final PaymentRouteRuleMerchantMapper ruleMerchantMapper;
    private final PaymentRouteStrategyMapper strategyMapper;
    private final PaymentRouteCandidateMapper candidateMapper;
    private final PaymentRouteMethodMapper methodMapper;
    private final PaymentRouteTransactionTypeMapper transactionTypeMapper;
    private final PaymentRouteChangeLogMapper changeLogMapper;
    private final ChannelInfoMapper channelInfoMapper;
    private final ChannelMidConfigMapper midConfigMapper;
    private final MerchantChannelMidBindingMapper midBindingMapper;

    public AdminPaymentRouteServiceImpl(PaymentRouteRuleMapper ruleMapper,
                                        PaymentRouteRuleMerchantMapper ruleMerchantMapper,
                                        PaymentRouteStrategyMapper strategyMapper,
                                        PaymentRouteCandidateMapper candidateMapper,
                                        PaymentRouteMethodMapper methodMapper,
                                        PaymentRouteTransactionTypeMapper transactionTypeMapper,
                                        PaymentRouteChangeLogMapper changeLogMapper,
                                        ChannelInfoMapper channelInfoMapper,
                                        ChannelMidConfigMapper midConfigMapper,
                                        MerchantChannelMidBindingMapper midBindingMapper) {
        this.ruleMapper = ruleMapper;
        this.ruleMerchantMapper = ruleMerchantMapper;
        this.strategyMapper = strategyMapper;
        this.candidateMapper = candidateMapper;
        this.methodMapper = methodMapper;
        this.transactionTypeMapper = transactionTypeMapper;
        this.changeLogMapper = changeLogMapper;
        this.channelInfoMapper = channelInfoMapper;
        this.midConfigMapper = midConfigMapper;
        this.midBindingMapper = midBindingMapper;
    }

    /** 查询规则列表，并批量装配策略和候选，避免按行重复访问数据库。 */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<RouteRuleResponse> pageRules(RouteRuleQuery request) {
        RouteRuleQuery query = request == null ? new RouteRuleQuery() : request;
        Set<PaymentScopeKey> paymentScopes = normalizePaymentScopes(query.getPaymentScopes(), false);
        Set<String> paymentMethods = normalizeQueryDimensions(query.getPaymentMethods(), query.getPaymentMethod());
        Set<String> transactionTypes = normalizeQueryDimensions(query.getTransactionTypes(), query.getTransactionType());
        Set<Long> methodRuleIds = paymentScopes.isEmpty() && paymentMethods.isEmpty()
                ? Set.of()
                : methodMapper.selectList(Wrappers.<PaymentRouteMethodDO>lambdaQuery()
                                .select(PaymentRouteMethodDO::getRuleId, PaymentRouteMethodDO::getPaymentMethod, PaymentRouteMethodDO::getCardBrand)
                                .eq(PaymentRouteMethodDO::getDeleted, NOT_DELETED))
                        .stream()
                        .filter(item -> paymentScopes.isEmpty()
                                ? paymentMethods.contains(normalize(item.getPaymentMethod()))
                                : paymentScopes.contains(paymentScopeKey(item.getPaymentMethod(), item.getCardBrand())))
                        .map(PaymentRouteMethodDO::getRuleId)
                        .collect(Collectors.toSet());
        List<PaymentRouteTransactionTypeDO> activeTransactionTypes = transactionTypes.isEmpty()
                ? List.of()
                : transactionTypeMapper.selectList(Wrappers.<PaymentRouteTransactionTypeDO>lambdaQuery()
                        .select(PaymentRouteTransactionTypeDO::getRuleId, PaymentRouteTransactionTypeDO::getTransactionType)
                        .eq(PaymentRouteTransactionTypeDO::getDeleted, NOT_DELETED));
        Set<Long> explicitlyScopedTransactionRuleIds = activeTransactionTypes.stream()
                .map(PaymentRouteTransactionTypeDO::getRuleId)
                .collect(Collectors.toSet());
        Set<Long> matchingTransactionRuleIds = activeTransactionTypes.stream()
                .filter(item -> transactionTypes.contains(normalize(item.getTransactionType())))
                .map(PaymentRouteTransactionTypeDO::getRuleId)
                .collect(Collectors.toSet());
        Set<Long> merchantRuleIds = StringUtils.hasText(query.getMerchantId())
                ? ruleMerchantMapper.selectList(Wrappers.<PaymentRouteRuleMerchantDO>lambdaQuery()
                                .select(PaymentRouteRuleMerchantDO::getRuleId)
                                .eq(PaymentRouteRuleMerchantDO::getMerchantId, query.getMerchantId().trim())
                                .eq(PaymentRouteRuleMerchantDO::getDeleted, NOT_DELETED))
                        .stream()
                        .map(PaymentRouteRuleMerchantDO::getRuleId)
                        .collect(Collectors.toSet())
                : Set.of();
        var ruleQuery = Wrappers.<PaymentRouteRuleDO>lambdaQuery()
                .eq(PaymentRouteRuleDO::getDeleted, NOT_DELETED)
                .eq(StringUtils.hasText(query.getRuleType()), PaymentRouteRuleDO::getRuleType, normalize(query.getRuleType()))
                .eq(StringUtils.hasText(query.getScopeType()), PaymentRouteRuleDO::getScopeType, normalize(query.getScopeType()))
                .eq(StringUtils.hasText(query.getRuleStatus()), PaymentRouteRuleDO::getRuleStatus, normalize(query.getRuleStatus()))
                .eq(StringUtils.hasText(query.getUnavailablePolicy()), PaymentRouteRuleDO::getUnavailablePolicy, normalize(query.getUnavailablePolicy()))
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(PaymentRouteRuleDO::getRuleCode, query.getKeyword().trim())
                        .or().like(PaymentRouteRuleDO::getRuleName, query.getKeyword().trim()));
        if (StringUtils.hasText(query.getMerchantId())) {
            ruleQuery.in(PaymentRouteRuleDO::getId, merchantRuleIds.isEmpty() ? Set.of(-1L) : merchantRuleIds);
        }
        if (!paymentScopes.isEmpty() || !paymentMethods.isEmpty()) {
            ruleQuery.in(PaymentRouteRuleDO::getId, methodRuleIds.isEmpty() ? Set.of(-1L) : methodRuleIds);
        }
        if (!transactionTypes.isEmpty()) {
            // 没有交易类型关系记录表示适用于全部交易类型，按具体类型查询时同样必须命中。
            if (explicitlyScopedTransactionRuleIds.isEmpty()) {
                // 所有规则都未限定交易类型，无需追加过滤条件。
            } else if (matchingTransactionRuleIds.isEmpty()) {
                ruleQuery.notIn(PaymentRouteRuleDO::getId, explicitlyScopedTransactionRuleIds);
            } else {
                ruleQuery.and(wrapper -> wrapper
                        .in(PaymentRouteRuleDO::getId, matchingTransactionRuleIds)
                        .or()
                        .notIn(PaymentRouteRuleDO::getId, explicitlyScopedTransactionRuleIds));
            }
        }
        ruleQuery.orderByAsc(PaymentRouteRuleDO::getRulePriority)
                .orderByDesc(PaymentRouteRuleDO::getUpdateTime)
                .orderByDesc(PaymentRouteRuleDO::getId);
        Page<PaymentRouteRuleDO> page = ruleMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                ruleQuery);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), assemble(page.getRecords()));
    }

    /** 查询规则完整详情。 */
    @Override
    @DS(DataSourceName.SLAVE)
    public RouteRuleResponse getRule(Long id) {
        return assembleOne(findRule(id));
    }

    /** 批量返回指定商户已有的路由配置；全局规则不在此处制造重复提示。 */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<MerchantRouteConflictResponse> merchantConflicts(MerchantRouteConflictRequest request) {
        Set<String> merchantIds = normalizeMerchantIds(request == null ? null : request.getMerchantIds(), null);
        if (merchantIds.isEmpty()) {
            return List.of();
        }
        List<PaymentRouteRuleMerchantDO> relations = ruleMerchantMapper.selectList(
                Wrappers.<PaymentRouteRuleMerchantDO>lambdaQuery()
                        .in(PaymentRouteRuleMerchantDO::getMerchantId, merchantIds)
                        .eq(PaymentRouteRuleMerchantDO::getDeleted, NOT_DELETED));
        Long excludeRuleId = request.getExcludeRuleId();
        Set<Long> ruleIds = relations.stream()
                .map(PaymentRouteRuleMerchantDO::getRuleId)
                .filter(ruleId -> !Objects.equals(ruleId, excludeRuleId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ruleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, PaymentRouteRuleDO> rulesById = ruleMapper.selectList(
                        Wrappers.<PaymentRouteRuleDO>lambdaQuery()
                                .in(PaymentRouteRuleDO::getId, ruleIds)
                                .eq(PaymentRouteRuleDO::getScopeType, SCOPE_MERCHANT)
                                .eq(PaymentRouteRuleDO::getDeleted, NOT_DELETED)
                                .orderByAsc(PaymentRouteRuleDO::getRulePriority)
                                .orderByDesc(PaymentRouteRuleDO::getUpdateTime))
                .stream()
                .collect(Collectors.toMap(PaymentRouteRuleDO::getId, Function.identity(), (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, List<MerchantRouteRuleBrief>> conflicts = new LinkedHashMap<>();
        relations.stream()
                .filter(relation -> !Objects.equals(relation.getRuleId(), excludeRuleId))
                .forEach(relation -> {
                    PaymentRouteRuleDO rule = rulesById.get(relation.getRuleId());
                    if (rule != null) {
                        conflicts.computeIfAbsent(relation.getMerchantId(), ignored -> new ArrayList<>())
                                .add(toMerchantRouteRuleBrief(rule));
                    }
                });
        return merchantIds.stream()
                .filter(conflicts::containsKey)
                .map(merchantId -> {
                    MerchantRouteConflictResponse response = new MerchantRouteConflictResponse();
                    response.setMerchantId(merchantId);
                    response.setRules(conflicts.get(merchantId));
                    return response;
                })
                .toList();
    }

    /** 创建草稿规则并写入首条审计快照。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RouteRuleResponse createRule(RouteRuleSaveRequest request) {
        validateSaveRequest(request, false);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperatorName();
        PaymentRouteRuleDO rule = new PaymentRouteRuleDO();
        fillRule(rule, request);
        rule.setRuleCode(generateRuleCode());
        rule.setRuleStatus(STATUS_DRAFT);
        rule.setVersion(0);
        rule.setCreateBy(operator);
        rule.setCreateTime(now);
        rule.setUpdateBy(operator);
        rule.setUpdateTime(now);
        rule.setDeleted(NOT_DELETED);
        ruleMapper.insert(rule);
        replaceAssociations(rule.getId(), normalizeRequestMerchantIds(request), normalizeRequestPaymentScopes(request),
                request.getTransactionTypes(), request.getStrategy(), request.getCandidates(), operator, now);
        RouteRuleResponse created = assembleOne(rule);
        insertChangeLog(rule, "CREATE", null, created, null, rule.getVersion(), request.getOperationReason(), now);
        log.info("支付路由规则草稿已创建, event: PAYMENT_ROUTE_CONFIG_CREATED, ruleId: {}, ruleCode: {}, version: {}, operator: {}",
                rule.getId(), rule.getRuleCode(), rule.getVersion(), operator);
        return created;
    }

    /** 更新草稿或停用规则；乐观锁保证并发修改不会互相覆盖。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RouteRuleResponse updateRule(Long id, RouteRuleSaveRequest request) {
        PaymentRouteRuleDO existing = findRule(id);
        requireExpectedVersion(existing, request.getVersion());
        if (STATUS_ENABLED.equals(existing.getRuleStatus())) {
            throw invalid("Enabled route rule cannot be edited; disable or copy it first");
        }
        validateSaveRequest(request, false);
        RouteRuleResponse before = assembleOne(existing);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperatorName();
        PaymentRouteRuleDO changed = copyRuleEntity(existing);
        fillRule(changed, request);
        changed.setVersion(existing.getVersion() + 1);
        changed.setUpdateBy(operator);
        changed.setUpdateTime(now);
        updateRuleVersioned(changed, existing.getVersion(), Set.of(STATUS_DRAFT, STATUS_DISABLED));
        replaceAssociations(id, normalizeRequestMerchantIds(request), normalizeRequestPaymentScopes(request),
                request.getTransactionTypes(), request.getStrategy(), request.getCandidates(), operator, now);
        RouteRuleResponse after = assembleOne(changed);
        insertChangeLog(changed, "UPDATE", before, after, existing.getVersion(), changed.getVersion(), request.getOperationReason(), now);
        log.info("支付路由规则已更新, event: PAYMENT_ROUTE_CONFIG_UPDATED, ruleId: {}, ruleCode: {}, beforeVersion: {}, afterVersion: {}, operator: {}",
                id, changed.getRuleCode(), existing.getVersion(), changed.getVersion(), operator);
        return after;
    }

    /** 复制规则及其类型专属配置，复制结果固定为可编辑草稿。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RouteRuleResponse copyRule(Long id, RouteCopyRequest request) {
        PaymentRouteRuleDO source = findRule(id);
        requireExpectedVersion(source, request.getVersion());
        RouteRuleResponse sourceResponse = assembleOne(source);
        if (sourceResponse.getStrategy() == null) {
            throw invalid("Source route rule strategy is missing");
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperatorName();

        PaymentRouteRuleDO copy = copyRuleEntity(source);
        copy.setId(null);
        // 新规则只以关系表保存支付方式，旧单值列仅保留给历史数据读取。
        copy.setPaymentMethod(null);
        copy.setRuleCode(generateRuleCode());
        copy.setRuleName(request.getRuleName().trim());
        copy.setRuleStatus(STATUS_DRAFT);
        copy.setVersion(0);
        copy.setPublishedBy(null);
        copy.setPublishedTime(null);
        copy.setDisabledBy(null);
        copy.setDisabledTime(null);
        copy.setCreateBy(operator);
        copy.setCreateTime(now);
        copy.setUpdateBy(operator);
        copy.setUpdateTime(now);
        copy.setDeleted(NOT_DELETED);
        ruleMapper.insert(copy);

        RouteStrategyRequest strategy = toStrategyRequest(sourceResponse.getStrategy());
        List<RouteCandidateRequest> candidates = sourceResponse.getCandidates().stream()
                .map(this::toCandidateRequest)
                .toList();
        replaceAssociations(copy.getId(), sourceResponse.getMerchantIds(),
                normalizePaymentScopes(sourceResponse.getPaymentScopes(), !RULE_HISTORY_AFFINITY.equals(sourceResponse.getRuleType())),
                sourceResponse.getTransactionTypes(), strategy, candidates, operator, now);
        RouteRuleResponse after = assembleOne(copy);
        insertChangeLog(copy, "COPY", sourceResponse, after, source.getVersion(), copy.getVersion(), request.getOperationReason(), now);
        log.info("支付路由规则已复制为草稿, event: PAYMENT_ROUTE_CONFIG_COPIED, sourceRuleId: {}, ruleId: {}, ruleCode: {}, operator: {}",
                id, copy.getId(), copy.getRuleCode(), operator);
        return after;
    }

    /** 发布规则；事务内锁定规则集合，防止并发发布两条同优先级重叠规则。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RouteRuleResponse publishRule(Long id, RouteActionRequest request) {
        List<PaymentRouteRuleDO> lockedRules = lockRulesForPublish();
        PaymentRouteRuleDO rule = lockedRules.stream()
                .filter(item -> Objects.equals(item.getId(), id))
                .findFirst()
                .orElseThrow(() -> invalid("Route rule not found"));
        requireExpectedVersion(rule, request.getVersion());
        if (STATUS_ENABLED.equals(rule.getRuleStatus())) {
            throw invalid("Route rule is already enabled");
        }
        RouteRuleResponse before = assembleOne(rule);
        List<ValidationIssue> issues = collectValidationIssues(rule, true, lockedRules);
        if (!issues.isEmpty()) {
            throw invalid("Route validation failed: " + issues.get(0).getCode());
        }
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperatorName();
        int beforeVersion = rule.getVersion();
        rule.setRuleStatus(STATUS_ENABLED);
        rule.setVersion(beforeVersion + 1);
        rule.setPublishedBy(operator);
        rule.setPublishedTime(now);
        rule.setDisabledBy(null);
        rule.setDisabledTime(null);
        rule.setUpdateBy(operator);
        rule.setUpdateTime(now);
        updateRuleVersioned(rule, beforeVersion, Set.of(STATUS_DRAFT, STATUS_DISABLED));
        RouteRuleResponse after = assembleOne(rule);
        insertChangeLog(rule, "PUBLISH", before, after, beforeVersion, rule.getVersion(), request.getOperationReason(), now);
        log.info("支付路由规则已发布, event: PAYMENT_ROUTE_CONFIG_PUBLISHED, ruleId: {}, ruleCode: {}, rulePriority: {}, version: {}, operator: {}",
                rule.getId(), rule.getRuleCode(), rule.getRulePriority(), rule.getVersion(), operator);
        return after;
    }

    /** 停用已发布规则，配置仍保留以便修改或重新发布。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RouteRuleResponse disableRule(Long id, RouteActionRequest request) {
        PaymentRouteRuleDO rule = findRule(id);
        requireExpectedVersion(rule, request.getVersion());
        if (!STATUS_ENABLED.equals(rule.getRuleStatus())) {
            throw invalid("Only enabled route rule can be disabled");
        }
        RouteRuleResponse before = assembleOne(rule);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperatorName();
        int beforeVersion = rule.getVersion();
        rule.setRuleStatus(STATUS_DISABLED);
        rule.setVersion(beforeVersion + 1);
        rule.setDisabledBy(operator);
        rule.setDisabledTime(now);
        rule.setUpdateBy(operator);
        rule.setUpdateTime(now);
        updateRuleVersioned(rule, beforeVersion, Set.of(STATUS_ENABLED));
        RouteRuleResponse after = assembleOne(rule);
        insertChangeLog(rule, "DISABLE", before, after, beforeVersion, rule.getVersion(), request.getOperationReason(), now);
        log.info("支付路由规则已停用, event: PAYMENT_ROUTE_CONFIG_DISABLED, ruleId: {}, ruleCode: {}, version: {}, operator: {}",
                rule.getId(), rule.getRuleCode(), rule.getVersion(), operator);
        return after;
    }

    /** 软删除未启用规则；变更日志作为独立审计事实保留。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id, RouteActionRequest request) {
        PaymentRouteRuleDO rule = findRule(id);
        requireExpectedVersion(rule, request.getVersion());
        if (STATUS_ENABLED.equals(rule.getRuleStatus())) {
            throw invalid("Enabled route rule cannot be deleted; disable it first");
        }
        RouteRuleResponse before = assembleOne(rule);
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperatorName();
        int affected = ruleMapper.update(null, Wrappers.<PaymentRouteRuleDO>lambdaUpdate()
                .eq(PaymentRouteRuleDO::getId, id)
                .eq(PaymentRouteRuleDO::getVersion, request.getVersion())
                .eq(PaymentRouteRuleDO::getDeleted, NOT_DELETED)
                .ne(PaymentRouteRuleDO::getRuleStatus, STATUS_ENABLED)
                .set(PaymentRouteRuleDO::getDeleted, id)
                .set(PaymentRouteRuleDO::getVersion, rule.getVersion() + 1)
                .set(PaymentRouteRuleDO::getUpdateBy, operator)
                .set(PaymentRouteRuleDO::getUpdateTime, now));
        ensureUpdated(affected);
        softDeleteAssociations(id, operator, now);
        rule.setVersion(rule.getVersion() + 1);
        insertChangeLog(rule, "DELETE", before, null, request.getVersion(), rule.getVersion(), request.getOperationReason(), now);
        log.info("支付路由规则已删除, event: PAYMENT_ROUTE_CONFIG_DELETED, ruleId: {}, ruleCode: {}, version: {}, operator: {}",
                id, rule.getRuleCode(), rule.getVersion(), operator);
    }

    /** 返回发布校验明细；该操作不改变配置生命周期。 */
    @Override
    @DS(DataSourceName.MASTER)
    public RouteValidationResponse validateRule(Long id) {
        PaymentRouteRuleDO rule = findRule(id);
        List<PaymentRouteRuleDO> rules = ruleMapper.selectList(Wrappers.<PaymentRouteRuleDO>lambdaQuery()
                .eq(PaymentRouteRuleDO::getDeleted, NOT_DELETED));
        List<ValidationIssue> issues = collectValidationIssues(rule, true, rules);
        RouteValidationResponse response = new RouteValidationResponse();
        response.setRuleId(rule.getId());
        response.setVersion(rule.getVersion());
        response.setIssues(issues);
        response.setValid(issues.isEmpty());
        return response;
    }

    /** 分页查询不可变配置变更日志。 */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<RouteChangeLogResponse> pageChangeLogs(Long id, RouteChangeLogQuery request) {
        findRuleIncludingDeleted(id);
        RouteChangeLogQuery query = request == null ? new RouteChangeLogQuery() : request;
        Page<PaymentRouteChangeLogDO> page = changeLogMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<PaymentRouteChangeLogDO>lambdaQuery()
                        .eq(PaymentRouteChangeLogDO::getRuleId, id)
                        .eq(StringUtils.hasText(query.getActionType()), PaymentRouteChangeLogDO::getActionType, normalize(query.getActionType()))
                        .orderByDesc(PaymentRouteChangeLogDO::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toChangeLogResponse).toList());
    }

    /** 保存请求的基础规则校验；发布所需的可用性检查由 collectValidationIssues 完成。 */
    private void validateSaveRequest(RouteRuleSaveRequest request, boolean publishing) {
        if (request == null) {
            throw invalid("Route rule request is required");
        }
        String ruleType = normalize(request.getRuleType());
        String scopeType = normalize(request.getScopeType());
        if (!Set.of(RULE_FIXED, RULE_PRIORITY, RULE_WEIGHT, RULE_HISTORY_AFFINITY).contains(ruleType)) {
            throw invalid("ruleType must be FIXED, PRIORITY, WEIGHT or HISTORY_AFFINITY");
        }
        if (!Set.of(SCOPE_GLOBAL, SCOPE_MERCHANT).contains(scopeType)) {
            throw invalid("scopeType must be GLOBAL or MERCHANT");
        }
        Set<String> merchantIds = normalizeRequestMerchantIds(request);
        if (SCOPE_MERCHANT.equals(scopeType) && merchantIds.isEmpty()) {
            throw invalid("merchantIds is required for MERCHANT scope");
        }
        if (SCOPE_GLOBAL.equals(scopeType) && !merchantIds.isEmpty()) {
            throw invalid("merchantIds must be empty for GLOBAL scope");
        }
        if (!BUSINESS_ACQUIRING.equals(normalize(request.getBusinessType()))) {
            throw invalid("Stage 1A only supports ACQUIRING route rules");
        }
        if (!Set.of(POLICY_FALLBACK, POLICY_REJECT).contains(normalize(request.getUnavailablePolicy()))) {
            throw invalid("unavailablePolicy must be FALLBACK or REJECT");
        }
        Set<PaymentScopeKey> paymentScopes = normalizeRequestPaymentScopes(request);
        if (!RULE_HISTORY_AFFINITY.equals(ruleType) && paymentScopes.isEmpty()) {
            throw invalid("At least one explicit payment scope is required");
        }
        Set<String> transactionTypes = normalizeTransactionTypes(request.getTransactionTypes());
        if (RULE_HISTORY_AFFINITY.equals(ruleType)) {
            if (!paymentScopes.isEmpty()) {
                throw invalid("HISTORY_AFFINITY does not accept payment scopes");
            }
            if (!transactionTypes.isEmpty()) {
                throw invalid("HISTORY_AFFINITY does not accept transaction types");
            }
            if (!POLICY_FALLBACK.equals(normalize(request.getUnavailablePolicy()))) {
                throw invalid("HISTORY_AFFINITY unavailablePolicy must be FALLBACK");
            }
        }
        validateStrategy(ruleType, request.getStrategy());
        validateCandidateRequests(ruleType, paymentScopes, request.getCandidates());
        if (publishing && !RULE_HISTORY_AFFINITY.equals(ruleType)
                && (request.getCandidates() == null || request.getCandidates().isEmpty())) {
            throw invalid("At least one route candidate is required for publishing");
        }
    }

    /** 类型专属配置与规则类型必须一致，避免再次形成含糊的二级策略概念。 */
    private void validateStrategy(String ruleType, RouteStrategyRequest strategy) {
        if (strategy == null || !ruleType.equals(normalize(strategy.getStrategyType()))) {
            throw invalid("strategyType must match ruleType");
        }
        if (strategy.getStrategyStatus() == null || !Set.of(0, 1).contains(strategy.getStrategyStatus())) {
            throw invalid("strategyStatus must be 0 or 1");
        }
        if (RULE_HISTORY_AFFINITY.equals(ruleType)) {
            Integer lookback = strategy.getHistoryLookbackMonths();
            if (lookback == null || lookback < 1 || lookback > MAX_HISTORY_LOOKBACK_MONTHS) {
                throw invalid("historyLookbackMonths must be between 1 and 24");
            }
        } else if (strategy.getHistoryLookbackMonths() != null) {
            throw invalid("historyLookbackMonths is only valid for HISTORY_AFFINITY");
        }
    }

    /** 按路由类型校验候选数量、顺序和权重，公共唯一性只在支付方式分组内生效。 */
    private void validateCandidateRequests(String ruleType,
                                           Set<PaymentScopeKey> paymentScopes,
                                           List<RouteCandidateRequest> candidates) {
        List<RouteCandidateRequest> values = candidates == null ? List.of() : candidates;
        if (RULE_HISTORY_AFFINITY.equals(ruleType)) {
            if (!values.isEmpty()) {
                throw invalid("HISTORY_AFFINITY does not accept configured MID candidates");
            }
            return;
        }
        Set<String> midKeys = new LinkedHashSet<>();
        Set<String> priorityKeys = new LinkedHashSet<>();
        Map<PaymentScopeKey, List<RouteCandidateRequest>> candidatesByScope = new LinkedHashMap<>();
        for (RouteCandidateRequest candidate : values) {
            if (candidate == null || candidate.getChannelId() == null || candidate.getMidConfigId() == null
                    || candidate.getCandidatePriority() == null || !StringUtils.hasText(candidate.getPaymentMethod())) {
                throw invalid("Route candidate fields are incomplete");
            }
            String candidatePaymentMethod = normalize(candidate.getPaymentMethod());
            String candidateCardBrand = normalize(candidate.getCardBrand());
            if (ALL.equals(candidatePaymentMethod)) {
                throw invalid("Candidate paymentMethod must be explicit");
            }
            PaymentScopeKey candidateScope = validatePaymentScope(candidatePaymentMethod, candidateCardBrand);
            if (!paymentScopes.contains(candidateScope)) {
                throw invalid("Candidate payment scope must belong to the rule payment scopes");
            }
            if (!midKeys.add(candidateScope.key() + ':' + candidate.getMidConfigId())) {
                throw invalid("Candidate MID must be unique within a payment scope");
            }
            if (!priorityKeys.add(candidateScope.key() + ':' + candidate.getCandidatePriority())) {
                throw invalid("Candidate priority must be unique within a payment scope");
            }
            if (candidate.getCandidateStatus() == null || !Set.of(0, 1).contains(candidate.getCandidateStatus())) {
                throw invalid("candidateStatus must be 0 or 1");
            }
            if (RULE_WEIGHT.equals(ruleType)) {
                if (candidate.getCandidateWeight() == null || candidate.getCandidateWeight().compareTo(BigDecimal.ZERO) <= 0
                        || candidate.getCandidateWeight().compareTo(FULL_WEIGHT) > 0) {
                    throw invalid("WEIGHT candidateWeight must be greater than 0 and at most 100.00");
                }
            } else if (candidate.getCandidateWeight() != null) {
                throw invalid("candidateWeight is only valid for WEIGHT routes");
            }
            candidatesByScope.computeIfAbsent(candidateScope, ignored -> new ArrayList<>()).add(candidate);
        }
        for (PaymentScopeKey paymentScope : paymentScopes) {
            List<RouteCandidateRequest> grouped = candidatesByScope.getOrDefault(paymentScope, List.of());
            List<RouteCandidateRequest> enabled = grouped.stream()
                    .filter(candidate -> candidate.getCandidateStatus() == ENABLED)
                    .toList();
            if (RULE_FIXED.equals(ruleType) && (grouped.size() != 1 || enabled.size() != 1)) {
                throw invalid("FIXED requires exactly one enabled MID for each payment method");
            }
            if (!RULE_FIXED.equals(ruleType) && enabled.isEmpty()) {
                throw invalid("Each payment method requires at least one enabled MID");
            }
            if (RULE_WEIGHT.equals(ruleType)) {
                BigDecimal totalWeight = enabled.stream()
                        .map(RouteCandidateRequest::getCandidateWeight)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (totalWeight.compareTo(FULL_WEIGHT) != 0) {
                    throw invalid("Enabled candidate weights must total 100.00 for each payment method");
                }
            }
        }
    }

    private Set<String> normalizePaymentMethods(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> normalized = values.stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.contains(ALL)) {
            throw invalid("paymentMethods must contain explicit dictionary values");
        }
        return normalized;
    }

    /** 新契约按支付方式和卡品牌保存；旧非银行卡单值仅作为兼容输入。 */
    private Set<PaymentScopeKey> normalizeRequestPaymentScopes(RouteRuleSaveRequest request) {
        Set<PaymentScopeKey> scopes = normalizePaymentScopes(request.getPaymentScopes(), true);
        if (!scopes.isEmpty()) {
            return scopes;
        }
        Set<String> legacyMethods = normalizePaymentMethods(request.getPaymentMethods());
        if (legacyMethods.contains(BANK_CARD)) {
            throw invalid("BANK_CARD requires explicit card brand payment scopes");
        }
        return legacyMethods.stream()
                .map(method -> paymentScopeKey(method, NONE))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<PaymentScopeKey> normalizePaymentScopes(Collection<RoutePaymentScope> values, boolean strict) {
        if (values == null) {
            return Set.of();
        }
        Set<PaymentScopeKey> result = new LinkedHashSet<>();
        for (RoutePaymentScope value : values) {
            if (value == null || !StringUtils.hasText(value.getPaymentMethod()) || !StringUtils.hasText(value.getCardBrand())) {
                if (strict) {
                    throw invalid("Payment scope fields are incomplete");
                }
                continue;
            }
            result.add(validatePaymentScope(normalize(value.getPaymentMethod()), normalize(value.getCardBrand())));
        }
        return result;
    }

    private PaymentScopeKey validatePaymentScope(String paymentMethod, String cardBrand) {
        if (ALL.equals(paymentMethod)) {
            throw invalid("Payment scope must use an explicit payment method");
        }
        if (BANK_CARD.equals(paymentMethod)) {
            if (ALL.equals(cardBrand) || NONE.equals(cardBrand)) {
                throw invalid("BANK_CARD payment scope requires an explicit card brand");
            }
        } else if (!NONE.equals(cardBrand)) {
            throw invalid("Non-card payment scope cardBrand must be NONE");
        }
        return paymentScopeKey(paymentMethod, cardBrand);
    }

    private PaymentScopeKey paymentScopeKey(String paymentMethod, String cardBrand) {
        return new PaymentScopeKey(normalize(paymentMethod), normalize(cardBrand));
    }

    private Set<String> normalizeTransactionTypes(Collection<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> normalized = values.stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.contains(ALL)) {
            throw invalid("transactionTypes must contain explicit dictionary values or be empty");
        }
        return normalized;
    }

    private Set<String> normalizeRequestMerchantIds(RouteRuleSaveRequest request) {
        return normalizeMerchantIds(request.getMerchantIds(), request.getMerchantId());
    }

    /** 新调用方使用 merchantIds；单值 merchantId 只在集合为空时作为兼容输入。 */
    private Set<String> normalizeMerchantIds(Collection<String> values, String legacyMerchantId) {
        Set<String> normalized = values == null ? new LinkedHashSet<>() : values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty() && StringUtils.hasText(legacyMerchantId)) {
            normalized.add(legacyMerchantId.trim());
        }
        if (normalized.size() > 100 || normalized.stream().anyMatch(value -> value.length() > 32)) {
            throw invalid("merchantIds is invalid");
        }
        return normalized;
    }

    private Set<String> normalizeQueryDimensions(Collection<String> values, String legacyValue) {
        Set<String> normalized = values == null ? new LinkedHashSet<>() : values.stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty() && StringUtils.hasText(legacyValue)) {
            normalized.add(normalize(legacyValue));
        }
        if (normalized.remove(ALL)) {
            return Set.of();
        }
        return normalized;
    }

    /** 汇总发布前完整性、候选可用性和同优先级重叠问题。 */
    private List<ValidationIssue> collectValidationIssues(PaymentRouteRuleDO rule,
                                                          boolean publishing,
                                                          List<PaymentRouteRuleDO> allRules) {
        List<ValidationIssue> issues = new ArrayList<>();
        RouteStrategyResponse strategy = loadStrategy(rule.getId());
        Set<String> configuredMethods = RULE_HISTORY_AFFINITY.equals(rule.getRuleType())
                ? Set.of()
                : loadPaymentMethods(rule.getId()).stream()
                        .map(item -> paymentScopeKey(item.getPaymentMethod(), item.getCardBrand()).key())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> configuredMerchantIds = merchantIdsForRule(rule);
        List<PaymentRouteCandidateDO> candidates = loadCandidates(rule.getId());
        if (strategy == null) {
            issues.add(issue("ERROR", "STRATEGY_MISSING", "strategy", "A route type configuration is required"));
        } else if (!rule.getRuleType().equals(strategy.getStrategyType()) || strategy.getStrategyStatus() != ENABLED) {
            issues.add(issue("ERROR", "STRATEGY_UNAVAILABLE", "strategy", "The route type configuration must match the rule and be enabled"));
        } else if (RULE_HISTORY_AFFINITY.equals(rule.getRuleType())
                && (strategy.getHistoryLookbackMonths() == null || strategy.getHistoryLookbackMonths() < 1
                || strategy.getHistoryLookbackMonths() > MAX_HISTORY_LOOKBACK_MONTHS)) {
            issues.add(issue("ERROR", "HISTORY_LOOKBACK_INVALID", "strategy.historyLookbackMonths",
                    "History lookback months must be between 1 and 24"));
        }
        if (SCOPE_MERCHANT.equals(rule.getScopeType()) && configuredMerchantIds.isEmpty()) {
            issues.add(issue("ERROR", "MERCHANT_REQUIRED", "merchantIds", "At least one merchant is required"));
        }
        if (!RULE_HISTORY_AFFINITY.equals(rule.getRuleType()) && configuredMethods.isEmpty()) {
            issues.add(issue("ERROR", "PAYMENT_METHOD_REQUIRED", "paymentMethods", "At least one explicit payment method is required"));
        }
        List<PaymentRouteCandidateDO> enabledCandidates = candidates.stream()
                .filter(item -> item.getCandidateStatus() == ENABLED)
                .toList();
        if (RULE_HISTORY_AFFINITY.equals(rule.getRuleType())) {
            if (!candidates.isEmpty()) {
                issues.add(issue("ERROR", "HISTORY_CANDIDATE_NOT_ALLOWED", "candidates",
                        "History affinity routing must not configure fixed candidates"));
            }
        } else if (publishing && enabledCandidates.isEmpty()) {
            issues.add(issue("ERROR", "CANDIDATE_REQUIRED", "candidates", "At least one enabled candidate is required"));
        }
        Set<String> enabledMethods = enabledCandidates.stream()
                .map(item -> paymentScopeKey(item.getPaymentMethod(), item.getCardBrand()).key())
                .collect(Collectors.toSet());
        if (!RULE_HISTORY_AFFINITY.equals(rule.getRuleType())) {
            configuredMethods.stream().filter(method -> !enabledMethods.contains(method)).forEach(method ->
                issues.add(issue("ERROR", "PAYMENT_METHOD_CANDIDATE_REQUIRED", "candidates[" + method + "]",
                        "Each configured payment scope requires an enabled candidate")));
            validateTypeSpecificCandidates(rule.getRuleType(), configuredMethods, candidates, enabledCandidates, issues);
            validateCandidateAvailability(rule, configuredMerchantIds, enabledCandidates, issues);
        }
        if (allRules != null) {
            Map<Long, Set<String>> paymentMethodsByRule = candidatePaymentMethodsByRule(allRules);
            Map<Long, Set<String>> transactionTypesByRule = transactionTypesByRule(allRules);
            Map<Long, Set<String>> merchantIdsByRule = merchantIdsByRule(allRules);
            allRules.stream()
                    .filter(other -> !Objects.equals(other.getId(), rule.getId()))
                    .filter(other -> STATUS_ENABLED.equals(other.getRuleStatus()))
                    .filter(other -> Objects.equals(other.getRulePriority(), rule.getRulePriority()))
                    .filter(other -> rulesOverlap(
                            rule,
                            merchantIdsForRule(rule, merchantIdsByRule),
                            paymentMethodsForRule(rule, paymentMethodsByRule),
                            transactionTypesForRule(rule, transactionTypesByRule),
                            other,
                            merchantIdsForRule(other, merchantIdsByRule),
                            paymentMethodsForRule(other, paymentMethodsByRule),
                            transactionTypesForRule(other, transactionTypesByRule)))
                    .findFirst()
                    .ifPresent(other -> issues.add(issue("ERROR", "RULE_PRIORITY_CONFLICT", "rulePriority",
                            "Enabled rule " + other.getRuleCode() + " overlaps at the same priority")));
        }
        return issues;
    }

    /** 发布校验再次按持久化事实核对类型约束，防止绕过保存接口写入无效组合。 */
    private void validateTypeSpecificCandidates(String ruleType,
                                                Set<String> paymentMethods,
                                                List<PaymentRouteCandidateDO> candidates,
                                                List<PaymentRouteCandidateDO> enabledCandidates,
                                                List<ValidationIssue> issues) {
        for (String method : paymentMethods) {
            List<PaymentRouteCandidateDO> grouped = candidates.stream()
                    .filter(candidate -> method.equals(paymentScopeKey(candidate.getPaymentMethod(), candidate.getCardBrand()).key()))
                    .toList();
            List<PaymentRouteCandidateDO> enabled = enabledCandidates.stream()
                    .filter(candidate -> method.equals(paymentScopeKey(candidate.getPaymentMethod(), candidate.getCardBrand()).key()))
                    .toList();
            if (RULE_FIXED.equals(ruleType) && (grouped.size() != 1 || enabled.size() != 1)) {
                issues.add(issue("ERROR", "FIXED_CANDIDATE_COUNT_INVALID", "candidates[" + method + "]",
                        "Fixed routing requires exactly one enabled MID"));
            }
            if (RULE_WEIGHT.equals(ruleType)) {
                boolean invalidWeight = enabled.stream().anyMatch(candidate -> candidate.getCandidateWeight() == null
                        || candidate.getCandidateWeight().compareTo(BigDecimal.ZERO) <= 0);
                BigDecimal totalWeight = enabled.stream()
                        .map(PaymentRouteCandidateDO::getCandidateWeight)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (invalidWeight || totalWeight.compareTo(FULL_WEIGHT) != 0) {
                    issues.add(issue("ERROR", "WEIGHT_TOTAL_INVALID", "candidates[" + method + "]",
                            "Enabled candidate weights must total 100.00"));
                }
            }
        }
    }

    /** 校验候选渠道、MID、生效时间及商户绑定，避免发布悬挂配置。 */
    private void validateCandidateAvailability(PaymentRouteRuleDO rule,
                                               Set<String> merchantIds,
                                               List<PaymentRouteCandidateDO> candidates,
                                               List<ValidationIssue> issues) {
        if (candidates.isEmpty()) {
            return;
        }
        Set<Long> channelIds = candidates.stream().map(PaymentRouteCandidateDO::getChannelId).collect(Collectors.toSet());
        Set<Long> midIds = candidates.stream().map(PaymentRouteCandidateDO::getMidConfigId).collect(Collectors.toSet());
        Map<Long, ChannelInfoDO> channels = channelInfoMapper.selectList(Wrappers.<ChannelInfoDO>lambdaQuery()
                        .in(ChannelInfoDO::getId, channelIds)
                        .eq(ChannelInfoDO::getDeleted, NOT_DELETED))
                .stream().collect(Collectors.toMap(ChannelInfoDO::getId, Function.identity()));
        Map<Long, ChannelMidConfigDO> mids = midConfigMapper.selectList(Wrappers.<ChannelMidConfigDO>lambdaQuery()
                        .in(ChannelMidConfigDO::getId, midIds)
                        .eq(ChannelMidConfigDO::getDeleted, NOT_DELETED))
                .stream().collect(Collectors.toMap(ChannelMidConfigDO::getId, Function.identity()));
        Map<String, Set<Long>> boundMidIdsByMerchant = activeBoundMidIdsByMerchant(rule, merchantIds, midIds);
        LocalDateTime now = LocalDateTime.now();
        for (PaymentRouteCandidateDO candidate : candidates) {
            String candidatePaymentMethod = normalize(candidate.getPaymentMethod());
            String candidateCardBrand = normalize(candidate.getCardBrand());
            String field = "candidates[" + paymentScopeKey(candidatePaymentMethod, candidateCardBrand).key()
                    + ':' + candidate.getCandidatePriority() + "]";
            ChannelInfoDO channel = channels.get(candidate.getChannelId());
            ChannelMidConfigDO mid = mids.get(candidate.getMidConfigId());
            if (channel == null || channel.getChannelStatus() != ENABLED || channel.getSupportAcquiring() != ENABLED) {
                issues.add(issue("ERROR", "CHANNEL_UNAVAILABLE", field, "Candidate channel is not active for acquiring"));
                continue;
            }
            if (mid == null || mid.getMidStatus() != ENABLED || !BUSINESS_ACQUIRING.equals(mid.getBusinessType())
                    || !Objects.equals(mid.getChannelId(), candidate.getChannelId())) {
                issues.add(issue("ERROR", "MID_UNAVAILABLE", field, "Candidate MID is inactive or does not belong to the selected channel"));
                continue;
            }
            if (ALL.equals(candidatePaymentMethod) || !supportsPaymentMethod(mid.getPaymentMethodScope(), candidatePaymentMethod)) {
                issues.add(issue("ERROR", "MID_PAYMENT_METHOD_UNSUPPORTED", field,
                        "Candidate MID does not support the configured payment method"));
            }
            if (BANK_CARD.equals(candidatePaymentMethod) && !supportsCardBrand(mid.getCardBrandScope(), candidateCardBrand)) {
                issues.add(issue("ERROR", "MID_PAYMENT_METHOD_UNSUPPORTED", field,
                        "Candidate MID does not support the configured card brand"));
            }
            if (mid.getEffectiveTime() != null && mid.getEffectiveTime().isAfter(now)
                    || mid.getExpireTime() != null && !mid.getExpireTime().isAfter(now)) {
                issues.add(issue("ERROR", "MID_OUTSIDE_EFFECTIVE_WINDOW", field, "Candidate MID is outside its effective window"));
            }
            if (SCOPE_MERCHANT.equals(rule.getScopeType())) {
                List<String> missingMerchants = merchantIds.stream()
                        .filter(merchantId -> !boundMidIdsByMerchant.getOrDefault(merchantId, Set.of())
                                .contains(candidate.getMidConfigId()))
                        .toList();
                if (!missingMerchants.isEmpty()) {
                    issues.add(issue("ERROR", "MERCHANT_MID_BINDING_MISSING", field,
                            "Candidate MID is not actively bound to merchants: " + String.join(",", missingMerchants)));
                }
            }
        }
    }

    /** 一次读取商户有效绑定，发布时不按候选逐条查询。 */
    private Map<String, Set<Long>> activeBoundMidIdsByMerchant(PaymentRouteRuleDO rule,
                                                               Set<String> merchantIds,
                                                               Set<Long> midIds) {
        if (!SCOPE_MERCHANT.equals(rule.getScopeType()) || merchantIds.isEmpty() || midIds.isEmpty()) {
            return Map.of();
        }
        LocalDateTime now = LocalDateTime.now();
        return midBindingMapper.selectList(Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                        .in(MerchantChannelMidBindingDO::getMerchantId, merchantIds)
                        .in(MerchantChannelMidBindingDO::getMidConfigId, midIds)
                        .eq(MerchantChannelMidBindingDO::getBindingStatus, ENABLED)
                        .eq(MerchantChannelMidBindingDO::getDeleted, NOT_DELETED)
                        .and(wrapper -> wrapper.isNull(MerchantChannelMidBindingDO::getEffectiveTime)
                                .or().le(MerchantChannelMidBindingDO::getEffectiveTime, now))
                        .and(wrapper -> wrapper.isNull(MerchantChannelMidBindingDO::getExpireTime)
                                .or().gt(MerchantChannelMidBindingDO::getExpireTime, now)))
                .stream()
                .collect(Collectors.groupingBy(MerchantChannelMidBindingDO::getMerchantId,
                        Collectors.mapping(MerchantChannelMidBindingDO::getMidConfigId, Collectors.toSet())));
    }

    /** 判断两个规则的商户、支付方式和交易类型是否存在交集。 */
    private boolean rulesOverlap(PaymentRouteRuleDO left,
                                 Set<String> leftMerchantIds,
                                 Set<String> leftPaymentMethods,
                                 Set<String> leftTransactionTypes,
                                 PaymentRouteRuleDO right,
                                 Set<String> rightMerchantIds,
                                 Set<String> rightPaymentMethods,
                                 Set<String> rightTransactionTypes) {
        return sameBusiness(left, right)
                && merchantScopeOverlaps(left, leftMerchantIds, right, rightMerchantIds)
                && paymentScopeSetsOverlap(leftPaymentMethods, rightPaymentMethods)
                && scopeSetsOverlap(leftTransactionTypes, rightTransactionTypes);
    }

    private boolean sameBusiness(PaymentRouteRuleDO left, PaymentRouteRuleDO right) {
        return Objects.equals(left.getBusinessType(), right.getBusinessType());
    }

    private boolean merchantScopeOverlaps(PaymentRouteRuleDO left,
                                          Set<String> leftMerchantIds,
                                          PaymentRouteRuleDO right,
                                          Set<String> rightMerchantIds) {
        if (SCOPE_GLOBAL.equals(left.getScopeType()) || SCOPE_GLOBAL.equals(right.getScopeType())) {
            return true;
        }
        return leftMerchantIds.stream().anyMatch(rightMerchantIds::contains);
    }

    private boolean scopeSetsOverlap(Set<String> left, Set<String> right) {
        return left.contains(ALL) || right.contains(ALL) || left.stream().anyMatch(right::contains);
    }

    private boolean paymentScopeSetsOverlap(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return true;
        }
        return left.stream().map(PaymentScopeKey::parse).anyMatch(leftScope ->
                right.stream().map(PaymentScopeKey::parse).anyMatch(rightScope ->
                        leftScope.paymentMethod().equals(rightScope.paymentMethod())
                                && (leftScope.cardBrand().equals(rightScope.cardBrand())
                                || ALL.equals(leftScope.cardBrand())
                                || ALL.equals(rightScope.cardBrand()))));
    }

    /** 一次读取参与发布冲突检查的显式支付方式，历史路由不再依赖候选 MID 反推。 */
    private Map<Long, Set<String>> candidatePaymentMethodsByRule(List<PaymentRouteRuleDO> rules) {
        Set<Long> ruleIds = rules.stream().map(PaymentRouteRuleDO::getId).collect(Collectors.toSet());
        if (ruleIds.isEmpty()) {
            return Map.of();
        }
        return methodMapper.selectList(Wrappers.<PaymentRouteMethodDO>lambdaQuery()
                .select(PaymentRouteMethodDO::getRuleId, PaymentRouteMethodDO::getPaymentMethod, PaymentRouteMethodDO::getCardBrand)
                        .in(PaymentRouteMethodDO::getRuleId, ruleIds)
                        .eq(PaymentRouteMethodDO::getDeleted, NOT_DELETED))
                .stream().filter(item -> StringUtils.hasText(item.getPaymentMethod()))
                .collect(Collectors.groupingBy(PaymentRouteMethodDO::getRuleId,
                        Collectors.mapping(item -> paymentScopeKey(item.getPaymentMethod(), item.getCardBrand()).key(),
                                Collectors.toCollection(LinkedHashSet::new))));
    }

    private Set<String> paymentMethodsForRule(PaymentRouteRuleDO rule, Map<Long, Set<String>> methodsByRule) {
        if (RULE_HISTORY_AFFINITY.equals(rule.getRuleType())) {
            return Set.of();
        }
        Set<String> methods = methodsByRule.get(rule.getId());
        if (methods != null && !methods.isEmpty()) {
            return methods;
        }
        if (!StringUtils.hasText(rule.getPaymentMethod())) {
            return Set.of();
        }
        String paymentMethod = normalize(rule.getPaymentMethod());
        return Set.of(paymentScopeKey(paymentMethod, BANK_CARD.equals(paymentMethod) ? ALL : NONE).key());
    }

    private Map<Long, Set<String>> transactionTypesByRule(List<PaymentRouteRuleDO> rules) {
        Set<Long> ruleIds = rules.stream().map(PaymentRouteRuleDO::getId).collect(Collectors.toSet());
        if (ruleIds.isEmpty()) {
            return Map.of();
        }
        return transactionTypeMapper.selectList(Wrappers.<PaymentRouteTransactionTypeDO>lambdaQuery()
                        .select(PaymentRouteTransactionTypeDO::getRuleId, PaymentRouteTransactionTypeDO::getTransactionType)
                        .in(PaymentRouteTransactionTypeDO::getRuleId, ruleIds)
                        .eq(PaymentRouteTransactionTypeDO::getDeleted, NOT_DELETED))
                .stream().filter(item -> StringUtils.hasText(item.getTransactionType()))
                .collect(Collectors.groupingBy(PaymentRouteTransactionTypeDO::getRuleId,
                        Collectors.mapping(item -> normalize(item.getTransactionType()),
                                Collectors.toCollection(LinkedHashSet::new))));
    }

    private Map<Long, Set<String>> merchantIdsByRule(List<PaymentRouteRuleDO> rules) {
        Set<Long> ruleIds = rules.stream().map(PaymentRouteRuleDO::getId).collect(Collectors.toSet());
        if (ruleIds.isEmpty()) {
            return Map.of();
        }
        return ruleMerchantMapper.selectList(Wrappers.<PaymentRouteRuleMerchantDO>lambdaQuery()
                        .select(PaymentRouteRuleMerchantDO::getRuleId, PaymentRouteRuleMerchantDO::getMerchantId)
                        .in(PaymentRouteRuleMerchantDO::getRuleId, ruleIds)
                        .eq(PaymentRouteRuleMerchantDO::getDeleted, NOT_DELETED))
                .stream()
                .filter(item -> StringUtils.hasText(item.getMerchantId()))
                .collect(Collectors.groupingBy(PaymentRouteRuleMerchantDO::getRuleId,
                        Collectors.mapping(PaymentRouteRuleMerchantDO::getMerchantId,
                                Collectors.toCollection(LinkedHashSet::new))));
    }

    private Set<String> merchantIdsForRule(PaymentRouteRuleDO rule) {
        return merchantIdsForRule(rule, merchantIdsByRule(List.of(rule)));
    }

    private Set<String> merchantIdsForRule(PaymentRouteRuleDO rule, Map<Long, Set<String>> merchantIdsByRule) {
        if (SCOPE_GLOBAL.equals(rule.getScopeType())) {
            return Set.of();
        }
        Set<String> merchantIds = merchantIdsByRule.get(rule.getId());
        if (merchantIds != null && !merchantIds.isEmpty()) {
            return merchantIds;
        }
        return StringUtils.hasText(rule.getMerchantId())
                ? Set.of(rule.getMerchantId().trim())
                : Set.of();
    }

    private Set<String> transactionTypesForRule(PaymentRouteRuleDO rule, Map<Long, Set<String>> typesByRule) {
        if (RULE_HISTORY_AFFINITY.equals(rule.getRuleType())) {
            return Set.of(ALL);
        }
        Set<String> types = typesByRule.get(rule.getId());
        if (types != null && !types.isEmpty()) {
            return types;
        }
        return StringUtils.hasText(rule.getTransactionType()) ? Set.of(rule.getTransactionType()) : Set.of(ALL);
    }

    private boolean supportsPaymentMethod(String scope, String paymentMethod) {
        Set<String> supported = splitScope(scope);
        return supported.contains(ALL) || supported.contains(paymentMethod);
    }

    private boolean supportsCardBrand(String scope, String cardBrand) {
        Set<String> supported = splitScope(scope);
        return supported.contains(ALL) || supported.contains(cardBrand);
    }

    /** 用条件更新实现真正的乐观锁，不依赖先查后改的时间窗口。 */
    private void updateRuleVersioned(PaymentRouteRuleDO rule, int expectedVersion, Set<String> allowedStatuses) {
        LambdaUpdateWrapper<PaymentRouteRuleDO> update = Wrappers.<PaymentRouteRuleDO>lambdaUpdate()
                .eq(PaymentRouteRuleDO::getId, rule.getId())
                .eq(PaymentRouteRuleDO::getVersion, expectedVersion)
                .eq(PaymentRouteRuleDO::getDeleted, NOT_DELETED)
                .in(PaymentRouteRuleDO::getRuleStatus, allowedStatuses)
                .set(PaymentRouteRuleDO::getRuleCode, rule.getRuleCode())
                .set(PaymentRouteRuleDO::getRuleName, rule.getRuleName())
                .set(PaymentRouteRuleDO::getRuleType, rule.getRuleType())
                .set(PaymentRouteRuleDO::getScopeType, rule.getScopeType())
                .set(PaymentRouteRuleDO::getMerchantId, rule.getMerchantId())
                .set(PaymentRouteRuleDO::getBusinessType, rule.getBusinessType())
                .set(PaymentRouteRuleDO::getRulePriority, rule.getRulePriority())
                .set(PaymentRouteRuleDO::getUnavailablePolicy, rule.getUnavailablePolicy())
                .set(PaymentRouteRuleDO::getRuleStatus, rule.getRuleStatus())
                .set(PaymentRouteRuleDO::getVersion, rule.getVersion())
                .set(PaymentRouteRuleDO::getRemark, rule.getRemark())
                .set(PaymentRouteRuleDO::getPublishedBy, rule.getPublishedBy())
                .set(PaymentRouteRuleDO::getPublishedTime, rule.getPublishedTime())
                .set(PaymentRouteRuleDO::getDisabledBy, rule.getDisabledBy())
                .set(PaymentRouteRuleDO::getDisabledTime, rule.getDisabledTime())
                .set(PaymentRouteRuleDO::getUpdateBy, rule.getUpdateBy())
                .set(PaymentRouteRuleDO::getUpdateTime, rule.getUpdateTime());
        ensureUpdated(ruleMapper.update(null, update));
    }

    /** 替换策略和候选属于同一规则更新事务，任何一步失败均整体回滚。 */
    private void replaceAssociations(Long ruleId,
                                     Collection<String> merchantIds,
                                     Collection<PaymentScopeKey> paymentScopes,
                                     Collection<String> transactionTypes,
                                     RouteStrategyRequest strategy,
                                     List<RouteCandidateRequest> candidates,
                                     String operator,
                                     LocalDateTime now) {
        softDeleteAssociations(ruleId, operator, now);
        PaymentRouteStrategyDO strategyDO = new PaymentRouteStrategyDO();
        strategyDO.setRuleId(ruleId);
        strategyDO.setStrategyType(normalize(strategy.getStrategyType()));
        strategyDO.setStrategyPriority(100);
        strategyDO.setHistoryLookbackMonths(strategy.getHistoryLookbackMonths());
        strategyDO.setStrategyStatus(strategy.getStrategyStatus());
        strategyDO.setCreateBy(operator);
        strategyDO.setCreateTime(now);
        strategyDO.setUpdateBy(operator);
        strategyDO.setUpdateTime(now);
        strategyDO.setDeleted(NOT_DELETED);
        strategyMapper.insert(strategyDO);

        for (String merchantId : merchantIds) {
            PaymentRouteRuleMerchantDO relation = new PaymentRouteRuleMerchantDO();
            relation.setRuleId(ruleId);
            relation.setMerchantId(merchantId);
            relation.setCreateBy(operator);
            relation.setCreateTime(now);
            relation.setUpdateBy(operator);
            relation.setUpdateTime(now);
            relation.setDeleted(NOT_DELETED);
            ruleMerchantMapper.insert(relation);
        }

        for (PaymentScopeKey paymentScope : paymentScopes) {
            PaymentRouteMethodDO methodDO = new PaymentRouteMethodDO();
            methodDO.setRuleId(ruleId);
            methodDO.setPaymentMethod(paymentScope.paymentMethod());
            methodDO.setCardBrand(paymentScope.cardBrand());
            methodDO.setCreateBy(operator);
            methodDO.setCreateTime(now);
            methodDO.setUpdateBy(operator);
            methodDO.setUpdateTime(now);
            methodDO.setDeleted(NOT_DELETED);
            methodMapper.insert(methodDO);
        }

        for (String transactionType : normalizeTransactionTypes(transactionTypes)) {
            PaymentRouteTransactionTypeDO transactionTypeDO = new PaymentRouteTransactionTypeDO();
            transactionTypeDO.setRuleId(ruleId);
            transactionTypeDO.setTransactionType(transactionType);
            transactionTypeDO.setCreateBy(operator);
            transactionTypeDO.setCreateTime(now);
            transactionTypeDO.setUpdateBy(operator);
            transactionTypeDO.setUpdateTime(now);
            transactionTypeDO.setDeleted(NOT_DELETED);
            transactionTypeMapper.insert(transactionTypeDO);
        }

        List<RouteCandidateRequest> values = candidates == null ? List.of() : candidates;
        for (RouteCandidateRequest candidate : values) {
            PaymentRouteCandidateDO candidateDO = new PaymentRouteCandidateDO();
            candidateDO.setRuleId(ruleId);
            candidateDO.setPaymentMethod(normalize(candidate.getPaymentMethod()));
            candidateDO.setCardBrand(normalize(candidate.getCardBrand()));
            candidateDO.setChannelId(candidate.getChannelId());
            candidateDO.setMidConfigId(candidate.getMidConfigId());
            candidateDO.setCandidatePriority(candidate.getCandidatePriority());
            candidateDO.setCandidateWeight(candidate.getCandidateWeight());
            candidateDO.setCandidateStatus(candidate.getCandidateStatus());
            candidateDO.setCreateBy(operator);
            candidateDO.setCreateTime(now);
            candidateDO.setUpdateBy(operator);
            candidateDO.setUpdateTime(now);
            candidateDO.setDeleted(NOT_DELETED);
            candidateMapper.insert(candidateDO);
        }
    }

    /** 逐行设置唯一删除标识，保证多次替换不会撞软删除唯一键。 */
    private void softDeleteAssociations(Long ruleId, String operator, LocalDateTime now) {
        strategyMapper.selectList(Wrappers.<PaymentRouteStrategyDO>lambdaQuery()
                        .eq(PaymentRouteStrategyDO::getRuleId, ruleId)
                        .eq(PaymentRouteStrategyDO::getDeleted, NOT_DELETED))
                .forEach(item -> {
                    item.setDeleted(item.getId());
                    item.setUpdateBy(operator);
                    item.setUpdateTime(now);
                    strategyMapper.updateById(item);
                });
        ruleMerchantMapper.selectList(Wrappers.<PaymentRouteRuleMerchantDO>lambdaQuery()
                        .eq(PaymentRouteRuleMerchantDO::getRuleId, ruleId)
                        .eq(PaymentRouteRuleMerchantDO::getDeleted, NOT_DELETED))
                .forEach(item -> {
                    item.setDeleted(item.getId());
                    item.setUpdateBy(operator);
                    item.setUpdateTime(now);
                    ruleMerchantMapper.updateById(item);
                });
        methodMapper.selectList(Wrappers.<PaymentRouteMethodDO>lambdaQuery()
                        .eq(PaymentRouteMethodDO::getRuleId, ruleId)
                        .eq(PaymentRouteMethodDO::getDeleted, NOT_DELETED))
                .forEach(item -> {
                    item.setDeleted(item.getId());
                    item.setUpdateBy(operator);
                    item.setUpdateTime(now);
                    methodMapper.updateById(item);
                });
        transactionTypeMapper.selectList(Wrappers.<PaymentRouteTransactionTypeDO>lambdaQuery()
                        .eq(PaymentRouteTransactionTypeDO::getRuleId, ruleId)
                        .eq(PaymentRouteTransactionTypeDO::getDeleted, NOT_DELETED))
                .forEach(item -> {
                    item.setDeleted(item.getId());
                    item.setUpdateBy(operator);
                    item.setUpdateTime(now);
                    transactionTypeMapper.updateById(item);
                });
        candidateMapper.selectList(Wrappers.<PaymentRouteCandidateDO>lambdaQuery()
                        .eq(PaymentRouteCandidateDO::getRuleId, ruleId)
                        .eq(PaymentRouteCandidateDO::getDeleted, NOT_DELETED))
                .forEach(item -> {
                    item.setDeleted(item.getId());
                    item.setUpdateBy(operator);
                    item.setUpdateTime(now);
                    candidateMapper.updateById(item);
                });
    }

    /** 批量装配规则列表的关联事实。 */
    private List<RouteRuleResponse> assemble(List<PaymentRouteRuleDO> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        Set<Long> ruleIds = rules.stream().map(PaymentRouteRuleDO::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, PaymentRouteStrategyDO> strategies = strategyMapper.selectList(Wrappers.<PaymentRouteStrategyDO>lambdaQuery()
                        .in(PaymentRouteStrategyDO::getRuleId, ruleIds)
                        .eq(PaymentRouteStrategyDO::getDeleted, NOT_DELETED))
                .stream().collect(Collectors.toMap(PaymentRouteStrategyDO::getRuleId, Function.identity(), (left, right) -> left));
        Map<Long, List<PaymentRouteRuleMerchantDO>> merchantsByRule = ruleMerchantMapper.selectList(
                        Wrappers.<PaymentRouteRuleMerchantDO>lambdaQuery()
                                .in(PaymentRouteRuleMerchantDO::getRuleId, ruleIds)
                                .eq(PaymentRouteRuleMerchantDO::getDeleted, NOT_DELETED)
                                .orderByAsc(PaymentRouteRuleMerchantDO::getMerchantId))
                .stream()
                .collect(Collectors.groupingBy(PaymentRouteRuleMerchantDO::getRuleId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<PaymentRouteMethodDO>> methodsByRule = methodMapper.selectList(Wrappers.<PaymentRouteMethodDO>lambdaQuery()
                        .in(PaymentRouteMethodDO::getRuleId, ruleIds)
                        .eq(PaymentRouteMethodDO::getDeleted, NOT_DELETED)
                        .orderByAsc(PaymentRouteMethodDO::getPaymentMethod)
                        .orderByAsc(PaymentRouteMethodDO::getCardBrand))
                .stream().collect(Collectors.groupingBy(PaymentRouteMethodDO::getRuleId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<PaymentRouteTransactionTypeDO>> transactionTypesByRule = transactionTypeMapper.selectList(
                        Wrappers.<PaymentRouteTransactionTypeDO>lambdaQuery()
                                .in(PaymentRouteTransactionTypeDO::getRuleId, ruleIds)
                                .eq(PaymentRouteTransactionTypeDO::getDeleted, NOT_DELETED)
                                .orderByAsc(PaymentRouteTransactionTypeDO::getTransactionType))
                .stream().collect(Collectors.groupingBy(PaymentRouteTransactionTypeDO::getRuleId, LinkedHashMap::new, Collectors.toList()));
        List<PaymentRouteCandidateDO> candidateRows = candidateMapper.selectList(Wrappers.<PaymentRouteCandidateDO>lambdaQuery()
                .in(PaymentRouteCandidateDO::getRuleId, ruleIds)
                .eq(PaymentRouteCandidateDO::getDeleted, NOT_DELETED)
                .orderByAsc(PaymentRouteCandidateDO::getCandidatePriority));
        Map<Long, List<PaymentRouteCandidateDO>> candidatesByRule = candidateRows.stream()
                .collect(Collectors.groupingBy(PaymentRouteCandidateDO::getRuleId, LinkedHashMap::new, Collectors.toList()));
        CandidateReferenceMaps references = loadCandidateReferences(candidateRows);
        return rules.stream()
                .map(rule -> toResponse(rule, strategies.get(rule.getId()), merchantsByRule.getOrDefault(rule.getId(), List.of()),
                        methodsByRule.getOrDefault(rule.getId(), List.of()),
                        transactionTypesByRule.getOrDefault(rule.getId(), List.of()),
                        candidatesByRule.getOrDefault(rule.getId(), List.of()), references))
                .toList();
    }

    private RouteRuleResponse assembleOne(PaymentRouteRuleDO rule) {
        return assemble(List.of(rule)).get(0);
    }

    /** 候选展示名称来自现有渠道和 MID 主数据，不在路由表重复保存。 */
    private CandidateReferenceMaps loadCandidateReferences(List<PaymentRouteCandidateDO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new CandidateReferenceMaps(Map.of(), Map.of());
        }
        Set<Long> channelIds = candidates.stream().map(PaymentRouteCandidateDO::getChannelId).collect(Collectors.toSet());
        Set<Long> midIds = candidates.stream().map(PaymentRouteCandidateDO::getMidConfigId).collect(Collectors.toSet());
        Map<Long, ChannelInfoDO> channels = channelInfoMapper.selectList(Wrappers.<ChannelInfoDO>lambdaQuery()
                        .in(ChannelInfoDO::getId, channelIds)
                        .eq(ChannelInfoDO::getDeleted, NOT_DELETED))
                .stream().collect(Collectors.toMap(ChannelInfoDO::getId, Function.identity()));
        Map<Long, ChannelMidConfigDO> mids = midConfigMapper.selectList(Wrappers.<ChannelMidConfigDO>lambdaQuery()
                        .in(ChannelMidConfigDO::getId, midIds)
                        .eq(ChannelMidConfigDO::getDeleted, NOT_DELETED))
                .stream().collect(Collectors.toMap(ChannelMidConfigDO::getId, Function.identity()));
        return new CandidateReferenceMaps(channels, mids);
    }

    private RouteRuleResponse toResponse(PaymentRouteRuleDO rule,
                                         PaymentRouteStrategyDO strategy,
                                         List<PaymentRouteRuleMerchantDO> merchants,
                                         List<PaymentRouteMethodDO> methods,
                                         List<PaymentRouteTransactionTypeDO> transactionTypes,
                                         List<PaymentRouteCandidateDO> candidates,
                                         CandidateReferenceMaps references) {
        RouteRuleResponse response = new RouteRuleResponse();
        response.setId(rule.getId());
        response.setRuleCode(rule.getRuleCode());
        response.setRuleName(rule.getRuleName());
        response.setRuleType(rule.getRuleType());
        response.setScopeType(rule.getScopeType());
        List<String> merchantIds = merchants.stream()
                .map(PaymentRouteRuleMerchantDO::getMerchantId)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
        if (merchantIds.isEmpty() && SCOPE_MERCHANT.equals(rule.getScopeType()) && StringUtils.hasText(rule.getMerchantId())) {
            merchantIds = List.of(rule.getMerchantId());
        }
        response.setMerchantIds(merchantIds);
        response.setMerchantId(merchantIds.size() == 1 ? merchantIds.get(0) : null);
        response.setBusinessType(rule.getBusinessType());
        List<RoutePaymentScope> configuredScopes = RULE_HISTORY_AFFINITY.equals(rule.getRuleType())
                ? List.of()
                : methods.stream()
                .filter(item -> StringUtils.hasText(item.getPaymentMethod()))
                .map(item -> toPaymentScope(item.getPaymentMethod(), item.getCardBrand()))
                .distinct()
                .sorted(Comparator.comparing(RoutePaymentScope::getPaymentMethod).thenComparing(RoutePaymentScope::getCardBrand))
                .toList();
        if (!RULE_HISTORY_AFFINITY.equals(rule.getRuleType()) && configuredScopes.isEmpty()) {
            configuredScopes = candidates.stream()
                    .filter(item -> StringUtils.hasText(item.getPaymentMethod()) && !ALL.equals(item.getPaymentMethod()))
                    .map(item -> toPaymentScope(item.getPaymentMethod(), item.getCardBrand()))
                    .distinct()
                    .sorted(Comparator.comparing(RoutePaymentScope::getPaymentMethod).thenComparing(RoutePaymentScope::getCardBrand))
                    .toList();
        }
        if (!RULE_HISTORY_AFFINITY.equals(rule.getRuleType())
                && configuredScopes.isEmpty() && StringUtils.hasText(rule.getPaymentMethod())) {
            String legacyMethod = normalize(rule.getPaymentMethod());
            configuredScopes = List.of(toPaymentScope(
                    legacyMethod,
                    BANK_CARD.equals(legacyMethod) ? defaultCardBrand(rule.getCardBrand(), ALL) : NONE
            ));
        }
        response.setPaymentScopes(configuredScopes);
        response.setPaymentMethods(configuredScopes.stream()
                .map(RoutePaymentScope::getPaymentMethod)
                .distinct()
                .sorted()
                .toList());
        List<String> configuredTransactionTypes = RULE_HISTORY_AFFINITY.equals(rule.getRuleType())
                ? List.of()
                : transactionTypes.stream()
                .map(PaymentRouteTransactionTypeDO::getTransactionType)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
        if (!RULE_HISTORY_AFFINITY.equals(rule.getRuleType())
                && configuredTransactionTypes.isEmpty() && StringUtils.hasText(rule.getTransactionType())
                && !ALL.equals(rule.getTransactionType())) {
            configuredTransactionTypes = List.of(rule.getTransactionType());
        }
        response.setTransactionTypes(configuredTransactionTypes);
        response.setRulePriority(rule.getRulePriority());
        response.setUnavailablePolicy(rule.getUnavailablePolicy());
        response.setRuleStatus(rule.getRuleStatus());
        response.setVersion(rule.getVersion());
        response.setRemark(rule.getRemark());
        response.setPublishedBy(rule.getPublishedBy());
        response.setPublishedTime(rule.getPublishedTime());
        response.setDisabledBy(rule.getDisabledBy());
        response.setDisabledTime(rule.getDisabledTime());
        response.setCreateBy(rule.getCreateBy());
        response.setCreateTime(rule.getCreateTime());
        response.setUpdateBy(rule.getUpdateBy());
        response.setUpdateTime(rule.getUpdateTime());
        response.setStrategy(toStrategyResponse(strategy));
        response.setCandidates((RULE_HISTORY_AFFINITY.equals(rule.getRuleType()) ? List.<PaymentRouteCandidateDO>of() : candidates).stream()
                .sorted(Comparator.comparing(
                                PaymentRouteCandidateDO::getPaymentMethod,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(PaymentRouteCandidateDO::getCardBrand, Comparator.nullsLast(String::compareTo))
                        .thenComparing(PaymentRouteCandidateDO::getCandidatePriority))
                .map(item -> toCandidateResponse(item, references.channels().get(item.getChannelId()), references.mids().get(item.getMidConfigId())))
                .toList());
        return response;
    }

    private RouteStrategyResponse toStrategyResponse(PaymentRouteStrategyDO strategy) {
        if (strategy == null) {
            return null;
        }
        RouteStrategyResponse response = new RouteStrategyResponse();
        response.setId(strategy.getId());
        response.setStrategyType(strategy.getStrategyType());
        response.setHistoryLookbackMonths(strategy.getHistoryLookbackMonths());
        response.setStrategyStatus(strategy.getStrategyStatus());
        return response;
    }

    private RouteCandidateResponse toCandidateResponse(PaymentRouteCandidateDO candidate,
                                                       ChannelInfoDO channel,
                                                       ChannelMidConfigDO mid) {
        RouteCandidateResponse response = new RouteCandidateResponse();
        response.setId(candidate.getId());
        response.setPaymentMethod(candidate.getPaymentMethod());
        response.setCardBrand(defaultCardBrand(candidate.getCardBrand(), NONE));
        response.setChannelId(candidate.getChannelId());
        response.setChannelCode(channel == null ? null : channel.getChannelCode());
        response.setChannelName(channelName(channel));
        response.setMidConfigId(candidate.getMidConfigId());
        response.setChannelMid(mid == null ? null : mid.getChannelMid());
        response.setMidName(mid == null ? null : mid.getMidName());
        response.setPaymentMethodScope(mid == null ? null : mid.getPaymentMethodScope());
        response.setCurrencyScope(mid == null ? null : mid.getCurrencyScope());
        response.setDefaultTransactionCurrency(mid == null ? null : mid.getDefaultTransactionCurrency());
        response.setMidStatus(mid == null ? null : mid.getMidStatus());
        response.setCandidatePriority(candidate.getCandidatePriority());
        response.setCandidateWeight(toPlainString(candidate.getCandidateWeight()));
        response.setCandidateStatus(candidate.getCandidateStatus());
        return response;
    }

    private RouteStrategyResponse loadStrategy(Long ruleId) {
        return toStrategyResponse(strategyMapper.selectOne(Wrappers.<PaymentRouteStrategyDO>lambdaQuery()
                .eq(PaymentRouteStrategyDO::getRuleId, ruleId)
                .eq(PaymentRouteStrategyDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1")));
    }

    private List<PaymentRouteCandidateDO> loadCandidates(Long ruleId) {
        return candidateMapper.selectList(Wrappers.<PaymentRouteCandidateDO>lambdaQuery()
                .eq(PaymentRouteCandidateDO::getRuleId, ruleId)
                .eq(PaymentRouteCandidateDO::getDeleted, NOT_DELETED)
                .orderByAsc(PaymentRouteCandidateDO::getPaymentMethod)
                .orderByAsc(PaymentRouteCandidateDO::getCardBrand)
                .orderByAsc(PaymentRouteCandidateDO::getCandidatePriority));
    }

    private List<PaymentRouteMethodDO> loadPaymentMethods(Long ruleId) {
        return methodMapper.selectList(Wrappers.<PaymentRouteMethodDO>lambdaQuery()
                .eq(PaymentRouteMethodDO::getRuleId, ruleId)
                .eq(PaymentRouteMethodDO::getDeleted, NOT_DELETED)
                .orderByAsc(PaymentRouteMethodDO::getPaymentMethod)
                .orderByAsc(PaymentRouteMethodDO::getCardBrand));
    }

    /** 审计快照只记录路由维度和候选主键，不记录渠道 MID 明文及渠道元数据。 */
    private Map<String, Object> auditSnapshot(RouteRuleResponse response) {
        if (response == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", response.getId());
        snapshot.put("ruleCode", response.getRuleCode());
        snapshot.put("ruleName", response.getRuleName());
        snapshot.put("ruleType", response.getRuleType());
        snapshot.put("scopeType", response.getScopeType());
        snapshot.put("merchantId", response.getMerchantId());
        snapshot.put("merchantIds", response.getMerchantIds());
        snapshot.put("businessType", response.getBusinessType());
        snapshot.put("paymentMethods", response.getPaymentMethods());
        snapshot.put("paymentScopes", response.getPaymentScopes());
        snapshot.put("transactionTypes", response.getTransactionTypes());
        snapshot.put("rulePriority", response.getRulePriority());
        snapshot.put("unavailablePolicy", response.getUnavailablePolicy());
        snapshot.put("ruleStatus", response.getRuleStatus());
        snapshot.put("version", response.getVersion());
        snapshot.put("strategy", response.getStrategy());
        snapshot.put("candidates", response.getCandidates().stream().map(candidate -> {
            Map<String, Object> candidateSnapshot = new LinkedHashMap<>();
            candidateSnapshot.put("paymentMethod", candidate.getPaymentMethod());
            candidateSnapshot.put("cardBrand", candidate.getCardBrand());
            candidateSnapshot.put("channelId", candidate.getChannelId());
            candidateSnapshot.put("midConfigId", candidate.getMidConfigId());
            candidateSnapshot.put("candidatePriority", candidate.getCandidatePriority());
            candidateSnapshot.put("candidateWeight", candidate.getCandidateWeight());
            candidateSnapshot.put("candidateStatus", candidate.getCandidateStatus());
            return candidateSnapshot;
        }).toList());
        return snapshot;
    }

    private void insertChangeLog(PaymentRouteRuleDO rule,
                                 String actionType,
                                 RouteRuleResponse before,
                                 RouteRuleResponse after,
                                 Integer beforeVersion,
                                 Integer afterVersion,
                                 String reason,
                                 LocalDateTime now) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        PaymentRouteChangeLogDO change = new PaymentRouteChangeLogDO();
        change.setRuleId(rule.getId());
        change.setRuleCode(rule.getRuleCode());
        change.setActionType(actionType);
        change.setBeforeVersion(beforeVersion);
        change.setAfterVersion(afterVersion);
        change.setBeforeSnapshotJson(before == null ? null : JsonUtils.toJsonString(auditSnapshot(before)));
        change.setAfterSnapshotJson(after == null ? null : JsonUtils.toJsonString(auditSnapshot(after)));
        change.setOperatorAccountId(account == null ? null : account.getAccountId());
        change.setOperatorName(currentOperatorName());
        change.setOperationReason(reason.trim());
        change.setCreateTime(now);
        changeLogMapper.insert(change);
    }

    private RouteChangeLogResponse toChangeLogResponse(PaymentRouteChangeLogDO row) {
        RouteChangeLogResponse response = new RouteChangeLogResponse();
        response.setId(row.getId());
        response.setRuleId(row.getRuleId());
        response.setRuleCode(row.getRuleCode());
        response.setActionType(row.getActionType());
        response.setBeforeVersion(row.getBeforeVersion());
        response.setAfterVersion(row.getAfterVersion());
        response.setBeforeSnapshotJson(row.getBeforeSnapshotJson());
        response.setAfterSnapshotJson(row.getAfterSnapshotJson());
        response.setOperatorAccountId(row.getOperatorAccountId());
        response.setOperatorName(row.getOperatorName());
        response.setOperationReason(row.getOperationReason());
        response.setCreateTime(row.getCreateTime());
        return response;
    }

    private void fillRule(PaymentRouteRuleDO rule, RouteRuleSaveRequest request) {
        rule.setRuleName(request.getRuleName().trim());
        rule.setRuleType(normalize(request.getRuleType()));
        rule.setScopeType(normalize(request.getScopeType()));
        List<String> merchantIds = new ArrayList<>(normalizeRequestMerchantIds(request));
        rule.setMerchantId(SCOPE_MERCHANT.equals(rule.getScopeType()) && merchantIds.size() == 1
                ? merchantIds.get(0)
                : null);
        rule.setBusinessType(BUSINESS_ACQUIRING);
        rule.setRulePriority(request.getRulePriority());
        rule.setUnavailablePolicy(RULE_HISTORY_AFFINITY.equals(rule.getRuleType())
                ? POLICY_FALLBACK
                : normalize(request.getUnavailablePolicy()));
        rule.setRemark(trimToNull(request.getRemark()));
    }

    private MerchantRouteRuleBrief toMerchantRouteRuleBrief(PaymentRouteRuleDO rule) {
        MerchantRouteRuleBrief brief = new MerchantRouteRuleBrief();
        brief.setId(rule.getId());
        brief.setRuleCode(rule.getRuleCode());
        brief.setRuleName(rule.getRuleName());
        brief.setRuleType(rule.getRuleType());
        brief.setRulePriority(rule.getRulePriority());
        brief.setRuleStatus(rule.getRuleStatus());
        return brief;
    }

    private PaymentRouteRuleDO copyRuleEntity(PaymentRouteRuleDO source) {
        PaymentRouteRuleDO copy = new PaymentRouteRuleDO();
        copy.setId(source.getId());
        copy.setRuleCode(source.getRuleCode());
        copy.setRuleName(source.getRuleName());
        copy.setRuleType(source.getRuleType());
        copy.setScopeType(source.getScopeType());
        copy.setMerchantId(source.getMerchantId());
        copy.setBusinessType(source.getBusinessType());
        copy.setPaymentMethod(source.getPaymentMethod());
        copy.setRulePriority(source.getRulePriority());
        copy.setUnavailablePolicy(source.getUnavailablePolicy());
        copy.setRuleStatus(source.getRuleStatus());
        copy.setVersion(source.getVersion());
        copy.setRemark(source.getRemark());
        copy.setPublishedBy(source.getPublishedBy());
        copy.setPublishedTime(source.getPublishedTime());
        copy.setDisabledBy(source.getDisabledBy());
        copy.setDisabledTime(source.getDisabledTime());
        copy.setCreateBy(source.getCreateBy());
        copy.setCreateTime(source.getCreateTime());
        copy.setUpdateBy(source.getUpdateBy());
        copy.setUpdateTime(source.getUpdateTime());
        copy.setDeleted(source.getDeleted());
        return copy;
    }

    private RouteStrategyRequest toStrategyRequest(RouteStrategyResponse response) {
        RouteStrategyRequest request = new RouteStrategyRequest();
        request.setStrategyType(response.getStrategyType());
        request.setHistoryLookbackMonths(response.getHistoryLookbackMonths());
        request.setStrategyStatus(response.getStrategyStatus());
        return request;
    }

    private RouteCandidateRequest toCandidateRequest(RouteCandidateResponse response) {
        RouteCandidateRequest request = new RouteCandidateRequest();
        request.setPaymentMethod(response.getPaymentMethod());
        request.setCardBrand(response.getCardBrand());
        request.setChannelId(response.getChannelId());
        request.setMidConfigId(response.getMidConfigId());
        request.setCandidatePriority(response.getCandidatePriority());
        request.setCandidateWeight(response.getCandidateWeight() == null ? null : new BigDecimal(response.getCandidateWeight()));
        request.setCandidateStatus(response.getCandidateStatus());
        return request;
    }

    private PaymentRouteRuleDO findRule(Long id) {
        if (id == null) {
            throw invalid("Route rule id is required");
        }
        PaymentRouteRuleDO rule = ruleMapper.selectOne(Wrappers.<PaymentRouteRuleDO>lambdaQuery()
                .eq(PaymentRouteRuleDO::getId, id)
                .eq(PaymentRouteRuleDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (rule == null) {
            throw invalid("Route rule not found");
        }
        return rule;
    }

    private PaymentRouteRuleDO findRuleIncludingDeleted(Long id) {
        PaymentRouteRuleDO rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw invalid("Route rule not found");
        }
        return rule;
    }

    /** 发布时锁定配置集合，使重叠检查和状态更新处于同一串行化临界区。 */
    private List<PaymentRouteRuleDO> lockRulesForPublish() {
        return ruleMapper.selectList(Wrappers.<PaymentRouteRuleDO>lambdaQuery()
                .eq(PaymentRouteRuleDO::getDeleted, NOT_DELETED)
                .orderByAsc(PaymentRouteRuleDO::getId)
                .last("FOR UPDATE"));
    }

    /**
     * 生成只由服务端维护的规则编码，日期便于运营识别，UUID 与数据库唯一索引共同防重。
     */
    private String generateRuleCode() {
        String datePart = LocalDate.now(PAYMENT_ZONE_ID).format(RULE_CODE_DATE_FORMATTER);
        String identifier = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        return RULE_CODE_PREFIX + "_" + datePart + "_" + identifier;
    }

    private void requireExpectedVersion(PaymentRouteRuleDO rule, Integer expectedVersion) {
        if (expectedVersion == null || !Objects.equals(rule.getVersion(), expectedVersion)) {
            throw invalid("Route rule version is stale; refresh and retry");
        }
    }

    private void ensureUpdated(int affected) {
        if (affected != 1) {
            throw invalid("Route rule state changed; refresh and retry");
        }
    }

    private ValidationIssue issue(String level, String code, String field, String message) {
        ValidationIssue issue = new ValidationIssue();
        issue.setLevel(level);
        issue.setCode(code);
        issue.setField(field);
        issue.setMessage(message);
        return issue;
    }

    private Set<String> splitScope(String value) {
        if (!StringUtils.hasText(value) || ALL.equals(value)) {
            return Set.of(ALL);
        }
        return List.of(value.split(",")).stream()
                .map(this::normalize)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String channelName(ChannelInfoDO channel) {
        if (channel == null) {
            return null;
        }
        if (StringUtils.hasText(channel.getChannelCnName())) {
            return channel.getChannelCnName();
        }
        if (StringUtils.hasText(channel.getChannelEnName())) {
            return channel.getChannelEnName();
        }
        return channel.getChannelCode();
    }

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "system";
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        if (StringUtils.hasText(account.getLoginAccount())) {
            return account.getLoginAccount();
        }
        return "system";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String defaultCardBrand(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private RoutePaymentScope toPaymentScope(String paymentMethod, String cardBrand) {
        RoutePaymentScope scope = new RoutePaymentScope();
        scope.setPaymentMethod(normalize(paymentMethod));
        scope.setCardBrand(defaultCardBrand(cardBrand, NONE));
        return scope;
    }

    private String normalizeNullable(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() || ALL.equals(normalized) ? null : normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String toPlainString(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    private record CandidateReferenceMaps(Map<Long, ChannelInfoDO> channels,
                                          Map<Long, ChannelMidConfigDO> mids) {
    }

    private record PaymentScopeKey(String paymentMethod, String cardBrand) {
        private String key() {
            return paymentMethod + ':' + cardBrand;
        }

        private static PaymentScopeKey parse(String value) {
            int separator = value == null ? -1 : value.indexOf(':');
            if (separator < 0) {
                return new PaymentScopeKey(value == null ? "" : value, NONE);
            }
            return new PaymentScopeKey(value.substring(0, separator), value.substring(separator + 1));
        }
    }
}
