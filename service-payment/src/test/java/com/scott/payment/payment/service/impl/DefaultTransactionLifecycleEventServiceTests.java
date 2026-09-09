package com.scott.payment.payment.service.impl;

import com.scott.payment.component.mq.enums.PaymentTransactionEventStatus;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.payment.entity.TransactionEventOutboxDO;
import com.scott.payment.payment.service.TransactionEventOutboxService;
import com.scott.payment.payment.service.TransactionLifecycleEventService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionLifecycleEventServiceTests
 * @date : 2026-08-02 23:10
 * @email : scott_x@163.com
 * @description : 交易生命周期事件公开契约测试，验证只有成功或失败终态才能在当前事务中写入 FIFO Outbox。
 * @status : create
 */
@Slf4j
class DefaultTransactionLifecycleEventServiceTests {

    /** 非终态状态变更不得形成可被 Data 消费的商户通知事件。 */
    @Test
    void shouldRejectNonTerminalStatusWithoutSavingOutbox() {
        log.info("测试生命周期事件终态边界，关键输入: PROCESSING");
        TransactionEventOutboxService outboxService = mock(TransactionEventOutboxService.class);
        TransactionLifecycleEventService lifecycleEventService =
                new DefaultTransactionLifecycleEventService(outboxService);

        assertThatThrownBy(() -> lifecycleEventService.saveStatusChanged(
                "TX202608022310000000001",
                "OP202608022310000000001",
                "200001",
                "M202608020001",
                "AUTHORIZATION",
                PaymentTransactionEventStatus.PROCESSING.getCode(),
                LocalDateTime.of(2026, 8, 2, 23, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transaction status is not terminal");

        verifyNoInteractions(outboxService);
        log.info("生命周期事件终态边界测试完成，结果: 未写入 Outbox");
    }

    /** 成功和失败终态都必须写入带真实交易分片时间的 Outbox。 */
    @Test
    void shouldSaveSuccessAndFailedTerminalEvents() {
        log.info("测试生命周期终态事件写入，关键输入: SUCCESS、FAILED");
        TransactionEventOutboxService outboxService = mock(TransactionEventOutboxService.class);
        TransactionLifecycleEventService lifecycleEventService =
                new DefaultTransactionLifecycleEventService(outboxService);
        LocalDateTime successTime = LocalDateTime.of(2026, 8, 2, 23, 20);
        LocalDateTime failedTime = successTime.plusSeconds(1);

        saveStatusChanged(lifecycleEventService,
                "TX202608022320000000001",
                "OP202608022320000000001",
                PaymentTransactionEventStatus.SUCCESS,
                successTime);
        saveStatusChanged(lifecycleEventService,
                "TX202608022320010000001",
                "OP202608022320010000001",
                PaymentTransactionEventStatus.FAILED,
                failedTime);

        ArgumentCaptor<TransactionEventOutboxDO> eventCaptor =
                ArgumentCaptor.forClass(TransactionEventOutboxDO.class);
        verify(outboxService, times(2)).save(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(TransactionEventOutboxDO::getTransactionDateTime)
                .containsExactly(successTime, failedTime);
        assertThat(eventCaptor.getAllValues())
                .allSatisfy(event -> {
                    assertThat(event.getTopic()).isEqualTo(MqTopic.PAYMENT_TRANSACTION_FIFO);
                    assertThat(event.getMessageGroup()).isEqualTo(event.getOperationId());
                });
        assertThat(eventCaptor.getAllValues())
                .extracting(TransactionEventOutboxDO::getPayloadJson)
                .allSatisfy(payload -> assertThat(payload).contains("transactionStatus"))
                .satisfiesExactly(
                        payload -> assertThat(payload).contains("\"SUCCESS\""),
                        payload -> assertThat(payload).contains("\"FAILED\""));
        log.info("生命周期终态事件写入测试完成，结果: 两条 Outbox 均保留状态和真实分片时间");
    }

    /** 调用公开契约保存指定终态事件。 */
    private void saveStatusChanged(TransactionLifecycleEventService lifecycleEventService,
                                   String transactionId,
                                   String operationId,
                                   PaymentTransactionEventStatus status,
                                   LocalDateTime transactionDateTime) {
        lifecycleEventService.saveStatusChanged(
                transactionId,
                operationId,
                "200001",
                "M202608020001",
                "AUTHORIZATION",
                status.getCode(),
                transactionDateTime);
    }
}
