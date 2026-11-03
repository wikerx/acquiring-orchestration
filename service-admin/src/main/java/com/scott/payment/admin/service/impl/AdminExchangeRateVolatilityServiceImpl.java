package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEvaluationRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEvaluationResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventActionRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleImportError;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleImportResult;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.CurrencyPair;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.TrendPoint;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.TrendSeries;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityOverview;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityQuery;
import com.scott.payment.admin.entity.exchange.ExchangeRateAlertEntities.ExchangeRateAlertEvaluationDO;
import com.scott.payment.admin.entity.exchange.ExchangeRateAlertEntities.ExchangeRateAlertEventDO;
import com.scott.payment.admin.entity.exchange.ExchangeRateAlertEntities.ExchangeRateAlertRuleDO;
import com.scott.payment.admin.entity.exchange.ExchangeRateEntities.ExchangeBusinessRateDO;
import com.scott.payment.admin.mapper.ExchangeRateAlertEvaluationMapper;
import com.scott.payment.admin.mapper.ExchangeRateAlertEventMapper;
import com.scott.payment.admin.mapper.ExchangeRateAlertRuleMapper;
import com.scott.payment.admin.mapper.ExchangeRateVolatilityMapper;
import com.scott.payment.admin.service.AdminExchangeRateVolatilityService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.db.constant.DataSourceName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 汇率波动与预警服务实现。
 *
 * <p>交易汇率和结算汇率分别计算，同一类型和币种对汇总全部来源；来源仅保留在极值和最新值证据中。</p>
 */
@Slf4j
@Service
public class AdminExchangeRateVolatilityServiceImpl implements AdminExchangeRateVolatilityService {

    private static final long NOT_DELETED = 0L;
    private static final int ENABLED = 1;
    private static final int DISABLED = 0;
    private static final int MAX_TREND_POINTS = 500;
    private static final int MAX_CURRENCY_PAIRS = 100;
    private static final int MAX_QUERY_DAYS = 90;
    private static final String ALL = "ALL";
    private static final String OPEN = "OPEN";
    private static final String ACKNOWLEDGED = "ACKNOWLEDGED";
    private static final String RESOLVED = "RESOLVED";
    private static final String CLOSED = "CLOSED";
    private static final String SUCCESS = "SUCCESS";
    private static final String PROCESSING = "PROCESSING";
    private static final Set<String> RATE_TYPES = Set.of("TRANSACTION_RATE", "SETTLEMENT_RATE");
    private static final Set<String> ALERT_LEVELS = Set.of("WARNING", "ERROR", "CRITICAL");
    private static final Set<String> EVENT_STATUSES = Set.of(OPEN, ACKNOWLEDGED, RESOLVED, CLOSED);
    private static final BigDecimal SYSTEM_TRIGGER_LIMIT = new BigDecimal("3.0000");
    private static final BigDecimal SYSTEM_RECOVERY_LIMIT = new BigDecimal("2.0000");
    private static final int SYSTEM_WINDOW_LIMIT = 4320;

    private final ExchangeRateVolatilityMapper volatilityMapper;
    private final ExchangeRateAlertRuleMapper ruleMapper;
    private final ExchangeRateAlertEventMapper eventMapper;
    private final ExchangeRateAlertEvaluationMapper evaluationMapper;

    /** 创建汇率波动预警服务。 */
    public AdminExchangeRateVolatilityServiceImpl(ExchangeRateVolatilityMapper volatilityMapper,
                                                   ExchangeRateAlertRuleMapper ruleMapper,
                                                   ExchangeRateAlertEventMapper eventMapper,
                                                   ExchangeRateAlertEvaluationMapper evaluationMapper) {
        this.volatilityMapper = volatilityMapper;
        this.ruleMapper = ruleMapper;
        this.eventMapper = eventMapper;
        this.evaluationMapper = evaluationMapper;
    }

    /** 查询所选币种对在时间区间内的跨来源波动摘要。 */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<VolatilityOverview> overview(VolatilityQuery request) {
        NormalizedVolatilityQuery query = normalizeVolatilityQuery(request);
        List<ExchangeBusinessRateDO> facts = volatilityMapper.selectTrendFacts(
                query.rateType(), query.pairs(), query.startTime(), query.endTime());
        Map<String, ExchangeRateAlertEventDO> activeEvents = loadActiveEvents(query.rateType()).stream()
                .collect(Collectors.toMap(this::pairKey, Function.identity(), (left, right) -> left));
        return groupFacts(facts).values().stream()
                .map(group -> toOverview(group, activeEvents.get(group.key())))
                .toList();
    }

    /** 查询跨来源趋势序列，并在超出展示上限时保留每个时间桶的高低点。 */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<TrendSeries> trend(VolatilityQuery request) {
        NormalizedVolatilityQuery query = normalizeVolatilityQuery(request);
        List<ExchangeBusinessRateDO> facts = volatilityMapper.selectTrendFacts(
                query.rateType(), query.pairs(), query.startTime(), query.endTime());
        return groupFacts(facts).values().stream().map(group -> {
            TrendSeries series = new TrendSeries();
            series.setRateType(group.rateType());
            series.setBaseCurrency(group.baseCurrency());
            series.setQuoteCurrency(group.quoteCurrency());
            series.setPoints(downsample(group.facts()).stream().map(this::toTrendPoint).toList());
            return series;
        }).toList();
    }

    /** 分页查询预警规则。 */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<AlertRuleResponse> pageRules(AlertRuleQuery request) {
        AlertRuleQuery query = request == null ? new AlertRuleQuery() : request;
        Page<ExchangeRateAlertRuleDO> page = ruleMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), buildRuleQuery(query));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toRuleResponse).toList());
    }

    /** 按相同筛选条件查询全部规则，用于受控导出。 */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<AlertRuleResponse> listRules(AlertRuleQuery request) {
        AlertRuleQuery query = request == null ? new AlertRuleQuery() : request;
        return ruleMapper.selectList(buildRuleQuery(query)).stream().map(this::toRuleResponse).toList();
    }

    /** 查询规则详情。 */
    @Override
    @DS(DataSourceName.SLAVE)
    public AlertRuleResponse getRule(Long id) {
        return toRuleResponse(requireRule(id));
    }

    /** 新建自定义规则。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleResponse createRule(AlertRuleSaveRequest request) {
        RuleValues values = validateRule(request, false);
        LocalDateTime now = LocalDateTime.now();
        ExchangeRateAlertRuleDO row = new ExchangeRateAlertRuleDO();
        row.setRuleCode(PaymentOrderNoGenerator.nextOrderNo("ERR", now));
        row.setSystemMandatory(DISABLED);
        row.setRuleStatus(ENABLED);
        row.setCreateBy(currentOperator());
        row.setCreateTime(now);
        row.setDeleted(NOT_DELETED);
        fillRule(row, request, values, now);
        ruleMapper.insert(row);
        log.info("汇率预警规则已创建, event: EXCHANGE_RATE_ALERT_RULE_CREATED, ruleId: {}, ruleCode: {}, rateType: {}, baseCurrency: {}, quoteCurrency: {}",
                row.getId(), row.getRuleCode(), row.getRateType(), row.getBaseCurrency(), row.getQuoteCurrency());
        return toRuleResponse(row);
    }

    /** 修改自定义规则，系统强制规则只能收紧基线。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleResponse updateRule(Long id, AlertRuleSaveRequest request) {
        ExchangeRateAlertRuleDO row = requireRule(id);
        boolean mandatory = Integer.valueOf(ENABLED).equals(row.getSystemMandatory());
        RuleValues values = validateRule(request, mandatory);
        if (mandatory && (!row.getRateType().equals(normalizeRateType(request.getRateType()))
                || !ALL.equals(normalizeScopeCurrency(request.getBaseCurrency()))
                || !ALL.equals(normalizeScopeCurrency(request.getQuoteCurrency())))) {
            throw invalid("system mandatory rule scope cannot be changed");
        }
        fillRule(row, request, values, LocalDateTime.now());
        ruleMapper.updateById(row);
        log.info("汇率预警规则已更新, event: EXCHANGE_RATE_ALERT_RULE_UPDATED, ruleId: {}, ruleCode: {}, systemMandatory: {}",
                row.getId(), row.getRuleCode(), row.getSystemMandatory());
        return toRuleResponse(row);
    }

    /** 删除没有历史事件引用的自定义规则。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRule(Long id) {
        ExchangeRateAlertRuleDO row = requireRule(id);
        rejectMandatory(row, "system mandatory rule cannot be deleted");
        long eventCount = eventMapper.selectCount(Wrappers.<ExchangeRateAlertEventDO>lambdaQuery()
                .eq(ExchangeRateAlertEventDO::getRuleId, id)
                .eq(ExchangeRateAlertEventDO::getDeleted, NOT_DELETED));
        if (eventCount > 0) {
            throw invalid("rule with alert event history cannot be deleted");
        }
        row.setDeleted(row.getId());
        row.setRuleStatus(DISABLED);
        row.setUpdateBy(currentOperator());
        row.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(row);
        log.info("汇率预警规则已删除, event: EXCHANGE_RATE_ALERT_RULE_DELETED, ruleId: {}, ruleCode: {}",
                row.getId(), row.getRuleCode());
    }

    /** 启用或停用规则；系统强制规则禁止停用。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleResponse changeRuleStatus(Long id, boolean enabled) {
        ExchangeRateAlertRuleDO row = requireRule(id);
        if (!enabled) {
            rejectMandatory(row, "system mandatory rule cannot be disabled");
        }
        row.setRuleStatus(enabled ? ENABLED : DISABLED);
        row.setUpdateBy(currentOperator());
        row.setUpdateTime(LocalDateTime.now());
        ruleMapper.updateById(row);
        log.info("汇率预警规则状态已变更, event: EXCHANGE_RATE_ALERT_RULE_STATUS_CHANGED, ruleId: {}, enabled: {}",
                row.getId(), enabled);
        return toRuleResponse(row);
    }

    /**
     * 原子导入自定义规则。
     *
     * <p>先逐行完成全部业务校验，任一行失败时不写入任何规则并返回完整错误明细。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertRuleImportResult importRules(List<AlertRuleSaveRequest> requests) {
        AlertRuleImportResult result = new AlertRuleImportResult();
        int total = requests == null ? 0 : requests.size();
        result.setTotalCount(total);
        result.setSuccessCount(0);
        result.setFailureCount(0);
        if (total == 0) {
            AlertRuleImportError error = new AlertRuleImportError();
            error.setRowNumber(2);
            error.setMessage("import file contains no data rows");
            result.setFailureCount(1);
            result.setErrors(List.of(error));
            return result;
        }
        if (total > 1000) {
            AlertRuleImportError error = new AlertRuleImportError();
            error.setRowNumber(2);
            error.setMessage("import row count must not exceed 1000");
            result.setFailureCount(total);
            result.setErrors(List.of(error));
            return result;
        }

        List<RuleValues> values = new ArrayList<>(total);
        List<AlertRuleImportError> errors = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            try {
                values.add(validateRule(requests.get(index), false));
            } catch (ServiceException exception) {
                values.add(null);
                AlertRuleImportError error = new AlertRuleImportError();
                error.setRowNumber(index + 2);
                error.setMessage(exception.getMessage());
                errors.add(error);
            }
        }
        if (!errors.isEmpty()) {
            result.setFailureCount(errors.size());
            result.setErrors(errors);
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();
        for (int index = 0; index < total; index++) {
            ExchangeRateAlertRuleDO row = new ExchangeRateAlertRuleDO();
            row.setRuleCode(PaymentOrderNoGenerator.nextOrderNo("ERR", now.plusNanos(index)));
            row.setSystemMandatory(DISABLED);
            row.setRuleStatus(ENABLED);
            row.setCreateBy(operator);
            row.setCreateTime(now);
            row.setDeleted(NOT_DELETED);
            fillRule(row, requests.get(index), values.get(index), now);
            ruleMapper.insert(row);
        }
        result.setSuccessCount(total);
        log.info("汇率预警规则批量导入完成, event: EXCHANGE_RATE_ALERT_RULES_IMPORTED, totalCount: {}, operator: {}",
                total, operator);
        return result;
    }

    /** 分页查询预警事件。 */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<AlertEventResponse> pageEvents(AlertEventQuery request) {
        AlertEventQuery query = request == null ? new AlertEventQuery() : request;
        validateEventQuery(query);
        Page<ExchangeRateAlertEventDO> page = eventMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), buildEventQuery(query));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toEventResponse).toList());
    }

    /** 查询事件列表，用于受控导出。 */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<AlertEventResponse> listEvents(AlertEventQuery request) {
        AlertEventQuery query = request == null ? new AlertEventQuery() : request;
        validateEventQuery(query);
        return eventMapper.selectList(buildEventQuery(query)).stream().map(this::toEventResponse).toList();
    }

    /** 查询事件详情。 */
    @Override
    @DS(DataSourceName.SLAVE)
    public AlertEventResponse getEvent(Long id) {
        return toEventResponse(requireEvent(id));
    }

    /** 使用前端读取版本确认 OPEN 事件，重复提交不会覆盖已确认事实。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertEventResponse acknowledgeEvent(Long id, AlertEventActionRequest request) {
        requireActionRequest(request);
        ExchangeRateAlertEventDO current = requireEvent(id);
        if (ACKNOWLEDGED.equals(current.getEventStatus()) && Objects.equals(current.getVersion(), request.getVersion())) {
            return toEventResponse(current);
        }
        int updated = eventMapper.acknowledge(id, request.getVersion(), currentOperator(), request.getRemark().trim(), LocalDateTime.now());
        if (updated != 1) {
            throw conflict("alert event status or version changed");
        }
        log.info("汇率预警事件已确认, event: EXCHANGE_RATE_ALERT_EVENT_ACKNOWLEDGED, alertEventId: {}, eventNo: {}",
                id, current.getEventNo());
        return toEventResponse(requireEvent(id));
    }

    /** 仅允许关闭已自动恢复的事件，避免人工关闭掩盖持续风险。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AlertEventResponse closeEvent(Long id, AlertEventActionRequest request) {
        requireActionRequest(request);
        ExchangeRateAlertEventDO current = requireEvent(id);
        if (CLOSED.equals(current.getEventStatus()) && Objects.equals(current.getVersion(), request.getVersion())) {
            return toEventResponse(current);
        }
        int updated = eventMapper.close(id, request.getVersion(), currentOperator(), request.getRemark().trim(), LocalDateTime.now());
        if (updated != 1) {
            throw conflict("only a current RESOLVED alert event can be closed");
        }
        log.info("汇率预警事件已关闭, event: EXCHANGE_RATE_ALERT_EVENT_CLOSED, alertEventId: {}, eventNo: {}",
                id, current.getEventNo());
        return toEventResponse(requireEvent(id));
    }

    /**
     * 执行一轮跨来源汇率波动评估。
     *
     * <p>requestKey 先通过唯一索引取得执行权；活动事件再通过 active_event_key 和版本 CAS 防止并发重复。</p>
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AlertEvaluationResponse evaluate(AlertEvaluationRequest request) {
        validateEvaluationRequest(request);
        LocalDateTime start = LocalDateTime.now();
        ExchangeRateAlertEvaluationDO evaluation = new ExchangeRateAlertEvaluationDO();
        evaluation.setRequestKey(request.getRequestKey().trim());
        evaluation.setWindowEndTime(request.getEvaluationTime());
        evaluation.setEvaluationStatus(PROCESSING);
        evaluation.setStartTime(start);
        evaluation.setCreateTime(start);
        if (evaluationMapper.insertIgnore(evaluation) == 0) {
            ExchangeRateAlertEvaluationDO existing = evaluationMapper.selectOne(
                    Wrappers.<ExchangeRateAlertEvaluationDO>lambdaQuery()
                            .eq(ExchangeRateAlertEvaluationDO::getRequestKey, evaluation.getRequestKey()));
            if (existing == null || !SUCCESS.equals(existing.getEvaluationStatus())) {
                throw conflict("alert evaluation request is already processing");
            }
            return toEvaluationResponse(existing);
        }

        EvaluationCounters counters = new EvaluationCounters();
        List<ExchangeRateAlertRuleDO> rules = ruleMapper.selectList(
                Wrappers.<ExchangeRateAlertRuleDO>lambdaQuery()
                        .eq(ExchangeRateAlertRuleDO::getDeleted, NOT_DELETED)
                        .eq(ExchangeRateAlertRuleDO::getRuleStatus, ENABLED)
                        .orderByAsc(ExchangeRateAlertRuleDO::getId));
        counters.evaluatedRuleCount = rules.size();
        for (ExchangeRateAlertRuleDO rule : rules) {
            evaluateRule(rule, request.getEvaluationTime(), counters);
        }

        evaluation.setEvaluationStatus(SUCCESS);
        evaluation.setEvaluatedRuleCount(counters.evaluatedRuleCount);
        evaluation.setEvaluatedPairCount(counters.evaluatedPairCount);
        evaluation.setTriggeredEventCount(counters.triggeredEventCount);
        evaluation.setUpdatedEventCount(counters.updatedEventCount);
        evaluation.setResolvedEventCount(counters.resolvedEventCount);
        evaluation.setInsufficientDataCount(counters.insufficientDataCount);
        evaluation.setEndTime(LocalDateTime.now());
        if (evaluationMapper.completeProcessing(evaluation) != 1) {
            throw conflict("alert evaluation processing state changed");
        }
        log.info("汇率波动评估完成, event: EXCHANGE_RATE_VOLATILITY_EVALUATED, requestKey: {}, ruleCount: {}, pairCount: {}, triggeredCount: {}, updatedCount: {}, resolvedCount: {}, insufficientDataCount: {}, durationMs: {}",
                evaluation.getRequestKey(), counters.evaluatedRuleCount, counters.evaluatedPairCount,
                counters.triggeredEventCount, counters.updatedEventCount, counters.resolvedEventCount,
                counters.insufficientDataCount, Duration.between(start, evaluation.getEndTime()).toMillis());
        return toEvaluationResponse(evaluation);
    }

    /** 按一条规则的窗口和币种范围评估全部匹配币种对。 */
    private void evaluateRule(ExchangeRateAlertRuleDO rule, LocalDateTime evaluationTime, EvaluationCounters counters) {
        List<CurrencyPair> pairs = exactPair(rule);
        List<ExchangeBusinessRateDO> facts = volatilityMapper.selectTrendFacts(
                rule.getRateType(), pairs, evaluationTime.minusMinutes(rule.getWindowMinutes()), evaluationTime);
        Map<String, FactGroup> groups = groupFacts(facts);
        if (groups.isEmpty() && pairs != null) {
            counters.insufficientDataCount++;
            return;
        }
        for (FactGroup group : groups.values()) {
            counters.evaluatedPairCount++;
            long distinctTimes = group.facts().stream().map(ExchangeBusinessRateDO::getEffectiveTime).distinct().count();
            if (group.facts().size() < rule.getMinimumSampleCount() || distinctTimes < 2) {
                counters.insufficientDataCount++;
                continue;
            }
            RateWindow window = calculateWindow(group);
            applyWindow(rule, window, evaluationTime, counters);
        }
    }

    /** 根据阈值和当前活动事件执行触发、刷新或迟滞恢复。 */
    private void applyWindow(ExchangeRateAlertRuleDO rule, RateWindow window,
                             LocalDateTime evaluationTime, EvaluationCounters counters) {
        String activeKey = activeKey(rule.getId(), window.group());
        ExchangeRateAlertEventDO active = findActiveEvent(activeKey);
        if (window.volatilityRate().compareTo(rule.getTriggerThreshold()) > 0) {
            if (active == null) {
                ExchangeRateAlertEventDO candidate = newEvent(rule, window, evaluationTime, activeKey);
                if (eventMapper.insertActiveIgnore(candidate) == 1) {
                    counters.triggeredEventCount++;
                    log.warn("汇率波动超过阈值, event: EXCHANGE_RATE_VOLATILITY_ALERT_TRIGGERED, eventNo: {}, rateType: {}, baseCurrency: {}, quoteCurrency: {}, volatilityRate: {}, threshold: {}",
                            candidate.getEventNo(), candidate.getRateType(), candidate.getBaseCurrency(),
                            candidate.getQuoteCurrency(), plain(candidate.getVolatilityRate()), plain(candidate.getTriggerThreshold()));
                    return;
                }
                active = findActiveEvent(activeKey);
                if (active == null) {
                    // INSERT IGNORE 可能因活动键之外的约束拒绝写入；此时必须回滚，不能静默漏报。
                    throw conflict("alert event insert was ignored without an active event");
                }
            }
            if (active != null && updateTriggered(active, window, evaluationTime)) {
                counters.updatedEventCount++;
            }
            return;
        }
        if (active == null) {
            return;
        }
        if (window.volatilityRate().compareTo(rule.getRecoveryThreshold()) < 0) {
            int streak = (active.getRecoveryStreak() == null ? 0 : active.getRecoveryStreak()) + 1;
            boolean resolved = streak >= rule.getRecoveryConsecutiveCount();
            if (updateRecovery(active, window, evaluationTime, streak, resolved)) {
                if (resolved) {
                    counters.resolvedEventCount++;
                    log.info("汇率波动预警已自动恢复, event: EXCHANGE_RATE_VOLATILITY_ALERT_RESOLVED, eventNo: {}, rateType: {}, baseCurrency: {}, quoteCurrency: {}, volatilityRate: {}",
                            active.getEventNo(), active.getRateType(), active.getBaseCurrency(),
                            active.getQuoteCurrency(), plain(window.volatilityRate()));
                } else {
                    counters.updatedEventCount++;
                }
            }
        } else if (active.getRecoveryStreak() != null
                && active.getRecoveryStreak() > 0
                && updateRecovery(active, window, evaluationTime, 0, false)) {
            counters.updatedEventCount++;
        }
    }

    /** 通过版本 CAS 刷新持续超阈事件。 */
    private boolean updateTriggered(ExchangeRateAlertEventDO active, RateWindow window, LocalDateTime now) {
        fillWindow(active, window);
        active.setLastDetectedTime(now);
        active.setUpdateTime(now);
        return eventMapper.updateTriggered(active) == 1;
    }

    /** 通过版本 CAS 推进恢复计数或进入恢复终态。 */
    private boolean updateRecovery(ExchangeRateAlertEventDO active, RateWindow window,
                                   LocalDateTime now, int streak, boolean resolved) {
        fillWindow(active, window);
        active.setRecoveryStreak(streak);
        active.setUpdateTime(now);
        if (resolved) {
            active.setResolvedTime(now);
            return eventMapper.resolve(active) == 1;
        }
        return eventMapper.updateRecoveryStreak(active) == 1;
    }

    /** 构建首次触发事件，冻结规则和窗口证据。 */
    private ExchangeRateAlertEventDO newEvent(ExchangeRateAlertRuleDO rule, RateWindow window,
                                               LocalDateTime now, String activeKey) {
        ExchangeRateAlertEventDO row = new ExchangeRateAlertEventDO();
        row.setEventNo(PaymentOrderNoGenerator.nextOrderNo("ERA", now));
        row.setActiveEventKey(activeKey);
        row.setRuleId(rule.getId());
        row.setRuleCode(rule.getRuleCode());
        row.setRuleName(rule.getRuleName());
        row.setRateType(rule.getRateType());
        row.setBaseCurrency(window.group().baseCurrency());
        row.setQuoteCurrency(window.group().quoteCurrency());
        row.setAlertLevel(rule.getAlertLevel());
        row.setWindowMinutes(rule.getWindowMinutes());
        row.setTriggerThreshold(rule.getTriggerThreshold());
        row.setRecoveryThreshold(rule.getRecoveryThreshold());
        fillWindow(row, window);
        row.setMaximumVolatilityRate(window.volatilityRate());
        row.setRecoveryStreak(0);
        row.setEventStatus(OPEN);
        row.setFirstTriggerTime(now);
        row.setLastDetectedTime(now);
        row.setVersion(0);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        row.setDeleted(NOT_DELETED);
        return row;
    }

    /** 将本轮高低值、最新值及来源证据写入事件。 */
    private void fillWindow(ExchangeRateAlertEventDO row, RateWindow window) {
        row.setWindowStartTime(window.windowStartTime());
        row.setWindowEndTime(window.windowEndTime());
        row.setSampleCount(window.group().facts().size());
        row.setMinimumRate(window.minimum().getFinalRate());
        row.setMinimumRateSource(window.minimum().getSourceCode());
        row.setMinimumBusinessRateId(window.minimum().getId());
        row.setMaximumRate(window.maximum().getFinalRate());
        row.setMaximumRateSource(window.maximum().getSourceCode());
        row.setMaximumBusinessRateId(window.maximum().getId());
        row.setLatestRate(window.latest().getFinalRate());
        row.setLatestRateSource(window.latest().getSourceCode());
        row.setLatestBusinessRateId(window.latest().getId());
        row.setVolatilityRate(window.volatilityRate());
    }

    /** 精确规则返回一个币种对，ALL 范围返回空列表表示读取全部币种对。 */
    private List<CurrencyPair> exactPair(ExchangeRateAlertRuleDO rule) {
        if (ALL.equals(rule.getBaseCurrency()) || ALL.equals(rule.getQuoteCurrency())) {
            return null;
        }
        CurrencyPair pair = new CurrencyPair();
        pair.setBaseCurrency(rule.getBaseCurrency());
        pair.setQuoteCurrency(rule.getQuoteCurrency());
        return List.of(pair);
    }

    /** 计算区间振幅：(最高值-最低值)/最低值*100%。 */
    private RateWindow calculateWindow(FactGroup group) {
        Comparator<ExchangeBusinessRateDO> rateComparator = Comparator
                .comparing(ExchangeBusinessRateDO::getFinalRate)
                .thenComparing(ExchangeBusinessRateDO::getEffectiveTime)
                .thenComparing(ExchangeBusinessRateDO::getId);
        ExchangeBusinessRateDO minimum = group.facts().stream().min(rateComparator).orElseThrow();
        ExchangeBusinessRateDO maximum = group.facts().stream().max(rateComparator).orElseThrow();
        ExchangeBusinessRateDO latest = group.facts().get(group.facts().size() - 1);
        BigDecimal volatility = maximum.getFinalRate().subtract(minimum.getFinalRate())
                .divide(minimum.getFinalRate(), 12, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        return new RateWindow(group, minimum, maximum, latest, volatility,
                group.facts().get(0).getEffectiveTime(), latest.getEffectiveTime());
    }

    /** 将事实按不含来源的业务维度稳定分组。 */
    private Map<String, FactGroup> groupFacts(List<ExchangeBusinessRateDO> facts) {
        Map<String, List<ExchangeBusinessRateDO>> grouped = facts.stream()
                .collect(Collectors.groupingBy(this::pairKey, LinkedHashMap::new, Collectors.toList()));
        Map<String, FactGroup> result = new LinkedHashMap<>();
        grouped.forEach((key, values) -> {
            ExchangeBusinessRateDO first = values.get(0);
            result.put(key, new FactGroup(key, first.getRateType(), first.getBaseCurrency(), first.getQuoteCurrency(), values));
        });
        return result;
    }

    /** 长序列降采样时每个桶同时保留高点和低点，避免平均值掩盖异常。 */
    private List<ExchangeBusinessRateDO> downsample(List<ExchangeBusinessRateDO> facts) {
        if (facts.size() <= MAX_TREND_POINTS) {
            return facts;
        }
        int bucketCount = MAX_TREND_POINTS / 2;
        Map<Long, ExchangeBusinessRateDO> selected = new LinkedHashMap<>();
        for (int bucket = 0; bucket < bucketCount; bucket++) {
            // 使用整数边界均匀切桶，避免汇率处理代码引入不必要的二进制浮点计算。
            int from = (int) ((long) bucket * facts.size() / bucketCount);
            int to = Math.min(facts.size(),
                    (int) ((long) (bucket + 1) * facts.size() / bucketCount));
            if (from >= to) {
                continue;
            }
            List<ExchangeBusinessRateDO> slice = facts.subList(from, to);
            ExchangeBusinessRateDO minimum = slice.stream().min(Comparator.comparing(ExchangeBusinessRateDO::getFinalRate)).orElseThrow();
            ExchangeBusinessRateDO maximum = slice.stream().max(Comparator.comparing(ExchangeBusinessRateDO::getFinalRate)).orElseThrow();
            selected.put(minimum.getId(), minimum);
            selected.put(maximum.getId(), maximum);
        }
        return selected.values().stream()
                .sorted(Comparator.comparing(ExchangeBusinessRateDO::getEffectiveTime)
                        .thenComparing(ExchangeBusinessRateDO::getCreateTime)
                        .thenComparing(ExchangeBusinessRateDO::getId))
                .limit(MAX_TREND_POINTS)
                .toList();
    }

    /** 校验并规范趋势查询，不允许跨越九十天。 */
    private NormalizedVolatilityQuery normalizeVolatilityQuery(VolatilityQuery request) {
        if (request == null || request.getStartTime() == null || request.getEndTime() == null) {
            throw invalid("startTime and endTime are required");
        }
        String rateType = normalizeRateType(request.getRateType());
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw invalid("startTime must be before endTime");
        }
        if (Duration.between(request.getStartTime(), request.getEndTime()).toDays() > MAX_QUERY_DAYS) {
            throw invalid("query range must not exceed 90 days");
        }
        List<CurrencyPair> pairs = request.getCurrencyPairs() == null ? List.of() : request.getCurrencyPairs();
        if (pairs.size() > MAX_CURRENCY_PAIRS) {
            throw invalid("currencyPairs size must not exceed 100");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        List<CurrencyPair> normalizedPairs = new ArrayList<>();
        for (CurrencyPair source : pairs) {
            if (source == null) {
                throw invalid("currencyPair must not be null");
            }
            String base = normalizeCurrency(source.getBaseCurrency());
            String quote = normalizeCurrency(source.getQuoteCurrency());
            if (base.equals(quote)) {
                throw invalid("baseCurrency and quoteCurrency must differ");
            }
            if (unique.add(base + "/" + quote)) {
                CurrencyPair pair = new CurrencyPair();
                pair.setBaseCurrency(base);
                pair.setQuoteCurrency(quote);
                normalizedPairs.add(pair);
            }
        }
        return new NormalizedVolatilityQuery(rateType, normalizedPairs, request.getStartTime(), request.getEndTime());
    }

    /** 校验规则数值、币种范围和系统基线不可放宽约束。 */
    private RuleValues validateRule(AlertRuleSaveRequest request, boolean mandatory) {
        if (request == null) {
            throw invalid("rule request is required");
        }
        normalizeRateType(request.getRateType());
        String base = normalizeScopeCurrency(request.getBaseCurrency());
        String quote = normalizeScopeCurrency(request.getQuoteCurrency());
        if (!ALL.equals(base) && base.equals(quote)) {
            throw invalid("baseCurrency and quoteCurrency must differ");
        }
        if ((ALL.equals(base) && !ALL.equals(quote)) || (!ALL.equals(base) && ALL.equals(quote))) {
            throw invalid("baseCurrency and quoteCurrency must both be ALL or both be exact currencies");
        }
        if (!ALERT_LEVELS.contains(upper(request.getAlertLevel()))) {
            throw invalid("alertLevel must be WARNING, ERROR or CRITICAL");
        }
        if (request.getWindowMinutes() == null || request.getWindowMinutes() < 5 || request.getWindowMinutes() > MAX_QUERY_DAYS * 1440) {
            throw invalid("windowMinutes must be between 5 and 129600");
        }
        if (request.getRecoveryConsecutiveCount() == null || request.getRecoveryConsecutiveCount() < 1
                || request.getRecoveryConsecutiveCount() > 20) {
            throw invalid("recoveryConsecutiveCount must be between 1 and 20");
        }
        if (request.getMinimumSampleCount() == null || request.getMinimumSampleCount() < 2
                || request.getMinimumSampleCount() > 10000) {
            throw invalid("minimumSampleCount must be between 2 and 10000");
        }
        BigDecimal trigger = positivePercentage(request.getTriggerThreshold(), "triggerThreshold");
        BigDecimal recovery = nonNegativePercentage(request.getRecoveryThreshold(), "recoveryThreshold");
        if (recovery.compareTo(trigger) >= 0) {
            throw invalid("recoveryThreshold must be lower than triggerThreshold");
        }
        if (mandatory && (request.getWindowMinutes() > SYSTEM_WINDOW_LIMIT
                || trigger.compareTo(SYSTEM_TRIGGER_LIMIT) > 0
                || recovery.compareTo(SYSTEM_RECOVERY_LIMIT) > 0
                || request.getRecoveryConsecutiveCount() < 2
                || request.getMinimumSampleCount() > 2)) {
            throw invalid("system mandatory rule baseline cannot be relaxed");
        }
        return new RuleValues(trigger, recovery);
    }

    /** 将保存请求写入规则实体。 */
    private void fillRule(ExchangeRateAlertRuleDO row, AlertRuleSaveRequest request,
                          RuleValues values, LocalDateTime now) {
        row.setRuleName(request.getRuleName().trim());
        row.setRateType(normalizeRateType(request.getRateType()));
        row.setBaseCurrency(normalizeScopeCurrency(request.getBaseCurrency()));
        row.setQuoteCurrency(normalizeScopeCurrency(request.getQuoteCurrency()));
        row.setWindowMinutes(request.getWindowMinutes());
        row.setTriggerThreshold(values.triggerThreshold());
        row.setRecoveryThreshold(values.recoveryThreshold());
        row.setRecoveryConsecutiveCount(request.getRecoveryConsecutiveCount());
        row.setMinimumSampleCount(request.getMinimumSampleCount());
        row.setAlertLevel(upper(request.getAlertLevel()));
        row.setRemark(trimToNull(request.getRemark()));
        row.setUpdateBy(currentOperator());
        row.setUpdateTime(now);
    }

    /** 构建规则筛选条件。 */
    private LambdaQueryWrapper<ExchangeRateAlertRuleDO> buildRuleQuery(AlertRuleQuery query) {
        return Wrappers.<ExchangeRateAlertRuleDO>lambdaQuery()
                .eq(ExchangeRateAlertRuleDO::getDeleted, NOT_DELETED)
                .eq(StringUtils.hasText(query.getRateType()), ExchangeRateAlertRuleDO::getRateType, upper(query.getRateType()))
                .eq(StringUtils.hasText(query.getBaseCurrency()), ExchangeRateAlertRuleDO::getBaseCurrency, upper(query.getBaseCurrency()))
                .eq(StringUtils.hasText(query.getQuoteCurrency()), ExchangeRateAlertRuleDO::getQuoteCurrency, upper(query.getQuoteCurrency()))
                .eq(query.getRuleStatus() != null, ExchangeRateAlertRuleDO::getRuleStatus, query.getRuleStatus())
                .eq(query.getSystemMandatory() != null, ExchangeRateAlertRuleDO::getSystemMandatory, query.getSystemMandatory())
                .and(StringUtils.hasText(query.getKeyword()), wrapper -> wrapper
                        .like(ExchangeRateAlertRuleDO::getRuleCode, query.getKeyword().trim())
                        .or().like(ExchangeRateAlertRuleDO::getRuleName, query.getKeyword().trim()))
                .orderByDesc(ExchangeRateAlertRuleDO::getSystemMandatory)
                .orderByAsc(ExchangeRateAlertRuleDO::getRateType)
                .orderByAsc(ExchangeRateAlertRuleDO::getId);
    }

    /** 构建事件筛选条件。 */
    private LambdaQueryWrapper<ExchangeRateAlertEventDO> buildEventQuery(AlertEventQuery query) {
        return Wrappers.<ExchangeRateAlertEventDO>lambdaQuery()
                .eq(ExchangeRateAlertEventDO::getDeleted, NOT_DELETED)
                .like(StringUtils.hasText(query.getEventNo()), ExchangeRateAlertEventDO::getEventNo, trimToNull(query.getEventNo()))
                .eq(StringUtils.hasText(query.getRateType()), ExchangeRateAlertEventDO::getRateType, upper(query.getRateType()))
                .eq(StringUtils.hasText(query.getBaseCurrency()), ExchangeRateAlertEventDO::getBaseCurrency, upper(query.getBaseCurrency()))
                .eq(StringUtils.hasText(query.getQuoteCurrency()), ExchangeRateAlertEventDO::getQuoteCurrency, upper(query.getQuoteCurrency()))
                .eq(StringUtils.hasText(query.getEventStatus()), ExchangeRateAlertEventDO::getEventStatus, upper(query.getEventStatus()))
                .like(StringUtils.hasText(query.getAcknowledgedBy()), ExchangeRateAlertEventDO::getAcknowledgedBy, trimToNull(query.getAcknowledgedBy()))
                .ge(query.getTriggerStartTime() != null, ExchangeRateAlertEventDO::getFirstTriggerTime, query.getTriggerStartTime())
                .le(query.getTriggerEndTime() != null, ExchangeRateAlertEventDO::getFirstTriggerTime, query.getTriggerEndTime())
                .orderByDesc(ExchangeRateAlertEventDO::getFirstTriggerTime)
                .orderByDesc(ExchangeRateAlertEventDO::getId);
    }

    /** 校验事件筛选枚举和时间范围。 */
    private void validateEventQuery(AlertEventQuery query) {
        if (StringUtils.hasText(query.getRateType())) {
            normalizeRateType(query.getRateType());
        }
        if (StringUtils.hasText(query.getEventStatus()) && !EVENT_STATUSES.contains(upper(query.getEventStatus()))) {
            throw invalid("eventStatus is invalid");
        }
        if (query.getTriggerStartTime() != null && query.getTriggerEndTime() != null
                && query.getTriggerStartTime().isAfter(query.getTriggerEndTime())) {
            throw invalid("triggerStartTime must not be after triggerEndTime");
        }
    }

    /** 读取同类型全部活动事件，供趋势摘要标记预警状态。 */
    private List<ExchangeRateAlertEventDO> loadActiveEvents(String rateType) {
        return eventMapper.selectList(Wrappers.<ExchangeRateAlertEventDO>lambdaQuery()
                .eq(ExchangeRateAlertEventDO::getDeleted, NOT_DELETED)
                .eq(ExchangeRateAlertEventDO::getRateType, rateType)
                .in(ExchangeRateAlertEventDO::getEventStatus, OPEN, ACKNOWLEDGED)
                .isNotNull(ExchangeRateAlertEventDO::getActiveEventKey));
    }

    /** 按活动键读取当前事件。 */
    private ExchangeRateAlertEventDO findActiveEvent(String activeKey) {
        return eventMapper.selectOne(Wrappers.<ExchangeRateAlertEventDO>lambdaQuery()
                .eq(ExchangeRateAlertEventDO::getActiveEventKey, activeKey)
                .eq(ExchangeRateAlertEventDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
    }

    /** 将事实组转换为页面摘要。 */
    private VolatilityOverview toOverview(FactGroup group, ExchangeRateAlertEventDO active) {
        RateWindow window = calculateWindow(group);
        VolatilityOverview response = new VolatilityOverview();
        response.setRateType(group.rateType());
        response.setBaseCurrency(group.baseCurrency());
        response.setQuoteCurrency(group.quoteCurrency());
        response.setLatestRate(plain(window.latest().getFinalRate()));
        response.setMinimumRate(plain(window.minimum().getFinalRate()));
        response.setMaximumRate(plain(window.maximum().getFinalRate()));
        response.setVolatilityRate(plain(window.volatilityRate()));
        response.setSampleCount(group.facts().size());
        response.setLatestEffectiveTime(window.latest().getEffectiveTime());
        response.setAlertStatus(active == null ? "NORMAL" : active.getEventStatus());
        return response;
    }

    /** 转换趋势点并保持十进制字符串。 */
    private TrendPoint toTrendPoint(ExchangeBusinessRateDO row) {
        TrendPoint response = new TrendPoint();
        response.setBusinessRateId(row.getId());
        response.setRate(plain(row.getFinalRate()));
        response.setSourceCode(row.getSourceCode());
        response.setRawRateId(row.getRawRateId());
        response.setRuleId(row.getRuleId());
        response.setEffectiveTime(row.getEffectiveTime());
        response.setExpireTime(row.getExpireTime());
        return response;
    }

    /** 转换规则响应。 */
    private AlertRuleResponse toRuleResponse(ExchangeRateAlertRuleDO row) {
        AlertRuleResponse response = new AlertRuleResponse();
        response.setId(row.getId());
        response.setRuleCode(row.getRuleCode());
        response.setRuleName(row.getRuleName());
        response.setRateType(row.getRateType());
        response.setBaseCurrency(row.getBaseCurrency());
        response.setQuoteCurrency(row.getQuoteCurrency());
        response.setWindowMinutes(row.getWindowMinutes());
        response.setTriggerThreshold(plain(row.getTriggerThreshold()));
        response.setRecoveryThreshold(plain(row.getRecoveryThreshold()));
        response.setRecoveryConsecutiveCount(row.getRecoveryConsecutiveCount());
        response.setMinimumSampleCount(row.getMinimumSampleCount());
        response.setAlertLevel(row.getAlertLevel());
        response.setSystemMandatory(row.getSystemMandatory());
        response.setRuleStatus(row.getRuleStatus());
        response.setRemark(row.getRemark());
        response.setCreateBy(row.getCreateBy());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateBy(row.getUpdateBy());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    /** 转换事件响应。 */
    private AlertEventResponse toEventResponse(ExchangeRateAlertEventDO row) {
        AlertEventResponse response = new AlertEventResponse();
        response.setId(row.getId());
        response.setEventNo(row.getEventNo());
        response.setRuleId(row.getRuleId());
        response.setRuleCode(row.getRuleCode());
        response.setRuleName(row.getRuleName());
        response.setRateType(row.getRateType());
        response.setBaseCurrency(row.getBaseCurrency());
        response.setQuoteCurrency(row.getQuoteCurrency());
        response.setAlertLevel(row.getAlertLevel());
        response.setWindowMinutes(row.getWindowMinutes());
        response.setTriggerThreshold(plain(row.getTriggerThreshold()));
        response.setRecoveryThreshold(plain(row.getRecoveryThreshold()));
        response.setWindowStartTime(row.getWindowStartTime());
        response.setWindowEndTime(row.getWindowEndTime());
        response.setSampleCount(row.getSampleCount());
        response.setMinimumRate(plain(row.getMinimumRate()));
        response.setMinimumRateSource(row.getMinimumRateSource());
        response.setMinimumBusinessRateId(row.getMinimumBusinessRateId());
        response.setMaximumRate(plain(row.getMaximumRate()));
        response.setMaximumRateSource(row.getMaximumRateSource());
        response.setMaximumBusinessRateId(row.getMaximumBusinessRateId());
        response.setLatestRate(plain(row.getLatestRate()));
        response.setLatestRateSource(row.getLatestRateSource());
        response.setLatestBusinessRateId(row.getLatestBusinessRateId());
        response.setVolatilityRate(plain(row.getVolatilityRate()));
        response.setMaximumVolatilityRate(plain(row.getMaximumVolatilityRate()));
        response.setRecoveryStreak(row.getRecoveryStreak());
        response.setEventStatus(row.getEventStatus());
        response.setFirstTriggerTime(row.getFirstTriggerTime());
        response.setLastDetectedTime(row.getLastDetectedTime());
        response.setResolvedTime(row.getResolvedTime());
        response.setAcknowledgedBy(row.getAcknowledgedBy());
        response.setAcknowledgedTime(row.getAcknowledgedTime());
        response.setAcknowledgeRemark(row.getAcknowledgeRemark());
        response.setClosedBy(row.getClosedBy());
        response.setClosedTime(row.getClosedTime());
        response.setCloseRemark(row.getCloseRemark());
        response.setVersion(row.getVersion());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    /** 转换幂等评估结果。 */
    private AlertEvaluationResponse toEvaluationResponse(ExchangeRateAlertEvaluationDO row) {
        AlertEvaluationResponse response = new AlertEvaluationResponse();
        response.setRequestKey(row.getRequestKey());
        response.setEvaluationStatus(row.getEvaluationStatus());
        response.setEvaluatedRuleCount(row.getEvaluatedRuleCount());
        response.setEvaluatedPairCount(row.getEvaluatedPairCount());
        response.setTriggeredEventCount(row.getTriggeredEventCount());
        response.setUpdatedEventCount(row.getUpdatedEventCount());
        response.setResolvedEventCount(row.getResolvedEventCount());
        response.setInsufficientDataCount(row.getInsufficientDataCount());
        response.setStartTime(row.getStartTime());
        response.setEndTime(row.getEndTime());
        return response;
    }

    /** 读取未删除规则。 */
    private ExchangeRateAlertRuleDO requireRule(Long id) {
        if (id == null) {
            throw invalid("rule id is required");
        }
        ExchangeRateAlertRuleDO row = ruleMapper.selectById(id);
        if (row == null || !Long.valueOf(NOT_DELETED).equals(row.getDeleted())) {
            throw notFound("exchange rate alert rule not found");
        }
        return row;
    }

    /** 读取未删除事件。 */
    private ExchangeRateAlertEventDO requireEvent(Long id) {
        if (id == null) {
            throw invalid("event id is required");
        }
        ExchangeRateAlertEventDO row = eventMapper.selectById(id);
        if (row == null || !Long.valueOf(NOT_DELETED).equals(row.getDeleted())) {
            throw notFound("exchange rate alert event not found");
        }
        return row;
    }

    /** 系统规则不可删除或停用。 */
    private void rejectMandatory(ExchangeRateAlertRuleDO row, String message) {
        if (Integer.valueOf(ENABLED).equals(row.getSystemMandatory())) {
            throw invalid(message);
        }
    }

    /** 校验内部评估命令。 */
    private void validateEvaluationRequest(AlertEvaluationRequest request) {
        if (request == null || !StringUtils.hasText(request.getRequestKey()) || request.getEvaluationTime() == null) {
            throw invalid("requestKey and evaluationTime are required");
        }
        if (request.getRequestKey().trim().length() > 128) {
            throw invalid("requestKey length must not exceed 128");
        }
        if (request.getEvaluationTime().isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw invalid("evaluationTime must not be in the future");
        }
    }

    /** 校验人工事件操作请求。 */
    private void requireActionRequest(AlertEventActionRequest request) {
        if (request == null || !StringUtils.hasText(request.getRemark()) || request.getVersion() == null) {
            throw invalid("remark and version are required");
        }
    }

    /** 规范汇率类型。 */
    private String normalizeRateType(String value) {
        String normalized = upper(value);
        if (!RATE_TYPES.contains(normalized)) {
            throw invalid("rateType must be TRANSACTION_RATE or SETTLEMENT_RATE");
        }
        return normalized;
    }

    /** 规范精确 ISO 币种。 */
    private String normalizeCurrency(String value) {
        String normalized = upper(value);
        if (normalized == null || !normalized.matches("^[A-Z]{3}$")) {
            throw invalid("currency must be an uppercase ISO 4217 code");
        }
        return normalized;
    }

    /** 规范规则范围币种，允许 ALL。 */
    private String normalizeScopeCurrency(String value) {
        String normalized = upper(value);
        return ALL.equals(normalized) ? ALL : normalizeCurrency(normalized);
    }

    /** 解析正百分比并限制持久化精度。 */
    private BigDecimal positivePercentage(String value, String field) {
        BigDecimal result = decimal(value, field);
        if (result.compareTo(BigDecimal.ZERO) <= 0 || result.compareTo(new BigDecimal("1000")) > 0) {
            throw invalid(field + " must be greater than 0 and not exceed 1000");
        }
        return percentageScale(result, field);
    }

    /** 解析非负百分比并限制持久化精度。 */
    private BigDecimal nonNegativePercentage(String value, String field) {
        BigDecimal result = decimal(value, field);
        if (result.compareTo(BigDecimal.ZERO) < 0 || result.compareTo(new BigDecimal("1000")) > 0) {
            throw invalid(field + " must be between 0 and 1000");
        }
        return percentageScale(result, field);
    }

    /**
     * 百分比持久化列固定四位小数，超出精度时拒绝而不是静默改变业务阈值。
     */
    private BigDecimal percentageScale(BigDecimal value, String field) {
        try {
            return value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid(field + " scale must not exceed 4 decimal places");
        }
    }

    /** 使用 BigDecimal 严格解析请求字符串。 */
    private BigDecimal decimal(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw invalid(field + " is required");
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw invalid(field + " must be a decimal string");
        }
    }

    /** 当前操作人用于审计字段，内部任务无登录账号时使用 system。 */
    private String currentOperator() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "system";
        }
        return StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount() : "system";
    }

    /** 规则活动事件键不包含来源。 */
    private String activeKey(Long ruleId, FactGroup group) {
        return ruleId + ":" + group.rateType() + ":" + group.baseCurrency() + ":" + group.quoteCurrency();
    }

    /** 构建事实或事件的类型与币种对键。 */
    private String pairKey(ExchangeBusinessRateDO row) {
        return row.getRateType() + ":" + row.getBaseCurrency() + ":" + row.getQuoteCurrency();
    }

    private String pairKey(ExchangeRateAlertEventDO row) {
        return row.getRateType() + ":" + row.getBaseCurrency() + ":" + row.getQuoteCurrency();
    }

    /** 十进制值按普通字符串输出，不使用科学计数法。 */
    private String plain(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    private ServiceException conflict(String message) {
        return new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), message);
    }

    private ServiceException notFound(String message) {
        return new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), message);
    }

    private record NormalizedVolatilityQuery(String rateType, List<CurrencyPair> pairs,
                                             LocalDateTime startTime, LocalDateTime endTime) {
    }

    private record RuleValues(BigDecimal triggerThreshold, BigDecimal recoveryThreshold) {
    }

    private record FactGroup(String key, String rateType, String baseCurrency,
                             String quoteCurrency, List<ExchangeBusinessRateDO> facts) {
    }

    private record RateWindow(FactGroup group, ExchangeBusinessRateDO minimum,
                              ExchangeBusinessRateDO maximum, ExchangeBusinessRateDO latest,
                              BigDecimal volatilityRate, LocalDateTime windowStartTime,
                              LocalDateTime windowEndTime) {
    }

    private static final class EvaluationCounters {
        private int evaluatedRuleCount;
        private int evaluatedPairCount;
        private int triggeredEventCount;
        private int updatedEventCount;
        private int resolvedEventCount;
        private int insufficientDataCount;
    }
}
