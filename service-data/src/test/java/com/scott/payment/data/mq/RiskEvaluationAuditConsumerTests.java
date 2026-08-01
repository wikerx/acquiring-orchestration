package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.component.mq.properties.RiskAuditMqProperties;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import com.scott.payment.data.service.RiskAuditPersistenceService;
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
 * @classname : RiskEvaluationAuditConsumerTests
 * @date : 2026-08-01 14:50
 * @email : scott_x@163.com
 * @description : service-data 风控审计消费测试，覆盖重复消息、Redis 降级和数据库失败释放
 * @status : create
 */
@Slf4j
class RiskEvaluationAuditConsumerTests {

    /** Redis 重复结果应跳过数据库写入。 */
    @Test
    void shouldSkipRedisDuplicate() {
        log.info("测试风控审计重复消费，关键输入: Redis DUPLICATE");
        Fixture fixture = fixture(IdempotentAcquireResult.DUPLICATE);

        fixture.consumer().onMessage(fixture.payload());

        verify(fixture.persistenceService(), never()).persist(fixture.message());
        log.info("风控审计重复消费完成，结果: 未访问数据库");
    }

    /** Redis 不可用时应继续依赖数据库唯一键。 */
    @Test
    void shouldContinueToDatabaseWhenRedisFallsBack() {
        log.info("测试风控审计 Redis 降级，关键输入: FALLBACK");
        Fixture fixture = fixture(IdempotentAcquireResult.FALLBACK);

        fixture.consumer().onMessage(fixture.payload());

        verify(fixture.persistenceService()).persist(fixture.message());
        verify(fixture.idempotentService(), never()).releaseMq(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
        log.info("风控审计 Redis 降级完成，结果: 继续数据库最终幂等");
    }

    /** 数据库失败时应释放 ACQUIRED 占用并上抛异常触发重试。 */
    @Test
    void shouldReleaseAcquiredClaimWhenDatabaseFails() {
        log.info("测试风控审计失败重试，关键输入: ACQUIRED、数据库异常");
        Fixture fixture = fixture(IdempotentAcquireResult.ACQUIRED);
        doThrow(new IllegalStateException("database unavailable"))
                .when(fixture.persistenceService()).persist(fixture.message());

        assertThatThrownBy(() -> fixture.consumer().onMessage(fixture.payload()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(fixture.idempotentService()).releaseMq(
                "risk-audit", "RK202608010001", fixture.properties().getConsumeIdempotentTtlSeconds());
        log.info("风控审计失败重试完成，结果: Redis 占用已释放且原异常上抛");
    }

    /** 创建指定 Redis 结果的消费者测试夹具。 */
    private Fixture fixture(IdempotentAcquireResult acquireResult) {
        RiskAuditPersistenceService persistenceService = mock(RiskAuditPersistenceService.class);
        IdempotentService idempotentService = mock(IdempotentService.class);
        RiskAuditMqProperties properties = new RiskAuditMqProperties();
        RiskEvaluationAuditMessage message = new RiskEvaluationAuditMessage();
        message.setMessageId("MSG-RISK-001");
        message.setRiskRecordNo("RK202608010001");
        when(idempotentService.acquireMq(
                "risk-audit", "RK202608010001", properties.getConsumeIdempotentTtlSeconds()))
                .thenReturn(acquireResult);
        RiskEvaluationAuditConsumer consumer = new RiskEvaluationAuditConsumer(
                persistenceService, idempotentService, properties);
        return new Fixture(
                consumer,
                persistenceService,
                idempotentService,
                properties,
                message,
                JsonUtils.toJsonString(message)
        );
    }

    /** 风控审计消费测试依赖集合。 */
    private record Fixture(RiskEvaluationAuditConsumer consumer,
                           RiskAuditPersistenceService persistenceService,
                           IdempotentService idempotentService,
                           RiskAuditMqProperties properties,
                           RiskEvaluationAuditMessage message,
                           String payload) {
    }
}
