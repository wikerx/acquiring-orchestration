package com.scott.payment.payment.config;

import com.scott.payment.component.db.reference.cache.CardBinRangeIndex;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.generation.RedisCachePublication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinRangeIndexInitializer
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 支付服务启动阶段的 Card BIN 分片索引初始化器，使用 generation 门禁保证集群内只有一个实例执行发布
 * @status : create
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(
        prefix = "payment.card-bin.cache",
        name = "index-initializer-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CardBinRangeIndexInitializer implements ApplicationRunner {

    private static final String CACHE_NAMESPACE = "card-bin-range";
    private static final Duration INITIALIZATION_GATE_TTL = Duration.ofMinutes(30);

    private final CardBinRangeIndex rangeIndex;
    private final RedisCacheGenerationStore generationStore;

    public CardBinRangeIndexInitializer(CardBinRangeIndex rangeIndex,
                                        RedisCacheGenerationStore generationStore) {
        this.rangeIndex = rangeIndex;
        this.generationStore = generationStore;
    }

    /**
     * 在应用进入就绪状态前补齐首次索引；完成标记与当前 generation 一致时不重复扫描百万级基础数据。
     */
    @Override
    public void run(ApplicationArguments args) {
        if (rangeIndex.hasActiveSnapshot()) {
            return;
        }
        RedisCachePublication publication = null;
        try {
            publication = generationStore.begin(CACHE_NAMESPACE, INITIALIZATION_GATE_TTL);
            rangeIndex.publishFromReplica(publication.generation());
            if (!generationStore.commit(publication)) {
                throw new IllegalStateException("Card BIN initial generation commit failed");
            }
        } catch (RuntimeException exception) {
            if (publication != null) {
                try {
                    generationStore.abort(publication);
                } catch (RuntimeException abortException) {
                    exception.addSuppressed(abortException);
                }
            }
            // 初始化失败时查询路径仍会执行单次 SLAVE SQL；后续 MQ 变更或重启可再次构建。
            log.warn("event: CARD_BIN_RANGE_INDEX_INITIALIZATION_FAILED exceptionType: {}",
                    exception.getClass().getSimpleName(), exception);
        }
    }
}
