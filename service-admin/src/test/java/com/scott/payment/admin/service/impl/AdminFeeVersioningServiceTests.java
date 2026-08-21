package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeRuleRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeRuleTierRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanDetailResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeRuleResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeTemplateCreateRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeVersionSaveRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.MerchantFeeVersionSaveRequest;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanVersionDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleTierDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeSimulationRecordDO;
import com.scott.payment.admin.service.impl.AdminSettlementRateResolver.ResolvedSettlementRate;
import com.scott.payment.admin.mapper.FeePlanMapper;
import com.scott.payment.admin.mapper.FeePlanVersionMapper;
import com.scott.payment.admin.mapper.FeeRuleMapper;
import com.scott.payment.admin.mapper.FeeRuleTierMapper;
import com.scott.payment.admin.mapper.FeeSimulationRecordMapper;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    /** 创建模板草稿前必须锁定方案主记录，并在已有最大版本号上递增。 */
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

        System.out.println("版本号分配：验证锁定方案后由 v3 创建可编辑草稿 v4");
        verify(fixture.planMapper).selectByIdForUpdate(1L);
        assertThat(inserted.get().getVersionNo()).isEqualTo(4);
        assertThat(inserted.get().getVersionStatus()).isEqualTo("DRAFT");
    }

    /** 风控手续费试算必须按明确服务类型匹配，并将该维度写入审计快照。 */
    @Test
    void shouldMatchAndPersistRiskServiceTypeDuringSimulation() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = merchantPlan(1L, "M10001");
        plan.setStatus("ENABLED");
        plan.setCurrentVersionId(21L);
        FeePlanVersionDO version = version(21L, 1L, 1, "ACTIVE");
        version.setReserveRate(BigDecimal.ZERO);
        FeeRuleDO riskRule = atomicRule(31L, 21L, "PAYMENT", "BANK_CARD", "ALL", "RG-RISK", "0");
        riskRule.setFeeCategory("RISK_FEE");
        riskRule.setRiskServiceType("INTERNAL");
        riskRule.setChargeTrigger("SUCCESS_OR_FAILURE");
        riskRule.setFixedAmountUsd(new BigDecimal("0.08"));
        AtomicReference<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FeeRuleDO>> ruleQuery =
                new AtomicReference<>();
        AtomicReference<FeeSimulationRecordDO> insertedRecord = new AtomicReference<>();

        when(fixture.planMapper.selectOne(any())).thenReturn(plan);
        when(fixture.versionMapper.selectOne(any())).thenReturn(version);
        when(fixture.ruleMapper.selectList(any())).thenAnswer(invocation -> {
            ruleQuery.set(invocation.getArgument(0));
            return List.of(riskRule);
        });
        when(fixture.tierMapper.selectList(any())).thenReturn(List.of());
        LocalDateTime now = LocalDateTime.of(2026, 8, 20, 12, 0);
        when(fixture.settlementRateResolver.resolve(any(), any()))
                .thenReturn(new ResolvedSettlementRate(null, "SYSTEM_IDENTITY", BigDecimal.ONE, now, now));
        doAnswer(invocation -> {
            insertedRecord.set(invocation.getArgument(0));
            return 1;
        }).when(fixture.simulationMapper).insert(any(FeeSimulationRecordDO.class));

        FeeSimulationRequest request = new FeeSimulationRequest();
        request.setMerchantId("M10001");
        request.setFeeCategory("RISK_FEE");
        request.setTransactionType("PAYMENT");
        request.setPaymentType("BANK_CARD");
        request.setPaymentMethod("ALL");
        request.setRiskServiceType("INTERNAL");
        request.setLabelAmount(new BigDecimal("100"));
        request.setLabelCurrency("USD");

        fixture.service.simulate(request, 8L, "试算人");

        ruleQuery.get().getSqlSegment();
        assertThat(ruleQuery.get().getParamNameValuePairs().values()).contains("RISK_FEE", "INTERNAL");
        assertThat(insertedRecord.get().getRiskServiceType()).isEqualTo("INTERNAL");
        assertThat(insertedRecord.get().getMatchedRuleId()).isEqualTo(31L);
    }

    /** 草稿保存必须原地更新当前版本，不能重复占用新版本号。 */
    @Test
    void shouldUpdateTemplateDraftInPlace() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = templatePlan(1L, "DISABLED", null, null);
        FeePlanVersionDO draft = version(21L, 1L, 1, "DRAFT");
        draft.setSubmitById(8L);
        draft.setSubmitByName("创建人");
        when(fixture.versionMapper.selectByIdForUpdate(21L)).thenReturn(draft);
        when(fixture.planMapper.selectByIdForUpdate(1L)).thenReturn(plan);
        when(fixture.versionMapper.selectList(any())).thenReturn(List.of(draft));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            FeeRuleDO row = invocation.getArgument(0);
            row.setId(31L);
            return 1;
        }).when(fixture.ruleMapper).insert(any(FeeRuleDO.class));

        FeeVersionSaveRequest request = standardVersionRequest();
        request.setRegularDelayDays(9);
        fixture.service.updateTemplateDraft(1L, 21L, request, 9L, "修改人");

        System.out.println("模板草稿编辑：验证 v1 原地更新配置且记录最后保存人，不创建 v2");
        assertThat(draft.getVersionNo()).isEqualTo(1);
        assertThat(draft.getVersionStatus()).isEqualTo("DRAFT");
        assertThat(draft.getRegularDelayDays()).isEqualTo(9);
        assertThat(draft.getSubmitById()).isEqualTo(9L);
        assertThat(draft.getSubmitByName()).isEqualTo("修改人");
        verify(fixture.versionMapper, never()).insert(any(FeePlanVersionDO.class));
        verify(fixture.versionMapper).updateById(draft);
    }

    /** 只有显式提交动作才能把模板草稿转为待审核版本。 */
    @Test
    void shouldSubmitTemplateDraftForReview() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = templatePlan(1L, "DISABLED", null, null);
        FeePlanVersionDO draft = version(21L, 1L, 1, "DRAFT");
        draft.setSubmitById(8L);
        draft.setSubmitByName("保存人");
        when(fixture.versionMapper.selectByIdForUpdate(21L)).thenReturn(draft);
        when(fixture.planMapper.selectByIdForUpdate(1L)).thenReturn(plan);
        when(fixture.versionMapper.selectList(any())).thenReturn(List.of(draft));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        fixture.service.submitTemplateVersion(21L, 9L, "提交人");

        System.out.println("模板提交审核：验证草稿显式转为待审核并锁定本次提交人");
        assertThat(draft.getVersionStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(draft.getSubmitById()).isEqualTo(9L);
        assertThat(draft.getSubmitByName()).isEqualTo("提交人");
        assertThat(draft.getSubmitTime()).isNotNull();
        verify(fixture.versionMapper).updateById(draft);
    }

    /** 待审核模板只能由原提交人撤回，撤回后恢复为可编辑草稿。 */
    @Test
    void shouldWithdrawPendingTemplateBySubmitter() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = templatePlan(1L, "DISABLED", null, null);
        FeePlanVersionDO pending = version(21L, 1L, 1, "PENDING_REVIEW");
        pending.setSubmitById(8L);
        pending.setSubmitByName("提交人");
        when(fixture.versionMapper.selectByIdForUpdate(21L)).thenReturn(pending);
        when(fixture.planMapper.selectByIdForUpdate(1L)).thenReturn(plan);
        when(fixture.versionMapper.selectList(any())).thenReturn(List.of(pending));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        fixture.service.withdrawTemplateVersion(21L, 8L, "提交人");

        System.out.println("模板撤回审核：验证原提交人可将待审核 v1 恢复为草稿");
        assertThat(pending.getVersionStatus()).isEqualTo("DRAFT");
        verify(fixture.versionMapper).updateById(pending);
    }

    /** 非原提交人不能撤回待审核模板，避免越权篡改审核中的配置。 */
    @Test
    void shouldRejectTemplateWithdrawalByAnotherOperator() {
        Fixture fixture = new Fixture();
        FeePlanVersionDO pending = version(21L, 1L, 1, "PENDING_REVIEW");
        pending.setSubmitById(8L);
        pending.setSubmitByName("提交人");
        when(fixture.versionMapper.selectByIdForUpdate(21L)).thenReturn(pending);

        System.out.println("模板撤回权限：验证账号 9 不能撤回账号 8 提交的待审核版本");
        assertThatThrownBy(() -> fixture.service.withdrawTemplateVersion(21L, 9L, "其他人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能由原提交人撤回");
        verify(fixture.planMapper, never()).selectByIdForUpdate(any());
        verify(fixture.versionMapper, never()).updateById(any(FeePlanVersionDO.class));
    }

    /** 多选匹配维度必须在保存版本时展开为可唯一匹配的原子规则。 */
    @Test
    void shouldExpandSelectedDimensionsIntoAtomicRules() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = templatePlan(1L, "ENABLED", 20L, 3);
        FeePlanVersionDO latest = version(20L, 1L, 3, "ACTIVE");
        AtomicReference<FeePlanVersionDO> insertedVersion = new AtomicReference<>();
        List<FeeRuleDO> insertedRules = new ArrayList<>();
        when(fixture.planMapper.selectByIdForUpdate(1L)).thenReturn(plan);
        when(fixture.versionMapper.selectCount(any())).thenReturn(0L);
        when(fixture.versionMapper.selectOne(any()))
                .thenReturn(latest)
                .thenAnswer(invocation -> insertedVersion.get());
        when(fixture.versionMapper.selectList(any())).thenAnswer(invocation -> List.of(insertedVersion.get()));
        doAnswer(invocation -> {
            FeePlanVersionDO row = invocation.getArgument(0);
            row.setId(21L);
            insertedVersion.set(row);
            return 1;
        }).when(fixture.versionMapper).insert(any(FeePlanVersionDO.class));
        doAnswer(invocation -> {
            FeeRuleDO row = invocation.getArgument(0);
            row.setId(31L + insertedRules.size());
            insertedRules.add(row);
            return 1;
        }).when(fixture.ruleMapper).insert(any(FeeRuleDO.class));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest rule = request.getRules().get(0);
        rule.setRuleName("");
        rule.setTransactionType(null);
        rule.setPaymentType(null);
        rule.setPaymentMethod(null);
        rule.setTransactionTypes(List.of("PAYMENT", "CAPTURE"));
        rule.setPaymentTypes(List.of("BANK_CARD"));
        rule.setPaymentMethods(List.of("VISA", "MASTERCARD"));

        fixture.service.createTemplateVersion(1L, request, 8L, "提交人");

        System.out.println("规则批量展开：2 个交易类型与 2 个卡品牌生成 4 条原子规则");
        assertThat(insertedRules).hasSize(4);
        assertThat(insertedRules)
                .extracting(ruleRow -> ruleRow.getTransactionType() + "|" + ruleRow.getPaymentType()
                        + "|" + ruleRow.getPaymentMethod())
                .containsExactly(
                        "PAYMENT|BANK_CARD|VISA",
                        "PAYMENT|BANK_CARD|MASTERCARD",
                        "CAPTURE|BANK_CARD|VISA",
                        "CAPTURE|BANK_CARD|MASTERCARD");
        assertThat(insertedRules).allSatisfy(ruleRow -> assertThat(ruleRow.getRuleName()).isNotBlank());
        assertThat(insertedRules).extracting(FeeRuleDO::getRuleGroupCode)
                .doesNotContainNull()
                .allSatisfy(groupCode -> assertThat(groupCode).isNotBlank())
                .containsOnly(insertedRules.get(0).getRuleGroupCode());
    }

    /** 显式分组的原子规则应一次批量查询后还原为一个多选规则。 */
    @Test
    void shouldRestoreExplicitRuleGroupAndBatchVersionDetailQueries() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = templatePlan(1L, "ENABLED", 21L, 4);
        FeePlanVersionDO current = version(21L, 1L, 4, "ACTIVE");
        FeePlanVersionDO history = version(20L, 1L, 3, "SUPERSEDED");
        List<FeeRuleDO> rules = List.of(
                atomicRule(31L, 21L, "PAYMENT", "BANK_CARD", "VISA", "FRG001", "2.30"),
                atomicRule(32L, 21L, "PAYMENT", "BANK_CARD", "MASTERCARD", "FRG001", "2.30"),
                atomicRule(33L, 21L, "CAPTURE", "BANK_CARD", "VISA", "FRG001", "2.30"),
                atomicRule(34L, 21L, "CAPTURE", "BANK_CARD", "MASTERCARD", "FRG001", "2.30"));
        when(fixture.planMapper.selectOne(any())).thenReturn(plan);
        when(fixture.versionMapper.selectList(any())).thenReturn(List.of(current, history));
        when(fixture.ruleMapper.selectList(any())).thenReturn(rules);
        when(fixture.tierMapper.selectList(any())).thenReturn(List.of());

        FeePlanDetailResponse detail = fixture.service.getTemplate(1L);

        FeeRuleResponse logicalRule = detail.getCurrentVersion().getRules().get(0);
        System.out.println("编辑回显：4 条显式分组原子规则还原为 1 条多选规则，详情查询不随版本数增长");
        assertThat(detail.getCurrentVersion().getRules()).hasSize(1);
        assertThat(logicalRule.getTransactionTypes()).containsExactly("PAYMENT", "CAPTURE");
        assertThat(logicalRule.getPaymentTypes()).containsExactly("BANK_CARD");
        assertThat(logicalRule.getPaymentMethods()).containsExactly("VISA", "MASTERCARD");
        verify(fixture.ruleMapper, times(1)).selectList(any());
        verify(fixture.tierMapper, times(1)).selectList(any());
    }

    /** 无分组编码的历史规则只有形成完整维度组合时才允许合并。 */
    @Test
    void shouldSafelyRestoreCompleteLegacyCartesianRules() {
        Fixture fixture = detailFixture(List.of(
                atomicRule(31L, 21L, "PAYMENT", "BANK_CARD", "VISA", null, "2.30"),
                atomicRule(32L, 21L, "PAYMENT", "BANK_CARD", "MASTERCARD", null, "2.30"),
                atomicRule(33L, 21L, "CAPTURE", "BANK_CARD", "VISA", null, "2.30"),
                atomicRule(34L, 21L, "CAPTURE", "BANK_CARD", "MASTERCARD", null, "2.30")), List.of());

        List<FeeRuleResponse> rules = fixture.service.getTemplate(1L).getCurrentVersion().getRules();

        System.out.println("历史规则兼容：完整 2×1×2 原子组合安全还原为一个逻辑规则");
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getTransactionTypes()).containsExactly("PAYMENT", "CAPTURE");
        assertThat(rules.get(0).getPaymentMethods()).containsExactly("VISA", "MASTERCARD");
    }

    /** 历史原子规则缺少任一组合时必须逐条返回，避免编辑保存后制造不存在的匹配维度。 */
    @Test
    void shouldKeepIncompleteLegacyCartesianRulesAtomic() {
        Fixture fixture = detailFixture(List.of(
                atomicRule(31L, 21L, "PAYMENT", "BANK_CARD", "VISA", null, "2.30"),
                atomicRule(32L, 21L, "PAYMENT", "BANK_CARD", "MASTERCARD", null, "2.30"),
                atomicRule(33L, 21L, "CAPTURE", "BANK_CARD", "VISA", null, "2.30")), List.of());

        List<FeeRuleResponse> rules = fixture.service.getTemplate(1L).getCurrentVersion().getRules();

        System.out.println("历史规则兼容：不完整维度组合保持三条原子规则，禁止推导缺失组合");
        assertThat(rules).hasSize(3);
        assertThat(rules).allSatisfy(rule -> {
            assertThat(rule.getTransactionTypes()).hasSize(1);
            assertThat(rule.getPaymentTypes()).hasSize(1);
            assertThat(rule.getPaymentMethods()).hasSize(1);
        });
    }

    /** 金额配置不同的历史原子规则不能仅凭维度形状合并。 */
    @Test
    void shouldNotGroupLegacyRulesWithDifferentFeeConfiguration() {
        Fixture fixture = detailFixture(List.of(
                atomicRule(31L, 21L, "PAYMENT", "BANK_CARD", "VISA", null, "2.30"),
                atomicRule(32L, 21L, "PAYMENT", "BANK_CARD", "MASTERCARD", null, "2.40")), List.of());

        List<FeeRuleResponse> rules = fixture.service.getTemplate(1L).getCurrentVersion().getRules();

        System.out.println("历史规则兼容：不同费率配置保持独立，避免错误合并");
        assertThat(rules).hasSize(2);
    }

    /** 阶梯定义不同的历史原子规则必须保持独立，即使主规则金额字段完全相同。 */
    @Test
    void shouldNotGroupLegacyRulesWithDifferentTierDefinitions() {
        FeeRuleDO visa = atomicRule(31L, 21L, "PAYMENT", "BANK_CARD", "VISA", null, "0");
        FeeRuleDO mastercard = atomicRule(32L, 21L, "PAYMENT", "BANK_CARD", "MASTERCARD", null, "0");
        List.of(visa, mastercard).forEach(rule -> {
            rule.setFeeMode("TIER");
            rule.setTierMetric("COUNT");
            rule.setTierPeriod("MONTH");
        });
        Fixture fixture = detailFixture(List.of(visa, mastercard), List.of(
                tier(51L, 31L, "2.30"),
                tier(52L, 32L, "2.40")));

        List<FeeRuleResponse> rules = fixture.service.getTemplate(1L).getCurrentVersion().getRules();

        System.out.println("历史规则兼容：阶梯指纹不同的规则保持独立，避免错误合并");
        assertThat(rules).hasSize(2);
    }

    /** ALL 代表全部卡品牌，不能与具体卡品牌同时选择。 */
    @Test
    void shouldRejectAllPaymentMethodMixedWithSpecificBrands() {
        Fixture fixture = new Fixture();
        when(fixture.planMapper.selectByIdForUpdate(1L))
                .thenReturn(templatePlan(1L, "ENABLED", 20L, 3));
        when(fixture.versionMapper.selectCount(any())).thenReturn(0L);
        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest rule = request.getRules().get(0);
        rule.setPaymentMethods(List.of("ALL", "VISA"));

        System.out.println("支付方式边界：验证 ALL 不能与 VISA 等具体卡品牌混选");
        assertThatThrownBy(() -> fixture.service.createTemplateVersion(1L, request, 8L, "提交人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能同时选择 ALL 和具体卡品牌");
        verify(fixture.versionMapper, never()).insert(any(FeePlanVersionDO.class));
    }

    /** 非银行卡支付不参与卡品牌匹配，即使兼容请求残留卡品牌也必须统一保存为 ALL。 */
    @Test
    void shouldNormalizeNonBankCardPaymentMethodToAll() {
        Fixture fixture = new Fixture();
        FeePlanVersionDO latest = version(20L, 1L, 3, "ACTIVE");
        AtomicReference<FeePlanVersionDO> insertedVersion = new AtomicReference<>();
        AtomicReference<FeeRuleDO> insertedRule = new AtomicReference<>();
        when(fixture.planMapper.selectByIdForUpdate(1L))
                .thenReturn(templatePlan(1L, "ENABLED", 20L, 3));
        when(fixture.versionMapper.selectCount(any())).thenReturn(0L);
        when(fixture.versionMapper.selectOne(any()))
                .thenReturn(latest)
                .thenAnswer(invocation -> insertedVersion.get());
        when(fixture.versionMapper.selectList(any()))
                .thenAnswer(invocation -> List.of(insertedVersion.get()));
        doAnswer(invocation -> {
            FeePlanVersionDO row = invocation.getArgument(0);
            row.setId(21L);
            insertedVersion.set(row);
            return 1;
        }).when(fixture.versionMapper).insert(any(FeePlanVersionDO.class));
        doAnswer(invocation -> {
            FeeRuleDO row = invocation.getArgument(0);
            row.setId(31L);
            insertedRule.set(row);
            return 1;
        }).when(fixture.ruleMapper).insert(any(FeeRuleDO.class));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest rule = request.getRules().get(0);
        rule.setPaymentType(null);
        rule.setPaymentMethod(null);
        rule.setPaymentTypes(List.of("PAYPAL"));
        rule.setPaymentMethods(List.of("ALL", "VISA"));

        fixture.service.createTemplateVersion(1L, request, 8L, "提交人");

        System.out.println("非银行卡支付：忽略残留卡品牌并按 ALL 保存原子规则");
        assertThat(insertedRule.get().getPaymentType()).isEqualTo("PAYPAL");
        assertThat(insertedRule.get().getPaymentMethod()).isEqualTo("ALL");
    }

    /** 不同批量输入展开到同一匹配维度时必须拒绝，避免数据库唯一键兜底报错。 */
    @Test
    void shouldRejectDuplicateAtomicDimensionsAfterExpansion() {
        Fixture fixture = new Fixture();
        when(fixture.planMapper.selectByIdForUpdate(1L))
                .thenReturn(templatePlan(1L, "ENABLED", 20L, 3));
        when(fixture.versionMapper.selectCount(any())).thenReturn(0L);
        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest duplicate = standardRule();
        duplicate.setRuleName("另一配置名称");
        request.setRules(List.of(request.getRules().get(0), duplicate));

        System.out.println("规则唯一性：验证批量展开后的相同原子匹配维度被服务层拒绝");
        assertThatThrownBy(() -> fixture.service.createTemplateVersion(1L, request, 8L, "提交人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("费用匹配维度不能重复");
        verify(fixture.versionMapper, never()).insert(any(FeePlanVersionDO.class));
    }

    /** 新建模板 v1 可省略变更原因，但审计记录必须保存明确的系统原因。 */
    @Test
    void shouldDefaultChangeReasonForFirstTemplateVersion() {
        Fixture fixture = new Fixture();
        AtomicReference<FeePlanVersionDO> insertedVersion = new AtomicReference<>();
        FeeTemplateCreateRequest request = firstTemplateRequest();
        request.setChangeReason("");
        doAnswer(invocation -> {
            FeePlanDO row = invocation.getArgument(0);
            row.setId(1L);
            return 1;
        }).when(fixture.planMapper).insert(any(FeePlanDO.class));
        when(fixture.versionMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            FeePlanVersionDO row = invocation.getArgument(0);
            row.setId(21L);
            insertedVersion.set(row);
            return 1;
        }).when(fixture.versionMapper).insert(any(FeePlanVersionDO.class));
        doAnswer(invocation -> {
            FeeRuleDO row = invocation.getArgument(0);
            row.setId(31L);
            return 1;
        }).when(fixture.ruleMapper).insert(any(FeeRuleDO.class));
        when(fixture.versionMapper.selectList(any()))
                .thenAnswer(invocation -> List.of(insertedVersion.get()));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        fixture.service.createTemplate(request, 8L, "提交人");

        System.out.println("首次创建模板：未填写原因时保存系统审计原因");
        assertThat(insertedVersion.get().getChangeReason()).isEqualTo("首次创建模板");
        assertThat(insertedVersion.get().getSettlementCurrency()).isNull();
        assertThat(insertedVersion.get().getVersionStatus()).isEqualTo("DRAFT");
    }

    /** 商户首次独立配置可省略变更原因，但版本审计必须记录明确的首次配置原因。 */
    @Test
    void shouldDefaultChangeReasonForFirstIndependentMerchantVersion() {
        Fixture fixture = new Fixture();
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setMerchantId("M10001");
        merchant.setMerchantName("示例商户");
        AtomicReference<FeePlanVersionDO> insertedVersion = new AtomicReference<>();
        MerchantFeeVersionSaveRequest request = new MerchantFeeVersionSaveRequest();
        copyVersionSettings(standardVersionRequest(), request);
        request.setChangeReason("");
        when(fixture.merchantInfoMapper.selectOne(any())).thenReturn(merchant);
        when(fixture.planMapper.selectMerchantPlanForUpdate("M10001")).thenReturn(null);
        doAnswer(invocation -> {
            FeePlanDO row = invocation.getArgument(0);
            row.setId(100L);
            return 1;
        }).when(fixture.planMapper).insert(any(FeePlanDO.class));
        when(fixture.versionMapper.selectOne(any()))
                .thenReturn(null)
                .thenAnswer(invocation -> insertedVersion.get());
        doAnswer(invocation -> {
            FeePlanVersionDO row = invocation.getArgument(0);
            row.setId(300L);
            insertedVersion.set(row);
            return 1;
        }).when(fixture.versionMapper).insert(any(FeePlanVersionDO.class));
        when(fixture.versionMapper.selectList(any()))
                .thenAnswer(invocation -> List.of(insertedVersion.get()));
        doAnswer(invocation -> {
            FeeRuleDO row = invocation.getArgument(0);
            row.setId(400L);
            return 1;
        }).when(fixture.ruleMapper).insert(any(FeeRuleDO.class));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        fixture.service.createMerchantVersion("M10001", request, 8L, "提交人");

        System.out.println("商户首次独立配置：未填写原因时保存系统审计原因");
        assertThat(insertedVersion.get().getChangeReason()).isEqualTo("首次配置");
        assertThat(insertedVersion.get().getOriginType()).isEqualTo("INDEPENDENT");
        assertThat(insertedVersion.get().getSettlementCurrency()).isEqualTo("USD");
        assertThat(insertedVersion.get().getVersionStatus()).isEqualTo("PENDING_REVIEW");
    }

    /** 商户首次配置被拒绝后允许保留历史快照并创建下一待复核版本。 */
    @Test
    void shouldCreateNextMerchantVersionAfterRejectedVersion() {
        Fixture fixture = new Fixture();
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setMerchantId("M10001");
        merchant.setMerchantName("示例商户");
        merchant.setSettlementCurrency("USD");
        FeePlanDO plan = merchantPlan(100L, "M10001");
        FeePlanVersionDO rejected = version(300L, 100L, 1, "REJECTED");
        AtomicReference<FeePlanVersionDO> insertedVersion = new AtomicReference<>();
        MerchantFeeVersionSaveRequest request = new MerchantFeeVersionSaveRequest();
        copyVersionSettings(standardVersionRequest(), request);
        request.setChangeReason("根据审核意见调整");

        when(fixture.merchantInfoMapper.selectOne(any())).thenReturn(merchant);
        when(fixture.planMapper.selectMerchantPlanForUpdate("M10001")).thenReturn(plan);
        when(fixture.versionMapper.selectCount(any())).thenReturn(0L);
        when(fixture.versionMapper.selectOne(any())).thenReturn(rejected);
        doAnswer(invocation -> {
            FeePlanVersionDO row = invocation.getArgument(0);
            row.setId(301L);
            insertedVersion.set(row);
            return 1;
        }).when(fixture.versionMapper).insert(any(FeePlanVersionDO.class));
        doAnswer(invocation -> {
            FeeRuleDO row = invocation.getArgument(0);
            row.setId(401L);
            return 1;
        }).when(fixture.ruleMapper).insert(any(FeeRuleDO.class));
        when(fixture.versionMapper.selectList(any()))
                .thenAnswer(invocation -> List.of(insertedVersion.get(), rejected));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        fixture.service.createMerchantVersion("M10001", request, 8L, "提交人");

        System.out.println("商户拒绝后重提：保留已拒绝 v1，并创建待复核 v2");
        assertThat(rejected.getVersionStatus()).isEqualTo("REJECTED");
        assertThat(insertedVersion.get().getVersionNo()).isEqualTo(2);
        assertThat(insertedVersion.get().getVersionStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(insertedVersion.get().getChangeReason()).isEqualTo("根据审核意见调整");
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
        sourceVersion.setRegularDelayDays(7);
        sourceVersion.setSettlementFrequency("WEEKLY");
        sourceVersion.setFrequencyDay(5);
        FeeRuleDO sourceRule = sourceRule();
        sourceRule.setRuleGroupCode("FRG_SOURCE");
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
        request.setChangeReason("");
        FeePlanDetailResponse response = fixture.service.createMerchantVersion(
                "M10001", request, 8L, "提交人");

        System.out.println("模板复制隔离：验证商户 v1 保存来源模板 v3，但规则与阶梯使用独立主键");
        assertThat(response.getOriginType()).isEqualTo("TEMPLATE");
        assertThat(response.getSourceTemplateId()).isEqualTo(2L);
        assertThat(response.getSourceTemplateVersionNo()).isEqualTo(3);
        assertThat(insertedVersion.get().getVersionNo()).isEqualTo(1);
        assertThat(insertedVersion.get().getSourceTemplateId()).isEqualTo(2L);
        assertThat(insertedVersion.get().getSourceTemplateVersionNo()).isEqualTo(3);
        assertThat(insertedVersion.get().getOriginType()).isEqualTo("TEMPLATE");
        assertThat(insertedVersion.get().getChangeReason()).isEqualTo("首次绑定模板");
        assertThat(insertedVersion.get().getSettlementCurrency()).isEqualTo("USD");
        assertThat(insertedRule.get()).isNotSameAs(sourceRule);
        assertThat(insertedRule.get().getId()).isEqualTo(400L);
        assertThat(insertedRule.get().getRuleGroupCode()).isEqualTo("FRG_SOURCE");
        assertThat(insertedRule.get().getPlanVersionId()).isEqualTo(300L);
        assertThat(sourceRule.getId()).isEqualTo(40L);
        assertThat(sourceRule.getPlanVersionId()).isEqualTo(20L);
        assertThat(insertedTier.get()).isNotSameAs(sourceTier);
        assertThat(insertedTier.get().getFeeRuleId()).isEqualTo(400L);
        assertThat(sourceTier.getFeeRuleId()).isEqualTo(40L);
    }

    /** 模板已有历史版本时，提交新版本必须填写真实变更原因。 */
    @Test
    void shouldRequireChangeReasonForLaterTemplateVersion() {
        Fixture fixture = new Fixture();
        when(fixture.planMapper.selectByIdForUpdate(1L))
                .thenReturn(templatePlan(1L, "ENABLED", 20L, 1));
        when(fixture.versionMapper.selectCount(any())).thenReturn(0L);
        FeeVersionSaveRequest request = standardVersionRequest();
        request.setChangeReason(" ");

        System.out.println("模板后续版本：v2 及以上仍必须填写变更原因");
        assertThatThrownBy(() -> fixture.service.createTemplateVersion(1L, request, 8L, "提交人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("变更原因不能为空");
        verify(fixture.versionMapper, never()).insert(any(FeePlanVersionDO.class));
    }

    /** 阶梯采用左闭右开区间，非末档上界必须严格大于当前档下界。 */
    @Test
    void shouldRejectTierUpperBoundEqualToLowerBound() {
        Fixture fixture = new Fixture();
        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest rule = request.getRules().get(0);
        rule.setFeeMode("TIER");
        rule.setTierMetric("COUNT");
        rule.setTiers(List.of(
                tierRequest("0", "0", null, null),
                tierRequest("0", null, null, null)));

        System.out.println("阶梯边界：左闭右开区间的上界必须严格大于下界");
        assertThatThrownBy(() -> fixture.service.createTemplateVersion(1L, request, 8L, "提交人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("阶梯上界必须大于下界");
        verify(fixture.versionMapper, never()).insert(any(FeePlanVersionDO.class));
    }

    /** 阶梯档位同时配置最低和最高费用时，最高费用不得小于最低费用。 */
    @Test
    void shouldRejectTierMaximumBelowMinimum() {
        Fixture fixture = new Fixture();
        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest rule = request.getRules().get(0);
        rule.setFeeMode("TIER");
        rule.setTierMetric("AMOUNT");
        rule.setTiers(List.of(tierRequest("0", null, "5.00000001", "5.00000000")));

        System.out.println("阶梯费用限制：最高费用不能小于最低费用");
        assertThatThrownBy(() -> fixture.service.createTemplateVersion(1L, request, 8L, "提交人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("最低费用不能大于最高费用");
        verify(fixture.versionMapper, never()).insert(any(FeePlanVersionDO.class));
    }

    /** 最高费用等于最低费用属于合法边界，校验应继续执行后续计费模式规则。 */
    @Test
    void shouldAllowEqualMinimumAndMaximum() {
        Fixture fixture = new Fixture();
        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest rule = request.getRules().get(0);
        rule.setMinimumAmountUsd(new BigDecimal("5.00000000"));
        rule.setMaximumAmountUsd(new BigDecimal("5.00000000"));
        rule.setFeeMode("UNSUPPORTED");

        System.out.println("费用限制边界：最高费用等于最低费用时继续执行后续规则校验");
        assertThatThrownBy(() -> fixture.service.createTemplateVersion(1L, request, 8L, "提交人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("计费模式只允许 STANDARD 或 TIER");
        verify(fixture.versionMapper, never()).insert(any(FeePlanVersionDO.class));
    }

    /** 同一费用分类只能选择一种计费模式，不允许按不同匹配维度混用标准和阶梯规则。 */
    @Test
    void shouldRejectMixedFeeModesWithinSameCategory() {
        Fixture fixture = new Fixture();
        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest tierRule = standardRule();
        tierRule.setRuleName("授权阶梯手续费");
        tierRule.setTransactionType("AUTHORIZATION");
        tierRule.setFeeMode("TIER");
        tierRule.setTierMetric("COUNT");
        tierRule.setTiers(List.of(tierRequest("0", null, null, null)));
        request.setRules(List.of(request.getRules().get(0), tierRule));

        System.out.println("计费模式互斥：同一费用分类不能同时配置标准和阶梯规则");
        assertThatThrownBy(() -> fixture.service.createTemplateVersion(1L, request, 8L, "提交人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("同一费用分类只能选择一种计费模式");
        verify(fixture.versionMapper, never()).insert(any(FeePlanVersionDO.class));
    }

    /** 新版本的首次与常规结算周期共用单位，并独立保存保证金 T/D 周期。 */
    @Test
    void shouldNormalizeSettlementUnitAndPersistReserveUnit() {
        Fixture fixture = new Fixture();
        FeePlanVersionDO latest = version(20L, 1L, 3, "ACTIVE");
        AtomicReference<FeePlanVersionDO> insertedVersion = new AtomicReference<>();
        when(fixture.planMapper.selectByIdForUpdate(1L))
                .thenReturn(templatePlan(1L, "ENABLED", 20L, 3));
        when(fixture.versionMapper.selectCount(any())).thenReturn(0L);
        when(fixture.versionMapper.selectOne(any()))
                .thenReturn(latest)
                .thenAnswer(invocation -> insertedVersion.get());
        when(fixture.versionMapper.selectList(any()))
                .thenAnswer(invocation -> List.of(insertedVersion.get()));
        doAnswer(invocation -> {
            FeePlanVersionDO row = invocation.getArgument(0);
            row.setId(21L);
            insertedVersion.set(row);
            return 1;
        }).when(fixture.versionMapper).insert(any(FeePlanVersionDO.class));
        doAnswer(invocation -> {
            FeeRuleDO row = invocation.getArgument(0);
            row.setId(31L);
            return 1;
        }).when(fixture.ruleMapper).insert(any(FeeRuleDO.class));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        FeeVersionSaveRequest request = standardVersionRequest();
        request.setReserveDelayUnit("T");
        request.setInitialDelayUnit("D");
        request.setRegularDelayUnit("T");
        fixture.service.createTemplateVersion(1L, request, 8L, "提交人");

        System.out.println("结算周期：新版本统一使用 D 周期，同时保证金独立保存为 T+N");
        assertThat(insertedVersion.get().getReserveDelayUnit()).isEqualTo("T");
        assertThat(insertedVersion.get().getInitialDelayUnit()).isEqualTo("D");
        assertThat(insertedVersion.get().getInitialDelayUnit()).isEqualTo("D");
    }

    /** 风控手续费必须按风控服务保存收费时点，并收敛为固定 USD 单笔费用。 */
    @Test
    void shouldNormalizeRiskFeeAsFixedUsdPerEvent() {
        Fixture fixture = new Fixture();
        FeePlanVersionDO latest = version(20L, 1L, 3, "ACTIVE");
        AtomicReference<FeePlanVersionDO> insertedVersion = new AtomicReference<>();
        AtomicReference<FeeRuleDO> insertedRule = new AtomicReference<>();
        when(fixture.planMapper.selectByIdForUpdate(1L))
                .thenReturn(templatePlan(1L, "ENABLED", 20L, 3));
        when(fixture.versionMapper.selectCount(any())).thenReturn(0L);
        when(fixture.versionMapper.selectOne(any()))
                .thenReturn(latest)
                .thenAnswer(invocation -> insertedVersion.get());
        when(fixture.versionMapper.selectList(any()))
                .thenAnswer(invocation -> List.of(insertedVersion.get()));
        doAnswer(invocation -> {
            FeePlanVersionDO row = invocation.getArgument(0);
            row.setId(21L);
            insertedVersion.set(row);
            return 1;
        }).when(fixture.versionMapper).insert(any(FeePlanVersionDO.class));
        doAnswer(invocation -> {
            FeeRuleDO row = invocation.getArgument(0);
            row.setId(31L);
            insertedRule.set(row);
            return 1;
        }).when(fixture.ruleMapper).insert(any(FeeRuleDO.class));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest rule = request.getRules().get(0);
        rule.setFeeCategory("RISK_FEE");
        rule.setRiskServiceType("INTERNAL");
        rule.setChargeTrigger("SUCCESS_OR_FAILURE");
        rule.setFeeMode("TIER");
        rule.setPercentageRate(new BigDecimal("2.3"));
        rule.setFixedAmountUsd(new BigDecimal("0.08"));
        rule.setMinimumAmountUsd(BigDecimal.ONE);
        rule.setMaximumAmountUsd(BigDecimal.TEN);

        fixture.service.createTemplateVersion(1L, request, 8L, "提交人");

        System.out.println("风控费用：内风控成功或失败均收费时，保存固定 0.08 USD 单笔费用");
        assertThat(insertedRule.get().getRiskServiceType()).isEqualTo("INTERNAL");
        assertThat(insertedRule.get().getChargeTrigger()).isEqualTo("SUCCESS_OR_FAILURE");
        assertThat(insertedRule.get().getFeeMode()).isEqualTo("STANDARD");
        assertThat(insertedRule.get().getPercentageRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(insertedRule.get().getFixedAmountUsd()).isEqualByComparingTo("0.08");
        assertThat(insertedRule.get().getMinimumAmountUsd()).isNull();
        assertThat(insertedRule.get().getMaximumAmountUsd()).isNull();
    }

    /** 外风控和 3DS 仅允许不收费或调用即收费，禁止复用内风控成功收费语义。 */
    @Test
    void shouldRejectRiskTriggerNotSupportedByServiceType() {
        Fixture fixture = new Fixture();
        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest rule = request.getRules().get(0);
        rule.setFeeCategory("RISK_FEE");
        rule.setRiskServiceType("EXTERNAL");
        rule.setChargeTrigger("SUCCESS");

        System.out.println("风控费用边界：外风控不允许配置交易成功收费");
        assertThatThrownBy(() -> fixture.service.createTemplateVersion(1L, request, 8L, "提交人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("触发方式与风控类型不匹配");
        verify(fixture.versionMapper, never()).insert(any(FeePlanVersionDO.class));
    }

    /** 风控服务配置实际收费触发时，固定 USD 单笔费用必须大于零。 */
    @Test
    void shouldRejectZeroFixedAmountForChargeableRiskFee() {
        Fixture fixture = new Fixture();
        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest rule = request.getRules().get(0);
        rule.setFeeCategory("RISK_FEE");
        rule.setRiskServiceType("THREE_DS");
        rule.setChargeTrigger("ON_CALL");
        rule.setFixedAmountUsd(BigDecimal.ZERO);

        System.out.println("风控费用边界：3DS 调用即收费时固定 USD 单笔费用必须大于零");
        assertThatThrownBy(() -> fixture.service.createTemplateVersion(1L, request, 8L, "提交人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("固定 USD 金额必须大于 0");
        verify(fixture.versionMapper, never()).insert(any(FeePlanVersionDO.class));
    }

    /** 结算货币兑换费保存为无业务匹配维度的唯一标准单笔规则。 */
    @Test
    void shouldAcceptSettlementCurrencyConversionFeeCategory() {
        Fixture fixture = new Fixture();
        FeePlanVersionDO latest = version(20L, 1L, 3, "ACTIVE");
        AtomicReference<FeePlanVersionDO> insertedVersion = new AtomicReference<>();
        AtomicReference<FeeRuleDO> insertedRule = new AtomicReference<>();
        when(fixture.planMapper.selectByIdForUpdate(1L))
                .thenReturn(templatePlan(1L, "ENABLED", 20L, 3));
        when(fixture.versionMapper.selectCount(any())).thenReturn(0L);
        when(fixture.versionMapper.selectOne(any()))
                .thenReturn(latest)
                .thenAnswer(invocation -> insertedVersion.get());
        when(fixture.versionMapper.selectList(any()))
                .thenAnswer(invocation -> List.of(insertedVersion.get()));
        doAnswer(invocation -> {
            FeePlanVersionDO row = invocation.getArgument(0);
            row.setId(21L);
            insertedVersion.set(row);
            return 1;
        }).when(fixture.versionMapper).insert(any(FeePlanVersionDO.class));
        doAnswer(invocation -> {
            FeeRuleDO row = invocation.getArgument(0);
            row.setId(31L);
            insertedRule.set(row);
            return 1;
        }).when(fixture.ruleMapper).insert(any(FeeRuleDO.class));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest requestRule = request.getRules().get(0);
        requestRule.setFeeCategory("SETTLEMENT_FX_FEE");
        requestRule.setRuleName("前端传入名称");
        requestRule.setTransactionType("PAYMENT");
        requestRule.setPaymentType("BANK_CARD");
        requestRule.setPaymentMethod("VISA");
        fixture.service.createTemplateVersion(1L, request, 8L, "提交人");

        System.out.println("结算换汇费：固定名称、标准单笔模式和无业务匹配维度");
        assertThat(insertedRule.get().getFeeCategory()).isEqualTo("SETTLEMENT_FX_FEE");
        assertThat(insertedRule.get().getRuleName()).isEqualTo("结算货币兑换费");
        assertThat(insertedRule.get().getTransactionType()).isEqualTo("ALL");
        assertThat(insertedRule.get().getPaymentType()).isEqualTo("ALL");
        assertThat(insertedRule.get().getPaymentMethod()).isEqualTo("ALL");
        assertThat(insertedRule.get().getRiskServiceType()).isEqualTo("NONE");
        assertThat(insertedRule.get().getChargeTrigger()).isEqualTo("NOT_APPLICABLE");
        assertThat(insertedRule.get().getFeeMode()).isEqualTo("STANDARD");
        assertThat(insertedRule.get().getTierMetric()).isNull();
    }

    /** 同一费用版本最多允许一条结算货币兑换费，避免出现多条无维度规则。 */
    @Test
    void shouldRejectMultipleSettlementCurrencyConversionFees() {
        Fixture fixture = new Fixture();
        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest first = standardRule();
        first.setFeeCategory("SETTLEMENT_FX_FEE");
        FeeRuleRequest second = standardRule();
        second.setFeeCategory("SETTLEMENT_FX_FEE");
        request.setRules(List.of(first, second));

        System.out.println("结算换汇费唯一性：同一版本提交两条时直接拒绝");
        assertThatThrownBy(() -> fixture.service.createTemplateVersion(1L, request, 8L, "提交人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只能配置一条");
        verify(fixture.planMapper, never()).selectByIdForUpdate(any());
    }

    /** 结算货币兑换费不支持阶梯计费，后端不能依赖页面隐藏来保证规则。 */
    @Test
    void shouldRejectTieredSettlementCurrencyConversionFee() {
        Fixture fixture = new Fixture();
        FeeVersionSaveRequest request = standardVersionRequest();
        FeeRuleRequest rule = request.getRules().get(0);
        rule.setFeeCategory("SETTLEMENT_FX_FEE");
        rule.setFeeMode("TIER");

        System.out.println("结算换汇费计费模式：非标准单笔请求直接拒绝");
        assertThatThrownBy(() -> fixture.service.createTemplateVersion(1L, request, 8L, "提交人"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("只支持标准单笔");
        verify(fixture.planMapper, never()).selectByIdForUpdate(any());
    }

    /** 商户结算币种只在费用版本复核通过时同步到商户资料与资金账户。 */
    @Test
    void shouldSynchronizeMerchantSettlementCurrencyOnApproval() {
        Fixture fixture = new Fixture();
        FeePlanDO plan = merchantPlan(1L, "M10001");
        FeePlanVersionDO pending = version(21L, 1L, 1, "PENDING_REVIEW");
        pending.setSubmitById(8L);
        pending.setSubmitByName("提交人");
        pending.setSettlementCurrency("USD");
        when(fixture.versionMapper.selectByIdForUpdate(21L)).thenReturn(pending);
        when(fixture.planMapper.selectByIdForUpdate(1L)).thenReturn(plan);
        when(fixture.versionMapper.selectList(any())).thenReturn(List.of(pending));
        when(fixture.ruleMapper.selectList(any())).thenReturn(List.of());

        fixture.service.approveVersion(21L, "同意", 9L, "审核人");

        System.out.println("结算币种：商户费用版本审核通过时同步 USD，并立即激活版本");
        verify(fixture.settlementCurrencyService)
                .synchronizeApprovedCurrency("M10001", "USD", "审核人");
        verify(fixture.cacheInvalidationCoordinator)
                .prepare(PaymentCacheNames.MERCHANT_ACTIVE_FEE, "M10001");
        assertThat(plan.getCurrentVersionId()).isEqualTo(21L);
        assertThat(pending.getVersionStatus()).isEqualTo("ACTIVE");
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
        verify(fixture.cacheInvalidationCoordinator, never()).prepare(any(), any());
    }

    private static FeeVersionSaveRequest standardVersionRequest() {
        FeeVersionSaveRequest request = new FeeVersionSaveRequest();
        request.setInitialDelayUnit("T");
        request.setInitialDelayDays(15);
        request.setRegularDelayUnit("T");
        request.setRegularDelayDays(7);
        request.setSettlementFrequency("DAILY");
        request.setChangeReason("调整交易手续费");
        FeeRuleRequest rule = standardRule();
        request.setRules(List.of(rule));
        return request;
    }

    private static FeeRuleRequest standardRule() {
        FeeRuleRequest rule = new FeeRuleRequest();
        rule.setRuleName("支付手续费");
        rule.setTransactionType("PAYMENT");
        rule.setPaymentType("BANK_CARD");
        rule.setPaymentMethod("ALL");
        rule.setFeeMode("STANDARD");
        rule.setPercentageRate(new BigDecimal("2.3"));
        rule.setFixedAmountUsd(BigDecimal.ONE);
        return rule;
    }

    private static FeeRuleTierRequest tierRequest(String lowerBound,
                                                  String upperBound,
                                                  String minimumAmountUsd,
                                                  String maximumAmountUsd) {
        FeeRuleTierRequest tier = new FeeRuleTierRequest();
        tier.setLowerBound(new BigDecimal(lowerBound));
        tier.setUpperBound(upperBound == null ? null : new BigDecimal(upperBound));
        tier.setPercentageRate(BigDecimal.ZERO);
        tier.setFixedAmountUsd(BigDecimal.ZERO);
        tier.setMinimumAmountUsd(minimumAmountUsd == null ? null : new BigDecimal(minimumAmountUsd));
        tier.setMaximumAmountUsd(maximumAmountUsd == null ? null : new BigDecimal(maximumAmountUsd));
        return tier;
    }

    private static FeeTemplateCreateRequest firstTemplateRequest() {
        FeeVersionSaveRequest standard = standardVersionRequest();
        FeeTemplateCreateRequest request = new FeeTemplateCreateRequest();
        request.setPlanName("标准模板");
        copyVersionSettings(standard, request);
        return request;
    }

    private static void copyVersionSettings(FeeVersionSaveRequest source, FeeVersionSaveRequest target) {
        target.setReserveRate(source.getReserveRate());
        target.setReserveDelayUnit(source.getReserveDelayUnit());
        target.setReserveDelayDays(source.getReserveDelayDays());
        target.setSettlementCurrency(source.getSettlementCurrency());
        target.setInitialDelayUnit(source.getInitialDelayUnit());
        target.setInitialDelayDays(source.getInitialDelayDays());
        target.setRegularDelayDays(source.getRegularDelayDays());
        target.setSettlementFrequency(source.getSettlementFrequency());
        target.setFrequencyDay(source.getFrequencyDay());
        target.setChangeReason(source.getChangeReason());
        target.setRules(source.getRules());
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

    private static FeePlanDO merchantPlan(Long id, String merchantId) {
        FeePlanDO plan = templatePlan(id, "DISABLED", null, null);
        plan.setPlanType("MERCHANT");
        plan.setMerchantId(merchantId);
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

    /** 构造只读详情测试夹具，统一模拟一个当前生效版本及其原子规则。 */
    private static Fixture detailFixture(List<FeeRuleDO> rules, List<FeeRuleTierDO> tiers) {
        Fixture fixture = new Fixture();
        when(fixture.planMapper.selectOne(any()))
                .thenReturn(templatePlan(1L, "ENABLED", 21L, 4));
        when(fixture.versionMapper.selectList(any()))
                .thenReturn(List.of(version(21L, 1L, 4, "ACTIVE")));
        when(fixture.ruleMapper.selectList(any())).thenReturn(rules);
        when(fixture.tierMapper.selectList(any())).thenReturn(tiers);
        return fixture;
    }

    /** 构造费用详情聚合测试使用的原子规则。 */
    private static FeeRuleDO atomicRule(Long id,
                                        Long versionId,
                                        String transactionType,
                                        String paymentType,
                                        String paymentMethod,
                                        String groupCode,
                                        String percentageRate) {
        FeeRuleDO rule = new FeeRuleDO();
        rule.setId(id);
        rule.setPlanVersionId(versionId);
        rule.setRuleGroupCode(groupCode);
        rule.setFeeCategory("TRANSACTION_FEE");
        rule.setRuleName("支付手续费");
        rule.setTransactionType(transactionType);
        rule.setPaymentType(paymentType);
        rule.setPaymentMethod(paymentMethod);
        rule.setRiskServiceType("NONE");
        rule.setChargeTrigger("NOT_APPLICABLE");
        rule.setFeeMode("STANDARD");
        rule.setPercentageRate(new BigDecimal(percentageRate));
        rule.setFixedAmountUsd(BigDecimal.ONE);
        rule.setSortNo(1);
        rule.setDeleted(0L);
        return rule;
    }

    /** 构造历史聚合测试使用的单档阶梯。 */
    private static FeeRuleTierDO tier(Long id, Long ruleId, String percentageRate) {
        FeeRuleTierDO tier = new FeeRuleTierDO();
        tier.setId(id);
        tier.setFeeRuleId(ruleId);
        tier.setLowerBound(BigDecimal.ZERO);
        tier.setPercentageRate(new BigDecimal(percentageRate));
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
        private final AdminMerchantSettlementCurrencyService settlementCurrencyService =
                mock(AdminMerchantSettlementCurrencyService.class);
        private final ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator =
                mock(ManagedCacheInvalidationCoordinator.class);
        private final AdminFeeServiceImpl service = new AdminFeeServiceImpl(
                planMapper, versionMapper, ruleMapper, tierMapper, simulationMapper,
                merchantInfoMapper, new AdminFeeSimulationCalculator(), settlementRateResolver,
                settlementCurrencyService, cacheInvalidationCoordinator);

        private Fixture() {
            when(settlementCurrencyService.validateConfiguredCurrency(any())).thenReturn("USD");
        }
    }
}
