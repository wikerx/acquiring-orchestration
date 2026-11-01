package com.scott.payment.risk.service.impl;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.risk.domain.RedisReservationMarkerState;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import com.scott.payment.risk.service.MerchantLimitReservationCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 按预占时计数模式重建 Redis 投影并执行幂等撤销。
 */
@Slf4j
@Service
public class DefaultMerchantLimitReservationCounterService
        implements MerchantLimitReservationCounterService {

    private static final String CLUSTER_SAFE_COUNTER_MODE = "CLUSTER_SAFE";

    private static final String SCRIPT_BASE_PATH = "META-INF/payment/redis/scripts/v1/";

    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT =
            redisScript("merchant-limit-rollback.lua");

    private final StringRedisTemplate redisTemplate;

    private final PaymentRedisProperties redisProperties;

    /**
     * 创建累计限额 Redis 计数投影服务。
     *
     * @param redisTemplate 执行固定版本 Lua 的 Redis 模板
     * @param redisProperties 构造环境隔离和 Cluster 共槽 Key 的配置
     */
    public DefaultMerchantLimitReservationCounterService(StringRedisTemplate redisTemplate,
                                                         PaymentRedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
    }

    /**
     * 按持久化预占快照幂等回滚累计限额 Redis 投影。
     *
     * @param reservation 已锁定或由对账读取的持久化预占
     * @return 全部已知投影回滚成功时返回 {@code true}
     */
    @Override
    public boolean rollback(MerchantLimitReservationDO reservation) {
        List<CounterProjection> projections = projections(reservation);
        if (projections.isEmpty()) {
            return false;
        }
        boolean successful = true;
        for (int index = projections.size() - 1; index >= 0; index--) {
            CounterProjection projection = projections.get(index);
            try {
                Long result = redisTemplate.execute(
                        ROLLBACK_SCRIPT,
                        List.of(projection.aggregateKey(), projection.reservationKey()));
                if (result == null) {
                    successful = false;
                    log.error("商户累计限额生命周期回滚结果为空, event: RISK_MERCHANT_LIMIT_LIFECYCLE_ROLLBACK_EMPTY_RESULT, transactionId: {}, ruleId: {}, path: {}, aggregateKeyDigest: {}",
                            reservation.getTransactionId(),
                            reservation.getRuleId(),
                            projection.path(),
                            RedisKeyDigest.sha256(projection.aggregateKey()));
                }
            } catch (RuntimeException exception) {
                successful = false;
                log.error("商户累计限额生命周期回滚失败, event: RISK_MERCHANT_LIMIT_LIFECYCLE_ROLLBACK_FAILED, transactionId: {}, ruleId: {}, path: {}, aggregateKeyDigest: {}, exceptionType: {}",
                        reservation == null ? null : reservation.getTransactionId(),
                        reservation == null ? null : reservation.getRuleId(),
                        projection.path(),
                        RedisKeyDigest.sha256(projection.aggregateKey()),
                        exception.getClass().getSimpleName());
            }
        }
        return successful;
    }

    /**
     * 检查持久化预占对应的 Redis 幂等标记。
     *
     * @param reservation 持久化预占记录
     * @return 存在、不存在或基础设施未知状态
     */
    @Override
    public RedisReservationMarkerState markerState(MerchantLimitReservationDO reservation) {
        List<CounterProjection> projections = projections(reservation);
        if (projections.isEmpty()) {
            return RedisReservationMarkerState.UNKNOWN;
        }
        boolean found = false;
        try {
            for (CounterProjection projection : projections) {
                found = found || Boolean.TRUE.equals(redisTemplate.hasKey(projection.reservationKey()));
            }
            return found ? RedisReservationMarkerState.PRESENT : RedisReservationMarkerState.ABSENT;
        } catch (RuntimeException exception) {
            log.warn("商户累计限额 Redis 标记检查失败, event: RISK_MERCHANT_LIMIT_MARKER_CHECK_FAILED, transactionId: {}, ruleId: {}, exceptionType: {}",
                    reservation == null ? null : reservation.getTransactionId(),
                    reservation == null ? null : reservation.getRuleId(),
                    exception.getClass().getSimpleName());
            return RedisReservationMarkerState.UNKNOWN;
        }
    }

    /**
     * 根据持久化计数模式恢复需要检查或回滚的投影。
     *
     * @param reservation 持久化预占记录
     * @return 当前版本支持的投影；输入不完整或模式未知时为空
     */
    private List<CounterProjection> projections(MerchantLimitReservationDO reservation) {
        if (!valid(reservation)) {
            return List.of();
        }
        if (!CLUSTER_SAFE_COUNTER_MODE.equals(
                reservation.getCounterMode().trim().toUpperCase(Locale.ROOT))) {
            return List.of();
        }
        return List.of(clusterSafeProjection(reservation));
    }

    /**
     * 从持久化业务字段重新构造 Cluster 共槽的累计值和幂等标记 Key。
     *
     * @param reservation 持久化预占记录
     * @return 不包含原始交易号的 Redis 计数投影
     */
    private CounterProjection clusterSafeProjection(MerchantLimitReservationDO reservation) {
        String slotIdentity = String.join(
                ":",
                reservation.getLimitType().trim().toLowerCase(Locale.ROOT),
                String.valueOf(reservation.getRuleId()),
                reservation.getMerchantId().trim(),
                reservation.getCurrency().trim().toUpperCase(Locale.ROOT),
                reservation.getPeriodBucket().trim());
        String transactionDigest = RedisKeyDigest.sha256(reservation.getTransactionId().trim());
        return new CounterProjection(
                redisProperties.coLocatedBusinessKey(
                        "risk", "merchant-limit", slotIdentity, "total"),
                redisProperties.coLocatedBusinessKey(
                        "risk", "merchant-limit", slotIdentity, "reservation", transactionDigest),
                "cluster-safe");
    }

    /**
     * 校验重新构造 Redis 投影所需的持久化字段完整性。
     *
     * @param reservation 持久化预占记录
     * @return 所有必需字段存在时返回 {@code true}
     */
    private boolean valid(MerchantLimitReservationDO reservation) {
        return reservation != null
                && StringUtils.hasText(reservation.getTransactionId())
                && StringUtils.hasText(reservation.getMerchantId())
                && reservation.getRuleId() != null
                && StringUtils.hasText(reservation.getLimitType())
                && StringUtils.hasText(reservation.getCurrency())
                && StringUtils.hasText(reservation.getPeriodBucket())
                && StringUtils.hasText(reservation.getCounterMode());
    }

    /**
     * 从固定类路径加载返回长整数的累计限额 Lua 脚本。
     *
     * @param filename 受代码控制的脚本文件名
     * @return Spring Data Redis 脚本定义
     */
    private static DefaultRedisScript<Long> redisScript(String filename) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_BASE_PATH + filename));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 描述同一持久化预占对应的 Redis 累计值和幂等标记。
     *
     * @param aggregateKey 累计金额整数投影 Key
     * @param reservationKey 单交易幂等标记 Key
     * @param path 便于不含敏感值的错误分类
     */
    private record CounterProjection(String aggregateKey,
                                     String reservationKey,
                                     String path) {
    }
}
