package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.component.mq.properties.OperationLogMqProperties;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import com.scott.payment.data.service.OperationLogPersistenceService;
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
 * @classname : OperationLogConsumerServiceTests
 * @date : 2026-08-01 14:40
 * @email : scott_x@163.com
 * @description : service-data 操作日志消费幂等测试，覆盖成功、重复、Redis 降级和写库失败释放
 * @status : create
 */
@Slf4j
class OperationLogConsumerServiceTests {

    /** Redis 取得占用时应执行一次数据库写入。 */
    @Test
    void shouldPersistAcquiredOperationLog() {
        log.info("测试异步操作日志正常消费，关键输入: ADMIN、Redis ACQUIRED");
        Fixture fixture = fixture(OperationLogSource.ADMIN, "ADMIN-LOG-001", IdempotentAcquireResult.ACQUIRED);

        assertThatCode(() -> fixture.service().consume(OperationLogSource.ADMIN, fixture.payload()))
                .doesNotThrowAnyException();

        verify(fixture.persistenceService()).persist(fixture.message(), "ADMIN-LOG-001");
        log.info("异步操作日志正常消费完成，结果: 数据库写入 1 次");
    }

    /** Redis 已确认重复时不应再次访问数据库。 */
    @Test
    void shouldSkipRedisDuplicate() {
        log.info("测试异步操作日志重复消费，关键输入: MERCHANT、Redis DUPLICATE");
        Fixture fixture = fixture(OperationLogSource.MERCHANT, "MERCHANT-LOG-001", IdempotentAcquireResult.DUPLICATE);

        fixture.service().consume(OperationLogSource.MERCHANT, fixture.payload());

        verify(fixture.persistenceService(), never()).persist(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        log.info("异步操作日志重复消费完成，结果: 未访问数据库");
    }

    /** Redis 不可用时必须继续依赖数据库唯一键，不能丢弃审计消息。 */
    @Test
    void shouldContinueToDatabaseWhenRedisFallsBack() {
        log.info("测试异步操作日志 Redis 降级，关键输入: ADMIN、Redis FALLBACK");
        Fixture fixture = fixture(OperationLogSource.ADMIN, "ADMIN-LOG-002", IdempotentAcquireResult.FALLBACK);

        fixture.service().consume(OperationLogSource.ADMIN, fixture.payload());

        verify(fixture.persistenceService()).persist(fixture.message(), "ADMIN-LOG-002");
        verify(fixture.idempotentService(), never()).releaseMq(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
        log.info("异步操作日志 Redis 降级完成，结果: 继续数据库最终幂等且未误释放");
    }

    /** 数据库写入失败时只释放当前 ACQUIRED 占用并上抛原异常。 */
    @Test
    void shouldReleaseAcquiredClaimWhenDatabaseWriteFails() {
        log.info("测试异步操作日志失败重试，关键输入: MERCHANT、数据库异常");
        Fixture fixture = fixture(OperationLogSource.MERCHANT, "MERCHANT-LOG-002", IdempotentAcquireResult.ACQUIRED);
        doThrow(new IllegalStateException("database unavailable"))
                .when(fixture.persistenceService()).persist(fixture.message(), "MERCHANT-LOG-002");

        assertThatThrownBy(() -> fixture.service().consume(OperationLogSource.MERCHANT, fixture.payload()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(fixture.idempotentService()).releaseMq(
                "merchant-operation-log", "MERCHANT-LOG-002", fixture.properties().getConsumeIdempotentTtlSeconds());
        log.info("异步操作日志失败重试完成，结果: Redis 占用已释放且原异常上抛");
    }

    /** 创建指定来源和幂等结果的测试夹具。 */
    private Fixture fixture(OperationLogSource source,
                            String idempotentKey,
                            IdempotentAcquireResult acquireResult) {
        OperationLogPersistenceService persistenceService = mock(OperationLogPersistenceService.class);
        IdempotentService idempotentService = mock(IdempotentService.class);
        OperationLogMqProperties properties = new OperationLogMqProperties();
        OperationLogMessage message = new OperationLogMessage();
        message.setMessageId("MSG-" + idempotentKey);
        message.setIdempotentKey(idempotentKey);
        message.setSystemCode(source.name());
        when(idempotentService.acquireMq(
                source.getIdempotentNamespace(),
                idempotentKey,
                properties.getConsumeIdempotentTtlSeconds()
        )).thenReturn(acquireResult);
        OperationLogConsumerService service = new OperationLogConsumerService(
                persistenceService, idempotentService, properties);
        return new Fixture(
                service,
                persistenceService,
                idempotentService,
                properties,
                message,
                JsonUtils.toJsonString(message)
        );
    }

    /** 操作日志消费测试依赖集合。 */
    private record Fixture(OperationLogConsumerService service,
                           OperationLogPersistenceService persistenceService,
                           IdempotentService idempotentService,
                           OperationLogMqProperties properties,
                           OperationLogMessage message,
                           String payload) {
    }
}
