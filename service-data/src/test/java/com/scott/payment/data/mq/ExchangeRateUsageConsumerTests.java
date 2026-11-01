package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.ExchangeRateUsageMessage;
import com.scott.payment.data.service.ExchangeRateUsagePersistenceService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 汇率使用消息契约校验测试。
 *
 * <p>覆盖合法支付事实、同币种伪快照以及类型和生产服务不一致的拒绝路径。</p>
 */
class ExchangeRateUsageConsumerTests {

    /**
     * 合法跨币种支付汇率事实应进入数据库最终幂等服务。
     */
    @Test
    void shouldPersistValidTransactionRateUsage() {
        ExchangeRateUsagePersistenceService persistenceService =
                mock(ExchangeRateUsagePersistenceService.class);
        when(persistenceService.persist(any())).thenReturn(true);
        ExchangeRateUsageConsumer consumer = new ExchangeRateUsageConsumer(persistenceService);
        ExchangeRateUsageMessage message = message();

        consumer.onMessage(JsonUtils.toJsonString(message));

        verify(persistenceService).persist(message);
    }

    /**
     * 同币种 1:1 只属于内部恒等计算，不能进入汇率使用快照。
     */
    @Test
    void shouldRejectSameCurrencyIdentityRate() {
        ExchangeRateUsagePersistenceService persistenceService =
                mock(ExchangeRateUsagePersistenceService.class);
        ExchangeRateUsageConsumer consumer = new ExchangeRateUsageConsumer(persistenceService);
        ExchangeRateUsageMessage message = message();
        message.setQuoteCurrency("EUR");
        message.setUsedRate(BigDecimal.ONE);

        assertThatThrownBy(() -> consumer.onMessage(JsonUtils.toJsonString(message)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same-currency");
        verify(persistenceService, never()).persist(any());
    }

    /**
     * 结算汇率不得由支付服务冒充生产，避免跨域事件污染审计事实。
     */
    @Test
    void shouldRejectInconsistentRateContract() {
        ExchangeRateUsagePersistenceService persistenceService =
                mock(ExchangeRateUsagePersistenceService.class);
        ExchangeRateUsageConsumer consumer = new ExchangeRateUsageConsumer(persistenceService);
        ExchangeRateUsageMessage message = message();
        message.setRateType("SETTLEMENT_RATE");
        message.setUsageScene("SETTLEMENT");
        message.setBusinessType("SETTLEMENT");

        assertThatThrownBy(() -> consumer.onMessage(JsonUtils.toJsonString(message)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inconsistent");
        verify(persistenceService, never()).persist(any());
    }

    /**
     * 支付服务不得用未列入契约的业务类型污染交易汇率快照。
     */
    @Test
    void shouldRejectUnsupportedTransactionBusinessType() {
        ExchangeRateUsagePersistenceService persistenceService =
                mock(ExchangeRateUsagePersistenceService.class);
        ExchangeRateUsageConsumer consumer = new ExchangeRateUsageConsumer(persistenceService);
        ExchangeRateUsageMessage message = message();
        message.setBusinessType("QUERY");

        assertThatThrownBy(() -> consumer.onMessage(JsonUtils.toJsonString(message)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inconsistent");
        verify(persistenceService, never()).persist(any());
    }

    /**
     * 超出 DECIMAL(24,12) 小数位的汇率必须在消费边界拒绝。
     */
    @Test
    void shouldRejectRateScaleOverflow() {
        ExchangeRateUsagePersistenceService persistenceService =
                mock(ExchangeRateUsagePersistenceService.class);
        ExchangeRateUsageConsumer consumer = new ExchangeRateUsageConsumer(persistenceService);
        ExchangeRateUsageMessage message = message();
        message.setUsedRate(new BigDecimal("1.1234567890123"));

        assertThatThrownBy(() -> consumer.onMessage(JsonUtils.toJsonString(message)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DECIMAL(24,12)");
        verify(persistenceService, never()).persist(any());
    }

    /**
     * 创建合法交易汇率使用事实。
     */
    private ExchangeRateUsageMessage message() {
        LocalDateTime effectiveTime = LocalDateTime.of(2026, 11, 1, 12, 0);
        LocalDateTime appliedTime = effectiveTime.plusMinutes(1);
        ExchangeRateUsageMessage message = new ExchangeRateUsageMessage();
        message.setMessageId("FXTR:TX202611010001");
        message.setUsageEventId("FXTR:TX202611010001");
        message.setProducerService("service-payment");
        message.setRateType("TRANSACTION_RATE");
        message.setUsageScene("TRANSACTION");
        message.setBusinessType("PAYMENT");
        message.setBusinessNo("TX202611010001");
        message.setBaseCurrency("EUR");
        message.setQuoteCurrency("USD");
        message.setUsedRate(new BigDecimal("1.080000000000"));
        message.setBusinessRateId(101L);
        message.setRawRateId(81L);
        message.setRuleId(91L);
        message.setRateSource("BOC");
        message.setSourceQuoteDirection("DIRECT");
        message.setRateEffectiveTime(effectiveTime);
        message.setCalculationDescription("labelAmount * transactionRate");
        message.setAppliedTime(appliedTime);
        message.setCreatedAt(appliedTime);
        return message;
    }
}
