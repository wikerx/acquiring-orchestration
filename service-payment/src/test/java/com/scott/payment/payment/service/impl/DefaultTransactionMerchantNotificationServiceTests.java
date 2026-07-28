package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.sharding.PaymentQuarterShardingProperties;
import com.scott.payment.component.db.sharding.ShardingDataTemplate;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import com.scott.payment.component.db.sharding.ShardingTableRangeResolver;
import com.scott.payment.component.db.sharding.TransactionShardingKeyParser;
import com.scott.payment.payment.entity.TransactionMerchantNotificationDO;
import com.scott.payment.payment.entity.TransactionMerchantNotificationLogDO;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationLogMapper;
import com.scott.payment.payment.mapper.TransactionMerchantNotificationMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
 * @classname : DefaultTransactionMerchantNotificationServiceTests
 * @date : 2026-07-14 22:06
 * @email : scott_x@163.com
 * @description : 商户通知服务单元测试，验证到期任务抢占、HTTP 通知、日志写入和重试状态更新。
 * @status : create
 */
class DefaultTransactionMerchantNotificationServiceTests {

    /**
     * 商户回调返回 2xx 时应写成功日志并把通知任务更新为 SUCCESS。
     */
    @Test
    void shouldMarkNotificationSuccessWhenMerchantCallbackReturns2xx() {
        TransactionMerchantNotificationMapper notificationMapper = mock(TransactionMerchantNotificationMapper.class);
        TransactionMerchantNotificationLogMapper logMapper = mock(TransactionMerchantNotificationLogMapper.class);
        TransactionMerchantNotificationDO task = task();
        when(notificationMapper.selectDueForNotify(anyString(), any(), anyInt())).thenReturn(List.of(task));
        when(notificationMapper.markProcessing(anyString(), eq(1L), eq(0), any())).thenReturn(1);
        when(notificationMapper.markSuccess(anyString(), eq(1L), eq(1), any())).thenReturn(1);
        ArgumentCaptor<TransactionMerchantNotificationLogDO> logCaptor = ArgumentCaptor.forClass(TransactionMerchantNotificationLogDO.class);
        DefaultTransactionMerchantNotificationService service = new DefaultTransactionMerchantNotificationService(
                notificationMapper,
                logMapper,
                shardingDataTemplate(),
                new StubRestTemplate(HttpStatus.OK, "{\"result\":\"ok\"}"));

        int successCount = service.notifyDue(task.getTransactionDateTime(), 10);

        assertThat(successCount).isEqualTo(1);
        verify(notificationMapper).markSuccess(anyString(), eq(1L), eq(1), any());
        verify(logMapper).insertPhysical(eq("transaction_merchant_notification_log_202603"), logCaptor.capture());
        assertThat(logCaptor.getValue().getSuccess()).isEqualTo(1);
        assertThat(logCaptor.getValue().getHttpStatus()).isEqualTo(200);
    }

    /**
     * 商户回调返回非 2xx 时应写失败日志并安排下一次重试。
     */
    @Test
    void shouldMarkNotificationFailedAndScheduleRetryWhenMerchantCallbackReturnsNon2xx() {
        TransactionMerchantNotificationMapper notificationMapper = mock(TransactionMerchantNotificationMapper.class);
        TransactionMerchantNotificationLogMapper logMapper = mock(TransactionMerchantNotificationLogMapper.class);
        TransactionMerchantNotificationDO task = task();
        when(notificationMapper.selectDueForNotify(anyString(), any(), anyInt())).thenReturn(List.of(task));
        when(notificationMapper.markProcessing(anyString(), eq(1L), eq(0), any())).thenReturn(1);
        DefaultTransactionMerchantNotificationService service = new DefaultTransactionMerchantNotificationService(
                notificationMapper,
                logMapper,
                shardingDataTemplate(),
                new StubRestTemplate(HttpStatus.INTERNAL_SERVER_ERROR, "{\"result\":\"failed\"}"));

        int successCount = service.notifyDue(task.getTransactionDateTime(), 10);

        assertThat(successCount).isZero();
        verify(notificationMapper).markFailed(anyString(), eq(1L), eq(1), eq("FAILED"), any(), anyString(), any());
    }

    private TransactionMerchantNotificationDO task() {
        TransactionMerchantNotificationDO task = new TransactionMerchantNotificationDO();
        task.setId(1L);
        task.setNotifyId("TMN202607141000000000001");
        task.setTransactionId("TX202607141000000000001");
        task.setOperationId("OP202607141000000000001");
        task.setMerchantId("200001");
        task.setMerchantOrderNo("M202607140001");
        task.setNotifyStatus("INIT");
        task.setNotifyConfigSnapshotJson("{\"callbackUrl\":\"http://merchant.example/callback?token=secret-token&orderNo=M202607140001\"}");
        task.setTargetUrlMasked("http://merchant.example/callback?token=legacy-secret");
        task.setTargetUrlHash("hash");
        task.setPayloadJsonMasked("{\"transactionId\":\"TX202607141000000000001\"}");
        task.setLastAttemptNo(0);
        task.setMaxRetryCount(3);
        task.setTransactionDateTime(LocalDateTime.of(2026, 7, 14, 10, 0));
        task.setTransactionTimeZone("Asia/Shanghai");
        task.setVersion(0);
        return task;
    }

    private ShardingDataTemplate shardingDataTemplate() {
        PaymentQuarterShardingProperties properties = new PaymentQuarterShardingProperties();
        properties.getTables().put("transaction_merchant_notification", tableRule("transaction_merchant_notification"));
        properties.getTables().put("transaction_merchant_notification_log", tableRule("transaction_merchant_notification_log"));
        ShardingTableRangeResolver rangeResolver = new ShardingTableRangeResolver(
                properties,
                new ShardingQuarterResolver(),
                new ShardingPhysicalTableNameResolver());
        return new ShardingDataTemplate(rangeResolver);
    }

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

    private static class StubRestTemplate extends RestTemplate {

        /**
         * status，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private final HttpStatus status;

        /**
         * body，表示请求体、响应体或消息载荷，日志中只能保留脱敏摘要。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final String body;

        private StubRestTemplate(HttpStatus status, String body) {
            this.status = status;
            this.body = body;
        }

        @Override
        public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType, Object... uriVariables) {
            assertThat(url).isEqualTo("http://merchant.example/callback?token=secret-token&orderNo=M202607140001");
            assertThat(request).isInstanceOf(HttpEntity.class);
            return new ResponseEntity<>(responseType.cast(body), status);
        }
    }
}
