package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.CurrencyBalanceResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.CurrentFeeResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.DetailQuery;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FeeRuleResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FeeTierResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundAccountResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundLedgerResponse;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FeePlanDO;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FeePlanVersionDO;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FeeRuleDO;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FeeRuleTierDO;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FundAccountDO;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FundLedgerDO;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.PendingBalanceAggregate;
import com.scott.payment.merchant.mapper.MerchantFeePlanMapper;
import com.scott.payment.merchant.mapper.MerchantFeePlanVersionMapper;
import com.scott.payment.merchant.mapper.MerchantFeeRuleMapper;
import com.scott.payment.merchant.mapper.MerchantFeeRuleTierMapper;
import com.scott.payment.merchant.mapper.MerchantPortalFundAccountMapper;
import com.scott.payment.merchant.mapper.MerchantPortalFundLedgerMapper;
import com.scott.payment.merchant.mapper.MerchantPortalReserveFundMapper;
import com.scott.payment.merchant.service.MerchantFinanceService;
import com.scott.payment.merchant.service.MerchantPendingBalanceQueryService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFinanceServiceImpl
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户费率与资金账户只读实现，每条 SQL 同时限定认证商户号和账户归属。
 * @status : create
 */
@Service
public class MerchantFinanceServiceImpl implements MerchantFinanceService {

    private static final String NORMAL = "NORMAL";
    private static final String FROZEN = "FROZEN";

    private final MerchantFeePlanMapper planMapper;
    private final MerchantFeePlanVersionMapper versionMapper;
    private final MerchantFeeRuleMapper ruleMapper;
    private final MerchantFeeRuleTierMapper tierMapper;
    private final MerchantPortalFundAccountMapper accountMapper;
    private final MerchantPortalFundLedgerMapper ledgerMapper;
    private final MerchantPendingBalanceQueryService pendingBalanceQueryService;
    private final MerchantPortalReserveFundMapper reserveMapper;

    /**
     * 构造商户财务只读服务；所有查询均由调用方认证商户号限定数据边界。
     *
     * @param planMapper 商户费用方案只读数据访问
     * @param versionMapper 当前生效费用版本只读数据访问
     * @param ruleMapper 当前费用规则只读数据访问
     * @param tierMapper 当前阶梯费率只读数据访问
     * @param accountMapper 商户资金账户只读数据访问
     * @param ledgerMapper 商户不可变余额流水只读数据访问
     * @param pendingBalanceQueryService 认证商户交易副本在途余额实时汇总服务
     * @param reserveMapper 商户保证金留存净额汇总数据访问
     */
    public MerchantFinanceServiceImpl(MerchantFeePlanMapper planMapper,
                                      MerchantFeePlanVersionMapper versionMapper,
                                      MerchantFeeRuleMapper ruleMapper,
                                      MerchantFeeRuleTierMapper tierMapper,
                                      MerchantPortalFundAccountMapper accountMapper,
                                      MerchantPortalFundLedgerMapper ledgerMapper,
                                      MerchantPendingBalanceQueryService pendingBalanceQueryService,
                                      MerchantPortalReserveFundMapper reserveMapper) {
        this.planMapper = planMapper;
        this.versionMapper = versionMapper;
        this.ruleMapper = ruleMapper;
        this.tierMapper = tierMapper;
        this.accountMapper = accountMapper;
        this.ledgerMapper = ledgerMapper;
        this.pendingBalanceQueryService = pendingBalanceQueryService;
        this.reserveMapper = reserveMapper;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    @Cacheable(cacheNames = PaymentCacheNames.MERCHANT_ACTIVE_FEE,
            key = "#p0",
            unless = "#result == null")
    public CurrentFeeResponse getCurrentFee(String merchantId) {
        FeePlanDO plan = planMapper.selectOne(Wrappers.<FeePlanDO>lambdaQuery()
                .eq(FeePlanDO::getPlanType, "MERCHANT")
                .eq(FeePlanDO::getMerchantId, merchantId)
                .eq(FeePlanDO::getStatus, "ENABLED")
                .eq(FeePlanDO::getDeleted, 0L)
                .last("LIMIT 1"));
        if (plan == null || plan.getCurrentVersionId() == null) {
            return null;
        }
        FeePlanVersionDO version = versionMapper.selectOne(Wrappers.<FeePlanVersionDO>lambdaQuery()
                .eq(FeePlanVersionDO::getId, plan.getCurrentVersionId())
                .eq(FeePlanVersionDO::getPlanId, plan.getId())
                .eq(FeePlanVersionDO::getVersionStatus, "ACTIVE")
                .eq(FeePlanVersionDO::getDeleted, 0L)
                .last("LIMIT 1"));
        if (version == null) {
            return null;
        }
        CurrentFeeResponse response = new CurrentFeeResponse();
        response.setDisplayName(plan.getPlanName());
        response.setVersionNo(version.getVersionNo());
        response.setEffectiveTime(version.getEffectiveTime());
        response.setReserveRate(version.getReserveRate());
        response.setReserveDelayDays(version.getReserveDelayDays());
        response.setInitialDelayUnit(version.getInitialDelayUnit());
        response.setInitialDelayDays(version.getInitialDelayDays());
        response.setRegularDelayUnit(version.getRegularDelayUnit());
        response.setRegularDelayDays(version.getRegularDelayDays());
        response.setSettlementFrequency(version.getSettlementFrequency());
        response.setFrequencyDay(version.getFrequencyDay());
        response.setRules(loadRules(version.getId()));
        return response;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public FundAccountResponse getFundAccount(String merchantId) {
        return toAccount(requireAccount(merchantId));
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<FundLedgerResponse> pageLedgers(String merchantId, DetailQuery request) {
        FundAccountDO account = requireAccount(merchantId);
        DetailQuery query = request == null ? new DetailQuery() : request;
        validatePostedRange(query);
        var wrapper = Wrappers.<FundLedgerDO>lambdaQuery()
                .eq(FundLedgerDO::getMerchantId, merchantId)
                .eq(FundLedgerDO::getAccountId, account.getId())
                .eq(StringUtils.hasText(query.getBalanceType()), FundLedgerDO::getBalanceType, upper(query.getBalanceType()))
                .eq(StringUtils.hasText(query.getBusinessType()), FundLedgerDO::getBusinessType, upper(query.getBusinessType()))
                .ge(query.getPostedStartTime() != null, FundLedgerDO::getPostedTime, query.getPostedStartTime())
                .le(query.getPostedEndTime() != null, FundLedgerDO::getPostedTime, query.getPostedEndTime())
                .orderByDesc(FundLedgerDO::getPostedTime)
                .orderByDesc(FundLedgerDO::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(value -> value.like(FundLedgerDO::getLedgerNo, keyword)
                    .or().like(FundLedgerDO::getBusinessNo, keyword)
                    .or().like(FundLedgerDO::getTransactionId, keyword)
                    .or().like(FundLedgerDO::getSummary, keyword));
        }
        Page<FundLedgerDO> page = ledgerMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toLedger).toList());
    }

    /**
     * 加载当前费用版本下的规则和阶梯，保持规则排序稳定。
     *
     * @param versionId 当前生效费用版本主键
     * @return 当前版本规则列表
     */
    private List<FeeRuleResponse> loadRules(Long versionId) {
        List<FeeRuleDO> rules = ruleMapper.selectList(Wrappers.<FeeRuleDO>lambdaQuery()
                .eq(FeeRuleDO::getPlanVersionId, versionId)
                .eq(FeeRuleDO::getDeleted, 0L)
                .orderByAsc(FeeRuleDO::getSortNo));
        if (rules.isEmpty()) return new ArrayList<>();
        Map<Long, List<FeeRuleTierDO>> tiers = tierMapper.selectList(Wrappers.<FeeRuleTierDO>lambdaQuery()
                        .in(FeeRuleTierDO::getFeeRuleId, rules.stream().map(FeeRuleDO::getId).toList())
                        .eq(FeeRuleTierDO::getDeleted, 0L)
                        .orderByAsc(FeeRuleTierDO::getSortNo)).stream()
                .collect(Collectors.groupingBy(FeeRuleTierDO::getFeeRuleId));
        return rules.stream()
                .map(rule -> toRule(rule, tiers.getOrDefault(rule.getId(), List.of())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** 将费用规则持久化模型转换为商户可见响应，不暴露内部模板来源。 */
    private FeeRuleResponse toRule(FeeRuleDO row, List<FeeRuleTierDO> tiers) {
        FeeRuleResponse response = new FeeRuleResponse();
        response.setId(row.getId()); response.setFeeCategory(row.getFeeCategory()); response.setRuleName(row.getRuleName());
        response.setTransactionType(row.getTransactionType()); response.setPaymentType(row.getPaymentType());
        response.setPaymentMethod(row.getPaymentMethod()); response.setFeeMode(row.getFeeMode());
        response.setPercentageRate(row.getPercentageRate()); response.setFixedAmountUsd(row.getFixedAmountUsd());
        response.setMinimumAmountUsd(row.getMinimumAmountUsd()); response.setMaximumAmountUsd(row.getMaximumAmountUsd());
        response.setTierMetric(row.getTierMetric()); response.setTierPeriod(row.getTierPeriod());
        response.setTiers(tiers.stream()
                .map(this::toTier)
                .collect(Collectors.toCollection(ArrayList::new)));
        return response;
    }

    /** 将规则阶梯转换为商户可见响应。 */
    private FeeTierResponse toTier(FeeRuleTierDO row) {
        FeeTierResponse response = new FeeTierResponse();
        response.setId(row.getId()); response.setLowerBound(row.getLowerBound()); response.setUpperBound(row.getUpperBound());
        response.setPercentageRate(row.getPercentageRate()); response.setFixedAmountUsd(row.getFixedAmountUsd());
        response.setMinimumAmountUsd(row.getMinimumAmountUsd()); response.setMaximumAmountUsd(row.getMaximumAmountUsd());
        return response;
    }

    /**
     * 组装账户余额和能力快照；在途、保证金均从明细实时汇总，不读取冗余展示值。
     *
     * @param account 已按认证商户号查询到的唯一资金账户
     * @return 可用余额、派生余额和账户能力只读快照
     */
    private FundAccountResponse toAccount(FundAccountDO account) {
        FundAccountResponse response = new FundAccountResponse();
        response.setId(account.getId());
        response.setAccountNo(account.getAccountNo());
        response.setSettlementCurrency(account.getSettlementCurrency());
        response.setAvailableBalance(account.getAvailableBalance());
        BigDecimal reserveBalance = reserveMapper.sumHeldBalance(account.getId(), account.getMerchantId());
        response.setReserveBalance(reserveBalance == null ? BigDecimal.ZERO : reserveBalance);
        response.setAccountStatus(normalizeManualStatus(account.getAccountStatus()));
        response.setReverseRestricted(account.getReverseRestricted());
        response.setUpdateTime(account.getUpdateTime());
        response.setPendingBalances(pendingBalances(account.getMerchantId()));
        applyAccountCapabilities(response);
        return response;
    }

    /**
     * 按标签币种分别汇总在途余额，禁止跨币种直接相加。
     *
     * @param merchantId 认证商户号
     * @return 标签币种维度的在途净额列表
     */
    private List<CurrencyBalanceResponse> pendingBalances(String merchantId) {
        return pendingBalanceQueryService.sumPendingBalances(merchantId).stream()
                .map(this::toCurrencyBalance).toList();
    }

    /** 将在途余额聚合投影转换为币种余额响应。 */
    private CurrencyBalanceResponse toCurrencyBalance(PendingBalanceAggregate aggregate) {
        CurrencyBalanceResponse response = new CurrencyBalanceResponse();
        response.setCurrency(aggregate.getCurrency());
        response.setAmount(aggregate.getAmount());
        return response;
    }

    /** 将不可变余额流水转换为商户核对响应。 */
    private FundLedgerResponse toLedger(FundLedgerDO row) {
        FundLedgerResponse response = new FundLedgerResponse();
        response.setId(row.getId()); response.setLedgerNo(row.getLedgerNo()); response.setBalanceType(row.getBalanceType());
        response.setBusinessType(row.getBusinessType()); response.setSummary(row.getSummary()); response.setBusinessNo(row.getBusinessNo());
        response.setTransactionId(row.getTransactionId()); response.setCurrency(row.getCurrency()); response.setDirection(row.getDirection());
        response.setAmount(row.getAmount()); response.setBalanceBefore(row.getBalanceBefore()); response.setBalanceAfter(row.getBalanceAfter());
        response.setAccountSequence(row.getAccountSequence()); response.setOperatorName(row.getOperatorName());
        response.setReviewerName(row.getReviewerName()); response.setBusinessTime(row.getBusinessTime()); response.setPostedTime(row.getPostedTime());
        return response;
    }

    /**
     * 查询认证商户唯一资金账户；不存在时返回业务错误，禁止跨商户回退查询。
     *
     * @param merchantId 认证商户号
     * @return 当前商户唯一活动资金账户
     * @throws ServiceException 当前商户尚未完成资金账户初始化时抛出
     */
    private FundAccountDO requireAccount(String merchantId) {
        FundAccountDO account = accountMapper.selectOne(Wrappers.<FundAccountDO>lambdaQuery()
                .eq(FundAccountDO::getMerchantId, merchantId)
                .eq(FundAccountDO::getDeleted, 0L)
                .last("LIMIT 1"));
        if (account == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "资金账户不存在");
        }
        return account;
    }

    /** 将历史负余额状态归一为正常人工状态，负余额限制由独立标识表达。 */
    private String normalizeManualStatus(String status) {
        return "NEGATIVE_BALANCE".equals(status) ? NORMAL : status;
    }

    /**
     * 按账户人工状态和负余额规则生成只读业务能力。
     *
     * @param response 已包含人工状态和负余额限制标识的账户响应
     */
    private void applyAccountCapabilities(FundAccountResponse response) {
        boolean normal = NORMAL.equals(response.getAccountStatus());
        boolean frozen = FROZEN.equals(response.getAccountStatus());
        response.setCreditAllowed(true);
        response.setDebitAllowed(normal);
        response.setWithdrawalAllowed(normal);
        response.setSettlementAllowed(normal || frozen);
        response.setReverseTransactionAllowed(normal && response.getReverseRestricted() != null
                && response.getReverseRestricted() == 0);
    }

    /**
     * 拒绝结束时间早于开始时间的入账范围，避免产生误导性空结果。
     *
     * @param query 商户余额流水筛选条件
     * @throws ServiceException 入账结束时间早于开始时间时抛出
     */
    private void validatePostedRange(DetailQuery query) {
        if (query.getPostedStartTime() != null && query.getPostedEndTime() != null
                && query.getPostedEndTime().isBefore(query.getPostedStartTime())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "入账结束时间不能早于开始时间");
        }
    }

    /** 将枚举型查询条件转换为大写标准值，空值保持为空。 */
    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
