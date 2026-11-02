package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEvaluationRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEvaluationResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventActionRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleImportResult;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleSaveRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.TrendSeries;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityOverview;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityQuery;
import com.scott.payment.component.core.model.PageResult;

import java.util.List;

/** 汇率波动查询、预警规则、事件状态机和定时评估服务契约。 */
public interface AdminExchangeRateVolatilityService {

    List<VolatilityOverview> overview(VolatilityQuery request);

    List<TrendSeries> trend(VolatilityQuery request);

    PageResult<AlertRuleResponse> pageRules(AlertRuleQuery request);

    List<AlertRuleResponse> listRules(AlertRuleQuery request);

    AlertRuleResponse getRule(Long id);

    AlertRuleResponse createRule(AlertRuleSaveRequest request);

    AlertRuleResponse updateRule(Long id, AlertRuleSaveRequest request);

    void deleteRule(Long id);

    AlertRuleResponse changeRuleStatus(Long id, boolean enabled);

    AlertRuleImportResult importRules(List<AlertRuleSaveRequest> requests);

    PageResult<AlertEventResponse> pageEvents(AlertEventQuery request);

    List<AlertEventResponse> listEvents(AlertEventQuery request);

    AlertEventResponse getEvent(Long id);

    AlertEventResponse acknowledgeEvent(Long id, AlertEventActionRequest request);

    AlertEventResponse closeEvent(Long id, AlertEventActionRequest request);

    AlertEvaluationResponse evaluate(AlertEvaluationRequest request);
}
