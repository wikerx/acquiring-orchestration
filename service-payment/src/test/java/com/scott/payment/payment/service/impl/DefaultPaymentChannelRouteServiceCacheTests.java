package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.component.db.route.model.MerchantRouteProfile.RouteOption;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.entity.ChannelMidConfigDO;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        assertThat(result.isThreeDsSupported()).isTrue();
        assertThat(result.getMetadataValues()).containsEntry("merchantPassword", "local-only");
        verify(profileCache).findRouteProfile("200045");
        verify(metadataCache).getMetadataJson(10L, modifiedTime);
        verifyNoInteractions(midMapper, channelMapper);
    }

    @Test
    void shouldUseConfiguredDefaultCurrencyForEdcRoute() {
        PaymentChannelMidConfigMapper midMapper = mock(PaymentChannelMidConfigMapper.class);
        PaymentChannelInfoMapper channelMapper = mock(PaymentChannelInfoMapper.class);
        MerchantRouteProfileCacheService profileCache = mock(MerchantRouteProfileCacheService.class);
        PaymentChannelMidMetadataCache metadataCache = mock(PaymentChannelMidMetadataCache.class);
        MerchantRouteProfile profile = profile(LocalDateTime.of(2026, 8, 1, 15, 40));
        RouteOption option = profile.getRouteOptions().get(0);
        option.setSupportedCurrencies(new ArrayList<>(List.of("EUR", "USD")));
        option.setDefaultTransactionCurrency("USD");
        when(profileCache.findRouteProfile("200045")).thenReturn(profile);
        when(metadataCache.getMetadataJson(10L, option.getMidModifiedTime())).thenReturn("{}");
        PaymentCreateCommandDTO command = command();
        command.setCurrency("CNY");
        DefaultPaymentChannelRouteService service = new DefaultPaymentChannelRouteService(
                midMapper, channelMapper, profileCache, metadataCache);

        PaymentRouteResultDTO result = service.route(command);

        assertThat(result.getRequestedCurrency()).isEqualTo("CNY");
        assertThat(result.getRoutedCurrency()).isEqualTo("USD");
        assertThat(result.isEdcRequired()).isTrue();
    }

    @Test
    void shouldRouteDirectlyWhenRequestedCurrencyIsSupported() {
        PaymentChannelMidConfigMapper midMapper = mock(PaymentChannelMidConfigMapper.class);
        PaymentChannelInfoMapper channelMapper = mock(PaymentChannelInfoMapper.class);
        MerchantRouteProfileCacheService profileCache = mock(MerchantRouteProfileCacheService.class);
        PaymentChannelMidMetadataCache metadataCache = mock(PaymentChannelMidMetadataCache.class);
        MerchantRouteProfile profile = profile(LocalDateTime.of(2026, 8, 1, 15, 40));
        RouteOption option = profile.getRouteOptions().get(0);
        option.setSupportedCurrencies(new ArrayList<>(List.of("EUR", "USD")));
        option.setCurrencyScope("EUR,USD");
        option.setDefaultTransactionCurrency("USD");
        when(profileCache.findRouteProfile("200045")).thenReturn(profile);
        when(metadataCache.getMetadataJson(10L, option.getMidModifiedTime())).thenReturn("{}");
        PaymentCreateCommandDTO command = command();
        command.setCurrency("EUR");
        DefaultPaymentChannelRouteService service = new DefaultPaymentChannelRouteService(
                midMapper, channelMapper, profileCache, metadataCache);

        PaymentRouteResultDTO result = service.route(command);

        assertThat(result.getRequestedCurrency()).isEqualTo("EUR");
        assertThat(result.getRoutedCurrency()).isEqualTo("EUR");
        assertThat(result.isEdcRequired()).isFalse();
    }

    /** 原币直连必须优先于排序值更小的 EDC 候选，避免不必要的动态换汇。 */
    @Test
    void shouldPreferDirectCurrencyCandidateOverEdcCandidate() {
        PaymentChannelMidConfigMapper midMapper = mock(PaymentChannelMidConfigMapper.class);
        PaymentChannelInfoMapper channelMapper = mock(PaymentChannelInfoMapper.class);
        MerchantRouteProfileCacheService profileCache = mock(MerchantRouteProfileCacheService.class);
        PaymentChannelMidMetadataCache metadataCache = mock(PaymentChannelMidMetadataCache.class);
        LocalDateTime modifiedTime = LocalDateTime.of(2026, 8, 1, 15, 40);
        MerchantRouteProfile profile = profile(modifiedTime);
        RouteOption direct = profile.getRouteOptions().get(0);
        direct.setCurrencyScope("JPY");
        direct.setSupportedCurrencies(new ArrayList<>(List.of("JPY")));
        direct.setDefaultTransactionCurrency("JPY");
        direct.setCapabilitySortOrder(20);
        RouteOption edc = routeOption(modifiedTime, 11L, 21L, 31L, "SCOTT_CHANNEL_A", 1);
        edc.setCurrencyScope("ALL");
        edc.setSupportedCurrencies(new ArrayList<>(List.of("USD")));
        edc.setDefaultTransactionCurrency("USD");
        profile.setRouteOptions(new ArrayList<>(List.of(edc, direct)));
        when(profileCache.findRouteProfile("200045")).thenReturn(profile);
        when(metadataCache.getMetadataJson(10L, modifiedTime)).thenReturn("{}");
        PaymentCreateCommandDTO command = command();
        command.setCurrency("JPY");
        DefaultPaymentChannelRouteService service = new DefaultPaymentChannelRouteService(
                midMapper, channelMapper, profileCache, metadataCache);

        PaymentRouteResultDTO result = service.route(command);

        assertThat(result.getChannelCode()).isEqualTo("MPGS");
        assertThat(result.getRoutedCurrency()).isEqualTo("JPY");
        assertThat(result.isEdcRequired()).isFalse();
    }

    /** 两个原币候选同时可用时，能力配置中更小的排序值必须稳定胜出。 */
    @Test
    void shouldPreferLowerCapabilitySortOrderForDirectCandidates() {
        PaymentChannelMidConfigMapper midMapper = mock(PaymentChannelMidConfigMapper.class);
        PaymentChannelInfoMapper channelMapper = mock(PaymentChannelInfoMapper.class);
        MerchantRouteProfileCacheService profileCache = mock(MerchantRouteProfileCacheService.class);
        PaymentChannelMidMetadataCache metadataCache = mock(PaymentChannelMidMetadataCache.class);
        LocalDateTime modifiedTime = LocalDateTime.of(2026, 8, 1, 15, 40);
        MerchantRouteProfile profile = profile(modifiedTime);
        RouteOption lowerPriority = profile.getRouteOptions().get(0);
        lowerPriority.setCapabilitySortOrder(20);
        RouteOption preferred = routeOption(modifiedTime, 11L, 21L, 31L, "WORLDPAY", 10);
        profile.setRouteOptions(new ArrayList<>(List.of(lowerPriority, preferred)));
        when(profileCache.findRouteProfile("200045")).thenReturn(profile);
        when(metadataCache.getMetadataJson(11L, modifiedTime)).thenReturn("{}");
        DefaultPaymentChannelRouteService service = new DefaultPaymentChannelRouteService(
                midMapper, channelMapper, profileCache, metadataCache);

        PaymentRouteResultDTO result = service.route(command());

        assertThat(result.getChannelCode()).isEqualTo("WORLDPAY");
        assertThat(result.getMidConfigId()).isEqualTo(11L);
    }

    /** 最高优先级完全相同仍必须拒绝，禁止按缓存列表先后随机选资金通道。 */
    @Test
    void shouldRejectCandidatesWithSameHighestPriority() {
        PaymentChannelMidConfigMapper midMapper = mock(PaymentChannelMidConfigMapper.class);
        PaymentChannelInfoMapper channelMapper = mock(PaymentChannelInfoMapper.class);
        MerchantRouteProfileCacheService profileCache = mock(MerchantRouteProfileCacheService.class);
        PaymentChannelMidMetadataCache metadataCache = mock(PaymentChannelMidMetadataCache.class);
        LocalDateTime modifiedTime = LocalDateTime.of(2026, 8, 1, 15, 40);
        MerchantRouteProfile profile = profile(modifiedTime);
        RouteOption first = profile.getRouteOptions().get(0);
        RouteOption second = routeOption(modifiedTime, 11L, 21L, 31L, "WORLDPAY", 1);
        profile.setRouteOptions(new ArrayList<>(List.of(first, second)));
        when(profileCache.findRouteProfile("200045")).thenReturn(profile);
        DefaultPaymentChannelRouteService service = new DefaultPaymentChannelRouteService(
                midMapper, channelMapper, profileCache, metadataCache);

        assertThatThrownBy(() -> service.route(command()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid request parameter");
        verifyNoInteractions(metadataCache, midMapper, channelMapper);
    }

    @Test
    void shouldRejectEdcRouteWhenDefaultCurrencyIsOutsideMidIntersection() {
        PaymentChannelMidConfigMapper midMapper = mock(PaymentChannelMidConfigMapper.class);
        PaymentChannelInfoMapper channelMapper = mock(PaymentChannelInfoMapper.class);
        MerchantRouteProfileCacheService profileCache = mock(MerchantRouteProfileCacheService.class);
        PaymentChannelMidMetadataCache metadataCache = mock(PaymentChannelMidMetadataCache.class);
        MerchantRouteProfile profile = profile(LocalDateTime.of(2026, 8, 1, 15, 40));
        RouteOption option = profile.getRouteOptions().get(0);
        option.setSupportedCurrencies(new ArrayList<>(List.of("EUR")));
        option.setDefaultTransactionCurrency("USD");
        when(profileCache.findRouteProfile("200045")).thenReturn(profile);
        PaymentCreateCommandDTO command = command();
        command.setCurrency("CNY");
        DefaultPaymentChannelRouteService service = new DefaultPaymentChannelRouteService(
                midMapper, channelMapper, profileCache, metadataCache);

        assertThatThrownBy(() -> service.route(command))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid request parameter");
        verifyNoInteractions(metadataCache, midMapper, channelMapper);
    }

    /** 路由层必须再次校验卡品牌，不能选择仅支持 Visa/Mastercard 的 MID 处理 AMEX。 */
    @Test
    void shouldRejectRouteWhenMidDoesNotSupportCardBrand() {
        PaymentChannelMidConfigMapper midMapper = mock(PaymentChannelMidConfigMapper.class);
        PaymentChannelInfoMapper channelMapper = mock(PaymentChannelInfoMapper.class);
        MerchantRouteProfileCacheService profileCache = mock(MerchantRouteProfileCacheService.class);
        PaymentChannelMidMetadataCache metadataCache = mock(PaymentChannelMidMetadataCache.class);
        when(profileCache.findRouteProfile("200045"))
                .thenReturn(profile(LocalDateTime.of(2026, 8, 1, 15, 40)));
        DefaultPaymentChannelRouteService service = new DefaultPaymentChannelRouteService(
                midMapper, channelMapper, profileCache, metadataCache);
        PaymentCreateCommandDTO command = command();
        command.getTransactionInfo().setCardBrand("AMEX");

        assertThatThrownBy(() -> service.route(command))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid request parameter");
        verifyNoInteractions(metadataCache, midMapper, channelMapper);
    }

    /** 路由必须同时满足 MID 范围和渠道支付能力卡品牌，不能只校验 MID。 */
    @Test
    void shouldRejectRouteWhenCapabilityDoesNotSupportCardBrand() {
        PaymentChannelMidConfigMapper midMapper = mock(PaymentChannelMidConfigMapper.class);
        PaymentChannelInfoMapper channelMapper = mock(PaymentChannelInfoMapper.class);
        MerchantRouteProfileCacheService profileCache = mock(MerchantRouteProfileCacheService.class);
        PaymentChannelMidMetadataCache metadataCache = mock(PaymentChannelMidMetadataCache.class);
        MerchantRouteProfile profile = profile(LocalDateTime.of(2026, 8, 1, 15, 40));
        profile.getRouteOptions().get(0).setCapabilitySupportedCardBrands(
                new ArrayList<>(List.of("VISA"))
        );
        when(profileCache.findRouteProfile("200045")).thenReturn(profile);
        DefaultPaymentChannelRouteService service = new DefaultPaymentChannelRouteService(
                midMapper, channelMapper, profileCache, metadataCache);
        PaymentCreateCommandDTO command = command();
        command.getTransactionInfo().setCardBrand("MASTERCARD");

        assertThatThrownBy(() -> service.route(command))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid request parameter");
        verifyNoInteractions(metadataCache, midMapper, channelMapper);
    }

    /** 增量授权必须命中能力开关，不能只依赖交易类型文本范围。 */
    @Test
    void shouldRejectIncrementalAuthorizationWhenCapabilitySwitchIsDisabled() {
        PaymentChannelMidConfigMapper midMapper = mock(PaymentChannelMidConfigMapper.class);
        PaymentChannelInfoMapper channelMapper = mock(PaymentChannelInfoMapper.class);
        MerchantRouteProfileCacheService profileCache = mock(MerchantRouteProfileCacheService.class);
        PaymentChannelMidMetadataCache metadataCache = mock(PaymentChannelMidMetadataCache.class);
        MerchantRouteProfile profile = profile(LocalDateTime.of(2026, 8, 1, 15, 40));
        RouteOption option = profile.getRouteOptions().get(0);
        option.setCapabilityTransactionType("PAYMENT,INCREMENTAL_AUTHORIZATION");
        option.setCapabilitySupportIncrementalAuthorization(0);
        when(profileCache.findRouteProfile("200045")).thenReturn(profile);
        DefaultPaymentChannelRouteService service = new DefaultPaymentChannelRouteService(
                midMapper, channelMapper, profileCache, metadataCache);
        PaymentCreateCommandDTO command = command();
        command.setTransactionType("INCREMENTAL_AUTHORIZATION");

        assertThatThrownBy(() -> service.route(command))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid request parameter");
        verifyNoInteractions(metadataCache, midMapper, channelMapper);
    }

    /** 关联交易不得使用已禁用的原 MID，也不得向商户暴露具体配置原因。 */
    @Test
    void shouldRejectRestoredRouteWithF414WhenOriginalMidIsDisabled() {
        PaymentChannelMidConfigMapper midMapper = mock(PaymentChannelMidConfigMapper.class);
        PaymentChannelInfoMapper channelMapper = mock(PaymentChannelInfoMapper.class);
        MerchantRouteProfileCacheService profileCache = mock(MerchantRouteProfileCacheService.class);
        PaymentChannelMidMetadataCache metadataCache = mock(PaymentChannelMidMetadataCache.class);
        ChannelMidConfigDO midConfig = new ChannelMidConfigDO();
        midConfig.setId(10L);
        midConfig.setChannelId(20L);
        midConfig.setChannelCode("MPGS");
        midConfig.setChannelMid("ORIGINAL-MID");
        midConfig.setMidStatus(0);
        midConfig.setDeleted(0L);
        when(midMapper.selectById(10L)).thenReturn(midConfig);
        DefaultPaymentChannelRouteService service = new DefaultPaymentChannelRouteService(
                midMapper, channelMapper, profileCache, metadataCache);

        assertThatThrownBy(() -> service.restore("MPGS", 20L, 10L, "ORIGINAL-MID"))
                .isInstanceOfSatisfying(ServiceException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("F414");
                    assertThat(exception.getMessage()).isEqualTo("Original transaction rejected.");
                });
        verifyNoInteractions(channelMapper, metadataCache);
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
        PaymentCreateCommandDTO.TransactionInfoDTO transactionInfo = new PaymentCreateCommandDTO.TransactionInfoDTO();
        transactionInfo.setCardBrand("VISA");
        command.setTransactionInfo(transactionInfo);
        return command;
    }

    /** 构造仅包含非敏感路由字段的永久快照。 */
    private MerchantRouteProfile profile(LocalDateTime modifiedTime) {
        MerchantRouteProfile profile = new MerchantRouteProfile();
        profile.setMerchantId("200045");
        profile.setBindingCount(1);
        RouteOption option = routeOption(modifiedTime, 10L, 20L, 30L, "MPGS", 1);
        profile.setRouteOptions(new ArrayList<>(List.of(option)));
        return profile;
    }

    /** 构造指定身份和排序值的非敏感路由候选。 */
    private RouteOption routeOption(LocalDateTime modifiedTime,
                                    Long midConfigId,
                                    Long channelId,
                                    Long capabilityId,
                                    String channelCode,
                                    int sortOrder) {
        RouteOption option = new RouteOption();
        option.setBindingId(1L);
        option.setBindingStatus(1);
        option.setMidConfigId(midConfigId);
        option.setChannelMid("MERCHANT-001");
        option.setBusinessType("ACQUIRING");
        option.setPaymentMethodScope("ALL");
        option.setCardBrandScope("VISA,MASTERCARD");
        option.setTransactionTypeScope("ALL");
        option.setCurrencyScope("USD");
        option.setAllowedCountryScope("ALL");
        option.setMidStatus(1);
        option.setMidModifiedTime(modifiedTime);
        option.setChannelId(channelId);
        option.setChannelCode(channelCode);
        option.setChannelStatus(1);
        option.setSupportAcquiring(1);
        option.setRequestUrl("https://example.test/api");
        option.setConnectTimeoutSeconds(3);
        option.setReadTimeoutSeconds(10);
        option.setCapabilityId(capabilityId);
        option.setCapabilityBusinessType("ACQUIRING");
        option.setCapabilityPaymentMethod("BANK_CARD");
        option.setCapabilityTransactionType("PAYMENT");
        option.setCapabilitySupportedCardBrands(new ArrayList<>(List.of("VISA", "MASTERCARD")));
        option.setCapabilitySupport3ds(1);
        option.setCapabilityStatus(1);
        option.setCapabilitySortOrder(sortOrder);
        option.setDefaultTransactionCurrency("USD");
        option.setSupportedCurrencies(new ArrayList<>(List.of("USD")));
        return option;
    }
}
