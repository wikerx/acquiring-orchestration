package com.scott.payment.merchant.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.CurrentFeeResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.DetailQuery;
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
import com.scott.payment.merchant.service.MerchantPendingBalanceQueryService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFinanceServiceImplTests
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户财务只读服务测试，验证当前费率、账户和流水查询始终绑定认证商户号。
 * @status : create
 */
class MerchantFinanceServiceImplTests {

    /** 初始化纯单元测试中解析 Lambda 查询条件所需的表元数据。 */
    @BeforeEach
    void setUpTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, FeePlanDO.class);
        TableInfoHelper.initTableInfo(assistant, FeePlanVersionDO.class);
        TableInfoHelper.initTableInfo(assistant, FeeRuleDO.class);
        TableInfoHelper.initTableInfo(assistant, FeeRuleTierDO.class);
        TableInfoHelper.initTableInfo(assistant, FundAccountDO.class);
        TableInfoHelper.initTableInfo(assistant, FundLedgerDO.class);
    }

    /** 当前费率查询必须限定商户方案、认证商户号和当前 ACTIVE 版本。 */
    @Test
    void shouldScopeCurrentFeeToAuthenticatedMerchantAndActiveVersion() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = new FeePlanDO();
        plan.setId(11L);
        plan.setPlanName("当前商户费率");
        plan.setCurrentVersionId(21L);
        FeePlanVersionDO version = new FeePlanVersionDO();
        version.setId(21L);
        version.setPlanId(11L);
        version.setVersionNo(3);
        version.setVersionStatus("ACTIVE");
        version.setEffectiveTime(LocalDateTime.of(2026, 8, 18, 10, 0));
        AtomicReference<Wrapper<FeePlanDO>> planQuery = new AtomicReference<>();
        AtomicReference<Wrapper<FeePlanVersionDO>> versionQuery = new AtomicReference<>();
        when(fixture.planMapper.selectOne(any())).thenAnswer(invocation -> {
            planQuery.set(invocation.getArgument(0));
            return plan;
        });
        when(fixture.versionMapper.selectOne(any())).thenAnswer(invocation -> {
            versionQuery.set(invocation.getArgument(0));
            return version;
        });
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        CurrentFeeResponse response = fixture.service.getCurrentFee("M10001");

        System.out.println("当前费率查询：验证商户号 M10001 及 ACTIVE v3 查询边界");
        assertThat(response.getVersionNo()).isEqualTo(3);
        assertThat(response.getDisplayName()).isEqualTo("当前商户费率");
        assertThat(hasParams(planQuery.get(), "M10001", "MERCHANT", "ENABLED")).isTrue();
        assertThat(hasParams(versionQuery.get(), 21L, 11L, "ACTIVE")).isTrue();
    }

    /** 同一多选配置展开的原子规则必须还原为一条商户可读逻辑规则。 */
    @Test
    void shouldRestoreGroupedFeeRulesForMerchantDisplay() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = new FeePlanDO();
        plan.setId(11L);
        plan.setPlanName("卡支付交易手续费");
        plan.setCurrentVersionId(21L);
        FeePlanVersionDO version = new FeePlanVersionDO();
        version.setId(21L);
        version.setPlanId(11L);
        version.setVersionNo(1);
        version.setVersionStatus("ACTIVE");
        when(fixture.planMapper.selectOne(any())).thenReturn(plan);
        when(fixture.versionMapper.selectOne(any())).thenReturn(version);
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of(
                feeRule(31L, "FRG_CARD_FEE", "AUTHORIZATION", "VISA"),
                feeRule(32L, "FRG_CARD_FEE", "AUTHORIZATION", "MASTERCARD"),
                feeRule(33L, "FRG_CARD_FEE", "PAYMENT", "VISA"),
                feeRule(34L, "FRG_CARD_FEE", "PAYMENT", "MASTERCARD")
        ));
        when(fixture.tierMapper.selectList(any())).thenReturn(List.of());

        CurrentFeeResponse response = fixture.service.getCurrentFee("M10001");

        System.out.println("商户费率规则聚合：验证四条原子规则还原为一条多选逻辑规则");
        assertThat(response.getRules()).singleElement().satisfies(rule -> {
            assertThat(rule.getRuleName()).isEqualTo("卡支付交易手续费");
            assertThat(rule.getTransactionTypes()).containsExactly("AUTHORIZATION", "PAYMENT");
            assertThat(rule.getPaymentTypes()).containsExactly("BANK_CARD");
            assertThat(rule.getPaymentMethods()).containsExactly("VISA", "MASTERCARD");
        });
    }

    /** 多选规则聚合后的当前费率必须能够写入项目统一 Redis 缓存。 */
    @Test
    void shouldRoundTripGroupedCurrentFeeThroughRegisteredRedisSerializer() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = new FeePlanDO();
        plan.setId(11L);
        plan.setPlanName("卡支付交易手续费");
        plan.setCurrentVersionId(21L);
        FeePlanVersionDO version = new FeePlanVersionDO();
        version.setId(21L);
        version.setPlanId(11L);
        version.setVersionNo(1);
        version.setVersionStatus("ACTIVE");
        when(fixture.planMapper.selectOne(any())).thenReturn(plan);
        when(fixture.versionMapper.selectOne(any())).thenReturn(version);
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of(
                feeRule(31L, "FRG_CARD_FEE", "AUTHORIZATION", "VISA"),
                feeRule(32L, "FRG_CARD_FEE", "PAYMENT", "MASTERCARD")
        ));
        when(fixture.tierMapper.selectList(any())).thenReturn(List.of());
        CurrentFeeResponse response = fixture.service.getCurrentFee("M10001");
        RedisSerializer<Object> serializer = PaymentRedisSerializerFactory.create();

        Object restored = serializer.deserialize(serializer.serialize(response));

        System.out.println("商户费率缓存：验证多选规则聚合结果可通过统一 Redis 序列化器往返处理");
        assertThat(restored).usingRecursiveComparison().isEqualTo(response);
    }

    /** 余额流水查询必须同时限定认证商户号和该商户账户ID。 */
    @Test
    void shouldScopeLedgerQueryToMerchantAndOwnedAccount() {
        Fixture fixture = new Fixture();
        FundAccountDO account = account(31L, "M10001");
        FundLedgerDO ledger = new FundLedgerDO();
        ledger.setId(41L);
        ledger.setLedgerNo("FL10001");
        ledger.setMerchantId("M10001");
        ledger.setAccountId(31L);
        ledger.setAmount(new BigDecimal("12.50"));
        AtomicReference<Wrapper<FundAccountDO>> accountQuery = new AtomicReference<>();
        AtomicReference<Wrapper<FundLedgerDO>> ledgerQuery = new AtomicReference<>();
        when(fixture.accountMapper.selectOne(any())).thenAnswer(invocation -> {
            accountQuery.set(invocation.getArgument(0));
            return account;
        });
        when(fixture.ledgerMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            ledgerQuery.set(invocation.getArgument(1));
            Page<FundLedgerDO> result = new Page<>(1, 10);
            result.setTotal(1);
            result.setRecords(List.of(ledger));
            return result;
        });

        PageResult<FundLedgerResponse> response = fixture.service.pageLedgers("M10001", new DetailQuery());

        System.out.println("余额流水查询：验证商户号 M10001 与账户 31 双重隔离");
        assertThat(response.getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getLedgerNo()).isEqualTo("FL10001");
            assertThat(item.getAmount()).isEqualByComparingTo("12.50");
        });
        assertThat(hasParams(accountQuery.get(), "M10001")).isTrue();
        assertThat(hasParams(ledgerQuery.get(), "M10001", 31L)).isTrue();
    }

    /** 在途余额必须按标签币种分别汇总，并按资金方向保留正负影响。 */
    @Test
    void shouldKeepPendingBalancesSeparatedByLabelCurrency() {
        Fixture fixture = new Fixture();
        when(fixture.accountMapper.selectOne(any())).thenReturn(account(31L, "M10001"));
        when(fixture.reserveMapper.sumHeldBalance(31L, "M10001")).thenReturn(new BigDecimal("18.75"));
        when(fixture.pendingBalanceQueryService.sumPendingBalances("M10001")).thenReturn(List.of(
                pendingBalance("EUR", "50"),
                pendingBalance("USD", "80")
        ));

        FundAccountResponse response = fixture.service.getFundAccount("M10001");

        System.out.println("在途余额汇总：验证 USD 与 EUR 不直接相加，借方金额独立扣减");
        assertThat(response.getPendingBalances()).hasSize(2);
        assertThat(response.getPendingBalances().get(0).getCurrency()).isEqualTo("EUR");
        assertThat(response.getPendingBalances().get(0).getAmount()).isEqualByComparingTo("50");
        assertThat(response.getPendingBalances().get(1).getCurrency()).isEqualTo("USD");
        assertThat(response.getPendingBalances().get(1).getAmount()).isEqualByComparingTo("80");
        assertThat(response.getReserveBalance()).isEqualByComparingTo("18.75");
        assertThat(response.getCreditAllowed()).isTrue();
        assertThat(response.getSettlementAllowed()).isTrue();
    }

    /** 余额流水查询应包含入账起止时间，且结束时间不得早于开始时间。 */
    @Test
    void shouldFilterAndValidateLedgerPostedTimeRange() {
        Fixture fixture = new Fixture();
        when(fixture.accountMapper.selectOne(any())).thenReturn(account(31L, "M10001"));
        AtomicReference<Wrapper<FundLedgerDO>> ledgerQuery = new AtomicReference<>();
        when(fixture.ledgerMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            ledgerQuery.set(invocation.getArgument(1));
            return new Page<FundLedgerDO>(1, 10).setRecords(List.of());
        });
        LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 18, 23, 59, 59);
        DetailQuery query = new DetailQuery();
        query.setPostedStartTime(start);
        query.setPostedEndTime(end);

        fixture.service.pageLedgers("M10001", query);

        System.out.println("余额流水入账时间：验证查询条件包含闭区间并拒绝反向时间范围");
        assertThat(hasParams(ledgerQuery.get(), start, end)).isTrue();
        DetailQuery invalidQuery = new DetailQuery();
        invalidQuery.setPostedStartTime(end);
        invalidQuery.setPostedEndTime(start);
        assertThatThrownBy(() -> fixture.service.pageLedgers("M10001", invalidQuery))
                .hasMessageContaining("入账结束时间不能早于开始时间");
    }

    private static FundAccountDO account(Long id, String merchantId) {
        FundAccountDO account = new FundAccountDO();
        account.setId(id);
        account.setMerchantId(merchantId);
        account.setAccountNo("FA10001");
        account.setSettlementCurrency("USD");
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setAccountStatus("NORMAL");
        return account;
    }

    private static FeeRuleDO feeRule(Long id, String groupCode, String transactionType, String paymentMethod) {
        FeeRuleDO rule = new FeeRuleDO();
        rule.setId(id);
        rule.setPlanVersionId(21L);
        rule.setRuleGroupCode(groupCode);
        rule.setFeeCategory("TRANSACTION_FEE");
        rule.setRuleName("卡支付交易手续费");
        rule.setTransactionType(transactionType);
        rule.setPaymentType("BANK_CARD");
        rule.setPaymentMethod(paymentMethod);
        rule.setFeeMode("STANDARD");
        rule.setPercentageRate(new BigDecimal("2.3"));
        rule.setFixedAmountUsd(new BigDecimal("1.3"));
        rule.setSortNo(1);
        return rule;
    }

    private static PendingBalanceAggregate pendingBalance(String currency, String amount) {
        PendingBalanceAggregate aggregate = new PendingBalanceAggregate();
        aggregate.setCurrency(currency);
        aggregate.setAmount(new BigDecimal(amount));
        return aggregate;
    }

    private static boolean hasParams(Wrapper<?> wrapper, Object... expectedValues) {
        if (!(wrapper instanceof AbstractWrapper<?, ?, ?> queryWrapper)) {
            return false;
        }
        queryWrapper.getSqlSegment();
        return java.util.Arrays.stream(expectedValues)
                .allMatch(queryWrapper.getParamNameValuePairs().values()::contains);
    }

    private static final class Fixture {
        private final MerchantFeePlanMapper planMapper = mock(MerchantFeePlanMapper.class);
        private final MerchantFeePlanVersionMapper versionMapper = mock(MerchantFeePlanVersionMapper.class);
        private final MerchantFeeRuleMapper ruleMapper = mock(MerchantFeeRuleMapper.class);
        private final MerchantFeeRuleTierMapper tierMapper = mock(MerchantFeeRuleTierMapper.class);
        private final MerchantPortalFundAccountMapper accountMapper = mock(MerchantPortalFundAccountMapper.class);
        private final MerchantPortalFundLedgerMapper ledgerMapper = mock(MerchantPortalFundLedgerMapper.class);
        private final MerchantPendingBalanceQueryService pendingBalanceQueryService =
                mock(MerchantPendingBalanceQueryService.class);
        private final MerchantPortalReserveFundMapper reserveMapper = mock(MerchantPortalReserveFundMapper.class);
        private final MerchantFinanceServiceImpl service = new MerchantFinanceServiceImpl(
                planMapper, versionMapper, ruleMapper, tierMapper,
                accountMapper, ledgerMapper, pendingBalanceQueryService, reserveMapper);
    }
}
