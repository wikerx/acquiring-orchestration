package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO;
import com.scott.payment.payment.entity.PaymentCardBinRangeDO;
import com.scott.payment.payment.mapper.PaymentCardBinRangeMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
        MerchantRouteProfileCacheReader routeProfileReader = mock(MerchantRouteProfileCacheReader.class);
        PaymentCardBinRangeMapper cardBinRangeMapper = mock(PaymentCardBinRangeMapper.class);
        when(routeProfileReader.findCached("200045")).thenReturn(profile("VISA,MASTERCARD"));
        PaymentCheckoutCardCapabilityService service =
                new PaymentCheckoutCardCapabilityService(routeProfileReader, cardBinRangeMapper);

        List<PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO> methods =
                service.resolveAllowedMethods("200045", List.of(requestedMethod()));

        assertThat(methods).singleElement().satisfies(method -> {
            assertThat(method.getPaymentMethod()).isEqualTo("BANK_CARD");
            assertThat(method.getChannelCode()).isEqualTo("MPGS");
            assertThat(method.getBrands()).containsExactly("VISA", "MASTERCARD");
            assertThat(method.getThreeDsMode()).isEqualTo("OFF");
        });
    }

    /** BIN 基础表命中必须优先于公开前缀兜底，避免页面和服务端品牌结论不一致。 */
    @Test
    void shouldPreferDatabaseBinBrandOverPrefixFallback() {
        MerchantRouteProfileCacheReader routeProfileReader = mock(MerchantRouteProfileCacheReader.class);
        PaymentCardBinRangeMapper cardBinRangeMapper = mock(PaymentCardBinRangeMapper.class);
        PaymentCardBinRangeDO databaseMatch = new PaymentCardBinRangeDO();
        databaseMatch.setCardBrand("MASTERCARD");
        when(cardBinRangeMapper.selectBestMatch(41111100000L)).thenReturn(databaseMatch);
        PaymentCheckoutCardCapabilityService service =
                new PaymentCheckoutCardCapabilityService(routeProfileReader, cardBinRangeMapper);

        String brand = service.resolveCardBrand("411111");

        assertThat(brand).isEqualTo("MASTERCARD");
        verify(cardBinRangeMapper).selectBestMatch(41111100000L);
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
        option.setChannelCode("MPGS");
        profile.setRouteOptions(new ArrayList<>(List.of(option)));
        return profile;
    }
}
