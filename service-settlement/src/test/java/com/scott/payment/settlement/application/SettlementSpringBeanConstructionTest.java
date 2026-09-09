package com.scott.payment.settlement.application;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

/** 验证保留测试构造器的结算服务仍可由 Spring 选择生产构造器创建。 */
class SettlementSpringBeanConstructionTest {

    @ParameterizedTest(name = "Spring should construct {0}")
    @MethodSource("servicesWithTestConstructors")
    void shouldConstructServiceWithProductionDependencies(Class<?> serviceType) {
        Constructor<?> productionConstructor = Arrays.stream(serviceType.getConstructors())
                .findFirst()
                .orElseThrow();
        assertThat(serviceType.getDeclaredConstructors()).hasSizeGreaterThan(1);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            Arrays.stream(productionConstructor.getParameterTypes())
                    .filter(parameterType -> !parameterType.isPrimitive())
                    .forEach(parameterType -> registerMock(context, parameterType));
            context.register(serviceType);

            assertThatCode(context::refresh).doesNotThrowAnyException();
        }
    }

    private static Stream<Class<?>> servicesWithTestConstructors() {
        return Stream.of(
                SettlementManualReviewApplicationService.class,
                SettlementManualReviewTransactionService.class,
                SettlementReviewDecisionApplicationService.class,
                SettlementReviewDecisionTransactionService.class);
    }

    private <T> void registerMock(AnnotationConfigApplicationContext context, Class<T> dependencyType) {
        context.registerBean(dependencyType, () -> mock(dependencyType));
    }
}
