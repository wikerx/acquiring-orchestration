package com.scott.payment.admin.application.risk.cache;

import com.scott.payment.admin.entity.RiskCacheInvalidationOutboxDO;
import com.scott.payment.admin.mapper.RiskCacheInvalidationOutboxMapper;
import com.scott.payment.admin.observability.CacheInvalidationOutboxMetrics;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.generation.RedisCachePublication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 风控规则缓存失效事件发布服务。
 */
@Slf4j
@Service
public class RiskCacheInvalidationRelayService {

    /** generation 发布失败后的固定重试间隔，单位秒。 */
    private static final long RETRY_DELAY_SECONDS = 5L;

    /** 原发布凭证过期后重新申请的短期恢复门禁有效期。 */
    private static final Duration RECOVERY_GATE_TTL = Duration.ofSeconds(30);

    /** 查询并以乐观锁更新风控缓存失效事件。 */
    private final RiskCacheInvalidationOutboxMapper outboxMapper;

    /** 负责 generation 发布凭证的申请、提交和回滚。 */
    private final RedisCacheGenerationStore generationStore;

    /** 风控缓存失效 Outbox 低基数指标记录器。 */
    private final CacheInvalidationOutboxMetrics metrics;

    /**
     * 创建风控规则缓存 generation 发布中继服务。
     *
     * @param outboxMapper 风控缓存失效 Outbox 数据访问组件
     * @param generationStore Redis generation 状态存储
     * @param metrics 缓存失效 Outbox 指标记录器
     */
    @Autowired
    public RiskCacheInvalidationRelayService(RiskCacheInvalidationOutboxMapper outboxMapper,
                                             RedisCacheGenerationStore generationStore,
                                             CacheInvalidationOutboxMetrics metrics) {
        this.outboxMapper = outboxMapper;
        this.generationStore = generationStore;
        this.metrics = metrics;
    }

    /**
     * 创建不产生指标副作用的风控缓存失效中继服务，供纯单元测试直接构造。
     *
     * @param outboxMapper 风控缓存失效 Outbox 数据访问组件
     * @param generationStore Redis generation 状态存储
     */
    public RiskCacheInvalidationRelayService(RiskCacheInvalidationOutboxMapper outboxMapper,
                                             RedisCacheGenerationStore generationStore) {
        this(outboxMapper, generationStore, CacheInvalidationOutboxMetrics.noop());
    }

    /**
     * 在独立事务中发布指定失效事件。
     *
     * @param eventId 事件号
     * @return 是否发布成功
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publish(String eventId) {
        RiskCacheInvalidationOutboxDO event = outboxMapper.selectByEventId(eventId);
        return event != null && publishEvent(event, LocalDateTime.now());
    }

    /**
     * 在独立事务中发布一批到期事件。
     *
     * @param limit 最大处理数量
     * @return 成功数量
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int publishDueEvents(int limit) {
        long startNanos = System.nanoTime();
        try {
            List<RiskCacheInvalidationOutboxDO> events =
                    outboxMapper.selectDueEvents(LocalDateTime.now(), limit);
            if (events == null || events.isEmpty()) {
                metrics.recordBatch(
                        CacheInvalidationOutboxMetrics.Outbox.RISK_RULE,
                        0,
                        limit,
                        0,
                        System.nanoTime() - startNanos
                );
                return 0;
            }
            int successCount = 0;
            for (RiskCacheInvalidationOutboxDO event : events) {
                if (publishEvent(event, LocalDateTime.now())) {
                    successCount++;
                }
            }
            metrics.recordBatch(
                    CacheInvalidationOutboxMetrics.Outbox.RISK_RULE,
                    events.size(),
                    limit,
                    successCount,
                    System.nanoTime() - startNanos
            );
            return successCount;
        } catch (RuntimeException exception) {
            metrics.recordBatchError(
                    CacheInvalidationOutboxMetrics.Outbox.RISK_RULE,
                    System.nanoTime() - startNanos
            );
            throw exception;
        }
    }

    /**
     * 提交事件持有的 generation 并以 CAS 推进 Outbox，过期凭证只允许受控替换一次。
     *
     * @param event 待发布的 INIT/FAILED 事件
     * @param now 本次发布尝试时间
     * @return generation 已切换且事件已发送或被并发方发送时返回 {@code true}
     */
    private boolean publishEvent(RiskCacheInvalidationOutboxDO event, LocalDateTime now) {
        if ("SENT".equals(event.getEventStatus())) {
            return true;
        }
        if (!"INIT".equals(event.getEventStatus()) && !"FAILED".equals(event.getEventStatus())) {
            return false;
        }
        RedisCachePublication publication = publication(event);
        int version = event.getVersion() == null ? 0 : event.getVersion();
        try {
            if (!generationStore.commit(publication)) {
                publication = replaceExpiredPublication(event, version, now);
                if (publication == null) {
                    return false;
                }
                version++;
                if (!generationStore.commit(publication)) {
                    throw new IllegalStateException("Redis cache generation publication was not committed");
                }
            }
            int marked = outboxMapper.markSent(event.getId(), version, LocalDateTime.now());
            return marked == 1 || alreadySent(event.getEventId());
        } catch (RuntimeException exception) {
            LocalDateTime nextRetryTime = now.plusSeconds(RETRY_DELAY_SECONDS);
            outboxMapper.markFailed(
                    event.getId(),
                    version,
                    nextRetryTime,
                    failureReason(exception),
                    now
            );
            log.warn(
                    "event: RISK_CACHE_INVALIDATION_PUBLISH_FAILED eventId: {} retryCount: {} "
                            + "nextRetryTime: {} exceptionType: {}",
                    event.getEventId(),
                    event.getRetryCount(),
                    nextRetryTime,
                    exception.getClass().getSimpleName()
            );
            return false;
        }
    }

    /**
     * 原发布门禁过期时申请替代凭证，并先通过 Outbox CAS 取得替换所有权。
     *
     * <p>CAS 失败或持久化异常时必须回滚新凭证，防止遗留未确认 generation 门禁。</p>
     *
     * @param event 原失效事件
     * @param version 调用方读取到的 Outbox 版本
     * @param now 凭证替换时间
     * @return 持久化成功的新发布凭证；并发方已更新时返回 {@code null}
     */
    private RedisCachePublication replaceExpiredPublication(RiskCacheInvalidationOutboxDO event,
                                                            int version,
                                                            LocalDateTime now) {
        RedisCachePublication replacement = generationStore.begin(
                event.getNamespace(),
                RECOVERY_GATE_TTL
        );
        try {
            int replaced = outboxMapper.replacePublication(
                    event.getId(),
                    version,
                    replacement.token(),
                    replacement.generation(),
                    now
            );
            if (replaced != 1) {
                generationStore.abort(replacement);
                return null;
            }
            return replacement;
        } catch (RuntimeException exception) {
            try {
                generationStore.abort(replacement);
            } catch (RuntimeException abortException) {
                exception.addSuppressed(abortException);
            }
            throw exception;
        }
    }

    /**
     * 从 Outbox 恢复原 generation 发布凭证；token 仅用于内部提交校验。
     *
     * @param event 风控缓存失效事件
     * @return 可提交的 generation 发布凭证
     */
    private RedisCachePublication publication(RiskCacheInvalidationOutboxDO event) {
        return new RedisCachePublication(
                event.getNamespace(),
                event.getPublicationToken(),
                event.getGeneration()
        );
    }

    /**
     * CAS 未更新时复查事件是否已被并发发布方推进到 SENT。
     *
     * @param eventId 风控缓存失效事件唯一标识
     * @return 最新状态为 SENT 时返回 {@code true}
     */
    private boolean alreadySent(String eventId) {
        RiskCacheInvalidationOutboxDO latest = outboxMapper.selectByEventId(eventId);
        return latest != null && "SENT".equals(latest.getEventStatus());
    }

    /**
     * 提取并截断可持久化失败原因，避免异常文本无限增长。
     *
     * @param exception generation 发布异常
     * @return 最多 512 个字符的非空失败原因
     */
    private String failureReason(RuntimeException exception) {
        String failureType = exception.getClass().getSimpleName();
        return StringUtils.hasText(failureType) ? failureType : "RiskCachePublishException";
    }
}
