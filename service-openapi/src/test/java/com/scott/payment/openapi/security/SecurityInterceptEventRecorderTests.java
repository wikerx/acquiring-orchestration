package com.scott.payment.openapi.security;

import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.SecurityInterceptAuditMessage;
import com.scott.payment.component.mq.publisher.IndependentReliableMqPublisher;
import com.scott.payment.component.mq.properties.SecurityAuditMqProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SecurityInterceptEventRecorderTests
 * @date : 2026-08-01 18:00
 * @email : scott_x@163.com
 * @description : OpenAPI 安全拦截审计生产端测试，验证脱敏契约、开关与发布失败隔离
 * @status : create
 */
@Slf4j
class SecurityInterceptEventRecorderTests {

    /** 安全拦截请求应只发布脱敏元数据。 */
    @Test
    void shouldPublishSanitizedSecurityAuditMessage() {
        log.info("测试安全拦截审计发布，关键输入: 认证请求头存在且请求路径带查询参数");
        IndependentReliableMqPublisher producer = mock(IndependentReliableMqPublisher.class);
        SecurityInterceptEventRecorder recorder = new SecurityInterceptEventRecorder(
                producer, new SecurityAuditMqProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/rest/payment/v1/create");
        request.setQueryString("token=must-not-enter-message");
        request.addHeader("Authorization", "Bearer must-not-enter-message");
        request.addHeader("X-Request-Id", "REQ-SEC-001");
        request.addHeader("User-Agent", "sdk-test/1.0");
        request.addHeader(MerchantIpWhitelistAccessService.HEADER_GATEWAY_CLIENT_IP, "203.0.113.10");

        recorder.recordBlocked(request, SecurityInterceptEventRecorder.SOURCE_OPENAPI,
                "OPENAPI_IP_DENIED", SecurityInterceptEventRecorder.RISK_HIGH,
                "200045", "OPENAPI_IP_POLICY", "F403", "client ip denied");

        ArgumentCaptor<SecurityInterceptAuditMessage> captor =
                ArgumentCaptor.forClass(SecurityInterceptAuditMessage.class);
        verify(producer).publish(
                org.mockito.ArgumentMatchers.eq(MqTopic.SECURITY_INTERCEPT_AUDIT),
                org.mockito.ArgumentMatchers.eq(MqTag.SECURITY_INTERCEPT_AUDIT),
                captor.capture());
        SecurityInterceptAuditMessage message = captor.getValue();
        assertThat(message.getEventNo()).startsWith("SIE");
        assertThat(message.getMerchantId()).isEqualTo("200045");
        assertThat(message.getClientIp()).isEqualTo("203.0.113.10");
        assertThat(message.getRequestPath()).isEqualTo("/api/rest/payment/v1/create");
        assertThat(message.getHeaderSummary())
                .contains("authorizationPresent")
                .doesNotContain("must-not-enter-message");
        log.info("安全拦截审计发布完成，结果: 认证原文和查询参数未进入 MQ 契约");
    }

    /** 安全审计开关关闭时不得发布消息。 */
    @Test
    void shouldSkipPublishingWhenDisabled() {
        log.info("测试安全审计开关，关键输入: enabled=false");
        IndependentReliableMqPublisher producer = mock(IndependentReliableMqPublisher.class);
        SecurityAuditMqProperties properties = new SecurityAuditMqProperties();
        properties.setEnabled(false);
        SecurityInterceptEventRecorder recorder = new SecurityInterceptEventRecorder(producer, properties);

        recorder.recordBlocked(null, null, "OPENAPI_JWT_INVALID", null,
                null, null, null, null);

        verify(producer, never()).publish(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
        log.info("安全审计开关测试完成，结果: 未发布消息");
    }

    /** MQ 异常不得覆盖原始安全拦截结果。 */
    @Test
    void shouldIsolatePublishFailureFromSecurityDecision() {
        log.info("测试安全审计发布失败隔离，关键输入: RocketMQ 不可用");
        IndependentReliableMqPublisher producer = mock(IndependentReliableMqPublisher.class);
        doThrow(new IllegalStateException("rocketmq unavailable"))
                .when(producer).publish(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
        SecurityInterceptEventRecorder recorder = new SecurityInterceptEventRecorder(
                producer, new SecurityAuditMqProperties());

        assertThatCode(() -> recorder.recordBlocked(null, null, "OPENAPI_JWT_INVALID", null,
                null, null, null, null)).doesNotThrowAnyException();
        log.info("安全审计发布失败隔离完成，结果: 原始安全处置不受审计故障影响");
    }

    /** 异常原因只能返回稳定分类，禁止把任意异常正文带入安全审计 MQ。 */
    @Test
    void shouldUseExceptionTypeAsSecurityReasonMessage() {
        SecurityInterceptEventRecorder recorder = new SecurityInterceptEventRecorder(
                mock(IndependentReliableMqPublisher.class), new SecurityAuditMqProperties());

        String reason = recorder.reasonMessage(
                new IllegalArgumentException("secretKey=must-not-enter-security-audit"));

        assertThat(reason).isEqualTo("IllegalArgumentException");
    }
}
