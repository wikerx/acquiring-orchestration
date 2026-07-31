package com.scott.payment.risk.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.risk.repository.RiskAuditRecordWriter;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskEvaluationAuditConsumerTests
 * @date : 2026-07-30 18:30
 * @email : scott_x@163.com
 * @description : 验证风控审计在 Redis 获取、重复和降级三种结果下的数据库事务与失败释放边界
 * @status : create
 */
@Slf4j
class RiskEvaluationAuditConsumerTests {

    @Test
    void shouldReleaseIdempotentKeyWhenDatabaseWriteFails() {
        log.info("测试风控审计失败释放，关键输入: Redis ACQUIRED、数据库不可用");
        RecordingIdempotentService idempotentService =
                new RecordingIdempotentService(IdempotentAcquireResult.ACQUIRED);
        RiskAuditRecordWriter writer = message -> {
            throw new IllegalStateException("database unavailable");
        };
        RiskEvaluationAuditConsumer consumer = new RiskEvaluationAuditConsumer(
                writer, idempotentService, new RiskEvaluationProperties());

        assertThatThrownBy(() -> consumer.onMessage(payload("RK202607280001")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        assertThat(idempotentService.releasedKeys)
                .containsExactly("risk-audit:RK202607280001");
        log.info("风控审计失败释放测试完成，结果: 原异常上抛且 Redis 占用已释放");
    }

    @Test
    void shouldKeepIdempotentKeyAfterSuccessfulWrite() {
        log.info("测试风控审计成功消费，关键输入: Redis ACQUIRED、数据库写入成功");
        RecordingIdempotentService idempotentService =
                new RecordingIdempotentService(IdempotentAcquireResult.ACQUIRED);
        List<String> written = new ArrayList<>();
        RiskAuditRecordWriter writer = message -> written.add(message.getRiskRecordNo());
        RiskEvaluationAuditConsumer consumer = new RiskEvaluationAuditConsumer(
                writer, idempotentService, new RiskEvaluationProperties());

        consumer.onMessage(payload("RK202607280002"));

        assertThat(written).containsExactly("RK202607280002");
        assertThat(idempotentService.releasedKeys).isEmpty();
        log.info("风控审计成功消费测试完成，结果: Redis 占用保留到 TTL 自然过期");
    }

    @Test
    void shouldSkipDatabaseWriteForDuplicateMessage() {
        log.info("测试风控审计重复消息，关键输入: Redis DUPLICATE");
        RecordingIdempotentService idempotentService =
                new RecordingIdempotentService(IdempotentAcquireResult.DUPLICATE);
        List<String> written = new ArrayList<>();
        RiskAuditRecordWriter writer = message -> written.add(message.getRiskRecordNo());
        RiskEvaluationAuditConsumer consumer = new RiskEvaluationAuditConsumer(
                writer, idempotentService, new RiskEvaluationProperties());

        consumer.onMessage(payload("RK202607280003"));

        assertThat(written).isEmpty();
        assertThat(idempotentService.releasedKeys).isEmpty();
        log.info("风控审计重复消息测试完成，结果: 未产生数据库写入");
    }

    @Test
    void shouldContinueToDatabaseWithoutReleaseWhenRedisFallsBack() {
        log.info("测试风控审计 Redis 降级，关键输入: FALLBACK、数据库写入成功");
        RecordingIdempotentService idempotentService =
                new RecordingIdempotentService(IdempotentAcquireResult.FALLBACK);
        List<String> written = new ArrayList<>();
        RiskAuditRecordWriter writer = message -> written.add(message.getRiskRecordNo());
        RiskEvaluationAuditConsumer consumer = new RiskEvaluationAuditConsumer(
                writer, idempotentService, new RiskEvaluationProperties());

        consumer.onMessage(payload("RK202607280004"));

        assertThat(written).containsExactly("RK202607280004");
        assertThat(idempotentService.releasedKeys).isEmpty();
        log.info("风控审计 Redis 降级测试完成，结果: 继续数据库唯一约束且未释放 Redis");
    }

    private String payload(String riskRecordNo) {
        RiskEvaluationAuditMessage message = new RiskEvaluationAuditMessage();
        message.setRiskRecordNo(riskRecordNo);
        message.setDecisionResult("PASS");
        return JsonUtils.toJsonString(message);
    }

    private static class RecordingIdempotentService implements IdempotentService {

        /** 当前用例预设的 MQ 幂等获取结果。 */
        private final IdempotentAcquireResult acquireResult;

        /** 记录消费失败后释放的 namespace 与业务键，供断言失败补偿边界。 */
        private final List<String> releasedKeys = new ArrayList<>();

        private RecordingIdempotentService(IdempotentAcquireResult acquireResult) {
            this.acquireResult = acquireResult;
        }

        /**
         * 模拟旧版布尔幂等接口，重复结果返回获取失败，其余结果允许继续处理。
         *
         * @param idempotentKey 完整幂等键
         * @param ttlSeconds 幂等占用有效期，单位秒
         * @return 预设结果不是 DUPLICATE 时返回 {@code true}
         */
        @Override
        public boolean acquire(String idempotentKey, long ttlSeconds) {
            return acquireResult != IdempotentAcquireResult.DUPLICATE;
        }

        /**
         * 返回用例预设的 MQ 幂等结果，覆盖获取成功、重复消息和 Redis 降级路径。
         *
         * @param namespace MQ 消费幂等命名空间
         * @param businessKey 风控流水号业务键
         * @param ttlSeconds 幂等占用有效期，单位秒
         * @return 当前用例配置的获取结果
         */
        @Override
        public IdempotentAcquireResult acquireMq(String namespace, String businessKey, long ttlSeconds) {
            return acquireResult;
        }

        /**
         * 记录数据库写入失败后释放的 MQ 幂等键，不访问真实 Redis。
         *
         * @param namespace MQ 消费幂等命名空间
         * @param businessKey 风控流水号业务键
         * @param ttlSeconds 原幂等占用有效期，单位秒
         */
        @Override
        public void releaseMq(String namespace, String businessKey, long ttlSeconds) {
            releasedKeys.add(namespace + ":" + businessKey);
        }
    }
}
