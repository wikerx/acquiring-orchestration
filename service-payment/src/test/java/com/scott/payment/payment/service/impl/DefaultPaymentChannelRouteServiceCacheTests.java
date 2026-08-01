package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.component.db.route.model.MerchantRouteProfile.RouteOption;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.mapper.PaymentChannelInfoMapper;
import com.scott.payment.payment.mapper.PaymentChannelMidConfigMapper;
import com.scott.payment.payment.service.MerchantRouteProfileCacheService;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultPaymentChannelRouteServiceCacheTests
 * @date : 2026-08-01 15:50
 * @email : scott_x@163.com
 * @description : 验证支付选路直接使用 merchant:route 快照并从本地短时缓存获取渠道敏感元数据
 * @status : create
 */
class DefaultPaymentChannelRouteServiceCacheTests {

    /**
     * 验证正常选路不再逐表查询绑定、MID、渠道、能力和币种。
     */
    @Test
    void shouldRouteFromPersistentProfileWithoutRouteMapperQueries() {
        PaymentChannelMidConfigMapper midMapper = mock(PaymentChannelMidConfigMapper.class);
        PaymentChannelInfoMapper channelMapper = mock(PaymentChannelInfoMapper.class);
        MerchantRouteProfileCacheService profileCache = mock(MerchantRouteProfileCacheService.class);
        PaymentChannelMidMetadataCache metadataCache = mock(PaymentChannelMidMetadataCache.class);
        LocalDateTime modifiedTime = LocalDateTime.of(2026, 8, 1, 15, 40);
        when(profileCache.findRouteProfile("200045")).thenReturn(profile(modifiedTime));
        when(metadataCache.getMetadataJson(10L, modifiedTime))
                .thenReturn("{\"merchantPassword\":\"local-only\"}");
        DefaultPaymentChannelRouteService service = new DefaultPaymentChannelRouteService(
                midMapper,
                channelMapper,
                profileCache,
                metadataCache
        );

        PaymentRouteResultDTO result = service.route(command());

        assertThat(result.getChannelCode()).isEqualTo("MPGS");
        assertThat(result.getMidConfigId()).isEqualTo(10L);
        assertThat(result.getRoutedCurrency()).isEqualTo("USD");
        assertThat(result.getMetadataValues()).containsEntry("merchantPassword", "local-only");
        verify(profileCache).findRouteProfile("200045");
        verify(metadataCache).getMetadataJson(10L, modifiedTime);
        verifyNoInteractions(midMapper, channelMapper);
    }

    /** 构造交易命令。 */
    private PaymentCreateCommandDTO command() {
        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setMerchantId("200045");
        command.setMerchantOrderNo("ORDER-001");
        command.setTransactionId("TXN-001");
        command.setTransactionType("PAYMENT");
        command.setPaymentMethod("BANK_CARD");
        command.setCurrency("USD");
        command.setAmount(new BigDecimal("10.00"));
        return command;
    }

    /** 构造仅包含非敏感路由字段的永久快照。 */
    private MerchantRouteProfile profile(LocalDateTime modifiedTime) {
        MerchantRouteProfile profile = new MerchantRouteProfile();
        profile.setMerchantId("200045");
        profile.setBindingCount(1);
        RouteOption option = new RouteOption();
        option.setBindingId(1L);
        option.setBindingStatus(1);
        option.setMidConfigId(10L);
        option.setChannelMid("MERCHANT-001");
        option.setBusinessType("ACQUIRING");
        option.setPaymentMethodScope("ALL");
        option.setTransactionTypeScope("ALL");
        option.setCurrencyScope("USD");
        option.setAllowedCountryScope("ALL");
        option.setMidStatus(1);
        option.setMidModifiedTime(modifiedTime);
        option.setChannelId(20L);
        option.setChannelCode("MPGS");
        option.setChannelStatus(1);
        option.setSupportAcquiring(1);
        option.setRequestUrl("https://example.test/api");
        option.setConnectTimeoutSeconds(3);
        option.setReadTimeoutSeconds(10);
        option.setCapabilityId(30L);
        option.setCapabilityBusinessType("ACQUIRING");
        option.setCapabilityPaymentMethod("BANK_CARD");
        option.setCapabilityTransactionType("PAYMENT");
        option.setCapabilityStatus(1);
        option.setCapabilitySortOrder(1);
        option.setSupportedCurrencies(new ArrayList<>(List.of("USD")));
        profile.setRouteOptions(new ArrayList<>(List.of(option)));
        return profile;
    }
}
