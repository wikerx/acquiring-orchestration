package com.scott.payment.payment.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.RefundExecutionMessage;
import com.scott.payment.payment.domain.refund.RefundExecutionOutcomeEnum;
import com.scott.payment.payment.service.RefundExecutionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundExecutionConsumerTests
 * @date : 2026-08-21 18:30
 * @email : scott_x@163.com
 * @description : 退款执行 MQ 消费入口契约测试，覆盖完整状态机定位字段和毒消息处理
 * @status : create
 */
class RefundExecutionConsumerTests {

    /** 完整退款执行命令应交给数据库状态机且不改变消息身份。 */
    @Test
    void shouldExecuteValidMessage() {
        RefundExecutionService executionService = mock(RefundExecutionService.class);
        RefundExecutionConsumer consumer = new RefundExecutionConsumer(executionService);
        RefundExecutionMessage message = message();
        when(executionService.execute(message)).thenReturn(RefundExecutionOutcomeEnum.EXECUTED);

        consumer.onMessage(JsonUtils.toJsonString(message));

        verify(executionService).execute(message);
    }

    /** 缺少数据库版本时必须触发 Broker 重试且不得调用渠道状态机。 */
    @Test
    void shouldRejectMessageWithoutExpectedVersion() {
        RefundExecutionService executionService = mock(RefundExecutionService.class);
        RefundExecutionConsumer consumer = new RefundExecutionConsumer(executionService);
        RefundExecutionMessage message = message();
        message.setExpectedOperationVersion(null);

        assertThatThrownBy(() -> consumer.onMessage(JsonUtils.toJsonString(message)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refund execution message required fields are missing");

        verify(executionService, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    /** 畸形 JSON 必须触发 Broker 重试且不得调用渠道状态机。 */
    @Test
    void shouldRejectMalformedPayload() {
        RefundExecutionService executionService = mock(RefundExecutionService.class);
        RefundExecutionConsumer consumer = new RefundExecutionConsumer(executionService);

        assertThatThrownBy(() -> consumer.onMessage("{invalid-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("refund execution payload is invalid");

        verify(executionService, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    /** 创建最小合法退款执行命令。 */
    private RefundExecutionMessage message() {
        RefundExecutionMessage message = new RefundExecutionMessage();
        message.setMessageId("REFUND-EVENT-001");
        message.setApprovalId("APPROVAL-001");
        message.setRefundTransactionId("REFUND-TX-001");
        message.setRefundTransactionDateTime(LocalDateTime.of(2026, 8, 21, 18, 0));
        message.setExpectedOperationVersion(2);
        message.setEventType(MqTag.REFUND_EXECUTION_REQUESTED);
        return message;
    }
}
