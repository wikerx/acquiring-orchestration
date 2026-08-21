package com.scott.payment.admin.service.impl;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HolidayCalendarCachePolicy
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 结算节假日月视图缓存读取策略，在可靠失效窗口或 Redis 状态未知时回源主库
 * @status : create
 */
@Slf4j
@Service
public class HolidayCalendarCachePolicy {

    /** 永久缓存失效门禁。 */
    private final CacheInvalidationGuard invalidationGuard;

    /**
     * 创建节假日月视图缓存读取策略。
     *
     * @param invalidationGuard 永久缓存失效门禁
     */
    public HolidayCalendarCachePolicy(CacheInvalidationGuard invalidationGuard) {
        this.invalidationGuard = invalidationGuard;
    }

    /**
     * 将年、月规范化为稳定的缓存业务键。
     *
     * @param year 年份
     * @param month 月份
     * @return {@code yyyy-MM} 格式的业务键
     */
    public String monthKey(int year, int month) {
        return YearMonth.of(year, month).toString();
    }

    /**
     * 判断指定月视图是否允许读取并回写永久缓存。
     *
     * <p>门禁查询失败时返回 {@code false}，让请求直接从主库加载，
     * 既不中断日历查询，也不将不确定数据写入永久缓存。</p>
     *
     * @param year 年份
     * @param month 月份
     * @return 门禁明确空闲时返回 {@code true}
     */
    public boolean isCacheReadAllowed(int year, int month) {
        String businessKey = monthKey(year, month);
        try {
            return !invalidationGuard.isPending(
                    PaymentCacheNames.SETTLEMENT_HOLIDAY_MONTH,
                    businessKey
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "event: HOLIDAY_CALENDAR_CACHE_GUARD_CHECK_FAILED "
                            + "businessKey: {} exceptionType: {}",
                    businessKey,
                    exception.getClass().getSimpleName()
            );
            return false;
        }
    }
}
