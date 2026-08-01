package com.scott.payment.data.service.impl;

import com.scott.payment.component.db.sharding.PaymentQuarterShardingProperties;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import com.scott.payment.component.db.sharding.ShardingTableRangeResolver;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
import com.scott.payment.data.entity.DataMerchantNotificationLogDO;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.mapper.DataMerchantNotificationLogMapper;
import com.scott.payment.data.mapper.DataMerchantNotificationMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import static org.mockito.Mockito.verify;
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

    /** HTTP 2xx 时应写尝试日志并将任务推进为 SUCCESS。 */
    @Test
    void shouldPersistLogAndMarkSuccessWhenCallbackReturns2xx() {
        log.info("测试商户通知成功，关键输入: HTTP 200、任务版本 0");
        Fixture fixture = fixture(HttpStatus.OK, "{\"result\":\"ok\"}");
        when(fixture.notificationMapper().markSuccess(anyString(), eq(1L), eq(1), any())).thenReturn(1);

        int successCount = fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10);

        assertThat(successCount).isEqualTo(1);
        verify(fixture.notificationMapper()).markSuccess(anyString(), eq(1L), eq(1), any());
        ArgumentCaptor<DataMerchantNotificationLogDO> logCaptor =
                ArgumentCaptor.forClass(DataMerchantNotificationLogDO.class);
        verify(fixture.logMapper()).insertPhysical(
                eq("transaction_merchant_notification_log_202603"), logCaptor.capture());
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
                anyString(), eq(1L), eq(1), eq("FAILED"), any(), anyString(), any())).thenReturn(1);

        int successCount = fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10);

        assertThat(successCount).isZero();
        verify(fixture.notificationMapper()).markFailed(
                anyString(), eq(1L), eq(1), eq("FAILED"), any(), eq("merchant callback http status 500"), any());
        log.info("商户通知失败测试完成，结果: 任务进入 FAILED 并生成下次重试时间");
    }

    /** 每次扫描前应恢复超过执行窗口的 PROCESSING 任务。 */
    @Test
    void shouldRecoverStaleProcessingBeforeDueScan() {
        log.info("测试商户通知中断恢复，关键输入: 两条超时 PROCESSING 任务");
        Fixture fixture = fixture(HttpStatus.OK, "{}");
        when(fixture.notificationMapper().recoverStaleProcessing(anyString(), any(), any())).thenReturn(2);
        when(fixture.notificationMapper().selectDueForNotify(anyString(), any(), anyInt())).thenReturn(List.of());

        int successCount = fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10);

        assertThat(successCount).isZero();
        verify(fixture.notificationMapper()).recoverStaleProcessing(
                eq("transaction_merchant_notification_202603"), any(), any());
        log.info("商户通知中断恢复测试完成，结果: 超时任务已在查询前恢复");
    }

    /** HTTP 成功后状态 CAS 未更新时必须上抛，禁止错误确认 MQ。 */
    @Test
    void shouldFailWhenSuccessStateCompareAndSetMisses() {
        log.info("测试商户通知状态冲突，关键输入: HTTP 200、SUCCESS CAS 影响 0 行");
        Fixture fixture = fixture(HttpStatus.OK, "{}");
        when(fixture.notificationMapper().markSuccess(anyString(), eq(1L), eq(1), any())).thenReturn(0);

        assertThatThrownBy(() -> fixture.service().notifyDue(fixture.task().getTransactionDateTime(), 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("merchant notification state transition conflict");
        log.info("商户通知状态冲突测试完成，结果: 异常上抛并保留补偿机会");
    }

    /** 创建商户通知服务测试夹具。 */
    private Fixture fixture(HttpStatus status, String body) {
        DataMerchantNotificationMapper notificationMapper = mock(DataMerchantNotificationMapper.class);
        DataMerchantNotificationLogMapper logMapper = mock(DataMerchantNotificationLogMapper.class);
        DataMerchantNotificationTaskDO task = task();
        when(notificationMapper.selectDueForNotify(anyString(), any(), anyInt())).thenReturn(List.of(task));
        when(notificationMapper.markProcessing(anyString(), eq(1L), eq(0), any())).thenReturn(1);
        when(logMapper.insertPhysical(anyString(), any(DataMerchantNotificationLogDO.class))).thenReturn(1);
        DataMerchantNotificationProperties properties = new DataMerchantNotificationProperties();
        DefaultMerchantNotificationDeliveryService service = new DefaultMerchantNotificationDeliveryService(
                notificationMapper,
                logMapper,
                shardingDataTemplate(),
                new StubRestTemplate(status, body),
                properties);
        return new Fixture(service, notificationMapper, logMapper, task);
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

    /** 构造包含通知任务和通知日志分表规则的测试解析器。 */
    private ShardingDataTemplate shardingDataTemplate() {
        PaymentQuarterShardingProperties properties = new PaymentQuarterShardingProperties();
        properties.getTables().put("transaction_merchant_notification", tableRule("transaction_merchant_notification"));
        properties.getTables().put("transaction_merchant_notification_log", tableRule("transaction_merchant_notification_log"));
        return new ShardingDataTemplate(new ShardingTableRangeResolver(
                properties,
                new ShardingQuarterResolver(),
                new ShardingPhysicalTableNameResolver()));
    }

    /** 构造季度分表规则。 */
    private PaymentQuarterShardingProperties.TableRule tableRule(String logicalTable) {
        PaymentQuarterShardingProperties.TableRule tableRule = new PaymentQuarterShardingProperties.TableRule();
        tableRule.setLogicalTable(logicalTable);
        tableRule.setTemplateTable(logicalTable);
        tableRule.setStartYear(2026);
        tableRule.setStartQuarter(1);
        tableRule.setEndYear(2035);
        tableRule.setEndQuarter(4);
        tableRule.setTableNameFormat("%s_%d%02d");
        return tableRule;
    }

    /** 商户通知服务测试依赖集合。 */
    private record Fixture(DefaultMerchantNotificationDeliveryService service,
                           DataMerchantNotificationMapper notificationMapper,
                           DataMerchantNotificationLogMapper logMapper,
                           DataMerchantNotificationTaskDO task) {
    }

    /** 返回预设 HTTP 状态和响应体，并校验实际回调地址没有被脱敏值替换。 */
    private static class StubRestTemplate extends RestTemplate {

        /** 预设 HTTP 状态。 */
        private final HttpStatus status;

        /** 预设商户响应体。 */
        private final String body;

        private StubRestTemplate(HttpStatus status, String body) {
            this.status = status;
            this.body = body;
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
            return new ResponseEntity<>(responseType.cast(body), status);
        }
    }
}
