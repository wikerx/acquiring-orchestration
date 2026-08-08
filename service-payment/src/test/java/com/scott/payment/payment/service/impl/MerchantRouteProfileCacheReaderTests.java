package com.scott.payment.payment.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.payment.entity.ChannelCapabilityCurrencyDO;
import com.scott.payment.payment.entity.ChannelInfoDO;
import com.scott.payment.payment.entity.ChannelMidConfigDO;
import com.scott.payment.payment.entity.ChannelPaymentCapabilityDO;
import com.scott.payment.payment.entity.MerchantChannelMidBindingDO;
import com.scott.payment.payment.mapper.PaymentChannelCapabilityCurrencyMapper;
import com.scott.payment.payment.mapper.PaymentChannelInfoMapper;
import com.scott.payment.payment.mapper.PaymentChannelMidConfigMapper;
import com.scott.payment.payment.mapper.PaymentChannelPaymentCapabilityMapper;
import com.scott.payment.payment.mapper.PaymentMerchantChannelMidBindingMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRouteProfileCacheReaderTests
 * @date : 2026-08-01 15:45
 * @email : scott_x@163.com
 * @description : 验证商户路由快照使用五次批量查询构建且不会携带渠道敏感元数据正文
 * @status : create
 */
class MerchantRouteProfileCacheReaderTests {

    /** 初始化 MyBatis-Plus Lambda 字段缓存。 */
    @BeforeEach
    void setUp() {
        initializeTableInfo(MerchantChannelMidBindingDO.class);
        initializeTableInfo(ChannelMidConfigDO.class);
        initializeTableInfo(ChannelInfoDO.class);
        initializeTableInfo(ChannelPaymentCapabilityDO.class);
        initializeTableInfo(ChannelCapabilityCurrencyDO.class);
    }

    /**
     * 验证路由聚合按 Mapper 各执行一次批量查询，并剔除 metadata_value_json。
     */
    @Test
    void shouldBuildNonSensitiveRouteProfileWithFiveBatchQueries() {
        PaymentMerchantChannelMidBindingMapper bindingMapper =
                mock(PaymentMerchantChannelMidBindingMapper.class);
        PaymentChannelMidConfigMapper midMapper = mock(PaymentChannelMidConfigMapper.class);
        PaymentChannelInfoMapper channelMapper = mock(PaymentChannelInfoMapper.class);
        PaymentChannelPaymentCapabilityMapper capabilityMapper =
                mock(PaymentChannelPaymentCapabilityMapper.class);
        PaymentChannelCapabilityCurrencyMapper currencyMapper =
                mock(PaymentChannelCapabilityCurrencyMapper.class);
        when(bindingMapper.selectList(any())).thenReturn(List.of(binding()));
        when(midMapper.selectList(any())).thenReturn(List.of(mid()));
        when(channelMapper.selectList(any())).thenReturn(List.of(channel()));
        when(capabilityMapper.selectList(any())).thenReturn(List.of(capability()));
        when(currencyMapper.selectList(any())).thenReturn(List.of(currency()));
        MerchantRouteProfileCacheReader reader = new MerchantRouteProfileCacheReader(
                bindingMapper,
                midMapper,
                channelMapper,
                capabilityMapper,
                currencyMapper
        );

        MerchantRouteProfile profile = reader.findFresh("200045");

        assertThat(profile.getMerchantId()).isEqualTo("200045");
        assertThat(profile.getBindingCount()).isEqualTo(1);
        assertThat(profile.getRouteOptions()).singleElement().satisfies(option -> {
            assertThat(option.getMidConfigId()).isEqualTo(10L);
            assertThat(option.getChannelCode()).isEqualTo("MPGS");
            assertThat(option.getCardBrandScope()).isEqualTo("VISA,MASTERCARD");
            assertThat(option.getSupportedCurrencies()).containsExactly("USD");
        });
        assertThat(List.of(MerchantRouteProfile.RouteOption.class.getDeclaredFields()))
                .extracting(Field::getName)
                .doesNotContain("metadataValueJson");
        verify(bindingMapper).selectList(any());
        verify(midMapper).selectList(any());
        verify(channelMapper).selectList(any());
        verify(capabilityMapper).selectList(any());
        verify(currencyMapper).selectList(any());
    }

    /** 构造启用的商户 MID 绑定。 */
    private MerchantChannelMidBindingDO binding() {
        MerchantChannelMidBindingDO row = new MerchantChannelMidBindingDO();
        row.setId(1L);
        row.setMerchantId("200045");
        row.setMidConfigId(10L);
        row.setBindingStatus(1);
        row.setDeleted(0L);
        return row;
    }

    /** 构造包含敏感元数据的 MID 数据库行，敏感字段不得复制到返回模型。 */
    private ChannelMidConfigDO mid() {
        ChannelMidConfigDO row = new ChannelMidConfigDO();
        row.setId(10L);
        row.setChannelId(20L);
        row.setChannelCode("MPGS");
        row.setChannelMid("MERCHANT-001");
        row.setBusinessType("ACQUIRING");
        row.setPaymentMethodScope("ALL");
        row.setCardBrandScope("VISA,MASTERCARD");
        row.setTransactionTypeScope("ALL");
        row.setCurrencyScope("USD");
        row.setAllowedCountryScope("ALL");
        row.setMetadataValueJson("{\"password\":\"must-not-enter-redis\"}");
        row.setMidStatus(1);
        row.setUpdateTime(LocalDateTime.of(2026, 8, 1, 15, 40));
        row.setDeleted(0L);
        return row;
    }

    /** 构造启用的收单渠道。 */
    private ChannelInfoDO channel() {
        ChannelInfoDO row = new ChannelInfoDO();
        row.setId(20L);
        row.setChannelCode("MPGS");
        row.setChannelStatus(1);
        row.setSupportAcquiring(1);
        row.setDefaultRequestUrl("https://example.test/api");
        row.setConnectTimeoutSeconds(3);
        row.setReadTimeoutSeconds(10);
        row.setDeleted(0L);
        return row;
    }

    /** 构造启用的银行卡支付能力。 */
    private ChannelPaymentCapabilityDO capability() {
        ChannelPaymentCapabilityDO row = new ChannelPaymentCapabilityDO();
        row.setId(30L);
        row.setChannelId(20L);
        row.setChannelCode("MPGS");
        row.setBusinessType("ACQUIRING");
        row.setPaymentMethod("BANK_CARD");
        row.setTransactionType("PAYMENT");
        row.setCapabilityStatus(1);
        row.setSortOrder(1);
        row.setDeleted(0L);
        return row;
    }

    /** 构造能力支持币种。 */
    private ChannelCapabilityCurrencyDO currency() {
        ChannelCapabilityCurrencyDO row = new ChannelCapabilityCurrencyDO();
        row.setId(40L);
        row.setCapabilityId(30L);
        row.setCurrencyCode("USD");
        row.setCurrencyStatus(1);
        row.setDeleted(0L);
        return row;
    }

    /** 为纯单元测试初始化实体列信息。 */
    private void initializeTableInfo(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName() + "." + entityType.getSimpleName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
