package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEvaluationRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEvaluationResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleImportResult;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.CurrencyPair;
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
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 汇率波动计算、幂等与状态机边界测试。
 */
class AdminExchangeRateVolatilityServiceImplTest {

    private ExchangeRateVolatilityMapper volatilityMapper;
    private ExchangeRateAlertRuleMapper ruleMapper;
    private ExchangeRateAlertEventMapper eventMapper;
    private ExchangeRateAlertEvaluationMapper evaluationMapper;
    private AdminExchangeRateVolatilityServiceImpl service;

    @BeforeEach
    void setUp() {
        volatilityMapper = mock(ExchangeRateVolatilityMapper.class);
        ruleMapper = mock(ExchangeRateAlertRuleMapper.class);
        eventMapper = mock(ExchangeRateAlertEventMapper.class);
        evaluationMapper = mock(ExchangeRateAlertEvaluationMapper.class);
        when(evaluationMapper.completeProcessing(any())).thenReturn(1);
        service = new AdminExchangeRateVolatilityServiceImpl(
                volatilityMapper, ruleMapper, eventMapper, evaluationMapper);
    }

    /**
     * 精确 3% 不满足“大于 3%”，不得触发预警。
     */
    @Test
    void exactThreePercentShouldNotTriggerAlert() {
        prepareNewEvaluation(rule("3.0000", "2.0000", 2));
        when(volatilityMapper.selectTrendFacts(any(), any(), any(), any()))
                .thenReturn(List.of(
                        fact(1L, "BOC", "1.000000", time(9, 0)),
                        fact(2L, "MANUAL", "1.030000", time(10, 0))));

        AlertEvaluationResponse response = service.evaluate(evaluationRequest("run-exact-3"));

        assertThat(response.getTriggeredEventCount()).isZero();
        verify(eventMapper, never()).insertActiveIgnore(any());
    }

    /**
     * 略高于 3% 的真实波动不得因写库展示精度而提前四舍五入为 3%。
     */
    @Test
    void valueSlightlyAboveThreePercentShouldTriggerBeforePersistenceRounding() {
        prepareNewEvaluation(rule("3.0000", "2.0000", 2));
        when(volatilityMapper.selectTrendFacts(any(), any(), any(), any()))
                .thenReturn(List.of(
                        fact(1L, "BOC", "1.000000000000", time(9, 0)),
                        fact(2L, "MANUAL", "1.030000004000", time(10, 0))));
        when(eventMapper.insertActiveIgnore(any())).thenReturn(1);

        AlertEvaluationResponse response = service.evaluate(evaluationRequest("run-above-3-boundary"));

        assertThat(response.getTriggeredEventCount()).isEqualTo(1);
        ArgumentCaptor<ExchangeRateAlertEventDO> eventCaptor =
                ArgumentCaptor.forClass(ExchangeRateAlertEventDO.class);
        verify(eventMapper).insertActiveIgnore(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getVolatilityRate()).isGreaterThan(new BigDecimal("3.000000"));
    }

    /**
     * 不同来源的同类型同币种对必须合并计算，高低值来源作为证据保留。
     */
    @Test
    void crossSourceFactsAboveThresholdShouldTriggerSingleAlert() {
        prepareNewEvaluation(rule("3.0000", "2.0000", 2));
        when(volatilityMapper.selectTrendFacts(any(), any(), any(), any()))
                .thenReturn(List.of(
                        fact(1L, "BOC", "1.000000", time(9, 0)),
                        fact(2L, "NBP", "1.010000", time(9, 30)),
                        fact(3L, "MANUAL", "1.031000", time(10, 0))));
        when(eventMapper.insertActiveIgnore(any())).thenReturn(1);

        AlertEvaluationResponse response = service.evaluate(evaluationRequest("run-cross-source"));

        ArgumentCaptor<ExchangeRateAlertEventDO> eventCaptor =
                ArgumentCaptor.forClass(ExchangeRateAlertEventDO.class);
        verify(eventMapper).insertActiveIgnore(eventCaptor.capture());
        ExchangeRateAlertEventDO event = eventCaptor.getValue();
        assertThat(response.getTriggeredEventCount()).isEqualTo(1);
        assertThat(event.getActiveEventKey()).isEqualTo("1:TRANSACTION_RATE:EUR:USD");
        assertThat(event.getMinimumRateSource()).isEqualTo("BOC");
        assertThat(event.getMaximumRateSource()).isEqualTo("MANUAL");
        assertThat(event.getVolatilityRate()).isEqualByComparingTo("3.100000");
    }

    /**
     * 活动事件写入被非活动键约束忽略时必须失败并回滚，不能把漏告警伪装成评估成功。
     */
    @Test
    void ignoredInsertWithoutConcurrentActiveEventShouldFailEvaluation() {
        prepareNewEvaluation(rule("3.0000", "2.0000", 2));
        when(volatilityMapper.selectTrendFacts(any(), any(), any(), any()))
                .thenReturn(List.of(
                        fact(1L, "BOC", "1.000000", time(9, 0)),
                        fact(2L, "MANUAL", "1.031000", time(10, 0))));
        when(eventMapper.insertActiveIgnore(any())).thenReturn(0);

        assertThatThrownBy(() -> service.evaluate(evaluationRequest("run-ignored-insert")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("ignored without an active event");
    }

    /**
     * 活动事件只有连续两轮低于 2% 才能自动恢复。
     */
    @Test
    void alertShouldResolveAfterTwoConsecutiveRecoveryEvaluations() {
        ExchangeRateAlertRuleDO rule = rule("3.0000", "2.0000", 2);
        when(evaluationMapper.insertIgnore(any())).thenReturn(1);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(volatilityMapper.selectTrendFacts(any(), any(), any(), any()))
                .thenReturn(List.of(
                        fact(1L, "BOC", "1.000000", time(9, 0)),
                        fact(2L, "MANUAL", "1.010000", time(10, 0))));
        ExchangeRateAlertEventDO first = activeEvent(0, 4);
        ExchangeRateAlertEventDO second = activeEvent(1, 5);
        when(eventMapper.selectOne(any())).thenReturn(first, second);
        when(eventMapper.updateRecoveryStreak(any())).thenReturn(1);
        when(eventMapper.resolve(any())).thenReturn(1);

        AlertEvaluationResponse firstResponse = service.evaluate(evaluationRequest("run-recovery-1"));
        AlertEvaluationResponse secondResponse = service.evaluate(evaluationRequest("run-recovery-2"));

        assertThat(firstResponse.getResolvedEventCount()).isZero();
        assertThat(firstResponse.getUpdatedEventCount()).isEqualTo(1);
        assertThat(secondResponse.getResolvedEventCount()).isEqualTo(1);
        verify(eventMapper).updateRecoveryStreak(first);
        verify(eventMapper).resolve(second);
        assertThat(first.getMinimumRate()).isEqualByComparingTo("1.000000");
        assertThat(first.getMaximumRate()).isEqualByComparingTo("1.010000");
        assertThat(first.getMinimumRateSource()).isEqualTo("BOC");
        assertThat(first.getMaximumRateSource()).isEqualTo("MANUAL");
        assertThat(second.getRecoveryStreak()).isEqualTo(2);
        assertThat(second.getResolvedTime()).isNotNull();
    }

    /**
     * 重复 requestKey 直接返回既有成功结果，不得再次读取规则或创建事件。
     */
    @Test
    void duplicateSuccessfulRequestShouldReturnStoredResult() {
        ExchangeRateAlertEvaluationDO existing = new ExchangeRateAlertEvaluationDO();
        existing.setRequestKey("run-duplicate");
        existing.setEvaluationStatus("SUCCESS");
        existing.setEvaluatedRuleCount(2);
        existing.setTriggeredEventCount(1);
        when(evaluationMapper.insertIgnore(any())).thenReturn(0);
        when(evaluationMapper.selectOne(any())).thenReturn(existing);

        AlertEvaluationResponse response = service.evaluate(evaluationRequest("run-duplicate"));

        assertThat(response.getEvaluatedRuleCount()).isEqualTo(2);
        assertThat(response.getTriggeredEventCount()).isEqualTo(1);
        verify(ruleMapper, never()).selectList(any());
        verify(eventMapper, never()).insertActiveIgnore(any());
    }

    /**
     * 自定义 INSERT 未回填主键时，完成态仍必须按 requestKey 原子持久化全部统计。
     */
    @Test
    void successfulEvaluationShouldPersistCompletionByRequestKeyWithoutGeneratedId() {
        prepareNewEvaluation(rule("3.0000", "2.0000", 2));
        when(volatilityMapper.selectTrendFacts(any(), any(), any(), any()))
                .thenReturn(List.of(
                        fact(1L, "BOC", "1.000000", time(9, 0)),
                        fact(2L, "MANUAL", "1.010000", time(10, 0))));

        service.evaluate(evaluationRequest("run-persist-completion"));

        ArgumentCaptor<ExchangeRateAlertEvaluationDO> evaluationCaptor =
                ArgumentCaptor.forClass(ExchangeRateAlertEvaluationDO.class);
        verify(evaluationMapper).completeProcessing(evaluationCaptor.capture());
        ExchangeRateAlertEvaluationDO completed = evaluationCaptor.getValue();
        assertThat(completed.getId()).isNull();
        assertThat(completed.getRequestKey()).isEqualTo("run-persist-completion");
        assertThat(completed.getEvaluationStatus()).isEqualTo("SUCCESS");
        assertThat(completed.getEvaluatedRuleCount()).isEqualTo(1);
        assertThat(completed.getEvaluatedPairCount()).isEqualTo(1);
        assertThat(completed.getEndTime()).isNotNull();
    }

    /**
     * 完成态 CAS 未命中时必须回滚本轮事件变更，不能只返回内存中的伪成功结果。
     */
    @Test
    void completionStateChangeShouldFailEvaluation() {
        prepareNewEvaluation(rule("3.0000", "2.0000", 2));
        when(volatilityMapper.selectTrendFacts(any(), any(), any(), any()))
                .thenReturn(List.of(
                        fact(1L, "BOC", "1.000000", time(9, 0)),
                        fact(2L, "MANUAL", "1.010000", time(10, 0))));
        when(evaluationMapper.completeProcessing(any())).thenReturn(0);

        assertThatThrownBy(() -> service.evaluate(evaluationRequest("run-state-changed")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("processing state changed");
    }

    /**
     * 趋势摘要中的汇率和百分比必须以十进制字符串输出。
     */
    @Test
    void overviewShouldReturnDecimalStrings() {
        when(volatilityMapper.selectTrendFacts(any(), any(), any(), any()))
                .thenReturn(List.of(
                        fact(1L, "BOC", "1.200000", time(9, 0)),
                        fact(2L, "NBP", "1.236000", time(10, 0))));
        when(eventMapper.selectList(any())).thenReturn(List.of());

        List<VolatilityOverview> result = service.overview(volatilityQuery());

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.getMinimumRate()).isEqualTo("1.2");
            assertThat(item.getMaximumRate()).isEqualTo("1.236");
            assertThat(item.getLatestRate()).isEqualTo("1.236");
            assertThat(item.getVolatilityRate()).isEqualTo("3");
        });
    }

    /**
     * 币种对为空时必须透传为空列表，由数据层按汇率类型查询全部币种对。
     */
    @Test
    void emptyCurrencyPairsShouldQueryAllPairs() {
        VolatilityQuery query = volatilityQuery();
        query.setCurrencyPairs(List.of());
        when(volatilityMapper.selectTrendFacts(any(), any(), any(), any())).thenReturn(List.of());
        when(eventMapper.selectList(any())).thenReturn(List.of());

        service.overview(query);

        ArgumentCaptor<List<CurrencyPair>> pairsCaptor = ArgumentCaptor.forClass(List.class);
        verify(volatilityMapper).selectTrendFacts(any(), pairsCaptor.capture(), any(), any());
        assertThat(pairsCaptor.getValue()).isEmpty();
    }

    /**
     * 导入存在任一非法行时必须整批拒绝，不允许写入部分规则。
     */
    @Test
    void invalidImportShouldBeAllOrNothing() {
        AlertRuleSaveRequest valid = ruleRequest("Valid rule", "3", "2");
        AlertRuleSaveRequest invalid = ruleRequest("Invalid rule", "1", "2");

        AlertRuleImportResult result = service.importRules(List.of(valid, invalid));

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getRowNumber()).isEqualTo(3));
        verify(ruleMapper, never()).insert(any(ExchangeRateAlertRuleDO.class));
    }

    /**
     * 系统基线规则禁止删除和停用。
     */
    @Test
    void mandatoryRuleShouldRejectDeleteAndDisable() {
        ExchangeRateAlertRuleDO mandatory = rule("3.0000", "2.0000", 2);
        mandatory.setSystemMandatory(1);
        mandatory.setDeleted(0L);
        when(ruleMapper.selectById(1L)).thenReturn(mandatory);

        assertThatThrownBy(() -> service.deleteRule(1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("cannot be deleted");
        assertThatThrownBy(() -> service.changeRuleStatus(1L, false))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("cannot be disabled");
        verify(ruleMapper, never()).updateById(any(ExchangeRateAlertRuleDO.class));
    }

    private void prepareNewEvaluation(ExchangeRateAlertRuleDO rule) {
        when(evaluationMapper.insertIgnore(any())).thenReturn(1);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(eventMapper.selectOne(any())).thenReturn(null);
    }

    private ExchangeRateAlertRuleDO rule(String trigger, String recovery, int recoveryCount) {
        ExchangeRateAlertRuleDO rule = new ExchangeRateAlertRuleDO();
        rule.setId(1L);
        rule.setRuleCode("RULE-1");
        rule.setRuleName("Transaction rate baseline");
        rule.setRateType("TRANSACTION_RATE");
        rule.setBaseCurrency("EUR");
        rule.setQuoteCurrency("USD");
        rule.setWindowMinutes(4320);
        rule.setTriggerThreshold(new BigDecimal(trigger));
        rule.setRecoveryThreshold(new BigDecimal(recovery));
        rule.setRecoveryConsecutiveCount(recoveryCount);
        rule.setMinimumSampleCount(2);
        rule.setAlertLevel("WARNING");
        rule.setSystemMandatory(0);
        rule.setRuleStatus(1);
        rule.setDeleted(0L);
        return rule;
    }

    private ExchangeRateAlertEventDO activeEvent(int streak, int version) {
        ExchangeRateAlertEventDO event = new ExchangeRateAlertEventDO();
        event.setId(10L);
        event.setEventNo("ERA-1");
        event.setActiveEventKey("1:TRANSACTION_RATE:EUR:USD");
        event.setRateType("TRANSACTION_RATE");
        event.setBaseCurrency("EUR");
        event.setQuoteCurrency("USD");
        event.setRecoveryStreak(streak);
        event.setEventStatus("OPEN");
        event.setVersion(version);
        event.setDeleted(0L);
        return event;
    }

    private ExchangeBusinessRateDO fact(Long id, String source, String rate, LocalDateTime effectiveTime) {
        ExchangeBusinessRateDO fact = new ExchangeBusinessRateDO();
        fact.setId(id);
        fact.setRateType("TRANSACTION_RATE");
        fact.setSourceCode(source);
        fact.setBaseCurrency("EUR");
        fact.setQuoteCurrency("USD");
        fact.setRawRateId(id + 100);
        fact.setRuleId(id + 200);
        fact.setFinalRate(new BigDecimal(rate));
        fact.setEffectiveTime(effectiveTime);
        fact.setCreateTime(effectiveTime);
        return fact;
    }

    private AlertEvaluationRequest evaluationRequest(String requestKey) {
        AlertEvaluationRequest request = new AlertEvaluationRequest();
        request.setRequestKey(requestKey);
        request.setEvaluationTime(time(10, 5));
        return request;
    }

    private VolatilityQuery volatilityQuery() {
        CurrencyPair pair = new CurrencyPair();
        pair.setBaseCurrency("EUR");
        pair.setQuoteCurrency("USD");
        VolatilityQuery query = new VolatilityQuery();
        query.setRateType("TRANSACTION_RATE");
        query.setCurrencyPairs(List.of(pair));
        query.setStartTime(time(8, 0));
        query.setEndTime(time(11, 0));
        return query;
    }

    private AlertRuleSaveRequest ruleRequest(String name, String trigger, String recovery) {
        AlertRuleSaveRequest request = new AlertRuleSaveRequest();
        request.setRuleName(name);
        request.setRateType("TRANSACTION_RATE");
        request.setBaseCurrency("EUR");
        request.setQuoteCurrency("USD");
        request.setWindowMinutes(4320);
        request.setTriggerThreshold(trigger);
        request.setRecoveryThreshold(recovery);
        request.setRecoveryConsecutiveCount(2);
        request.setMinimumSampleCount(2);
        request.setAlertLevel("WARNING");
        return request;
    }

    private LocalDateTime time(int hour, int minute) {
        return LocalDateTime.of(2026, 11, 2, hour, minute);
    }
}
