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

    public DefaultMerchantLimitReservationCounterService(StringRedisTemplate redisTemplate,
                                                         PaymentRedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
    }

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
                    log.error("event: RISK_MERCHANT_LIMIT_LIFECYCLE_ROLLBACK_EMPTY_RESULT transactionId: {} ruleId: {} path: {} aggregateKeyDigest: {}",
                            reservation.getTransactionId(),
                            reservation.getRuleId(),
                            projection.path(),
                            RedisKeyDigest.sha256(projection.aggregateKey()));
                }
            } catch (RuntimeException exception) {
                successful = false;
                log.error("event: RISK_MERCHANT_LIMIT_LIFECYCLE_ROLLBACK_FAILED transactionId: {} ruleId: {} path: {} aggregateKeyDigest: {} exceptionType: {}",
                        reservation == null ? null : reservation.getTransactionId(),
                        reservation == null ? null : reservation.getRuleId(),
                        projection.path(),
                        RedisKeyDigest.sha256(projection.aggregateKey()),
                        exception.getClass().getSimpleName());
            }
        }
        return successful;
    }

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
            log.warn("event: RISK_MERCHANT_LIMIT_MARKER_CHECK_FAILED transactionId: {} ruleId: {} exceptionType: {}",
                    reservation == null ? null : reservation.getTransactionId(),
                    reservation == null ? null : reservation.getRuleId(),
                    exception.getClass().getSimpleName());
            return RedisReservationMarkerState.UNKNOWN;
        }
    }

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

    private static DefaultRedisScript<Long> redisScript(String filename) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_BASE_PATH + filename));
        script.setResultType(Long.class);
        return script;
    }

    private record CounterProjection(String aggregateKey,
                                     String reservationKey,
                                     String path) {
    }
}
