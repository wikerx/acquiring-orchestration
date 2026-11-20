package com.scott.payment.merchant.application.paymentlink;

import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.merchant.mapper.PaymentLinkQueryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 通过真实数据源拦截器验证所有链接查询入口在主库读取，并在返回后恢复调用方路由。 */
class MerchantPaymentLinkQueryDataSourceTest {
    @Test
    void everyQueryEntryReadsMasterAndRestoresCallerRoute() {
        var metadata = new PaymentLinkQueryMapper.DetailMetadata();
        metadata.setMerchantId("merchant-1"); metadata.setAllowedBrandsJson("[]");
        var view = new View(); view.setAmount("10.00"); view.setCurrencyExponent(2);
        var mapper = mock(PaymentLinkQueryMapper.class, invocation -> {
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo(DataSourceName.MASTER);
            return switch (invocation.getMethod().getName()) {
                case "detailMetadata" -> metadata;
                case "search" -> List.of(view);
                default -> RETURNS_DEFAULTS.answer(invocation);
            };
        });
        var currencies = mock(IsoDictionaryService.class, invocation -> {
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo(DataSourceName.MASTER);
            return RETURNS_DEFAULTS.answer(invocation);
        });
        var factory = new ProxyFactory(new MerchantPaymentLinkQueryService(mapper, currencies));
        factory.setProxyTargetClass(true);
        factory.addAdvice(new DynamicDataSourceAnnotationInterceptor(true, null));
        var queries = (MerchantPaymentLinkQueryService) factory.getProxy();
        var actor = new Actor(); actor.setAppCode("MERCHANT"); actor.setActorId("42");
        actor.setMerchantIds(Set.of("merchant-1"));
        DynamicDataSourceContextHolder.push("caller-replica");
        try {
            for (Runnable entry : List.<Runnable>of(
                    () -> queries.search(actor, new Query()),
                    () -> queries.detail(actor, "link-1"),
                    () -> queries.summary(actor, new Query()),
                    () -> queries.collections(actor, "link-1", new Query()),
                    () -> queries.attempts(actor, "link-1", new Query()),
                    () -> queries.events(actor, "link-1", new Query()),
                    () -> queries.filterCurrencies(actor))) {
                entry.run();
                assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("caller-replica");
            }
        } finally {
            DynamicDataSourceContextHolder.clear();
        }
    }
}
