package com.scott.payment.merchant.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.component.mq.properties.OperationLogMqProperties;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import com.scott.payment.merchant.converter.OperLogMessageConverter;
import com.scott.payment.merchant.dto.SysOperLogRecordRequest;
import com.scott.payment.merchant.service.MerchantOperLogService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperationLogConsumerTests
 * @date : 2026-07-30 18:30
 * @email : scott_x@163.com
 * @description : 验证商户端操作日志在 Redis 获取、重复和降级三种结果下的数据库幂等与释放边界
 * @status : create
 */
@Slf4j
class MerchantOperationLogConsumerTests {

    @Test
    void shouldReleaseIdempotentClaimWhenDatabaseWriteFails() {
        log.info("测试商户端审计失败释放，关键输入: Redis ACQUIRED、数据库不可用");
        MerchantOperLogService operLogService = mock(MerchantOperLogService.class);
        IdempotentService idempotentService = mock(IdempotentService.class);
        OperLogMessageConverter converter = mock(OperLogMessageConverter.class);
        OperationLogMqProperties properties = new OperationLogMqProperties();
        OperationLogMessage message = message("MERCHANT-LOG-001");
        SysOperLogRecordRequest request = new SysOperLogRecordRequest();
        when(idempotentService.acquireMq("merchant-operation-log", "MERCHANT-LOG-001",
                properties.getConsumeIdempotentTtlSeconds())).thenReturn(IdempotentAcquireResult.ACQUIRED);
        when(converter.toRecordRequest(message)).thenReturn(request);
        doThrow(new IllegalStateException("database unavailable"))
                .when(operLogService).recordOperLog(request);
        MerchantOperationLogConsumer consumer = new MerchantOperationLogConsumer(
                operLogService, idempotentService, properties, converter);

        assertThatThrownBy(() -> consumer.onMessage(JsonUtils.toJsonString(message)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(idempotentService).releaseMq(
                "merchant-operation-log", "MERCHANT-LOG-001", properties.getConsumeIdempotentTtlSeconds());
        log.info("商户端审计失败释放测试完成，结果: 原异常上抛且 Redis 占用已释放");
    }

    @Test
    void shouldContinueToDatabaseWithoutReleaseWhenRedisFallsBack() {
        log.info("测试商户端审计 Redis 降级，关键输入: FALLBACK、数据库写入成功");
        MerchantOperLogService operLogService = mock(MerchantOperLogService.class);
        IdempotentService idempotentService = mock(IdempotentService.class);
        OperLogMessageConverter converter = mock(OperLogMessageConverter.class);
        OperationLogMqProperties properties = new OperationLogMqProperties();
        OperationLogMessage message = message("MERCHANT-LOG-002");
        SysOperLogRecordRequest request = new SysOperLogRecordRequest();
        when(idempotentService.acquireMq("merchant-operation-log", "MERCHANT-LOG-002",
                properties.getConsumeIdempotentTtlSeconds())).thenReturn(IdempotentAcquireResult.FALLBACK);
        when(converter.toRecordRequest(message)).thenReturn(request);
        MerchantOperationLogConsumer consumer = new MerchantOperationLogConsumer(
                operLogService, idempotentService, properties, converter);

        assertThatCode(() -> consumer.onMessage(JsonUtils.toJsonString(message))).doesNotThrowAnyException();

        verify(operLogService).recordOperLog(request);
        verify(idempotentService, never()).releaseMq(
                "merchant-operation-log", "MERCHANT-LOG-002", properties.getConsumeIdempotentTtlSeconds());
        log.info("商户端审计 Redis 降级测试完成，结果: 继续数据库唯一约束且未释放 Redis");
    }

    @Test
    void shouldSkipDatabaseForRedisDuplicate() {
        log.info("测试商户端审计重复消息，关键输入: Redis DUPLICATE");
        MerchantOperLogService operLogService = mock(MerchantOperLogService.class);
        IdempotentService idempotentService = mock(IdempotentService.class);
        OperLogMessageConverter converter = mock(OperLogMessageConverter.class);
        OperationLogMqProperties properties = new OperationLogMqProperties();
        OperationLogMessage message = message("MERCHANT-LOG-003");
        when(idempotentService.acquireMq("merchant-operation-log", "MERCHANT-LOG-003",
                properties.getConsumeIdempotentTtlSeconds())).thenReturn(IdempotentAcquireResult.DUPLICATE);
        MerchantOperationLogConsumer consumer = new MerchantOperationLogConsumer(
                operLogService, idempotentService, properties, converter);

        consumer.onMessage(JsonUtils.toJsonString(message));

        verify(converter, never()).toRecordRequest(message);
        verify(operLogService, never()).recordOperLog(org.mockito.ArgumentMatchers.any());
        log.info("商户端审计重复消息测试完成，结果: 未产生数据库写入");
    }

    private OperationLogMessage message(String idempotentKey) {
        OperationLogMessage message = new OperationLogMessage();
        message.setMessageId("MSG-" + idempotentKey);
        message.setIdempotentKey(idempotentKey);
        return message;
    }
}
