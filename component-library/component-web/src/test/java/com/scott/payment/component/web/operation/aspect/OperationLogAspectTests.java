package com.scott.payment.component.web.operation.aspect;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.web.operation.dto.OperationLogRecord;
import com.scott.payment.component.web.operation.service.OperationLogPublisher;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogAspectTests
 * @date : 2026-08-18 22:10
 * @email : scott_x@163.com
 * @description : 操作日志参数过滤测试，防止文件导出时序列化 Servlet 请求或响应对象导致审计记录丢失。
 * @status : create
 */
class OperationLogAspectTests {

    @Test
    void shouldExcludeServletRequestAndResponseFromOperationParameters() {
        @SuppressWarnings("unchecked")
        ObjectProvider<OperationLogPublisher> publisherProvider = mock(ObjectProvider.class);
        OperationLogAspect aspect = new OperationLogAspect(publisherProvider);

        Boolean requestLoggable = ReflectionTestUtils.invokeMethod(
                aspect, "loggableArgument", new MockHttpServletRequest());
        Boolean responseLoggable = ReflectionTestUtils.invokeMethod(
                aspect, "loggableArgument", new MockHttpServletResponse());
        Boolean businessArgumentLoggable = ReflectionTestUtils.invokeMethod(
                aspect, "loggableArgument", new ExportQuery("200045"));

        assertThat(requestLoggable).isFalse();
        assertThat(responseLoggable).isFalse();
        assertThat(businessArgumentLoggable).isTrue();
        assertThat(new MockHttpServletRequest()).isInstanceOf(ServletRequest.class);
        assertThat(new MockHttpServletResponse()).isInstanceOf(ServletResponse.class);
    }

    @Test
    void shouldNotPersistSensitiveServiceExceptionMessage() {
        @SuppressWarnings("unchecked")
        ObjectProvider<OperationLogPublisher> publisherProvider = mock(ObjectProvider.class);
        OperationLogAspect aspect = new OperationLogAspect(publisherProvider);
        OperationLogRecord record = new OperationLogRecord();
        ServiceException failure = new ServiceException(
                "F500",
                "payment failed, cardNo=4111111111111111, secretKey=plain-text-secret");

        ReflectionTestUtils.invokeMethod(aspect, "fillFailure", record, failure);

        assertThat(record.getErrorCode()).isEqualTo("F500");
        assertThat(record.getErrorMsg()).isEqualTo(ServiceException.class.getSimpleName());
        assertThat(record.getErrorMsg())
                .doesNotContain("4111111111111111")
                .doesNotContain("plain-text-secret");
    }

    private record ExportQuery(String merchantId) {
    }
}
