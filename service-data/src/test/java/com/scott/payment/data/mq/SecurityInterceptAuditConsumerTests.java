package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.SecurityInterceptAuditMessage;
import com.scott.payment.component.mq.properties.SecurityAuditMqProperties;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import com.scott.payment.data.service.SecurityInterceptAuditPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SecurityInterceptAuditConsumerTests
 * @date : 2026-08-01 18:00
 * @email : scott_x@163.com
 * @description : service-data 安全拦截审计消费测试，覆盖重复消息、Redis 降级与数据库失败重试
 * @status : create
 */
@Slf4j
class SecurityInterceptAuditConsumerTests {

    /** Redis 判定重复时应跳过数据库写入。 */
    @Test
    void shouldSkipRedisDuplicate() {
        log.info("测试安全审计重复消费，关键输入: Redis DUPLICATE");
        Fixture fixture = fixture(IdempotentAcquireResult.DUPLICATE);

        fixture.consumer().onMessage(fixture.payload());

        verify(fixture.persistenceService(), never()).persist(fixture.message());
        log.info("安全审计重复消费完成，结果: 未访问数据库");
    }

    /** Redis 不可用时应继续依赖数据库唯一键。 */
    @Test
    void shouldContinueToDatabaseWhenRedisFallsBack() {
        log.info("测试安全审计 Redis 降级，关键输入: FALLBACK");
        Fixture fixture = fixture(IdempotentAcquireResult.FALLBACK);

        fixture.consumer().onMessage(fixture.payload());

        verify(fixture.persistenceService()).persist(fixture.message());
        verify(fixture.idempotentService(), never()).releaseMq(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
        log.info("安全审计 Redis 降级完成，结果: 继续由数据库最终幂等");
    }

    /** 数据库失败时应释放 ACQUIRED 占用并上抛异常。 */
    @Test
    void shouldReleaseAcquiredClaimWhenDatabaseFails() {
        log.info("测试安全审计失败重试，关键输入: ACQUIRED、数据库异常");
        Fixture fixture = fixture(IdempotentAcquireResult.ACQUIRED);
        doThrow(new IllegalStateException("database unavailable"))
                .when(fixture.persistenceService()).persist(fixture.message());

        assertThatThrownBy(() -> fixture.consumer().onMessage(fixture.payload()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(fixture.idempotentService()).releaseMq(
                "security-audit", "SIE202608010001", fixture.properties().getConsumeIdempotentTtlSeconds());
        log.info("安全审计失败重试完成，结果: Redis 占用已释放且原异常上抛");
    }

    /** 缺少最小事件字段的消息不得进入 Redis 或数据库。 */
    @Test
    void shouldSkipInvalidMessage() {
        log.info("测试安全审计畸形消息，关键输入: 缺少 eventNo");
        SecurityInterceptAuditPersistenceService persistenceService =
                mock(SecurityInterceptAuditPersistenceService.class);
        IdempotentService idempotentService = mock(IdempotentService.class);
        SecurityInterceptAuditConsumer consumer = new SecurityInterceptAuditConsumer(
                persistenceService, idempotentService, new SecurityAuditMqProperties());

        consumer.onMessage("{\"eventType\":\"OPENAPI_JWT_INVALID\"}");

        verify(persistenceService, never()).persist(org.mockito.ArgumentMatchers.any());
        verify(idempotentService, never()).acquireMq(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
        log.info("安全审计畸形消息测试完成，结果: 已忽略且未进入持久化链路");
    }

    /** 创建指定 Redis 获取结果的消费者测试夹具。 */
    private Fixture fixture(IdempotentAcquireResult acquireResult) {
        SecurityInterceptAuditPersistenceService persistenceService =
                mock(SecurityInterceptAuditPersistenceService.class);
        IdempotentService idempotentService = mock(IdempotentService.class);
        SecurityAuditMqProperties properties = new SecurityAuditMqProperties();
        SecurityInterceptAuditMessage message = message();
        when(idempotentService.acquireMq(
                "security-audit", message.getEventNo(), properties.getConsumeIdempotentTtlSeconds()))
                .thenReturn(acquireResult);
        SecurityInterceptAuditConsumer consumer = new SecurityInterceptAuditConsumer(
                persistenceService, idempotentService, properties);
        return new Fixture(consumer, persistenceService, idempotentService,
                properties, message, JsonUtils.toJsonString(message));
    }

    /** 创建包含最小合法事件字段的脱敏审计消息。 */
    private SecurityInterceptAuditMessage message() {
        SecurityInterceptAuditMessage message = new SecurityInterceptAuditMessage();
        message.setMessageId("MSG-SEC-001");
        message.setEventNo("SIE202608010001");
        message.setEventType("OPENAPI_JWT_INVALID");
        message.setSourceLayer("OPENAPI");
        message.setAction("BLOCK");
        return message;
    }

    /** 安全拦截审计消费测试依赖集合。 */
    private record Fixture(SecurityInterceptAuditConsumer consumer,
                           SecurityInterceptAuditPersistenceService persistenceService,
                           IdempotentService idempotentService,
                           SecurityAuditMqProperties properties,
                           SecurityInterceptAuditMessage message,
                           String payload) {
    }
}
