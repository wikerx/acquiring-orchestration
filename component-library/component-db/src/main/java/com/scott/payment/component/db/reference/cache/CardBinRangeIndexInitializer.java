package com.scott.payment.component.db.reference.cache;

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
 * @description : 支付与 OpenAPI 共用的 Card BIN 分片索引初始化器，允许任一查询服务独立补齐同一份 Redis 快照
 * @status : create
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(
        prefix = "payment.card-bin.cache",
        name = "index-initializer-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class CardBinRangeIndexInitializer implements ApplicationRunner {

    private static final String CACHE_NAMESPACE = "card-bin-range";
    private static final Duration INITIALIZATION_GATE_TTL = Duration.ofMinutes(30);

    private final CardBinRangeIndex rangeIndex;
    private final RedisCacheGenerationStore generationStore;

    /**
     * 创建共享索引初始化器。
     *
     * @param rangeIndex 从 SLAVE 构建并发布 Redis 分片的公共组件
     * @param generationStore 支付、OpenAPI 和管理端共用的发布代际门禁
     */
    public CardBinRangeIndexInitializer(CardBinRangeIndex rangeIndex,
                                        RedisCacheGenerationStore generationStore) {
        this.rangeIndex = rangeIndex;
        this.generationStore = generationStore;
    }

    /**
     * 在服务启动阶段补齐缺失或格式过期的索引。
     *
     * <p>支付与 OpenAPI 可以同时启用该初始化器。Redis generation 门禁保证集群中只有一个实例
     * 扫描从库并发布，其余实例竞争失败后继续使用单条 SLAVE SQL 降级，不会重复构建百万级快照。
     * 已发布索引的 generation 和格式均正确时直接返回，不访问 Card BIN 数据表。</p>
     *
     * @param args Spring Boot 启动参数，本初始化过程不读取业务参数
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
            // 初始化失败不阻断服务启动；业务查询退化为一条从库区间 SQL，并等待 MQ 或下次启动重建。
            log.warn("event: CARD_BIN_RANGE_INDEX_INITIALIZATION_FAILED exceptionType: {}",
                    exception.getClass().getSimpleName(), exception);
        }
    }
}
