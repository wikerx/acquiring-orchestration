package com.scott.payment.risk.service.impl;

import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.redis.support.RedisKeyDigest;
import com.scott.payment.risk.domain.FrequencySuccessReservationResult;
import com.scott.payment.risk.domain.FrequencySuccessReservationTransitionSummary;
import com.scott.payment.risk.service.FrequencySuccessReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultFrequencySuccessReservationService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 使用 Redis Cluster 同槽脚本实现频控成功名额生命周期。
 * @status : create
 *
 *
 * <p>同一商户的计数器和交易索引使用同一摘要 Hash Tag，使规则维度预占和终态释放
 * 保持原子性；物理 Key 不包含商户号、交易号或风控元素原文。</p>
 */
@Slf4j
@Service
public class DefaultFrequencySuccessReservationService
        implements FrequencySuccessReservationService {

    private static final String SCRIPT_BASE_PATH = "META-INF/payment/redis/scripts/v1/";

    private static final DefaultRedisScript<List> RESERVE_SCRIPT =
            listScript("frequency-success-reserve.lua");

    private static final DefaultRedisScript<List> TRANSITION_SCRIPT =
            listScript("frequency-success-transition.lua");

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            longScript("frequency-success-release.lua");

    private final StringRedisTemplate redisTemplate;

    private final PaymentRedisProperties redisProperties;

    public DefaultFrequencySuccessReservationService(StringRedisTemplate redisTemplate,
                                                     PaymentRedisProperties redisProperties) {
        this.redisTemplate = redisTemplate;
        this.redisProperties = redisProperties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FrequencySuccessReservationResult reserve(String merchantId,
                                                     String transactionId,
                                                     long ruleId,
                                                     String counterIdentity,
                                                     int successLimit,
                                                     int windowSeconds) {
        if (!validIdentity(merchantId, transactionId)
                || ruleId <= 0L
                || !StringUtils.hasText(counterIdentity)
                || successLimit <= 0
                || windowSeconds <= 0) {
            return FrequencySuccessReservationResult.unavailable();
        }
        String indexKey = transactionIndexKey(merchantId, transactionId);
        String counterKey = counterKey(merchantId, ruleId, counterIdentity);
        try {
            List<?> rawResult = redisTemplate.execute(
                    RESERVE_SCRIPT,
                    List.of(counterKey, indexKey),
                    String.valueOf(successLimit),
                    String.valueOf(windowSeconds));
            if (rawResult == null || rawResult.size() < 2) {
                throw new IllegalStateException("frequency success reserve script returned no result");
            }
            long code = longValue(rawResult.get(0));
            long currentCount = Math.max(0L, longValue(rawResult.get(1)));
            FrequencySuccessReservationResult.Outcome outcome = switch ((int) code) {
                case 1 -> FrequencySuccessReservationResult.Outcome.RESERVED;
                case 2 -> FrequencySuccessReservationResult.Outcome.IDEMPOTENT;
                case 0 -> FrequencySuccessReservationResult.Outcome.LIMIT_EXCEEDED;
                default -> FrequencySuccessReservationResult.Outcome.CLOSED;
            };
            return new FrequencySuccessReservationResult(outcome, currentCount);
        } catch (RuntimeException exception) {
            log.warn("event: RISK_FREQUENCY_SUCCESS_RESERVE_FAILED merchantDigest: {} transactionDigest: {} ruleId: {} exceptionType: {}",
                    RedisKeyDigest.sha256(merchantId.trim()),
                    RedisKeyDigest.sha256(transactionId.trim()),
                    ruleId,
                    exception.getClass().getSimpleName());
            return FrequencySuccessReservationResult.unavailable();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FrequencySuccessReservationTransitionSummary confirm(String merchantId,
                                                                String transactionId) {
        List<?> result = transition(merchantId, transactionId, "CONFIRM");
        long code = transitionCode(result);
        return switch ((int) code) {
            case 1 -> new FrequencySuccessReservationTransitionSummary(1, 0, 0);
            case 2 -> new FrequencySuccessReservationTransitionSummary(0, 1, 0);
            case -1 -> new FrequencySuccessReservationTransitionSummary(0, 0, 1);
            default -> FrequencySuccessReservationTransitionSummary.empty();
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FrequencySuccessReservationTransitionSummary release(String merchantId,
                                                                String transactionId) {
        String indexKey = requireTransactionIndexKey(merchantId, transactionId);
        List<?> transitionResult = transition(indexKey, "RELEASE");
        long code = transitionCode(transitionResult);
        if (code == -1L) {
            return new FrequencySuccessReservationTransitionSummary(0, 0, 1);
        }
        if (code == 3L) {
            return new FrequencySuccessReservationTransitionSummary(0, 1, 0);
        }
        if (code == 0L) {
            return FrequencySuccessReservationTransitionSummary.empty();
        }
        List<String> releaseKeys = releaseKeys(indexKey, transitionResult);
        Long released = redisTemplate.execute(RELEASE_SCRIPT, releaseKeys);
        if (released == null) {
            throw new IllegalStateException("frequency success release script returned no result");
        }
        return new FrequencySuccessReservationTransitionSummary(
                Math.toIntExact(Math.max(0L, released)),
                code == 2L ? 1 : 0,
                0);
    }

    private List<?> transition(String merchantId, String transactionId, String action) {
        return transition(requireTransactionIndexKey(merchantId, transactionId), action);
    }

    private List<?> transition(String indexKey, String action) {
        List<?> result = redisTemplate.execute(TRANSITION_SCRIPT, List.of(indexKey), action);
        if (result == null || result.isEmpty()) {
            throw new IllegalStateException("frequency success transition script returned no result");
        }
        return result;
    }

    private List<String> releaseKeys(String indexKey, List<?> transitionResult) {
        List<String> keys = new ArrayList<>();
        keys.add(indexKey);
        String expectedPrefix = indexKey.substring(0, indexKey.indexOf("}:" ) + 2) + "rule:";
        String expectedTag = hashTag(indexKey);
        for (int index = 1; index < transitionResult.size(); index++) {
            String counterKey = String.valueOf(transitionResult.get(index));
            if (!counterKey.startsWith(expectedPrefix) || !expectedTag.equals(hashTag(counterKey))) {
                throw new IllegalStateException("frequency success transition returned an invalid counter key");
            }
            keys.add(counterKey);
        }
        return keys;
    }

    private String requireTransactionIndexKey(String merchantId, String transactionId) {
        if (!validIdentity(merchantId, transactionId)) {
            throw new IllegalArgumentException("frequency success transaction identity is incomplete");
        }
        return transactionIndexKey(merchantId, transactionId);
    }

    private String transactionIndexKey(String merchantId, String transactionId) {
        return redisProperties.coLocatedBusinessKey(
                "risk",
                "frequency-success",
                merchantSlotIdentity(merchantId),
                "transaction",
                RedisKeyDigest.sha256(transactionId.trim()),
                "reservations");
    }

    private String counterKey(String merchantId, long ruleId, String counterIdentity) {
        return redisProperties.coLocatedBusinessKey(
                "risk",
                "frequency-success",
                merchantSlotIdentity(merchantId),
                "rule",
                String.valueOf(ruleId),
                "element",
                RedisKeyDigest.sha256(counterIdentity.trim()),
                "counter");
    }

    private String merchantSlotIdentity(String merchantId) {
        return RedisKeyDigest.sha256(merchantId.trim());
    }

    private boolean validIdentity(String merchantId, String transactionId) {
        return redisTemplate != null
                && redisProperties != null
                && StringUtils.hasText(merchantId)
                && StringUtils.hasText(transactionId);
    }

    private long transitionCode(List<?> result) {
        return longValue(result.get(0));
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private String hashTag(String key) {
        int begin = key.indexOf('{');
        int end = key.indexOf('}', begin + 1);
        if (begin < 0 || end <= begin + 1) {
            throw new IllegalStateException("frequency success key has no cluster hash tag");
        }
        return key.substring(begin + 1, end);
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> listScript(String filename) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_BASE_PATH + filename));
        script.setResultType(List.class);
        return script;
    }

    private static DefaultRedisScript<Long> longScript(String filename) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_BASE_PATH + filename));
        script.setResultType(Long.class);
        return script;
    }
}
