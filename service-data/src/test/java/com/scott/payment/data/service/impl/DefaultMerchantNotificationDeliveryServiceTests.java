package com.scott.payment.data.service.impl;

import com.scott.payment.data.config.DataMerchantNotificationProperties;
import com.scott.payment.data.entity.DataMerchantNotificationLogDO;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.mapper.DataMerchantNotificationLogMapper;
import com.scott.payment.data.mapper.DataMerchantNotificationMapper;
import com.scott.payment.data.mapper.DataMerchantNotificationRetryOutboxMapper;
import com.scott.payment.data.model.MerchantCallbackHttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantNotificationDeliveryServiceTests
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知投递测试，覆盖成功、失败重试、过期任务恢复和状态冲突
 * @status : create
 */
@Slf4j
class DefaultMerchantNotificationDeliveryServiceTests {

    /** 通知链路必须用逻辑表完成季度扫描、CAS 和日志写入。 */
    @Test
    void shouldUseLogicalMapperForSuccessfulNotification() {
        Fixture fixture = fixture(HttpStatus.OK, "succeed");
        LocalDateTime transactionDateTime = fixture.task().getTransactionDateTime();
        when(fixture.notificationMapper().markSuccess(eq(1L), eq(transactionDateTime), eq(1), any()))
                .thenReturn(1);

        int successCount = fixture.service().notifyDue(transactionDateTime, 10);

        assertThat(successCount).isEqualTo(1);
        verify(fixture.notificationMapper()).selectStaleProcessing(
                eq(LocalDateTime.of(2026, 7, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                any(),
                eq(100));
        verify(fixture.notificationMapper()).selectDueForNotify(
                eq(LocalDateTime.of(2026, 7, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)),
                any(),
                eq(10));
        verify(fixture.notificationMapper()).markProcessing(eq(1L), eq(transactionDateTime), eq(0), any());
        verify(fixture.notificationMapper()).markSuccess(eq(1L), eq(transactionDateTime), eq(1), any());
        ArgumentCaptor<DataMerchantNotificationLogDO> logCaptor =
                ArgumentCaptor.forClass(DataMerchantNotificationLogDO.class);
        verify(fixture.logMapper()).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getTransactionDateTime()).isEqualTo(transactionDateTime);
    }

    /** 自动 MQ 重试必须继续使用普通请求工厂，保持 notifyId、Header 和 Body 协议不变。 */
    @Test
    void retryDueShouldUseExistingAutomaticCallbackContract() {
        Fixture fixture = fixture(HttpStatus.OK, "succeed");
        DataMerchantNotificationTaskDO task = fixture.task();
        when(fixture.notificationMapper().selectReadyByRetryEvent(
                eq(task.getTransactionId()), eq(task.getNotifyId()), eq(task.getTransactionDateTime()),
                eq(0), any())).thenReturn(task);
        when(fixture.notificationMapper().markSuccess(
                eq(task.getId()), eq(task.getTransactionDateTime()), eq(1), any())).thenReturn(1);

        boolean notified = fixture.service().retryDue(
                task.getTransactionDateTime(), task.getTransactionId(), task.getNotifyId(), 0, 1);

        assertThat(notified).isTrue();
        verify(fixture.requestFactory()).create(task, 1);
        verify(fixture.requestFactory(), never()).create(
                any(DataMerchantNotificationTaskDO.class), anyInt(), anyString());
    }

    /** 失败重试 CAS 必须携带任务交易分片时间。 */
    @Test
    void shouldUseLogicalFailureCas() {
        Fixture fixture = fixture(HttpStatus.INTERNAL_SERVER_ERROR, "{\"result\":\"failed\"}");
        LocalDateTime transactionDateTime = fixture.task().getTransactionDateTime();
        when(fixture.notificationMapper().markFailed(
                eq(1L), eq(transactionDateTime), eq(1), eq("FAILED"), any(), anyString(), any()))
                .thenReturn(1);

        int successCount = fixture.service().notifyDue(transactionDateTime, 10);

        assertThat(successCount).isZero();
        verify(fixture.notificationMapper()).markFailed(
                eq(1L),
                eq(transactionDateTime),
                eq(1),
                eq("FAILED"),
                any(),
                eq("merchant callback http status 500"),
                any());
    }

    /** MQ 精确通知必须用消息恢复的分片时间查询并推进同一季度任务。 */
    @Test
    void shouldUseExactTransactionTimeForLogicalSingleNotification() {
        Fixture fixture = fixture(HttpStatus.OK, "succeed");
        LocalDateTime transactionDateTime = fixture.task().getTransactionDateTime();
        when(fixture.notificationMapper().selectReadyByTransactionId(
                eq(fixture.task().getTransactionId()), eq(transactionDateTime), any()))
                .thenReturn(fixture.task());
        when(fixture.notificationMapper().markSuccess(eq(1L), eq(transactionDateTime), eq(1), any()))
                .thenReturn(1);

        boolean notified = fixture.service().notifyTransaction(
                transactionDateTime, fixture.task().getTransactionId());

        assertThat(notified).isTrue();
        verify(fixture.notificationMapper()).selectReadyByTransactionId(
                eq(fixture.task().getTransactionId()), eq(transactionDateTime), any());
        verify(fixture.notificationMapper(), never())
                .selectStaleProcessing(any(), any(), any(), anyInt());
    }

    /** 后台人工重发应重新抢占终态通知，并把 MQ 消息号固定为回调协议事件 ID。 */
    @Test
    void shouldManuallyRetryTerminalNotificationWithStableEventId() {
        log.info("测试后台人工重发终态通知，关键输入: SUCCESS 任务和稳定 MQ 事件号");
        Fixture fixture = fixture(HttpStatus.OK, "succeed");
        LocalDateTime transactionDateTime = fixture.task().getTransactionDateTime();
        when(fixture.notificationMapper().selectRetryableByTransactionId(
                fixture.task().getTransactionId(), transactionDateTime)).thenReturn(fixture.task());
        when(fixture.notificationMapper().markProcessingForManualRetry(
                eq(1L), eq(transactionDateTime), eq(0), any())).thenReturn(1);
        when(fixture.notificationMapper().markSuccess(eq(1L), eq(transactionDateTime), eq(1), any()))
                .thenReturn(1);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-token-not-a-real-secret");
        when(fixture.requestFactory().create(
                fixture.task(), 1, "MNR-20260804-0001"))
                .thenReturn(new MerchantCallbackHttpRequest(
                        "MNR-20260804-0001", headers, "{\"data\":\"encrypted\"}", "{\"data\":\"***\"}"));

        boolean notified = fixture.service().retryTransaction(
                transactionDateTime,
                fixture.task().getTransactionId(),
                "MNR-20260804-0001");

        assertThat(notified).isTrue();
        verify(fixture.notificationMapper()).selectRetryableByTransactionId(
                fixture.task().getTransactionId(), transactionDateTime);
        verify(fixture.notificationMapper()).markProcessingForManualRetry(
                eq(1L), eq(transactionDateTime), eq(0), any());
        verify(fixture.requestFactory()).create(fixture.task(), 1, "MNR-20260804-0001");
        log.info("后台人工重发终态通知测试完成，结果: 精确分片 CAS 和稳定事件 ID 均已使用");
    }

    /** 人工重发抢占冲突必须上抛，让 RocketMQ 重投该事件而不是静默确认。 */
    @Test
    void shouldRequestMqRedeliveryWhenManualRetryClaimConflicts() {
        Fixture fixture = fixture(HttpStatus.OK, "succeed");
        LocalDateTime transactionDateTime = fixture.task().getTransactionDateTime();
        when(fixture.notificationMapper().selectRetryableByTransactionId(
                fixture.task().getTransactionId(), transactionDateTime)).thenReturn(fixture.task());
        when(fixture.notificationMapper().markProcessingForManualRetry(
                eq(1L), eq(transactionDateTime), eq(0), any())).thenReturn(0);

        assertThatThrownBy(() -> fixture.service().retryTransaction(
                transactionDateTime,
                fixture.task().getTransactionId(),
                "MNR-20260804-CONFLICT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("merchant notification manual retry claim conflict");

        assertThat(fixture.restTemplate().postCount).isZero();
        verifyNoInteractions(fixture.requestFactory());
    }

    /** 重复 MQ 在首笔成功后不得再次抢占任务或发起商户 HTTP 回调。 */
    @Test
    void shouldAbsorbDuplicateMessageAfterNotificationSucceeds() {
        log.info("测试商户通知重复 MQ，关键输入: 同一交易连续消费两次");
        Fixture fixture = fixture(HttpStatus.OK, "succeed");
        LocalDateTime transactionDateTime = fixture.task().getTransactionDateTime();
        when(fixture.notificationMapper().selectReadyByTransactionId(
                eq(fixture.task().getTransactionId()), eq(transactionDateTime), any()))
                .thenReturn(fixture.task())
                .thenReturn(null);
        when(fixture.notificationMapper().markSuccess(eq(1L), eq(transactionDateTime), eq(1), any()))
                .thenReturn(1);

        boolean first = fixture.service().notifyTransaction(
                transactionDateTime, fixture.task().getTransactionId());
        boolean duplicate = fixture.service().notifyTransaction(
                transactionDateTime, fixture.task().getTransactionId());

        assertThat(first).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(fixture.restTemplate().postCount).isEqualTo(1);
        verify(fixture.notificationMapper(), times(1))
                .markProcessing(eq(1L), eq(transactionDateTime), eq(0), any());
        log.info("商户通知重复 MQ 测试完成，结果: 第二次消费未抢占且未发 HTTP");
    }

    /** 逻辑 SUCCESS CAS 冲突必须上抛，禁止把重复或异常消费确认成功。 */
    @Test
    void shouldFailWhenLogicalSuccessStateCompareAndSetMisses() {
        Fixture fixture = fixture(HttpStatus.OK, "succeed");

        assertThatThrownBy(() -> fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("merchant notification state transition conflict");
    }

    /** 数据库任务缺失分片时间时必须在任何状态 Update 之前失败。 */
    @Test
    void shouldRejectTaskWithoutTransactionTimeBeforeAnyStateUpdate() {
        Fixture fixture = fixture(HttpStatus.OK, "succeed");
        fixture.task().setTransactionDateTime(null);

        assertThatThrownBy(() -> fixture.service().notifyDue(LocalDateTime.of(2026, 8, 1, 16, 0), 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("merchant notification task transaction_date_time is required");
        verify(fixture.notificationMapper(), never()).markProcessing(any(), any(), any(), any());
        verifyNoInteractions(fixture.logMapper());
    }

    /** HTTP 200 且返回 succeed 时应写尝试日志并将任务推进为 SUCCESS。 */
    @Test
    void shouldPersistLogAndMarkSuccessWhenCallbackReturns2xx() {
        log.info("测试商户通知成功，关键输入: HTTP 200、任务版本 0");
        Fixture fixture = fixture(HttpStatus.OK, "succeed");
        when(fixture.notificationMapper().markSuccess(eq(1L), eq(fixture.task().getTransactionDateTime()), eq(1), any()))
                .thenReturn(1);

        int successCount = fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10);

        assertThat(successCount).isEqualTo(1);
        verify(fixture.notificationMapper()).markSuccess(
                eq(1L), eq(fixture.task().getTransactionDateTime()), eq(1), any());
        ArgumentCaptor<DataMerchantNotificationLogDO> logCaptor =
                ArgumentCaptor.forClass(DataMerchantNotificationLogDO.class);
        verify(fixture.logMapper()).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getSuccess()).isEqualTo(1);
        assertThat(logCaptor.getValue().getHttpStatus()).isEqualTo(200);
        log.info("商户通知成功测试完成，结果: SUCCESS 状态和脱敏日志均已写入");
    }

    /** HTTP 非 2xx 时应写失败日志并安排下一次重试。 */
    @Test
    void shouldScheduleRetryWhenCallbackReturnsNon2xx() {
        log.info("测试商户通知失败重试，关键输入: HTTP 500、最大重试 3 次");
        Fixture fixture = fixture(HttpStatus.INTERNAL_SERVER_ERROR, "{\"result\":\"failed\"}");
        when(fixture.notificationMapper().markFailed(
                eq(1L), eq(fixture.task().getTransactionDateTime()), eq(1),
                eq("FAILED"), any(), anyString(), any())).thenReturn(1);

        int successCount = fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10);

        assertThat(successCount).isZero();
        verify(fixture.notificationMapper()).markFailed(
                eq(1L), eq(fixture.task().getTransactionDateTime()), eq(1),
                eq("FAILED"), any(), eq("merchant callback http status 500"), any());
        log.info("商户通知失败测试完成，结果: 任务进入 FAILED 并生成下次重试时间");
    }

    /** HTTP 错误响应仍须保留已签发的事件号和脱敏请求体，保证失败投递可审计。 */
    @Test
    void shouldPersistCallbackAuditContextWhenHttpRequestFails() {
        Fixture fixture = fixture(HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "merchant failure",
                HttpHeaders.EMPTY,
                "{\"result\":\"failed\"}".getBytes(),
                null));
        when(fixture.notificationMapper().markFailed(
                eq(1L), eq(fixture.task().getTransactionDateTime()), eq(1),
                eq("FAILED"), any(), anyString(), any())).thenReturn(1);

        assertThat(fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10)).isZero();

        ArgumentCaptor<DataMerchantNotificationLogDO> logCaptor =
                ArgumentCaptor.forClass(DataMerchantNotificationLogDO.class);
        verify(fixture.logMapper()).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getRequestHeaderJsonMasked()).contains("EVENT-1");
        assertThat(logCaptor.getValue().getRequestBodyJsonMasked()).isEqualTo("{\"data\":\"***\"}");
        assertThat(logCaptor.getValue().getHttpStatus()).isEqualTo(500);
    }

    /** 网络异常发生在请求构造后时，也必须保留回调事件审计上下文。 */
    @Test
    void shouldPersistCallbackAuditContextWhenTransportFails() {
        Fixture fixture = fixture(new ResourceAccessException("simulated timeout"));
        when(fixture.notificationMapper().markFailed(
                eq(1L), eq(fixture.task().getTransactionDateTime()), eq(1),
                eq("FAILED"), any(), anyString(), any())).thenReturn(1);

        assertThat(fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10)).isZero();

        ArgumentCaptor<DataMerchantNotificationLogDO> logCaptor =
                ArgumentCaptor.forClass(DataMerchantNotificationLogDO.class);
        verify(fixture.logMapper()).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getRequestHeaderJsonMasked()).contains("EVENT-1");
        assertThat(logCaptor.getValue().getRequestBodyJsonMasked()).isEqualTo("{\"data\":\"***\"}");
        assertThat(logCaptor.getValue().getHttpStatus()).isNull();
    }

    /** 其它 2xx 也不能确认成功，避免商户网关吞掉正文或返回异步受理。 */
    @Test
    void shouldRetryWhenCallbackReturns204() {
        Fixture fixture = fixture(HttpStatus.NO_CONTENT, "succeed");
        when(fixture.notificationMapper().markFailed(
                eq(1L), eq(fixture.task().getTransactionDateTime()), eq(1),
                eq("FAILED"), any(), anyString(), any())).thenReturn(1);

        assertThat(fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10)).isZero();
        verify(fixture.notificationMapper()).markFailed(
                eq(1L), eq(fixture.task().getTransactionDateTime()), eq(1), eq("FAILED"), any(),
                eq("merchant callback http status 204"), any());
    }

    /** HTTP 200 但确认词不是 succeed 时必须重试。 */
    @Test
    void shouldRetryWhenCallbackAcknowledgementDoesNotMatch() {
        Fixture fixture = fixture(HttpStatus.OK, "{\"result\":\"ok\"}");
        when(fixture.notificationMapper().markFailed(
                eq(1L), eq(fixture.task().getTransactionDateTime()), eq(1),
                eq("FAILED"), any(), anyString(), any())).thenReturn(1);

        assertThat(fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10)).isZero();
        verify(fixture.notificationMapper()).markFailed(
                eq(1L), eq(fixture.task().getTransactionDateTime()), eq(1), eq("FAILED"), any(),
                eq("merchant callback acknowledgement must be succeed"), any());
    }

    /** 每次扫描前应恢复超过执行窗口的 PROCESSING 任务。 */
    @Test
    void shouldRecoverStaleProcessingBeforeDueScan() {
        log.info("测试商户通知中断恢复，关键输入: 两条超时 PROCESSING 任务");
        Fixture fixture = fixture(HttpStatus.OK, "succeed");
        DataMerchantNotificationTaskDO first = recoveryCandidate(11L, 3, fixture.task().getTransactionDateTime());
        DataMerchantNotificationTaskDO second = recoveryCandidate(12L, 7, fixture.task().getTransactionDateTime());
        when(fixture.notificationMapper().selectStaleProcessing(any(), any(), any(), anyInt()))
                .thenReturn(List.of(first, second));
        when(fixture.notificationMapper().recoverStaleProcessingCas(eq(11L), any(), eq(3), any(), any()))
                .thenReturn(1);
        when(fixture.notificationMapper().recoverStaleProcessingCas(eq(12L), any(), eq(7), any(), any()))
                .thenReturn(1);
        when(fixture.notificationMapper().selectDueForNotify(any(), any(), any(), anyInt())).thenReturn(List.of());

        int successCount = fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10);

        assertThat(successCount).isZero();
        verify(fixture.notificationMapper()).selectStaleProcessing(
                eq(LocalDateTime.of(2026, 7, 1, 0, 0)),
                eq(LocalDateTime.of(2026, 10, 1, 0, 0)), any(), eq(100));
        verify(fixture.notificationMapper()).recoverStaleProcessingCas(
                eq(11L), eq(first.getTransactionDateTime()), eq(3), any(), any());
        verify(fixture.notificationMapper()).recoverStaleProcessingCas(
                eq(12L), eq(second.getTransactionDateTime()), eq(7), any(), any());
        log.info("商户通知中断恢复测试完成，结果: 超时任务已在查询前恢复");
    }

    /** HTTP 成功后状态 CAS 未更新时必须上抛，禁止错误确认 MQ。 */
    @Test
    void shouldFailWhenSuccessStateCompareAndSetMisses() {
        log.info("测试商户通知状态冲突，关键输入: HTTP 200、SUCCESS CAS 影响 0 行");
        Fixture fixture = fixture(HttpStatus.OK, "succeed");
        when(fixture.notificationMapper().markSuccess(
                eq(1L), eq(fixture.task().getTransactionDateTime()), eq(1), any())).thenReturn(0);

        assertThatThrownBy(() -> fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("merchant notification state transition conflict");
        log.info("商户通知状态冲突测试完成，结果: 异常上抛并保留补偿机会");
    }

    /** 创建商户通知服务测试夹具。 */
    private Fixture fixture(HttpStatus status, String body) {
        return fixture(new StubRestTemplate(status, body));
    }

    /** 创建会在 HTTP 边界抛出指定异常的商户通知服务测试夹具。 */
    private Fixture fixture(RestClientException exception) {
        return fixture(new StubRestTemplate(exception));
    }

    /** 使用指定 HTTP 客户端创建商户通知服务测试夹具。 */
    private Fixture fixture(StubRestTemplate restTemplate) {
        DataMerchantNotificationMapper notificationMapper = mock(DataMerchantNotificationMapper.class);
        DataMerchantNotificationLogMapper logMapper = mock(DataMerchantNotificationLogMapper.class);
        DataMerchantNotificationRetryOutboxMapper outboxMapper =
                mock(DataMerchantNotificationRetryOutboxMapper.class);
        DataMerchantNotificationTaskDO task = task();
        when(notificationMapper.selectDueForNotify(any(), any(), any(), anyInt())).thenReturn(List.of(task));
        when(notificationMapper.selectStaleProcessing(any(), any(), any(), anyInt())).thenReturn(List.of());
        when(notificationMapper.markProcessing(eq(1L), eq(task.getTransactionDateTime()), eq(0), any()))
                .thenReturn(1);
        when(logMapper.insert(any(DataMerchantNotificationLogDO.class))).thenReturn(1);
        when(outboxMapper.insert(any())).thenReturn(1);
        DataMerchantNotificationProperties properties = new DataMerchantNotificationProperties();
        MerchantCallbackRequestFactory requestFactory = mock(MerchantCallbackRequestFactory.class);
        MerchantCallbackTargetValidator targetValidator = mock(MerchantCallbackTargetValidator.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("test-token-not-a-real-secret");
        when(requestFactory.create(any(DataMerchantNotificationTaskDO.class), anyInt()))
                .thenReturn(new MerchantCallbackHttpRequest(
                        "EVENT-1", headers, "{\"data\":\"encrypted\"}", "{\"data\":\"***\"}"));
        DefaultMerchantNotificationDeliveryService service = new DefaultMerchantNotificationDeliveryService(
                notificationMapper,
                logMapper,
                restTemplate,
                properties,
                requestFactory,
                targetValidator,
                new MerchantNotificationRetryStateService(notificationMapper, outboxMapper));
        return new Fixture(service, notificationMapper, logMapper, task, restTemplate, requestFactory);
    }

    /** 构造不含卡数据和密钥的通知任务。 */
    private DataMerchantNotificationTaskDO task() {
        DataMerchantNotificationTaskDO task = new DataMerchantNotificationTaskDO();
        task.setId(1L);
        task.setNotifyId("TMN202608011600000000001");
        task.setTransactionId("TX202608011600000000001");
        task.setOperationId("OP202608011600000000001");
        task.setMerchantId("200001");
        task.setMerchantOrderNo("M202608010001");
        task.setNotifyConfigSnapshotJson(
                "{\"callbackUrl\":\"https://merchant.example/callback?token=secret-token\"}");
        task.setTargetUrlMasked("https://merchant.example/callback?***");
        task.setTargetUrlHash("callback-url-sha256");
        task.setPayloadJsonMasked("{\"transactionId\":\"TX202608011600000000001\"}");
        task.setLastAttemptNo(0);
        task.setMaxRetryCount(3);
        task.setTransactionDateTime(LocalDateTime.of(2026, 8, 1, 16, 0));
        task.setVersion(0);
        return task;
    }

    /** 构造只包含恢复 CAS 所需字段的超时任务候选。 */
    private DataMerchantNotificationTaskDO recoveryCandidate(Long id,
                                                             Integer version,
                                                             LocalDateTime transactionDateTime) {
        DataMerchantNotificationTaskDO task = new DataMerchantNotificationTaskDO();
        task.setId(id);
        task.setVersion(version);
        task.setTransactionDateTime(transactionDateTime);
        return task;
    }

    /** 商户通知服务测试依赖集合。 */
    private record Fixture(DefaultMerchantNotificationDeliveryService service,
                           DataMerchantNotificationMapper notificationMapper,
                           DataMerchantNotificationLogMapper logMapper,
                           DataMerchantNotificationTaskDO task,
                           StubRestTemplate restTemplate,
                           MerchantCallbackRequestFactory requestFactory) {
    }

    /** 返回预设 HTTP 状态和响应体，并校验实际回调地址没有被脱敏值替换。 */
    private static class StubRestTemplate extends RestTemplate {

        /** 预设 HTTP 状态。 */
        private final HttpStatus status;

        /** 预设商户响应体。 */
        private final String body;

        /** 需要模拟的 HTTP 边界异常；正常响应时为空。 */
        private final RestClientException exception;

        /** 实际发起商户 HTTP 回调的次数。 */
        private int postCount;

        private StubRestTemplate(HttpStatus status, String body) {
            this.status = status;
            this.body = body;
            this.exception = null;
        }

        private StubRestTemplate(RestClientException exception) {
            this.status = null;
            this.body = null;
            this.exception = exception;
        }

        /**
         * 模拟商户回调并校验数据库配置快照中的实际 URL 被用于网络请求。
         */
        @Override
        public <T> ResponseEntity<T> postForEntity(String url,
                                                   Object request,
                                                   Class<T> responseType,
                                                   Object... uriVariables) {
            assertThat(url).isEqualTo("https://merchant.example/callback?token=secret-token");
            assertThat(request).isInstanceOf(HttpEntity.class);
            postCount++;
            if (exception != null) {
                throw exception;
            }
            return new ResponseEntity<>(responseType.cast(body), status);
        }
    }
}
