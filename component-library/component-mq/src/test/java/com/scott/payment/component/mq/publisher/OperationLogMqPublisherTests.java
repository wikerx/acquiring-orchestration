package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.mq.enums.OperationLogSystemCode;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.component.mq.properties.OperationLogMqProperties;
import com.scott.payment.component.web.operation.dto.OperationLogRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogMqPublisherTests
 * @date : 2026-08-02 00:31
 * @email : scott_x@163.com
 * @description : 操作日志 MQ 发布契约测试，确保错误摘要符合数据库字段边界
 * @status : create
 */
class OperationLogMqPublisherTests {

    /** 错误摘要使用数据库上限，请求和响应正文继续使用通用消息上限。 */
    @Test
    void shouldLimitErrorSummaryWithoutReducingRequestAndResponseLimit() {
        IndependentReliableMqPublisher mqPublisher = mock(IndependentReliableMqPublisher.class);
        OperationLogMqProperties properties = new OperationLogMqProperties();
        OperationLogMqPublisher publisher = new OperationLogMqPublisher(
                mqPublisher,
                properties,
                OperationLogSystemCode.ADMIN,
                new OperationLogTopicResolver(properties),
                new OperationLogMessageSanitizer(properties));
        OperationLogRecord record = new OperationLogRecord();
        record.setRequestId("request-long-error");
        record.setMethodName("AdminOperation.execute");
        record.setBusinessType(2);
        record.setRequestParam("Q".repeat(properties.getMaxMessageLength() + 1));
        record.setResponseResult("R".repeat(properties.getMaxMessageLength() + 1));
        record.setErrorMsg("E".repeat(properties.getMaxMessageLength()));

        publisher.publish(record);

        ArgumentCaptor<BaseMqMessage> captor = ArgumentCaptor.forClass(BaseMqMessage.class);
        verify(mqPublisher).publish(eq(properties.getAdminTopic()), isNull(), captor.capture());
        OperationLogMessage message = (OperationLogMessage) captor.getValue();
        assertThat(message.getErrorMessage()).hasSize(OperationLogMessage.ERROR_MESSAGE_MAX_LENGTH);
        assertThat(message.getRequestParams()).hasSize(properties.getMaxMessageLength());
        assertThat(message.getResponseResult()).hasSize(properties.getMaxMessageLength());
    }
}
