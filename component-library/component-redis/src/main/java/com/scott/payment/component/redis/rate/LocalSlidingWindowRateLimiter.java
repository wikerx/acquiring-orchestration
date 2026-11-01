package com.scott.payment.component.redis.rate;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Redis 故障时使用的有界进程内精确滑动窗口，容量耗尽时拒绝新身份以保护 JVM
 */
public class LocalSlidingWindowRateLimiter {

    private final int maxIdentities;
    private final LongSupplier currentTimeMillis;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 创建使用系统时钟的有界本地滑动窗口限流器。
     *
     * @param maxIdentities 进程内最多保留的身份数量
     */
    public LocalSlidingWindowRateLimiter(int maxIdentities) {
        this(maxIdentities, System::currentTimeMillis);
    }

    /**
     * 创建可注入时钟的有界本地滑动窗口限流器，供确定性测试使用。
     *
     * @param maxIdentities 进程内最多保留的身份数量
     * @param currentTimeMillis 当前毫秒时间提供器
     */
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

    /**
     * 清理超过一个完整窗口未访问的身份，释放本地容量。
     *
     * @param now 当前毫秒时间
     * @param windowMillis 滑动窗口毫秒数
     */
    private void cleanupIdle(long now, long windowMillis) {
        windows.entrySet().removeIf(entry -> now - entry.getValue().lastAccessMillis > windowMillis);
    }

    /**
     * 单一身份已接受请求时间序列及最后访问时间。
     */
    private static final class Window {
        private final ArrayDeque<Long> accepted = new ArrayDeque<>();
        private volatile long lastAccessMillis;

        /**
         * 创建空窗口并记录首次访问时间。
         *
         * @param lastAccessMillis 首次访问毫秒时间
         */
        private Window(long lastAccessMillis) {
            this.lastAccessMillis = lastAccessMillis;
        }
    }
}
