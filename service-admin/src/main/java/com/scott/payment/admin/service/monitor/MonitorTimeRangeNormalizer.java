package com.scott.payment.admin.service.monitor;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorTimeRangeNormalizer
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 系统监控时间范围规范器，补齐默认二十四小时范围、限制最长七天、转换数据库时区并计算统一时间桶粒度。
 * @status : create
 */
@Component
public class MonitorTimeRangeNormalizer {

    /** 交易和管理数据库统一使用的 IANA 时区。 */
    public static final String DATABASE_ZONE_ID = "Asia/Shanghai";
    /** 未提供时间范围时采用的默认查询跨度。 */
    public static final Duration DEFAULT_RANGE = Duration.ofHours(24);
    /** 单次系统监控查询允许的最大跨度。 */
    public static final Duration MAX_RANGE = Duration.ofDays(7);

    /**
     * 规范化监控查询时间并计算时间桶粒度。
     *
     * @param beginTime 查询时区下的开始时间；为空时取结束时间前二十四小时
     * @param endTime 查询时区下的结束时间；为空时取当前时间
     * @param queryTimeZone IANA 查询时区；为空时使用 Asia/Shanghai
     * @return 已转换到数据库时区的闭区间和桶粒度
     * @throws ServiceException 时间顺序、跨度或时区非法时抛出
     */
    public NormalizedRange normalize(LocalDateTime beginTime,
                                     LocalDateTime endTime,
                                     String queryTimeZone) {
        ZoneId queryZone = resolveZone(queryTimeZone);
        ZoneId databaseZone = ZoneId.of(DATABASE_ZONE_ID);
        LocalDateTime now = LocalDateTime.now(queryZone);
        LocalDateTime end = endTime == null ? now : endTime;
        LocalDateTime begin = beginTime == null ? end.minus(DEFAULT_RANGE) : beginTime;
        if (begin.isAfter(end)) {
            throw invalid("beginTime must not be after endTime");
        }
        Duration duration = Duration.between(begin, end);
        if (duration.compareTo(MAX_RANGE) > 0) {
            throw invalid("monitor query range must not exceed 7 days");
        }
        LocalDateTime databaseBegin = begin.atZone(queryZone).withZoneSameInstant(databaseZone).toLocalDateTime();
        LocalDateTime databaseEnd = end.atZone(queryZone).withZoneSameInstant(databaseZone).toLocalDateTime();
        return new NormalizedRange(databaseBegin, databaseEnd, queryZone, bucketMinutes(duration));
    }

    /**
     * 解析 IANA 查询时区，空值统一回退到数据库时区。
     *
     * @param queryTimeZone 页面提交的 IANA 时区
     * @return 可用于时间转换的时区对象
     * @throws ServiceException 时区标识非法时抛出参数错误
     */
    private ZoneId resolveZone(String queryTimeZone) {
        String zone = StringUtils.hasText(queryTimeZone) ? queryTimeZone.trim() : DATABASE_ZONE_ID;
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "queryTimeZone is invalid", exception);
        }
    }

    /**
     * 按查询跨度选择图表聚合粒度，限制长范围查询产生的时间桶数量。
     *
     * @param duration 查询跨度
     * @return 聚合桶宽度，单位分钟
     */
    private int bucketMinutes(Duration duration) {
        long minutes = Math.max(duration.toMinutes(), 1L);
        if (minutes <= 60) {
            return 5;
        }
        if (minutes <= 6 * 60) {
            return 15;
        }
        if (minutes <= 24 * 60) {
            return 60;
        }
        return 6 * 60;
    }

    /**
     * 构造系统监控统一参数异常。
     *
     * @param message 面向调用方的安全错误描述
     * @return 参数无效异常
     */
    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    /**
     * 规范化后的查询范围。
     *
     * @param beginTime 数据库时区下的开始时间
     * @param endTime 数据库时区下的结束时间
     * @param queryZone 原始查询时区
     * @param bucketMinutes 图表聚合桶宽度，单位分钟
     */
    public record NormalizedRange(LocalDateTime beginTime,
                                  LocalDateTime endTime,
                                  ZoneId queryZone,
                                  int bucketMinutes) {
    }
}
