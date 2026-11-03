package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.ExchangeRateUsageMessage;
import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.dto.PaymentExchangeRateDTO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 交易汇率使用 Outbox 消息业务类型测试。
 *
 * <p>首次授权和预授权共享准备骨架，但快照必须保留真实交易类型，不能统一降级为 PAYMENT。</p>
 */
class TransactionRateUsageOutboxSupportTests {

    /**
     * 授权交易应在汇率消息中保留 AUTHORIZATION 类型。
     */
    @Test
    void shouldKeepAuthorizationBusinessType() {
        assertBusinessType(PaymentTransactionTypeEnum.AUTHORIZATION.getCode());
    }

    /**
     * 预授权交易应在汇率消息中保留 PRE_AUTHORIZATION 类型。
     */
    @Test
    void shouldKeepPreAuthorizationBusinessType() {
        assertBusinessType(PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode());
    }

    /**
     * 构造指定交易类型的汇率事件并核对序列化消息。
     *
     * @param transactionType 真实交易类型
     */
    private void assertBusinessType(String transactionType) {
        TransactionEventOutboxService outboxService = mock(TransactionEventOutboxService.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 11, 2, 9, 30);
        PaymentCreateCommandDTO command = new PaymentCreateCommandDTO();
        command.setMerchantId("200001");
        command.setMerchantOrderNo("ORDER-20261102-001");
        command.setTransactionDateTime(transactionTime);

        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        result.setTransactionId("202611020930000000001");
        result.setOperationId("OP202611020930000000001");
        result.setTransactionType(transactionType);

        TransactionRateUsageOutboxSupport.save(
                outboxService,
                command,
                result,
                rate(transactionTime.minusMinutes(5)),
                transactionTime,
                "transactionAmount = labelAmount * transactionRate");

        ArgumentCaptor<TransactionEventOutboxDO> captor =
                ArgumentCaptor.forClass(TransactionEventOutboxDO.class);
        verify(outboxService).save(captor.capture());
        ExchangeRateUsageMessage message = JsonUtils.parseObject(
                captor.getValue().getPayloadJson(), ExchangeRateUsageMessage.class);
        assertThat(message.getBusinessType()).isEqualTo(transactionType);
        assertThat(message.getBusinessNo()).isEqualTo(result.getTransactionId());
    }

    /**
     * 构造完整且可追溯的交易汇率版本。
     *
     * @param effectiveTime 汇率生效时间
     * @return 测试汇率版本
     */
    private PaymentExchangeRateDTO rate(LocalDateTime effectiveTime) {
        PaymentExchangeRateDTO rate = new PaymentExchangeRateDTO();
        rate.setRateId(1001L);
        rate.setRawRateId(901L);
        rate.setRuleId(801L);
        rate.setBaseCurrency("AUD");
        rate.setQuoteCurrency("USD");
        rate.setFinalRate(new BigDecimal("0.70000521"));
        rate.setSourceCode("PLATFORM");
        rate.setEffectiveTime(effectiveTime);
        return rate;
    }
}
