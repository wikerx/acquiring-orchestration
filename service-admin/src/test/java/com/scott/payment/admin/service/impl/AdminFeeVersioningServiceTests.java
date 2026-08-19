package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeRuleRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeVersionSaveRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.MerchantFeeVersionSaveRequest;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanVersionDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleTierDO;
import com.scott.payment.admin.mapper.FeePlanMapper;
import com.scott.payment.admin.mapper.FeePlanVersionMapper;
import com.scott.payment.admin.mapper.FeeRuleMapper;
import com.scott.payment.admin.mapper.FeeRuleTierMapper;
import com.scott.payment.admin.mapper.FeeSimulationRecordMapper;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFeeVersioningServiceTests
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 费用版本服务测试，验证主记录锁、模板快照复制隔离和提交人与审核人分离。
 * @status : create
 */
class AdminFeeVersioningServiceTests {

    /** 初始化纯单元测试所需的 MyBatis-Plus Lambda 字段元数据。 */
    @BeforeEach
    void setUpTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, FeePlanDO.class);
        TableInfoHelper.initTableInfo(assistant, FeePlanVersionDO.class);
        TableInfoHelper.initTableInfo(assistant, FeeRuleDO.class);
        TableInfoHelper.initTableInfo(assistant, FeeRuleTierDO.class);
        TableInfoHelper.initTableInfo(assistant, BaseMerchantInfoDO.class);
    }

    /** 创建新版本前必须锁定方案主记录，并在已有最大版本号上递增。 */
    @Test
    void shouldAllocateNextVersionWhilePlanIsLocked() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = templatePlan(1L, "ENABLED", 20L, 3);
        FeePlanVersionDO latest = version(20L, 1L, 3, "ACTIVE");
        AtomicReference<FeePlanVersionDO> inserted = new AtomicReference<>();
        when(fixture.planMapper.selectByIdForUpdate(1L)).thenReturn(plan);
        when(fixture.versionMapper.selectCount(any())).thenReturn(0L);
        when(fixture.versionMapper.selectOne(any()))
                .thenReturn(latest)
                .thenAnswer(invocation -> inserted.get());
        when(fixture.versionMapper.selectList(any())).thenAnswer(invocation -> List.of(inserted.get()));
        doAnswer(invocation -> {
            FeePlanVersionDO row = invocation.getArgument(0);
            row.setId(21L);
            inserted.set(row);
            return 1;
        }).when(fixture.versionMapper).insert(any(FeePlanVersionDO.class));
        doAnswer(invocation -> {
            FeeRuleDO row = invocation.getArgument(0);
            row.setId(31L);
            return 1;
        }).when(fixture.ruleMapper).insert(any(FeeRuleDO.class));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        fixture.service.createTemplateVersion(1L, standardVersionRequest(), 8L, "提交人");

        System.out.println("版本号分配：验证锁定方案后由 v3 创建待审核 v4");
        verify(fixture.planMapper).selectByIdForUpdate(1L);
        assertThat(inserted.get().getVersionNo()).isEqualTo(4);
        assertThat(inserted.get().getVersionStatus()).isEqualTo("PENDING_REVIEW");
    }

    /** 商户选择模板时复制当前版本，后续持久化对象不能复用模板规则和阶梯主键。 */
    @Test
    void shouldCopyCurrentTemplateVersionWithoutSharingRuleRows() {
        Fixture fixture = new Fixture();
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setMerchantId("M10001");
        merchant.setMerchantName("示例商户");
        FeePlanDO template = templatePlan(2L, "ENABLED", 20L, 3);
        FeePlanVersionDO sourceVersion = version(20L, 2L, 3, "ACTIVE");
        sourceVersion.setInitialDelayUnit("T");
        sourceVersion.setInitialDelayDays(15);
        sourceVersion.setRegularDelayUnit("T");
        sourceVersion.setRegularDelayDays(7);
        sourceVersion.setSettlementFrequency("WEEKLY");
        sourceVersion.setFrequencyDay(5);
        FeeRuleDO sourceRule = sourceRule();
        FeeRuleTierDO sourceTier = sourceTier();
        AtomicReference<FeePlanVersionDO> insertedVersion = new AtomicReference<>();
        AtomicReference<FeeRuleDO> insertedRule = new AtomicReference<>();
        AtomicReference<FeeRuleTierDO> insertedTier = new AtomicReference<>();

        when(fixture.merchantInfoMapper.selectOne(any())).thenReturn(merchant);
        when(fixture.planMapper.selectMerchantPlanForUpdate("M10001")).thenReturn(null);
        doAnswer(invocation -> {
            FeePlanDO row = invocation.getArgument(0);
            row.setId(100L);
            return 1;
        }).when(fixture.planMapper).insert(any(FeePlanDO.class));
        when(fixture.planMapper.selectOne(any())).thenReturn(template);
        when(fixture.versionMapper.selectOne(any()))
                .thenReturn(sourceVersion)
                .thenReturn(null)
                .thenAnswer(invocation -> insertedVersion.get());
        doAnswer(invocation -> {
            FeePlanVersionDO row = invocation.getArgument(0);
            row.setId(300L);
            insertedVersion.set(row);
            return 1;
        }).when(fixture.versionMapper).insert(any(FeePlanVersionDO.class));
        when(fixture.versionMapper.selectList(any())).thenAnswer(invocation -> List.of(insertedVersion.get()));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of(sourceRule));
        doAnswer(invocation -> {
            FeeRuleDO row = invocation.getArgument(0);
            row.setId(400L);
            insertedRule.set(row);
            return 1;
        }).when(fixture.ruleMapper).insert(any(FeeRuleDO.class));
        when(fixture.tierMapper.selectList(any())).thenReturn(List.of(sourceTier));
        doAnswer(invocation -> {
            FeeRuleTierDO row = invocation.getArgument(0);
            row.setId(500L);
            insertedTier.set(row);
            return 1;
        }).when(fixture.tierMapper).insert(any(FeeRuleTierDO.class));

        MerchantFeeVersionSaveRequest request = new MerchantFeeVersionSaveRequest();
        request.setTemplateId(2L);
        request.setChangeReason("首次分配模板");
        fixture.service.createMerchantVersion("M10001", request, 8L, "提交人");

        System.out.println("模板复制隔离：验证商户 v1 保存来源模板 v3，但规则与阶梯使用独立主键");
        assertThat(insertedVersion.get().getVersionNo()).isEqualTo(1);
        assertThat(insertedVersion.get().getSourceTemplateId()).isEqualTo(2L);
        assertThat(insertedVersion.get().getSourceTemplateVersionNo()).isEqualTo(3);
        assertThat(insertedVersion.get().getOriginType()).isEqualTo("TEMPLATE");
        assertThat(insertedRule.get()).isNotSameAs(sourceRule);
        assertThat(insertedRule.get().getId()).isEqualTo(400L);
        assertThat(insertedRule.get().getPlanVersionId()).isEqualTo(300L);
        assertThat(sourceRule.getId()).isEqualTo(40L);
        assertThat(sourceRule.getPlanVersionId()).isEqualTo(20L);
        assertThat(insertedTier.get()).isNotSameAs(sourceTier);
        assertThat(insertedTier.get().getFeeRuleId()).isEqualTo(400L);
        assertThat(sourceTier.getFeeRuleId()).isEqualTo(40L);
    }

    /** 提交人不能审核自己提交的费用版本。 */
    @Test
    void shouldRejectSamePersonReview() {
        Fixture fixture = new Fixture();
        FeePlanVersionDO pending = version(21L, 1L, 4, "PENDING_REVIEW");
        pending.setSubmitById(8L);
        pending.setSubmitByName("同一人");
        when(fixture.versionMapper.selectByIdForUpdate(21L)).thenReturn(pending);

        System.out.println("双人复核：验证提交账号 8 不能审核自己提交的待审核版本");
        assertThatThrownBy(() -> fixture.service.approveVersion(21L, "同意", 8L, "同一人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("提交人与审核人不能是同一人");
        verify(fixture.planMapper, never()).selectByIdForUpdate(any());
    }

    /** 禁用模板只改变模板可选状态，不改变其当前生效版本。 */
    @Test
    void shouldDisableTemplateWithoutChangingCurrentVersion() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = templatePlan(1L, "ENABLED", 20L, 3);
        when(fixture.planMapper.selectByIdForUpdate(1L)).thenReturn(plan);
        when(fixture.versionMapper.selectCount(any())).thenReturn(0L);

        fixture.service.updateTemplateStatus(1L, false, "操作人");

        assertThat(plan.getStatus()).isEqualTo("DISABLED");
        assertThat(plan.getCurrentVersionId()).isEqualTo(20L);
        assertThat(plan.getCurrentVersionNo()).isEqualTo(3);
        verify(fixture.planMapper).updateById(plan);
    }

    /** 已禁用模板的新版本通过审核后仍保持禁用，避免审核动作意外恢复可选状态。 */
    @Test
    void shouldKeepDisabledTemplateDisabledAfterVersionApproval() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = templatePlan(1L, "DISABLED", 20L, 3);
        FeePlanVersionDO current = version(20L, 1L, 3, "ACTIVE");
        FeePlanVersionDO pending = version(21L, 1L, 4, "PENDING_REVIEW");
        pending.setSubmitById(8L);
        pending.setSubmitByName("提交人");
        when(fixture.versionMapper.selectByIdForUpdate(21L)).thenReturn(pending);
        when(fixture.planMapper.selectByIdForUpdate(1L)).thenReturn(plan);
        when(fixture.versionMapper.selectOne(any())).thenReturn(current).thenReturn(null);
        when(fixture.versionMapper.selectList(any())).thenReturn(List.of(pending, current));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        fixture.service.approveVersion(21L, "同意", 9L, "审核人");

        assertThat(plan.getStatus()).isEqualTo("DISABLED");
        assertThat(plan.getCurrentVersionId()).isEqualTo(21L);
        assertThat(plan.getCurrentVersionNo()).isEqualTo(4);
        assertThat(current.getVersionStatus()).isEqualTo("SUPERSEDED");
        assertThat(current.getSupersededTime()).isNotNull();
        assertThat(pending.getVersionStatus()).isEqualTo("ACTIVE");
        assertThat(pending.getEffectiveTime()).isNotNull();
        assertThat(pending.getReviewById()).isEqualTo(9L);
        assertThat(pending.getReviewByName()).isEqualTo("审核人");
    }

    private static FeeVersionSaveRequest standardVersionRequest() {
        FeeVersionSaveRequest request = new FeeVersionSaveRequest();
        request.setInitialDelayUnit("T");
        request.setInitialDelayDays(15);
        request.setRegularDelayUnit("T");
        request.setRegularDelayDays(7);
        request.setSettlementFrequency("DAILY");
        request.setChangeReason("调整交易手续费");
        FeeRuleRequest rule = new FeeRuleRequest();
        rule.setRuleName("支付手续费");
        rule.setTransactionType("PAYMENT");
        rule.setPaymentType("BANK_CARD");
        rule.setPaymentMethod("ALL");
        rule.setFeeMode("STANDARD");
        rule.setPercentageRate(new BigDecimal("2.3"));
        rule.setFixedAmountUsd(BigDecimal.ONE);
        request.setRules(List.of(rule));
        return request;
    }

    private static FeePlanDO templatePlan(Long id, String status, Long currentVersionId, Integer currentVersionNo) {
        FeePlanDO plan = new FeePlanDO();
        plan.setId(id);
        plan.setPlanCode("FT0001");
        plan.setPlanName("标准模板");
        plan.setPlanType("TEMPLATE");
        plan.setStatus(status);
        plan.setCurrentVersionId(currentVersionId);
        plan.setCurrentVersionNo(currentVersionNo);
        plan.setDeleted(0L);
        return plan;
    }

    private static FeePlanVersionDO version(Long id, Long planId, Integer versionNo, String status) {
        FeePlanVersionDO version = new FeePlanVersionDO();
        version.setId(id);
        version.setPlanId(planId);
        version.setVersionNo(versionNo);
        version.setVersionStatus(status);
        version.setOriginType("INDEPENDENT");
        version.setDeleted(0L);
        return version;
    }

    private static FeeRuleDO sourceRule() {
        FeeRuleDO rule = new FeeRuleDO();
        rule.setId(40L);
        rule.setPlanVersionId(20L);
        rule.setRuleName("支付手续费");
        rule.setTransactionType("PAYMENT");
        rule.setPaymentType("BANK_CARD");
        rule.setPaymentMethod("ALL");
        rule.setFeeMode("TIER");
        rule.setPercentageRate(new BigDecimal("2.3"));
        rule.setFixedAmountUsd(BigDecimal.ONE);
        rule.setTierMetric("COUNT");
        rule.setTierPeriod("MONTH");
        rule.setSortNo(1);
        rule.setDeleted(0L);
        return rule;
    }

    private static FeeRuleTierDO sourceTier() {
        FeeRuleTierDO tier = new FeeRuleTierDO();
        tier.setId(50L);
        tier.setFeeRuleId(40L);
        tier.setLowerBound(BigDecimal.ZERO);
        tier.setPercentageRate(new BigDecimal("2.3"));
        tier.setFixedAmountUsd(BigDecimal.ONE);
        tier.setSortNo(1);
        tier.setDeleted(0L);
        return tier;
    }

    private static final class Fixture {
        private final FeePlanMapper planMapper = mock(FeePlanMapper.class);
        private final FeePlanVersionMapper versionMapper = mock(FeePlanVersionMapper.class);
        private final FeeRuleMapper ruleMapper = mock(FeeRuleMapper.class);
        private final FeeRuleTierMapper tierMapper = mock(FeeRuleTierMapper.class);
        private final FeeSimulationRecordMapper simulationMapper = mock(FeeSimulationRecordMapper.class);
        private final BaseMerchantInfoMapper merchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        private final AdminSettlementRateResolver settlementRateResolver = mock(AdminSettlementRateResolver.class);
        private final AdminFeeServiceImpl service = new AdminFeeServiceImpl(
                planMapper, versionMapper, ruleMapper, tierMapper, simulationMapper,
                merchantInfoMapper, new AdminFeeSimulationCalculator(), settlementRateResolver);
    }
}
