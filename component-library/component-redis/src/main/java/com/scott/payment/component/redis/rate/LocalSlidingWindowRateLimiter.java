package com.scott.payment.component.redis.rate;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LocalSlidingWindowRateLimiter
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : Redis 故障时使用的有界进程内精确滑动窗口，容量耗尽时拒绝新身份以保护 JVM
 * @status : create
 */
public class LocalSlidingWindowRateLimiter {

    private final int maxIdentities;
    private final LongSupplier currentTimeMillis;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public LocalSlidingWindowRateLimiter(int maxIdentities) {
        this(maxIdentities, System::currentTimeMillis);
    }

    LocalSlidingWindowRateLimiter(int maxIdentities, LongSupplier currentTimeMillis) {
        if (maxIdentities <= 0) {
            throw new IllegalArgumentException("Local rate limit identity capacity must be positive");
        }
        this.maxIdentities = maxIdentities;
        this.currentTimeMillis = currentTimeMillis;
    }

    /**
     * 对单个身份执行精确滚动窗口判定；拒绝请求不进入窗口。
     */
    public SlidingWindowRateLimitResult tryAcquire(String identity,
                                                   int limit,
                                                   Duration windowDuration) {
        long now = currentTimeMillis.getAsLong();
        long windowMillis = windowDuration.toMillis();
        Window window = windows.get(identity);
        if (window == null) {
            cleanupIdle(now, windowMillis);
            if (windows.size() >= maxIdentities) {
                return SlidingWindowRateLimitResult.rejected(1L);
            }
            window = windows.computeIfAbsent(identity, ignored -> new Window(now));
        }
        synchronized (window) {
            while (!window.accepted.isEmpty()
                    && window.accepted.peekFirst() <= now - windowMillis) {
                window.accepted.removeFirst();
            }
            window.lastAccessMillis = now;
            if (window.accepted.size() >= limit) {
                long retryMillis = window.accepted.peekFirst() + windowMillis - now;
                return SlidingWindowRateLimitResult.rejected((retryMillis + 999L) / 1000L);
            }
            window.accepted.addLast(now);
            return SlidingWindowRateLimitResult.permitted();
        }
    }

    private void cleanupIdle(long now, long windowMillis) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().lastAccessMillis > windowMillis);
    }

    private static final class Window {
        private final ArrayDeque<Long> accepted = new ArrayDeque<>();
        private volatile long lastAccessMillis;

        private Window(long lastAccessMillis) {
            this.lastAccessMillis = lastAccessMillis;
        }
    }
}
