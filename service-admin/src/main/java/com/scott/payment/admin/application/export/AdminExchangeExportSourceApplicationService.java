package com.scott.payment.admin.application.export;

import com.scott.payment.admin.application.exchange.AdminExchangeRateVolatilityApplicationService;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleResponse;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityOverview;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.BusinessRatePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.BusinessRatePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.BusinessRateRow;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.RawRatePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.RawRatePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.RawRateRow;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.UsageSnapshotPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.UsageSnapshotPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.UsageSnapshotRow;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.SourcePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.SourcePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.SourceRow;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.RulePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.RulePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.RuleRow;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.VolatilityPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.VolatilityPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.VolatilityRow;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.AlertRulePageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.AlertRulePageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.AlertRuleRow;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.AlertEventPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.AlertEventPageResponse;
import com.scott.payment.admin.dto.exporttask.AdminExchangeExportInternalDTOs.AlertEventRow;
import com.scott.payment.admin.service.AdminExchangeRateService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * 管理端汇率事实异步导出来源服务，复用权威分页查询并输出最小安全投影。
 */
@Service
public class AdminExchangeExportSourceApplicationService {
    private final AdminExchangeRateService exchangeRateService;
    private final AdminExchangeRateVolatilityApplicationService volatilityApplicationService;

    /**
     * 创建管理端汇率事实异步导出来源服务。
     *
     * @param exchangeRateService 汇率事实权威查询服务
     */
    public AdminExchangeExportSourceApplicationService(
            AdminExchangeRateService exchangeRateService,
            AdminExchangeRateVolatilityApplicationService volatilityApplicationService) {
        this.exchangeRateService = exchangeRateService;
        this.volatilityApplicationService = volatilityApplicationService;
    }

    /** 按冻结条件分页读取原始汇率事实。 */
    public RawRatePageResponse pageRawRates(RawRatePageRequest request) {
        PageResult<RawRateResponse> page = exchangeRateService.pageRawRates(toRawRateQuery(request));
        RawRatePageResponse response = new RawRatePageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toRawRateRow).toList());
        return response;
    }

    /** 按冻结条件分页读取业务汇率事实。 */
    public BusinessRatePageResponse pageBusinessRates(BusinessRatePageRequest request) {
        PageResult<BusinessRateResponse> page = exchangeRateService.pageBusinessRates(toBusinessRateQuery(request));
        BusinessRatePageResponse response = new BusinessRatePageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toBusinessRateRow).toList());
        return response;
    }

    /** 按冻结条件分页读取汇率使用快照事实。 */
    public UsageSnapshotPageResponse pageUsageSnapshots(UsageSnapshotPageRequest request) {
        PageResult<UsageSnapshotResponse> page = exchangeRateService.pageUsageSnapshots(toUsageSnapshotQuery(request));
        UsageSnapshotPageResponse response = new UsageSnapshotPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toUsageSnapshotRow).toList());
        return response;
    }

    /** 按冻结条件分页读取汇率源配置。 */
    public SourcePageResponse pageSources(SourcePageRequest request) {
        PageResult<SourceResponse> page = exchangeRateService.pageSources(toSourceQuery(request));
        SourcePageResponse response = new SourcePageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toSourceRow).toList());
        return response;
    }

    /** 按冻结条件分页读取汇率规则配置。 */
    public RulePageResponse pageRules(RulePageRequest request) {
        PageResult<RuleResponse> page = exchangeRateService.pageRules(toRuleQuery(request));
        RulePageResponse response = new RulePageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toRuleRow).toList());
        return response;
    }

    /** 复用权威聚合算法读取汇率波动摘要，再按内部请求进行有界切片。 */
    public VolatilityPageResponse pageVolatility(VolatilityPageRequest request) {
        var overviews = volatilityApplicationService.overview(toVolatilityQuery(request));
        int fromIndex = Math.min((request.getPageNo() - 1) * request.getPageSize(), overviews.size());
        int toIndex = Math.min(fromIndex + request.getPageSize(), overviews.size());
        VolatilityPageResponse response = new VolatilityPageResponse();
        response.setTotal(overviews.size());
        response.setPageNo(request.getPageNo());
        response.setPageSize(request.getPageSize());
        response.setRecords(overviews.subList(fromIndex, toIndex).stream().map(this::toVolatilityRow).toList());
        return response;
    }

    /** 按冻结条件分页读取汇率预警规则。 */
    public AlertRulePageResponse pageAlertRules(AlertRulePageRequest request) {
        PageResult<AlertRuleResponse> page = volatilityApplicationService.pageRules(toAlertRuleQuery(request));
        AlertRulePageResponse response = new AlertRulePageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toAlertRuleRow).toList());
        return response;
    }

    /** 按冻结条件分页读取汇率预警事件事实。 */
    public AlertEventPageResponse pageAlertEvents(AlertEventPageRequest request) {
        PageResult<AlertEventResponse> page = volatilityApplicationService.pageEvents(toAlertEventQuery(request));
        AlertEventPageResponse response = new AlertEventPageResponse();
        response.setTotal(page.getTotal());
        response.setPageNo(page.getPageNo());
        response.setPageSize(page.getPageSize());
        response.setRecords(page.getRecords().stream().map(this::toAlertEventRow).toList());
        return response;
    }

    /** 使用内部服务端分页覆盖页面分页。 */
    private com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceQuery toSourceQuery(
            SourcePageRequest request) {
        var source = request.getQuery();
        var target = new com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.SourceQuery();
        target.setKeyword(source.getKeyword());
        target.setSourceType(source.getSourceType());
        target.setSourceStatus(source.getSourceStatus());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 使用内部服务端分页覆盖页面分页。 */
    private com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleQuery toRuleQuery(
            RulePageRequest request) {
        var source = request.getQuery();
        var target = new com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RuleQuery();
        target.setRateType(source.getRateType());
        target.setSourceCode(source.getSourceCode());
        target.setBaseCurrency(source.getBaseCurrency());
        target.setQuoteCurrency(source.getQuoteCurrency());
        target.setAdjustDirection(source.getAdjustDirection());
        target.setAdjustMethod(source.getAdjustMethod());
        target.setRuleStatus(source.getRuleStatus());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 保持币种对方向与时间范围，构造权威波动查询。 */
    private com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityQuery toVolatilityQuery(
            VolatilityPageRequest request) {
        var source = request.getQuery();
        var target = new com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityQuery();
        target.setRateType(source.getRateType());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setCurrencyPairs(source.getCurrencyPairs().stream().map(pair -> {
            var copy = new com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.CurrencyPair();
            copy.setBaseCurrency(pair.getBaseCurrency());
            copy.setQuoteCurrency(pair.getQuoteCurrency());
            return copy;
        }).toList());
        return target;
    }

    /** 使用内部服务端分页覆盖页面分页。 */
    private com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleQuery toAlertRuleQuery(
            AlertRulePageRequest request) {
        var source = request.getQuery();
        var target = new com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertRuleQuery();
        target.setKeyword(source.getKeyword());
        target.setRateType(source.getRateType());
        target.setBaseCurrency(source.getBaseCurrency());
        target.setQuoteCurrency(source.getQuoteCurrency());
        target.setRuleStatus(source.getRuleStatus());
        target.setSystemMandatory(source.getSystemMandatory());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 使用内部服务端分页覆盖页面分页。 */
    private com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventQuery toAlertEventQuery(
            AlertEventPageRequest request) {
        var source = request.getQuery();
        var target = new com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventQuery();
        target.setEventNo(source.getEventNo());
        target.setRateType(source.getRateType());
        target.setBaseCurrency(source.getBaseCurrency());
        target.setQuoteCurrency(source.getQuoteCurrency());
        target.setEventStatus(source.getEventStatus());
        target.setAcknowledgedBy(source.getAcknowledgedBy());
        target.setTriggerStartTime(source.getTriggerStartTime());
        target.setTriggerEndTime(source.getTriggerEndTime());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 使用内部服务端分页覆盖任务创建时可能携带的页面分页。 */
    private com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery toRawRateQuery(
            RawRatePageRequest request) {
        var source = request.getQuery();
        var target = new com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.RawRateQuery();
        target.setSourceCode(source.getSourceCode());
        target.setBaseCurrency(source.getBaseCurrency());
        target.setQuoteCurrency(source.getQuoteCurrency());
        target.setRateStatus(source.getRateStatus());
        target.setCreateMethod(source.getCreateMethod());
        target.setPublishStartTime(source.getPublishStartTime());
        target.setPublishEndTime(source.getPublishEndTime());
        target.setFetchStartTime(source.getFetchStartTime());
        target.setFetchEndTime(source.getFetchEndTime());
        target.setEffectiveStartTime(source.getEffectiveStartTime());
        target.setEffectiveEndTime(source.getEffectiveEndTime());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 使用内部服务端分页覆盖任务创建时可能携带的页面分页。 */
    private com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateQuery toBusinessRateQuery(
            BusinessRatePageRequest request) {
        var source = request.getQuery();
        var target = new com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.BusinessRateQuery();
        target.setRateType(source.getRateType());
        target.setSourceCode(source.getSourceCode());
        target.setBaseCurrency(source.getBaseCurrency());
        target.setQuoteCurrency(source.getQuoteCurrency());
        target.setRateStatus(source.getRateStatus());
        target.setGenerateMethod(source.getGenerateMethod());
        target.setEffectiveStartTime(source.getEffectiveStartTime());
        target.setEffectiveEndTime(source.getEffectiveEndTime());
        target.setCreateStartTime(source.getCreateStartTime());
        target.setCreateEndTime(source.getCreateEndTime());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 使用内部服务端分页覆盖任务创建时可能携带的页面分页。 */
    private com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotQuery toUsageSnapshotQuery(
            UsageSnapshotPageRequest request) {
        var source = request.getQuery();
        var target = new com.scott.payment.admin.dto.exchange.ExchangeRateDTOs.UsageSnapshotQuery();
        target.setRateType(source.getRateType());
        target.setUsageScene(source.getUsageScene());
        target.setBusinessType(source.getBusinessType());
        target.setBusinessNo(source.getBusinessNo());
        target.setBaseCurrency(source.getBaseCurrency());
        target.setQuoteCurrency(source.getQuoteCurrency());
        target.setAppliedStartTime(source.getAppliedStartTime());
        target.setAppliedEndTime(source.getAppliedEndTime());
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        return target;
    }

    /** 复制原始汇率事实，所有 BigDecimal 字段保持原值。 */
    private RawRateRow toRawRateRow(RawRateResponse source) {
        RawRateRow row = new RawRateRow();
        row.setSourceCode(source.getSourceCode());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setSpotBuyRate(source.getSpotBuyRate());
        row.setSpotSellRate(source.getSpotSellRate());
        row.setCashBuyRate(source.getCashBuyRate());
        row.setCashSellRate(source.getCashSellRate());
        row.setMiddleRate(source.getMiddleRate());
        row.setPublishTime(source.getPublishTime());
        row.setFetchTime(source.getFetchTime());
        row.setEffectiveTime(source.getEffectiveTime());
        row.setCreateMethod(source.getCreateMethod());
        row.setBatchNo(source.getBatchNo());
        row.setRateStatus(source.getRateStatus());
        row.setVoidReason(source.getVoidReason());
        row.setCreateTime(source.getCreateTime());
        return row;
    }

    /** 复制业务汇率事实，不做取倒数、换方向或舍入。 */
    private BusinessRateRow toBusinessRateRow(BusinessRateResponse source) {
        BusinessRateRow row = new BusinessRateRow();
        row.setRateType(source.getRateType());
        row.setSourceCode(source.getSourceCode());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setOriginalRate(source.getOriginalRate());
        row.setFinalRate(source.getFinalRate());
        row.setEffectiveTime(source.getEffectiveTime());
        row.setExpireTime(source.getExpireTime());
        row.setGenerateMethod(source.getGenerateMethod());
        row.setRateStatus(source.getRateStatus());
        row.setRawRateId(source.getRawRateId());
        row.setRuleId(source.getRuleId());
        row.setAdjustDescription(source.getAdjustDescription());
        row.setRemark(source.getRemark());
        row.setCreateTime(source.getCreateTime());
        return row;
    }

    /** 复制汇率使用快照事实，不重新解释报价方向。 */
    private UsageSnapshotRow toUsageSnapshotRow(UsageSnapshotResponse source) {
        UsageSnapshotRow row = new UsageSnapshotRow();
        row.setRateType(source.getRateType());
        row.setUsageEventId(source.getUsageEventId());
        row.setProducerService(source.getProducerService());
        row.setUsageScene(source.getUsageScene());
        row.setBusinessType(source.getBusinessType());
        row.setBusinessNo(source.getBusinessNo());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setUsedRate(source.getUsedRate());
        row.setRawRateId(source.getRawRateId());
        row.setRuleId(source.getRuleId());
        row.setBusinessRateId(source.getBusinessRateId());
        row.setRateSource(source.getRateSource());
        row.setSourceQuoteDirection(source.getSourceQuoteDirection());
        row.setRateEffectiveTime(source.getRateEffectiveTime());
        row.setAppliedTime(source.getAppliedTime());
        row.setCalculationDescription(source.getCalculationDescription());
        row.setCreateTime(source.getCreateTime());
        return row;
    }

    /** 复制汇率源运营可见字段。 */
    private SourceRow toSourceRow(SourceResponse source) {
        SourceRow row = new SourceRow();
        row.setSourceCode(source.getSourceCode());
        row.setSourceName(source.getSourceName());
        row.setSourceType(source.getSourceType());
        row.setRequestUrl(source.getRequestUrl());
        row.setDefaultSource(source.getDefaultSource());
        row.setPriority(source.getPriority());
        row.setTimeoutSeconds(source.getTimeoutSeconds());
        row.setSourceStatus(source.getSourceStatus());
        row.setLastFetchTime(source.getLastFetchTime());
        row.setLastFetchStatus(source.getLastFetchStatus());
        row.setRemark(source.getRemark());
        row.setCreateTime(source.getCreateTime());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /** 复制汇率规则字段，调整值保持 BigDecimal 原值。 */
    private RuleRow toRuleRow(RuleResponse source) {
        RuleRow row = new RuleRow();
        row.setRateType(source.getRateType());
        row.setSourceCode(source.getSourceCode());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setRateField(source.getRateField());
        row.setAdjustDirection(source.getAdjustDirection());
        row.setAdjustMethod(source.getAdjustMethod());
        row.setAdjustValue(source.getAdjustValue());
        row.setDecimalScale(source.getDecimalScale());
        row.setRoundingMode(source.getRoundingMode());
        row.setPriority(source.getPriority());
        row.setEffectiveStartTime(source.getEffectiveStartTime());
        row.setEffectiveEndTime(source.getEffectiveEndTime());
        row.setRuleStatus(source.getRuleStatus());
        row.setRemark(source.getRemark());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /** 复制权威波动摘要，不重新解释汇率方向和百分比。 */
    private VolatilityRow toVolatilityRow(VolatilityOverview source) {
        VolatilityRow row = new VolatilityRow();
        row.setRateType(source.getRateType());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setLatestRate(source.getLatestRate());
        row.setMinimumRate(source.getMinimumRate());
        row.setMaximumRate(source.getMaximumRate());
        row.setVolatilityRate(source.getVolatilityRate());
        row.setSampleCount(source.getSampleCount());
        row.setLatestEffectiveTime(source.getLatestEffectiveTime());
        row.setAlertStatus(source.getAlertStatus());
        return row;
    }

    /** 复制预警规则只读投影。 */
    private AlertRuleRow toAlertRuleRow(AlertRuleResponse source) {
        AlertRuleRow row = new AlertRuleRow();
        row.setRuleCode(source.getRuleCode());
        row.setRuleName(source.getRuleName());
        row.setRateType(source.getRateType());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setWindowMinutes(source.getWindowMinutes());
        row.setTriggerThreshold(source.getTriggerThreshold());
        row.setRecoveryThreshold(source.getRecoveryThreshold());
        row.setRecoveryConsecutiveCount(source.getRecoveryConsecutiveCount());
        row.setMinimumSampleCount(source.getMinimumSampleCount());
        row.setAlertLevel(source.getAlertLevel());
        row.setSystemMandatory(source.getSystemMandatory());
        row.setRuleStatus(source.getRuleStatus());
        row.setRemark(source.getRemark());
        row.setUpdateTime(source.getUpdateTime());
        return row;
    }

    /** 复制预警事件冻结证据，不触发确认、关闭或状态变更。 */
    private AlertEventRow toAlertEventRow(AlertEventResponse source) {
        AlertEventRow row = new AlertEventRow();
        row.setEventNo(source.getEventNo());
        row.setRuleCode(source.getRuleCode());
        row.setRateType(source.getRateType());
        row.setBaseCurrency(source.getBaseCurrency());
        row.setQuoteCurrency(source.getQuoteCurrency());
        row.setAlertLevel(source.getAlertLevel());
        row.setEventStatus(source.getEventStatus());
        row.setVolatilityRate(source.getVolatilityRate());
        row.setMaximumVolatilityRate(source.getMaximumVolatilityRate());
        row.setTriggerThreshold(source.getTriggerThreshold());
        row.setMinimumRate(source.getMinimumRate());
        row.setMinimumRateSource(source.getMinimumRateSource());
        row.setMaximumRate(source.getMaximumRate());
        row.setMaximumRateSource(source.getMaximumRateSource());
        row.setLatestRate(source.getLatestRate());
        row.setLatestRateSource(source.getLatestRateSource());
        row.setSampleCount(source.getSampleCount());
        row.setFirstTriggerTime(source.getFirstTriggerTime());
        row.setLastDetectedTime(source.getLastDetectedTime());
        row.setAcknowledgedBy(source.getAcknowledgedBy());
        row.setResolvedTime(source.getResolvedTime());
        return row;
    }
}
