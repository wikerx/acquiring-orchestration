package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCardBrandDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCurrencyDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelMidConfigDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelPaymentCapabilityDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.MerchantChannelMidBindingDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleDO;
import com.scott.payment.admin.mapper.ChannelCapabilityCardBrandMapper;
import com.scott.payment.admin.mapper.ChannelCapabilityCurrencyMapper;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.admin.mapper.ChannelMidConfigMapper;
import com.scott.payment.admin.mapper.ChannelPaymentCapabilityMapper;
import com.scott.payment.admin.mapper.FeeRuleMapper;
import com.scott.payment.admin.mapper.MerchantChannelMidBindingMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 商户实际支付能力门禁测试，覆盖路由唯一性和费用规则覆盖。
 */
@ExtendWith(MockitoExtension.class)
class MerchantPaymentCapabilityReadinessServiceTest {

    @Mock
    private MerchantChannelMidBindingMapper bindingMapper;
    @Mock
    private ChannelMidConfigMapper midMapper;
    @Mock
    private ChannelInfoMapper channelMapper;
    @Mock
    private ChannelPaymentCapabilityMapper capabilityMapper;
    @Mock
    private ChannelCapabilityCurrencyMapper currencyMapper;
    @Mock
    private ChannelCapabilityCardBrandMapper cardBrandMapper;
    @Mock
    private FeeRuleMapper feeRuleMapper;

    private MerchantPaymentCapabilityReadinessService service;

    /** 初始化嵌套实体的 MyBatis 元数据和默认可用支付能力。 */
    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, MerchantChannelMidBindingDO.class);
        TableInfoHelper.initTableInfo(assistant, ChannelMidConfigDO.class);
        TableInfoHelper.initTableInfo(assistant, ChannelInfoDO.class);
        TableInfoHelper.initTableInfo(assistant, ChannelPaymentCapabilityDO.class);
        TableInfoHelper.initTableInfo(assistant, ChannelCapabilityCurrencyDO.class);
        TableInfoHelper.initTableInfo(assistant, ChannelCapabilityCardBrandDO.class);
        TableInfoHelper.initTableInfo(assistant, FeeRuleDO.class);
        service = new MerchantPaymentCapabilityReadinessService(
                bindingMapper, midMapper, channelMapper, capabilityMapper,
                currencyMapper, cardBrandMapper, feeRuleMapper);
        useSingleRoute();
        when(feeRuleMapper.selectList(any())).thenReturn(List.of(
                feeRule("TRANSACTION_FEE", "PAYMENT"),
                feeRule("REFUND_FEE", "REFUND")));
    }

    /** 完整的支付和退款能力均有唯一路由及费率时允许开通。 */
    @Test
    void shouldPassWhenRoutesAndFeesCoverEveryChargeableCapability() {
        var result = service.evaluate("MTEST0001", 8L, LocalDateTime.now());

        assertThat(result.issueCodes()).isEmpty();
        assertThat(result.capabilities()).extracting(
                MerchantPaymentCapabilityReadinessService.CapabilityKey::transactionType)
                .containsExactly("PAYMENT", "REFUND");
        assertThat(result.activeBindingCount()).isEqualTo(1);
    }

    /** 退款能力没有退款费规则时必须报告覆盖缺口。 */
    @Test
    void shouldRejectMissingFeeCoverage() {
        when(feeRuleMapper.selectList(any())).thenReturn(List.of(
                feeRule("TRANSACTION_FEE", "PAYMENT")));

        var result = service.evaluate("MTEST0001", 8L, LocalDateTime.now());

        assertThat(result.issueCodes()).containsExactly("FEE_CAPABILITY_NOT_COVERED");
        assertThat(result.feeGaps()).extracting(
                MerchantPaymentCapabilityReadinessService.CapabilityKey::transactionType)
                .containsExactly("REFUND");
    }

    /** 同一能力同时命中通用和精确费率时必须拒绝开通，避免清分费用规则出现歧义。 */
    @Test
    void shouldRejectAmbiguousFeeCoverage() {
        when(feeRuleMapper.selectList(any())).thenReturn(List.of(
                feeRule("TRANSACTION_FEE", "PAYMENT"),
                feeRule("TRANSACTION_FEE", "ALL"),
                feeRule("REFUND_FEE", "REFUND")));

        var result = service.evaluate("MTEST0001", 8L, LocalDateTime.now());

        assertThat(result.issueCodes()).containsExactly("FEE_CAPABILITY_AMBIGUOUS");
        assertThat(result.feeAmbiguities()).extracting(
                MerchantPaymentCapabilityReadinessService.CapabilityKey::transactionType)
                .containsExactly("PAYMENT");
    }

    /** 两个 MID 对同一能力使用相同最高优先级时必须与支付运行时一致地失败关闭。 */
    @Test
    void shouldRejectEqualPriorityRouteConflict() {
        MerchantChannelMidBindingDO secondBinding = binding(2L, 11L);
        when(bindingMapper.selectList(any())).thenReturn(List.of(binding(1L, 10L), secondBinding));
        ChannelMidConfigDO secondMid = mid(11L);
        when(midMapper.selectList(any())).thenReturn(List.of(mid(10L), secondMid));

        var result = service.evaluate("MTEST0001", 8L, LocalDateTime.now());

        assertThat(result.issueCodes()).containsExactly("CHANNEL_ROUTE_CONFLICT");
        assertThat(result.routeConflicts()).hasSize(2);
    }

    /** 生效绑定引用停用 MID 时既要标识不可用，也不能伪造任何支付能力。 */
    @Test
    void shouldRejectUnavailableMid() {
        ChannelMidConfigDO disabled = mid(10L);
        disabled.setMidStatus(0);
        when(midMapper.selectList(any())).thenReturn(List.of(disabled));

        var result = service.evaluate("MTEST0001", 8L, LocalDateTime.now());

        assertThat(result.issueCodes())
                .containsExactly("CHANNEL_MID_UNAVAILABLE", "CHANNEL_PAYMENT_CAPABILITY_EMPTY");
        assertThat(result.capabilities()).isEmpty();
    }

    /** 配置单条银行卡能力和对应的启用币种、卡品牌。 */
    private void useSingleRoute() {
        when(bindingMapper.selectList(any())).thenReturn(List.of(binding(1L, 10L)));
        when(midMapper.selectList(any())).thenReturn(List.of(mid(10L)));
        when(channelMapper.selectList(any())).thenReturn(List.of(channel()));
        when(capabilityMapper.selectList(any())).thenReturn(List.of(capability()));
        when(currencyMapper.selectList(any())).thenReturn(List.of(currency()));
        when(cardBrandMapper.selectList(any())).thenReturn(List.of(cardBrand()));
    }

    private MerchantChannelMidBindingDO binding(Long id, Long midId) {
        MerchantChannelMidBindingDO row = new MerchantChannelMidBindingDO();
        row.setId(id);
        row.setMerchantId("MTEST0001");
        row.setMidConfigId(midId);
        row.setBindingStatus(1);
        row.setDeleted(0L);
        return row;
    }

    private ChannelMidConfigDO mid(Long id) {
        ChannelMidConfigDO row = new ChannelMidConfigDO();
        row.setId(id);
        row.setChannelId(5L);
        row.setChannelCode("TEST_CHANNEL");
        row.setBusinessType("ACQUIRING");
        row.setPaymentMethodScope("BANK_CARD");
        row.setCardBrandScope("VISA");
        row.setTransactionTypeScope("PAYMENT,REFUND");
        row.setCurrencyScope("USD");
        row.setMidStatus(1);
        row.setDeleted(0L);
        return row;
    }

    private ChannelInfoDO channel() {
        ChannelInfoDO row = new ChannelInfoDO();
        row.setId(5L);
        row.setChannelCode("TEST_CHANNEL");
        row.setChannelStatus(1);
        row.setSupportAcquiring(1);
        row.setDeleted(0L);
        return row;
    }

    private ChannelPaymentCapabilityDO capability() {
        ChannelPaymentCapabilityDO row = new ChannelPaymentCapabilityDO();
        row.setId(16L);
        row.setChannelId(5L);
        row.setChannelCode("TEST_CHANNEL");
        row.setBusinessType("ACQUIRING");
        row.setPaymentMethod("BANK_CARD");
        row.setTransactionType("PAYMENT,REFUND");
        row.setSupportIncrementalAuthorization(0);
        row.setCapabilityStatus(1);
        row.setSortOrder(10);
        row.setDeleted(0L);
        return row;
    }

    private ChannelCapabilityCurrencyDO currency() {
        ChannelCapabilityCurrencyDO row = new ChannelCapabilityCurrencyDO();
        row.setCapabilityId(16L);
        row.setCurrencyCode("USD");
        row.setCurrencyStatus(1);
        row.setDeleted(0L);
        return row;
    }

    private ChannelCapabilityCardBrandDO cardBrand() {
        ChannelCapabilityCardBrandDO row = new ChannelCapabilityCardBrandDO();
        row.setCapabilityId(16L);
        row.setCardBrand("VISA");
        row.setBrandStatus(1);
        row.setDeleted(0L);
        return row;
    }

    private FeeRuleDO feeRule(String category, String transactionType) {
        FeeRuleDO row = new FeeRuleDO();
        row.setPlanVersionId(8L);
        row.setFeeCategory(category);
        row.setTransactionType(transactionType);
        row.setPaymentType("BANK_CARD");
        row.setPaymentMethod("VISA");
        row.setRiskServiceType("NONE");
        row.setDeleted(0L);
        return row;
    }
}
