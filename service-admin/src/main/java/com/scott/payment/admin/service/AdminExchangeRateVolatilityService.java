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

    /** 按币种对及时间范围汇总汇率波动，不触发报价或交易换汇。 */
    List<VolatilityOverview> overview(VolatilityQuery request);

    /** 读取指定范围的汇率趋势序列，保留来源及采样口径。 */
    List<TrendSeries> trend(VolatilityQuery request);

    /** 分页读取汇率预警规则，供管理列表展示。 */
    PageResult<AlertRuleResponse> pageRules(AlertRuleQuery request);

    /** 按筛选条件读取预警规则集合，供导出等非分页用例。 */
    List<AlertRuleResponse> listRules(AlertRuleQuery request);

    /** 读取预警规则及阈值配置，不触发评估。 */
    AlertRuleResponse getRule(Long id);

    /** 校验币种对、阈值及窗口后创建预警配置。 */
    AlertRuleResponse createRule(AlertRuleSaveRequest request);

    /** 校验规则变更并保留配置审计，后续评估采用有效配置。 */
    AlertRuleResponse updateRule(Long id, AlertRuleSaveRequest request);

    /** 删除受控预警配置，历史预警事件保留独立追溯。 */
    void deleteRule(Long id);

    /** 显式启用或停用预警规则，不把停用当作历史事件关闭。 */
    AlertRuleResponse changeRuleStatus(Long id, boolean enabled);

    /** 校验批量规则并返回逐项导入结果，不能静默忽略无效项。 */
    AlertRuleImportResult importRules(List<AlertRuleSaveRequest> requests);

    /** 分页读取预警事件及处理状态，不推进事件状态机。 */
    PageResult<AlertEventResponse> pageEvents(AlertEventQuery request);

    /** 按筛选范围读取预警事件集合，查询不自动确认或关闭。 */
    List<AlertEventResponse> listEvents(AlertEventQuery request);

    /** 读取单个预警事件的触发依据与处理状态。 */
    AlertEventResponse getEvent(Long id);

    /** 在当前状态允许时记录人工确认，保留处理意见和操作者。 */
    AlertEventResponse acknowledgeEvent(Long id, AlertEventActionRequest request);

    /** 在当前状态允许时关闭预警事件，并记录处理原因。 */
    AlertEventResponse closeEvent(Long id, AlertEventActionRequest request);

    /** 使用有效规则评估指定范围的波动并幂等产生或更新预警事件。 */
    AlertEvaluationResponse evaluate(AlertEvaluationRequest request);
}
