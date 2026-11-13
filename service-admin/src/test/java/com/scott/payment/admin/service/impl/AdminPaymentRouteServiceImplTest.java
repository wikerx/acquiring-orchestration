package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.MerchantRouteConflictRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteCandidateRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteCopyRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RoutePaymentScope;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteRuleResponse;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteRuleQuery;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteRuleSaveRequest;
import com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteStrategyRequest;
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
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付路由配置服务规则测试。
 *
 * <p>测试只验证管理端配置事实，不构造或断言任何支付运行时选路行为。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminPaymentRouteServiceImplTest {

    @Mock
    private PaymentRouteRuleMapper ruleMapper;
    @Mock
    private PaymentRouteRuleMerchantMapper ruleMerchantMapper;
    @Mock
    private PaymentRouteStrategyMapper strategyMapper;
    @Mock
    private PaymentRouteCandidateMapper candidateMapper;
    @Mock
    private PaymentRouteMethodMapper methodMapper;
    @Mock
    private PaymentRouteTransactionTypeMapper transactionTypeMapper;
    @Mock
    private PaymentRouteChangeLogMapper changeLogMapper;
    @Mock
    private ChannelInfoMapper channelInfoMapper;
    @Mock
    private ChannelMidConfigMapper midConfigMapper;
    @Mock
    private MerchantChannelMidBindingMapper midBindingMapper;

    private final Fixture fixture = new Fixture();
    private AdminPaymentRouteServiceImpl service;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "payment-route-test");
        TableInfoHelper.initTableInfo(assistant, PaymentRouteRuleDO.class);
        TableInfoHelper.initTableInfo(assistant, PaymentRouteRuleMerchantDO.class);
        TableInfoHelper.initTableInfo(assistant, PaymentRouteCandidateDO.class);
        TableInfoHelper.initTableInfo(assistant, PaymentRouteMethodDO.class);
        TableInfoHelper.initTableInfo(assistant, PaymentRouteTransactionTypeDO.class);
        service = new AdminPaymentRouteServiceImpl(
                ruleMapper,
                ruleMerchantMapper,
                strategyMapper,
                candidateMapper,
                methodMapper,
                transactionTypeMapper,
                changeLogMapper,
                channelInfoMapper,
                midConfigMapper,
                midBindingMapper);

        InternalAuthAccount account = new InternalAuthAccount();
        account.setAccountId(99L);
        account.setLoginAccount("route.admin");
        account.setRealName("路由管理员");
        InternalAuthContextHolder.set(account);

        when(ruleMapper.selectCount(any())).thenReturn(0L);
        when(ruleMapper.selectOne(any())).thenAnswer(invocation -> fixture.rule);
        when(ruleMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(fixture.rules));
        when(ruleMapper.insert(any(PaymentRouteRuleDO.class))).thenAnswer(invocation -> {
            PaymentRouteRuleDO row = invocation.getArgument(0);
            row.setId(101L);
            fixture.rule = row;
            fixture.rules.add(row);
            return 1;
        });
        when(ruleMapper.update(any(), any())).thenReturn(1);
        when(ruleMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<PaymentRouteRuleDO> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        when(strategyMapper.selectOne(any())).thenAnswer(invocation -> fixture.strategy);
        when(strategyMapper.selectList(any())).thenAnswer(invocation ->
                fixture.strategy == null ? List.of() : List.of(fixture.strategy));
        when(strategyMapper.insert(any(PaymentRouteStrategyDO.class))).thenAnswer(invocation -> {
            PaymentRouteStrategyDO row = invocation.getArgument(0);
            row.setId(201L);
            fixture.strategy = row;
            return 1;
        });
        when(strategyMapper.updateById(any(PaymentRouteStrategyDO.class))).thenReturn(1);

        when(ruleMerchantMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(fixture.ruleMerchants));
        when(ruleMerchantMapper.insert(any(PaymentRouteRuleMerchantDO.class))).thenAnswer(invocation -> {
            PaymentRouteRuleMerchantDO row = invocation.getArgument(0);
            row.setId(225L + fixture.ruleMerchants.size() + 1L);
            fixture.ruleMerchants.add(row);
            return 1;
        });
        when(ruleMerchantMapper.updateById(any(PaymentRouteRuleMerchantDO.class))).thenReturn(1);

        when(candidateMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(fixture.candidates));
        when(candidateMapper.insert(any(PaymentRouteCandidateDO.class))).thenAnswer(invocation -> {
            PaymentRouteCandidateDO row = invocation.getArgument(0);
            row.setId(300L + fixture.candidates.size() + 1L);
            fixture.candidates.add(row);
            return 1;
        });
        when(candidateMapper.updateById(any(PaymentRouteCandidateDO.class))).thenReturn(1);

        when(methodMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(fixture.methods));
        when(methodMapper.insert(any(PaymentRouteMethodDO.class))).thenAnswer(invocation -> {
            PaymentRouteMethodDO row = invocation.getArgument(0);
            row.setId(250L + fixture.methods.size() + 1L);
            fixture.methods.add(row);
            return 1;
        });
        when(methodMapper.updateById(any(PaymentRouteMethodDO.class))).thenReturn(1);

        when(transactionTypeMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(fixture.transactionTypes));
        when(transactionTypeMapper.insert(any(PaymentRouteTransactionTypeDO.class))).thenAnswer(invocation -> {
            PaymentRouteTransactionTypeDO row = invocation.getArgument(0);
            row.setId(270L + fixture.transactionTypes.size() + 1L);
            fixture.transactionTypes.add(row);
            return 1;
        });
        when(transactionTypeMapper.updateById(any(PaymentRouteTransactionTypeDO.class))).thenReturn(1);

        when(channelInfoMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(fixture.channels));
        when(midConfigMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(fixture.mids));
        when(midBindingMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(fixture.bindings));
        when(changeLogMapper.insert(any(PaymentRouteChangeLogDO.class))).thenAnswer(invocation -> {
            PaymentRouteChangeLogDO row = invocation.getArgument(0);
            row.setId(401L);
            return 1;
        });
    }

    @AfterEach
    void tearDown() {
        InternalAuthContextHolder.clear();
    }

    /** 创建草稿必须写入审计日志，且审计快照不能泄露渠道 MID 明文或元数据。 */
    @Test
    void shouldCreateDraftAndWriteSanitizedAuditSnapshot() {
        fixture.channels.add(activeChannel(11L, 1));
        fixture.mids.add(activeMid(501L, 11L));
        RouteRuleSaveRequest request = standardRequest();
        request.setCandidates(List.of(candidateRequest(11L, 501L, 10)));

        RouteRuleResponse response = service.createRule(request);

        assertThat(response.getRuleStatus()).isEqualTo("DRAFT");
        assertThat(response.getVersion()).isZero();
        assertThat(response.getRuleCode()).matches("^ROUTE_\\d{6}_[A-F0-9]{32}$");
        assertThat(fixture.rule.getPaymentMethod()).isNull();
        assertThat(response.getPaymentMethods()).containsExactly("BANK_CARD");
        assertThat(response.getTransactionTypes()).containsExactly("AUTHORIZATION", "PAYMENT");
        ArgumentCaptor<PaymentRouteChangeLogDO> logCaptor = ArgumentCaptor.forClass(PaymentRouteChangeLogDO.class);
        verify(changeLogMapper).insert(logCaptor.capture());
        PaymentRouteChangeLogDO audit = logCaptor.getValue();
        assertThat(audit.getActionType()).isEqualTo("CREATE");
        assertThat(audit.getOperatorAccountId()).isEqualTo(99L);
        assertThat(audit.getAfterSnapshotJson())
                .contains("\"midConfigId\":501")
                .contains("\"paymentMethod\":\"BANK_CARD\"")
                .contains("\"transactionTypes\":[\"AUTHORIZATION\",\"PAYMENT\"]")
                .doesNotContain("MID-SECRET", "PRIVATE-CREDENTIAL", "channelMid", "metadataValueJson");
    }

    /** 同一规则内候选优先级必须唯一，否则无法形成确定的第三层顺序。 */
    @Test
    void shouldRejectDuplicateCandidatePriority() {
        RouteRuleSaveRequest request = standardRequest();
        request.setCandidates(List.of(
                candidateRequest(11L, 501L, 10),
                candidateRequest(12L, 502L, 10)));

        assertThatThrownBy(() -> service.createRule(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Candidate priority must be unique within a payment scope");
    }

    /** 同一 MID 可分别参与不同支付方式，且各分组拥有独立优先级序列。 */
    @Test
    void shouldAllowSameMidAndPriorityAcrossPaymentMethods() {
        fixture.channels.add(activeChannel(11L, 1));
        fixture.mids.add(activeMid(501L, 11L));
        RouteRuleSaveRequest request = standardRequest();
        RouteCandidateRequest cardCandidate = candidateRequest(11L, 501L, 10);
        RouteCandidateRequest paypalCandidate = candidateRequest(11L, 501L, 10);
        paypalCandidate.setPaymentMethod("PAYPAL");
        paypalCandidate.setCardBrand("NONE");
        request.setPaymentScopes(List.of(paymentScope("BANK_CARD", "VISA"), paymentScope("PAYPAL", "NONE")));
        request.setCandidates(List.of(cardCandidate, paypalCandidate));

        RouteRuleResponse response = service.createRule(request);

        assertThat(response.getPaymentMethods()).containsExactly("BANK_CARD", "PAYPAL");
        assertThat(response.getCandidates()).extracting("paymentMethod")
                .containsExactly("BANK_CARD", "PAYPAL");
    }

    /** Visa and Mastercard are independent payment scopes even when they use the same MID and priority. */
    @Test
    void shouldKeepCardBrandsInIndependentPaymentScopes() {
        fixture.channels.add(activeChannel(11L, 1));
        fixture.mids.add(activeMid(501L, 11L));
        RouteRuleSaveRequest request = standardRequest();
        request.setPaymentScopes(List.of(
                paymentScope("BANK_CARD", "VISA"),
                paymentScope("BANK_CARD", "MASTERCARD")));
        RouteCandidateRequest visa = candidateRequest(11L, 501L, 10);
        RouteCandidateRequest mastercard = candidateRequest(11L, 501L, 10);
        mastercard.setCardBrand("MASTERCARD");
        request.setCandidates(List.of(visa, mastercard));

        RouteRuleResponse response = service.createRule(request);

        assertThat(response.getPaymentScopes()).extracting("paymentMethod", "cardBrand")
                .containsExactlyInAnyOrder(tuple("BANK_CARD", "VISA"), tuple("BANK_CARD", "MASTERCARD"));
        assertThat(response.getCandidates()).extracting("cardBrand")
                .containsExactlyInAnyOrder("VISA", "MASTERCARD");
    }

    /** 发布校验必须阻止 MID 被配置到其能力范围之外的支付方式。 */
    @Test
    void shouldRejectUnsupportedCandidatePaymentMethodOnPublish() {
        preparePublishFixture("GLOBAL", 1);
        fixture.mids.get(0).setPaymentMethodScope("PAYPAL");

        assertThatThrownBy(() -> service.publishRule(101L, actionRequest(0)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("MID_PAYMENT_METHOD_UNSUPPORTED");
    }

    /** 固定路由每个支付方式只能配置一个启用 MID，避免产生含糊的固定目标。 */
    @Test
    void shouldRejectMultipleCandidatesForFixedRoute() {
        RouteRuleSaveRequest request = standardRequest();
        request.setRuleType("FIXED");
        request.getStrategy().setStrategyType("FIXED");
        request.setCandidates(List.of(
                candidateRequest(11L, 501L, 10),
                candidateRequest(12L, 502L, 20)));

        assertThatThrownBy(() -> service.createRule(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("FIXED requires exactly one enabled MID");
    }

    /** 优先级路由按候选优先级返回确定顺序，不依赖请求数组原始顺序。 */
    @Test
    void shouldReturnPriorityCandidatesInConfiguredOrder() {
        fixture.channels.add(activeChannel(11L, 1));
        fixture.channels.add(activeChannel(12L, 1));
        fixture.mids.add(activeMid(501L, 11L));
        fixture.mids.add(activeMid(502L, 12L));
        RouteRuleSaveRequest request = standardRequest();
        request.setCandidates(List.of(
                candidateRequest(11L, 501L, 20),
                candidateRequest(12L, 502L, 10)));

        RouteRuleResponse response = service.createRule(request);

        assertThat(response.getCandidates()).extracting("midConfigId")
                .containsExactly(502L, 501L);
        assertThat(response.getCandidates()).extracting("candidatePriority")
                .containsExactly(10, 20);
    }

    /** 权重路由按支付方式独立校验，启用候选权重合计必须精确为 100.00。 */
    @Test
    void shouldRejectWeightTotalBelowOneHundred() {
        RouteRuleSaveRequest request = standardRequest();
        request.setRuleType("WEIGHT");
        request.getStrategy().setStrategyType("WEIGHT");
        RouteCandidateRequest first = candidateRequest(11L, 501L, 10);
        first.setCandidateWeight(new BigDecimal("50.00"));
        RouteCandidateRequest second = candidateRequest(12L, 502L, 20);
        second.setCandidateWeight(new BigDecimal("49.99"));
        request.setCandidates(List.of(first, second));

        assertThatThrownBy(() -> service.createRule(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("weights must total 100.00");
    }

    /** 历史成功 MID 路由只保存回看期限，不允许混入固定候选 MID。 */
    @Test
    void shouldCreateHistoryAffinityWithoutCandidates() {
        RouteRuleSaveRequest request = standardRequest();
        request.setRuleType("HISTORY_AFFINITY");
        request.getStrategy().setStrategyType("HISTORY_AFFINITY");
        request.getStrategy().setHistoryLookbackMonths(6);
        request.setPaymentScopes(List.of());
        request.setTransactionTypes(List.of());

        RouteRuleResponse response = service.createRule(request);

        assertThat(response.getPaymentMethods()).isEmpty();
        assertThat(response.getTransactionTypes()).isEmpty();
        assertThat(response.getCandidates()).isEmpty();
        assertThat(response.getStrategy().getHistoryLookbackMonths()).isEqualTo(6);
        assertThat(response.getUnavailablePolicy()).isEqualTo("FALLBACK");
    }

    /** 历史成功 MID 回看期限必须位于已批准的 1 至 24 个月范围内。 */
    @Test
    void shouldRejectInvalidHistoryLookback() {
        RouteRuleSaveRequest request = standardRequest();
        request.setRuleType("HISTORY_AFFINITY");
        request.getStrategy().setStrategyType("HISTORY_AFFINITY");
        request.getStrategy().setHistoryLookbackMonths(25);
        request.setPaymentScopes(List.of());
        request.setTransactionTypes(List.of());

        assertThatThrownBy(() -> service.createRule(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("historyLookbackMonths must be between 1 and 24");
    }

    /** 指定商户规则使用关系表保存完整商户集合，并对旧单值响应保持兼容。 */
    @Test
    void shouldCreateRouteForMultipleMerchantsWithoutDuplicates() {
        fixture.channels.add(activeChannel(11L, 1));
        fixture.mids.add(activeMid(501L, 11L));
        RouteRuleSaveRequest request = standardRequest();
        request.setScopeType("MERCHANT");
        request.setMerchantIds(List.of("M10001", "M10002", "M10001"));
        request.setCandidates(List.of(candidateRequest(11L, 501L, 10)));

        RouteRuleResponse response = service.createRule(request);

        assertThat(response.getMerchantIds()).containsExactly("M10001", "M10002");
        assertThat(response.getMerchantId()).isNull();
        assertThat(fixture.rule.getMerchantId()).isNull();
        assertThat(fixture.ruleMerchants).extracting("merchantId")
                .containsExactly("M10001", "M10002");
    }

    /** 商户冲突检查必须去重，并在编辑时排除当前规则，只返回其他已配置规则。 */
    @Test
    void shouldReturnMerchantConflictsExcludingCurrentRule() {
        PaymentRouteRuleDO currentRule = routeRule(101L, "DRAFT", "MERCHANT", 100);
        currentRule.setRuleName("当前编辑规则");
        PaymentRouteRuleDO existingRule = routeRule(102L, "ENABLED", "MERCHANT", 20);
        existingRule.setRuleCode("PRIORITY_EXISTING");
        existingRule.setRuleName("已有优先级路由");
        fixture.rules.add(currentRule);
        fixture.rules.add(existingRule);
        fixture.ruleMerchants.add(routeMerchant(101L, "M10001"));
        fixture.ruleMerchants.add(routeMerchant(102L, "M10001"));
        fixture.ruleMerchants.add(routeMerchant(102L, "M10002"));

        MerchantRouteConflictRequest request = new MerchantRouteConflictRequest();
        request.setMerchantIds(List.of("M10001", "M10002", "M10001"));
        request.setExcludeRuleId(101L);

        var conflicts = service.merchantConflicts(request);

        assertThat(conflicts).extracting("merchantId")
                .containsExactly("M10001", "M10002");
        assertThat(conflicts).allSatisfy(conflict -> {
            assertThat(conflict.getRules()).singleElement().satisfies(rule -> {
                assertThat(rule.getId()).isEqualTo(102L);
                assertThat(rule.getRuleCode()).isEqualTo("PRIORITY_EXISTING");
                assertThat(rule.getRuleName()).isEqualTo("已有优先级路由");
                assertThat(rule.getRulePriority()).isEqualTo(20);
                assertThat(rule.getRuleStatus()).isEqualTo("ENABLED");
            });
        });
    }

    /** 已发布规则必须先停用，不能绕过生命周期直接修改。 */
    @Test
    void shouldRejectEditingEnabledRule() {
        fixture.installRule(routeRule(101L, "ENABLED", "GLOBAL", 100));
        RouteRuleSaveRequest request = standardRequest();
        request.setVersion(0);

        assertThatThrownBy(() -> service.updateRule(101L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("Enabled route rule cannot be edited");
    }

    /** 客户端版本落后时必须拒绝覆盖较新的路由配置。 */
    @Test
    void shouldRejectStaleVersion() {
        PaymentRouteRuleDO rule = routeRule(101L, "DRAFT", "GLOBAL", 100);
        rule.setVersion(3);
        fixture.installRule(rule);
        RouteRuleSaveRequest request = standardRequest();
        request.setVersion(2);

        assertThatThrownBy(() -> service.updateRule(101L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("version is stale");
    }

    /** 编辑规则只更新业务配置，已生成的稳定规则编码必须保持不变。 */
    @Test
    void shouldKeepGeneratedRuleCodeWhenUpdatingRule() {
        PaymentRouteRuleDO rule = routeRule(101L, "DRAFT", "GLOBAL", 100);
        fixture.installRule(rule);
        RouteRuleSaveRequest request = standardRequest();
        request.setRuleName("更新后的优先级路由");
        request.setVersion(0);
        request.setCandidates(List.of(candidateRequest(11L, 501L, 10)));

        RouteRuleResponse response = service.updateRule(101L, request);

        assertThat(response.getRuleCode()).isEqualTo("PRIORITY_DEFAULT");
        assertThat(response.getRuleName()).isEqualTo("更新后的优先级路由");
    }

    /** 商户专属规则只能发布已与该商户有效绑定的候选 MID。 */
    @Test
    void shouldRejectMerchantPublishWithoutActiveMidBinding() {
        preparePublishFixture("MERCHANT", 1);

        assertThatThrownBy(() -> service.publishRule(101L, actionRequest(0)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("MERCHANT_MID_BINDING_MISSING");
    }

    /** 已停用或不支持收单的渠道不能成为可发布候选。 */
    @Test
    void shouldRejectInactiveChannelPublish() {
        preparePublishFixture("GLOBAL", 0);

        assertThatThrownBy(() -> service.publishRule(101L, actionRequest(0)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("CHANNEL_UNAVAILABLE");
    }

    /** 同一优先级的已发布规则不能覆盖相同商户和交易维度。 */
    @Test
    void shouldRejectOverlappingEnabledRuleAtSamePriority() {
        preparePublishFixture("GLOBAL", 1);
        PaymentRouteRuleDO existing = routeRule(102L, "ENABLED", "GLOBAL", 100);
        existing.setRuleCode("PRIORITY_EXISTING");
        fixture.rules.add(existing);

        assertThatThrownBy(() -> service.publishRule(101L, actionRequest(0)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("RULE_PRIORITY_CONFLICT");
    }

    /** 交易类型互不重叠时，同一优先级可以并存，避免把多选维度错误地退化为全量冲突。 */
    @Test
    void shouldAllowSamePriorityWhenTransactionTypesDoNotOverlap() {
        preparePublishFixture("GLOBAL", 1);
        fixture.transactionTypes.add(routeTransactionType(101L, "PAYMENT"));
        PaymentRouteRuleDO existing = routeRule(102L, "ENABLED", "GLOBAL", 100);
        existing.setRuleCode("PRIORITY_REFUND");
        fixture.rules.add(existing);
        fixture.methods.add(routeMethod(102L, "BANK_CARD"));
        fixture.transactionTypes.add(routeTransactionType(102L, "REFUND"));

        RouteRuleResponse response = service.publishRule(101L, actionRequest(0));

        assertThat(response.getRuleStatus()).isEqualTo("ENABLED");
    }

    /** 具体交易类型查询必须保留未限定交易类型的规则，因为空关系集合表示适用于全部类型。 */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldIncludeUnrestrictedRulesWhenFilteringByTransactionType() {
        fixture.transactionTypes.add(routeTransactionType(102L, "REFUND"));
        RouteRuleQuery query = new RouteRuleQuery();
        query.setTransactionTypes(List.of("PAYMENT"));

        service.pageRules(query);

        ArgumentCaptor<Wrapper<PaymentRouteRuleDO>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(ruleMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("NOT IN");
    }

    /** 校验通过后应执行带版本条件的更新，并记录发布前后版本。 */
    @Test
    void shouldPublishWithOptimisticVersionAndAuditLog() {
        preparePublishFixture("GLOBAL", 1);

        RouteRuleResponse response = service.publishRule(101L, actionRequest(0));

        assertThat(response.getRuleStatus()).isEqualTo("ENABLED");
        assertThat(response.getVersion()).isEqualTo(1);
        verify(ruleMapper).update(any(), any());
        ArgumentCaptor<PaymentRouteChangeLogDO> logCaptor = ArgumentCaptor.forClass(PaymentRouteChangeLogDO.class);
        verify(changeLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getActionType()).isEqualTo("PUBLISH");
        assertThat(logCaptor.getValue().getBeforeVersion()).isZero();
        assertThat(logCaptor.getValue().getAfterVersion()).isEqualTo(1);
    }

    /** 历史异常数据缺失策略时，复制应返回稳定业务错误而不是空指针。 */
    @Test
    void shouldRejectCopyWhenSourceStrategyIsMissing() {
        fixture.installRule(routeRule(101L, "DRAFT", "GLOBAL", 100));
        RouteCopyRequest request = new RouteCopyRequest();
        request.setRuleName("优先级路由副本");
        request.setVersion(0);
        request.setOperationReason("复制验证");

        assertThatThrownBy(() -> service.copyRule(101L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("strategy is missing");
    }

    /** 复制规则由服务端生成新编码，不能沿用源规则的稳定业务标识。 */
    @Test
    void shouldGenerateDifferentRuleCodeWhenCopyingRule() {
        fixture.installRule(routeRule(101L, "DRAFT", "GLOBAL", 100));
        fixture.strategy = priorityStrategy(101L);
        fixture.methods.add(routeMethod(101L, "BANK_CARD"));
        RouteCopyRequest request = new RouteCopyRequest();
        request.setRuleName("优先级路由副本");
        request.setVersion(0);
        request.setOperationReason("复制验证");

        RouteRuleResponse response = service.copyRule(101L, request);

        assertThat(response.getRuleCode())
                .matches("^ROUTE_\\d{6}_[A-F0-9]{32}$")
                .isNotEqualTo("PRIORITY_DEFAULT");
        assertThat(response.getRuleName()).isEqualTo("优先级路由副本");
    }

    private void preparePublishFixture(String scopeType, int channelStatus) {
        PaymentRouteRuleDO rule = routeRule(101L, "DRAFT", scopeType, 100);
        fixture.installRule(rule);
        fixture.strategy = priorityStrategy(101L);
        fixture.methods.add(routeMethod(101L, "BANK_CARD"));
        fixture.candidates.add(candidate(101L, 11L, 501L, 10));
        fixture.channels.add(activeChannel(11L, channelStatus));
        fixture.mids.add(activeMid(501L, 11L));
    }

    private RouteRuleSaveRequest standardRequest() {
        RouteRuleSaveRequest request = new RouteRuleSaveRequest();
        request.setRuleName("优先级收单路由");
        request.setRuleType("PRIORITY");
        request.setScopeType("GLOBAL");
        request.setBusinessType("ACQUIRING");
        request.setPaymentScopes(List.of(paymentScope("BANK_CARD", "VISA")));
        request.setTransactionTypes(List.of("PAYMENT", "AUTHORIZATION"));
        request.setRulePriority(100);
        request.setUnavailablePolicy("FALLBACK");
        RouteStrategyRequest strategy = new RouteStrategyRequest();
        strategy.setStrategyType("PRIORITY");
        strategy.setStrategyStatus(1);
        request.setStrategy(strategy);
        request.setCandidates(new ArrayList<>());
        request.setOperationReason("阶段 1A 配置验证");
        return request;
    }

    private com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteActionRequest actionRequest(int version) {
        com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteActionRequest request =
                new com.scott.payment.admin.dto.channel.PaymentRouteDTOs.RouteActionRequest();
        request.setVersion(version);
        request.setOperationReason("发布验证");
        return request;
    }

    private RouteCandidateRequest candidateRequest(long channelId, long midId, int priority) {
        RouteCandidateRequest request = new RouteCandidateRequest();
        request.setPaymentMethod("BANK_CARD");
        request.setCardBrand("VISA");
        request.setChannelId(channelId);
        request.setMidConfigId(midId);
        request.setCandidatePriority(priority);
        request.setCandidateStatus(1);
        return request;
    }

    private PaymentRouteRuleDO routeRule(long id, String status, String scopeType, int priority) {
        PaymentRouteRuleDO rule = new PaymentRouteRuleDO();
        rule.setId(id);
        rule.setRuleCode("PRIORITY_DEFAULT");
        rule.setRuleName("优先级收单路由");
        rule.setRuleType("PRIORITY");
        rule.setScopeType(scopeType);
        rule.setMerchantId("MERCHANT".equals(scopeType) ? "200001" : null);
        rule.setBusinessType("ACQUIRING");
        rule.setIssuerCountryScope("ALL");
        rule.setBillingCountryScope("ALL");
        rule.setRulePriority(priority);
        rule.setUnavailablePolicy("FALLBACK");
        rule.setRuleStatus(status);
        rule.setVersion(0);
        rule.setDeleted(0L);
        return rule;
    }

    private PaymentRouteStrategyDO priorityStrategy(long ruleId) {
        PaymentRouteStrategyDO strategy = new PaymentRouteStrategyDO();
        strategy.setId(201L);
        strategy.setRuleId(ruleId);
        strategy.setStrategyType("PRIORITY");
        strategy.setStrategyPriority(100);
        strategy.setStrategyStatus(1);
        strategy.setDeleted(0L);
        return strategy;
    }

    private PaymentRouteCandidateDO candidate(long ruleId, long channelId, long midId, int priority) {
        PaymentRouteCandidateDO candidate = new PaymentRouteCandidateDO();
        candidate.setId(301L);
        candidate.setRuleId(ruleId);
        candidate.setPaymentMethod("BANK_CARD");
        candidate.setCardBrand("VISA");
        candidate.setChannelId(channelId);
        candidate.setMidConfigId(midId);
        candidate.setCandidatePriority(priority);
        candidate.setCandidateStatus(1);
        candidate.setDeleted(0L);
        return candidate;
    }

    private PaymentRouteMethodDO routeMethod(long ruleId, String paymentMethod) {
        PaymentRouteMethodDO method = new PaymentRouteMethodDO();
        method.setId(251L);
        method.setRuleId(ruleId);
        method.setPaymentMethod(paymentMethod);
        method.setCardBrand("BANK_CARD".equals(paymentMethod) ? "VISA" : "NONE");
        method.setDeleted(0L);
        return method;
    }

    private PaymentRouteTransactionTypeDO routeTransactionType(long ruleId, String transactionType) {
        PaymentRouteTransactionTypeDO type = new PaymentRouteTransactionTypeDO();
        type.setId(271L + fixture.transactionTypes.size());
        type.setRuleId(ruleId);
        type.setTransactionType(transactionType);
        type.setDeleted(0L);
        return type;
    }

    private PaymentRouteRuleMerchantDO routeMerchant(long ruleId, String merchantId) {
        PaymentRouteRuleMerchantDO merchant = new PaymentRouteRuleMerchantDO();
        merchant.setId(225L + fixture.ruleMerchants.size() + 1L);
        merchant.setRuleId(ruleId);
        merchant.setMerchantId(merchantId);
        merchant.setDeleted(0L);
        return merchant;
    }

    private ChannelInfoDO activeChannel(long id, int status) {
        ChannelInfoDO channel = new ChannelInfoDO();
        channel.setId(id);
        channel.setChannelCode("CHANNEL_A");
        channel.setChannelCnName("测试渠道");
        channel.setChannelStatus(status);
        channel.setSupportAcquiring(1);
        channel.setDeleted(0L);
        return channel;
    }

    private ChannelMidConfigDO activeMid(long id, long channelId) {
        ChannelMidConfigDO mid = new ChannelMidConfigDO();
        mid.setId(id);
        mid.setChannelId(channelId);
        mid.setChannelCode("CHANNEL_A");
        mid.setChannelMid("MID-SECRET");
        mid.setMidName("收单 MID");
        mid.setBusinessType("ACQUIRING");
        mid.setPaymentMethodScope("BANK_CARD,PAYPAL");
        mid.setCardBrandScope("VISA,MASTERCARD");
        mid.setCurrencyScope("USD,EUR");
        mid.setMidStatus(1);
        mid.setEffectiveTime(LocalDateTime.now().minusDays(1));
        mid.setExpireTime(LocalDateTime.now().plusDays(1));
        mid.setMetadataValueJson("{\"privateKey\":\"PRIVATE-CREDENTIAL\"}");
        mid.setDeleted(0L);
        return mid;
    }

    private RoutePaymentScope paymentScope(String paymentMethod, String cardBrand) {
        RoutePaymentScope scope = new RoutePaymentScope();
        scope.setPaymentMethod(paymentMethod);
        scope.setCardBrand(cardBrand);
        return scope;
    }

    private static final class Fixture {
        private PaymentRouteRuleDO rule;
        private PaymentRouteStrategyDO strategy;
        private final List<PaymentRouteRuleDO> rules = new ArrayList<>();
        private final List<PaymentRouteMethodDO> methods = new ArrayList<>();
        private final List<PaymentRouteRuleMerchantDO> ruleMerchants = new ArrayList<>();
        private final List<PaymentRouteTransactionTypeDO> transactionTypes = new ArrayList<>();
        private final List<PaymentRouteCandidateDO> candidates = new ArrayList<>();
        private final List<ChannelInfoDO> channels = new ArrayList<>();
        private final List<ChannelMidConfigDO> mids = new ArrayList<>();
        private final List<MerchantChannelMidBindingDO> bindings = new ArrayList<>();

        private void installRule(PaymentRouteRuleDO value) {
            rule = value;
            rules.add(value);
        }
    }
}
