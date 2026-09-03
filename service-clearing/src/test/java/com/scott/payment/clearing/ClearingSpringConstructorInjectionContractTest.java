package com.scott.payment.clearing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.clearing.config.ClearingFinanceCoreConfiguration;
import com.scott.payment.clearing.mapper.ClearingTransactionEventOutboxMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionMerchantSnapshotMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.ClearingAnomalyService;
import com.scott.payment.clearing.service.ClearingCompletionService;
import com.scott.payment.clearing.service.ClearingPreparationService;
import com.scott.payment.clearing.service.ClearingProjectionService;
import com.scott.payment.clearing.service.ClearingRecalculationService;
import com.scott.payment.clearing.service.FeeConfigurationSnapshotService;
import com.scott.payment.clearing.service.FeeVersionQueryService;
import com.scott.payment.clearing.service.TierPeriodReplayService;
import com.scott.payment.clearing.service.impl.DefaultClearingManagementCommandService;
import com.scott.payment.clearing.service.impl.DefaultClearingRecalculationService;
import com.scott.payment.clearing.service.impl.DefaultFeeConfigurationSnapshotService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.clearing.support.TierPeriodReplayScheduler;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.finance.reserve.core.ReserveCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingSpringConstructorInjectionContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Spring 生产构造器选择契约，防止测试辅助构造器破坏服务启动。
 * @status : create
 */
class ClearingSpringConstructorInjectionContractTest {

    @Test
    void shouldConstructClearingManagementCommandService() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            registerMock(context, ClearingTransactionFinanceStateMapper.class);
            registerMock(context, ClearingTransactionOperationMapper.class);
            registerMock(context, ClearingTransactionEventOutboxMapper.class);
            registerMock(context, ClearingProjectionService.class);
            registerMock(context, ClearingRecalculationService.class);
            registerMock(context, ClearingOperationalMetrics.class);
            registerMock(context, ClearingAnomalyService.class);
            context.registerBean(DefaultClearingManagementCommandService.class);
            context.refresh();

            assertThat(context.getBean(DefaultClearingManagementCommandService.class)).isNotNull();
        }
    }

    @Test
    void shouldConstructFeeConfigurationSnapshotService() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            registerMock(context, ClearingTransactionMerchantSnapshotMapper.class);
            registerMock(context, FeeVersionQueryService.class);
            registerMock(context, StringRedisTemplate.class);
            registerMock(context, PaymentRedisKeyResolver.class);
            registerMock(context, ClearingOperationalMetrics.class);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(DefaultFeeConfigurationSnapshotService.class);
            context.refresh();

            assertThat(context.getBean(DefaultFeeConfigurationSnapshotService.class)).isNotNull();
        }
    }

    @Test
    void shouldConstructClearingRecalculationService() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            registerMock(context, ClearingTransactionFinanceStateMapper.class);
            registerMock(context, ClearingTransactionOperationMapper.class);
            registerMock(context, ClearingTransactionMerchantSnapshotMapper.class);
            registerMock(context, FeeConfigurationSnapshotService.class);
            registerMock(context, ClearingPreparationService.class);
            registerMock(context, ClearingCompletionService.class);
            context.registerBean(DefaultClearingRecalculationService.class);
            context.refresh();

            assertThat(context.getBean(DefaultClearingRecalculationService.class)).isNotNull();
        }
    }

    @Test
    void shouldConstructTierPeriodReplayScheduler() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            registerMock(context, TierPeriodReplayService.class);
            context.registerBean(TierPeriodReplayScheduler.class);
            context.refresh();

            assertThat(context.getBean(TierPeriodReplayScheduler.class)).isNotNull();
        }
    }

    @Test
    void shouldProvideReserveCalculatorAtServiceStartup() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(ClearingFinanceCoreConfiguration.class)) {
            assertThat(context.getBean(ReserveCalculator.class)).isNotNull();
        }
    }

    private <T> void registerMock(AnnotationConfigApplicationContext context, Class<T> type) {
        context.registerBean(type, () -> mock(type));
    }
}
