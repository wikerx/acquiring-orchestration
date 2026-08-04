package com.scott.payment.data.service.impl;

import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.MerchantCallbackJwtSigner;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
import com.scott.payment.data.service.MerchantCallbackSecurityMaterialProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 验证商户回调请求工厂在真实 Spring 构造器解析规则下可以完成装配。 */
class MerchantCallbackRequestFactorySpringWiringTests {

    /** 多构造器类必须明确生产注入入口，避免 service-data 在启动阶段寻找默认构造器。 */
    @Test
    void shouldResolveProductionConstructorInSpringContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MerchantCallbackSecurityMaterialProvider.class,
                    () -> mock(MerchantCallbackSecurityMaterialProvider.class));
            context.registerBean(OpenApiPayloadCrypto.class, OpenApiPayloadCrypto::new);
            context.registerBean(MerchantCallbackJwtSigner.class, MerchantCallbackJwtSigner::new);
            context.registerBean(DataMerchantNotificationProperties.class, DataMerchantNotificationProperties::new);
            context.registerBean(MerchantCallbackRequestFactory.class);

            context.refresh();

            assertThat(context.getBean(MerchantCallbackRequestFactory.class)).isNotNull();
        }
    }

    /** 地址校验器的可测试解析器构造器不得干扰 Spring 选择生产构造器。 */
    @Test
    void shouldResolveTargetValidatorProductionConstructorInSpringContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DataMerchantNotificationProperties.class, DataMerchantNotificationProperties::new);
            context.registerBean(MerchantCallbackTargetValidator.class);

            context.refresh();

            assertThat(context.getBean(MerchantCallbackTargetValidator.class)).isNotNull();
        }
    }
}
