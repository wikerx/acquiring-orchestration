package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.model.PaymentCardBinCacheEntry;
import com.scott.payment.payment.service.MerchantRouteProfileCacheService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutCardCapabilityServiceTests
 * @date : 2026-08-08 15:05
 * @email : scott_x@163.com
 * @description : 验证收银台支付方式只暴露商户 MID 实际能力，并由 BIN 基础表优先判定卡品牌。
 * @status : create
 */
class PaymentCheckoutCardCapabilityServiceTests {

    /** MID 仅支持 Visa 和 Mastercard 时，收银台不得展示商户请求中的其他品牌。 */
    @Test
    void shouldIntersectRequestedBrandsWithActiveMidScope() {
        MerchantRouteProfileCacheService routeProfileCacheService = mock(MerchantRouteProfileCacheService.class);
        PaymentCardBinCacheReader cardBinCacheReader = mock(PaymentCardBinCacheReader.class);
        when(routeProfileCacheService.findRouteProfile("200045")).thenReturn(profile("VISA,MASTERCARD"));
        PaymentCheckoutCardCapabilityService service =
                new PaymentCheckoutCardCapabilityService(routeProfileCacheService, cardBinCacheReader);

        List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> methods =
                service.resolveAllowedMethods("200045", List.of(requestedMethod()));

        assertThat(methods).singleElement().satisfies(method -> {
            assertThat(method.getPaymentMethod()).isEqualTo("BANK_CARD");
            assertThat(method.getChannelCode()).isEqualTo("MPGS");
            assertThat(method.getBrands()).containsExactly("VISA", "MASTERCARD");
            assertThat(method.getThreeDsMode()).isEqualTo("AUTO");
        });
    }

    /** BIN 基础表命中必须优先于公开前缀兜底，避免页面和服务端品牌结论不一致。 */
    @Test
    void shouldPreferDatabaseBinBrandOverPrefixFallback() {
        MerchantRouteProfileCacheService routeProfileCacheService = mock(MerchantRouteProfileCacheService.class);
        PaymentCardBinCacheReader cardBinCacheReader = mock(PaymentCardBinCacheReader.class);
        PaymentCardBinCacheEntry databaseMatch = new PaymentCardBinCacheEntry();
        databaseMatch.setCardBrand("MASTERCARD");
        when(cardBinCacheReader.findByPrefix("41111100000")).thenReturn(databaseMatch);
        PaymentCheckoutCardCapabilityService service =
                new PaymentCheckoutCardCapabilityService(routeProfileCacheService, cardBinCacheReader);

        String brand = service.resolveCardBrand("411111");

        assertThat(brand).isEqualTo("MASTERCARD");
    }

    /** BIN 基础表未命中时，使用平台卡品牌标准和公开 IIN 范围识别卡品牌。 */
    @Test
    void shouldResolvePlatformCardBrandsFromCardNumberPrefixWhenDatabaseMisses() {
        PaymentCardBinCacheReader cardBinCacheReader = mock(PaymentCardBinCacheReader.class);
        PaymentCheckoutCardCapabilityService service = new PaymentCheckoutCardCapabilityService(
                mock(MerchantRouteProfileCacheService.class), cardBinCacheReader);

        assertThat(service.resolveCardBrand("4111111111111111")).isEqualTo("VISA");
        assertThat(service.resolveCardBrand("5555555555554444")).isEqualTo("MASTERCARD");
        assertThat(service.resolveCardBrand("371449635398431")).isEqualTo("AMEX");
        assertThat(service.resolveCardBrand("3530111333300000")).isEqualTo("JCB");
        assertThat(service.resolveCardBrand("30569309025904")).isEqualTo("DINERS_CLUB");
        assertThat(service.resolveCardBrand("6011111111111117")).isEqualTo("DISCOVER");
        assertThat(service.resolveCardBrand("6212345678901234")).isEqualTo("UNIONPAY");
        assertThat(service.resolveCardBrand("6759649826438453")).isEqualTo("MAESTRO");
    }

    /** 商户不传 cardBrand 时，服务端必须在风控和路由前补齐卡品牌和 ISO Alpha-3 发卡国家。 */
    @Test
    void shouldEnrichInternalTransactionCardBrandFromCardNumber() {
        PaymentCardBinCacheReader cardBinCacheReader = mock(PaymentCardBinCacheReader.class);
        PaymentCardBinCacheEntry databaseMatch = new PaymentCardBinCacheEntry();
        databaseMatch.setCardBrand("MASTERCARD");
        databaseMatch.setIssuerCountryAlpha2("AE");
        databaseMatch.setIssuerCountryAlpha3("ARE");
        when(cardBinCacheReader.findByPrefix("51234567890")).thenReturn(databaseMatch);
        PaymentCheckoutCardCapabilityService service = new PaymentCheckoutCardCapabilityService(
                mock(MerchantRouteProfileCacheService.class), cardBinCacheReader);
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setPaymentMethod("BANK_CARD");
        PaymentCreateCommandDTO.CardInfoDTO cardInfoDTO = new PaymentCreateCommandDTO.CardInfoDTO();
        cardInfoDTO.setCardNo("5123456789010008");
        commandDTO.setCardInfo(cardInfoDTO);

        service.enrichCardBrand(commandDTO);

        assertThat(commandDTO.getTransactionInfo()).isNotNull();
        assertThat(commandDTO.getTransactionInfo().getCardBrand()).isEqualTo("MASTERCARD");
        assertThat(commandDTO.getTransactionInfo().getIssuerCountry()).isEqualTo("ARE");
    }

    /** BIN 数据缺少有效 Alpha-3 时不得降级保存两位代码，避免分析维度混用国家代码标准。 */
    @Test
    void shouldNotFallbackToAlpha2IssuerCountry() {
        PaymentCardBinCacheReader cardBinCacheReader = mock(PaymentCardBinCacheReader.class);
        PaymentCardBinCacheEntry databaseMatch = new PaymentCardBinCacheEntry();
        databaseMatch.setCardBrand("MASTERCARD");
        databaseMatch.setIssuerCountryAlpha2("AE");
        databaseMatch.setIssuerCountryAlpha3("AE");
        when(cardBinCacheReader.findByPrefix("51234500000")).thenReturn(databaseMatch);
        PaymentCheckoutCardCapabilityService service = new PaymentCheckoutCardCapabilityService(
                mock(MerchantRouteProfileCacheService.class), cardBinCacheReader);
        PaymentCreateCommandDTO commandDTO = new PaymentCreateCommandDTO();
        commandDTO.setPaymentMethod("BANK_CARD");
        PaymentCreateCommandDTO.CardInfoDTO cardInfoDTO = new PaymentCreateCommandDTO.CardInfoDTO();
        cardInfoDTO.setCardNo("5123450000000008");
        commandDTO.setCardInfo(cardInfoDTO);

        service.enrichCardBrand(commandDTO);

        assertThat(commandDTO.getTransactionInfo()).isNotNull();
        assertThat(commandDTO.getTransactionInfo().getIssuerCountry()).isNull();
    }

    /** 构造商户请求允许的银行卡品牌集合。 */
    private PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO requestedMethod() {
        PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO method =
                new PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO();
        method.setPaymentMethod("BANK_CARD");
        method.setChannelCode("MPGS");
        method.setBrands(List.of("VISA", "MASTERCARD", "AMEX"));
        return method;
    }

    /** 构造启用且具备银行卡支付能力的商户 MID 路由快照。 */
    private MerchantRouteProfile profile(String cardBrandScope) {
        MerchantRouteProfile profile = new MerchantRouteProfile();
        profile.setMerchantId("200045");
        profile.setBindingCount(1);
        MerchantRouteProfile.RouteOption option = new MerchantRouteProfile.RouteOption();
        option.setBindingStatus(1);
        option.setMidStatus(1);
        option.setChannelStatus(1);
        option.setSupportAcquiring(1);
        option.setCapabilityStatus(1);
        option.setBusinessType("ACQUIRING");
        option.setCapabilityPaymentMethod("BANK_CARD");
        option.setPaymentMethodScope("BANK_CARD");
        option.setCardBrandScope(cardBrandScope);
        option.setCapabilitySupportedCardBrands(new ArrayList<>(List.of("VISA", "MASTERCARD")));
        option.setCapabilitySupport3ds(1);
        option.setChannelCode("MPGS");
        profile.setRouteOptions(new ArrayList<>(List.of(option)));
        return profile;
    }
}
