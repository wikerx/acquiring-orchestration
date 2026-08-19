package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarDayResponse;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarMonthResponse;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarYearResponse;
import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminHolidayCalendarCacheSerializationTests
 * @date : 2026-08-18 20:20
 * @email : scott_x@163.com
 * @description : 验证节假日日历月视图满足统一 Redis 精确类型白名单和往返序列化约束。
 * @status : create
 */
@Slf4j
class AdminHolidayCalendarCacheSerializationTests {

    /**
     * 日历月视图包含业务 DTO、可变列表和 LocalDate，必须能够完整写入并恢复。
     */
    @Test
    void shouldRoundTripHolidayCalendarMonthThroughRegisteredRedisSerializer() {
        log.info("测试节假日日历月视图 Redis 往返序列化，年份：2026，日期：2026-08-18");
        CalendarYearResponse year = new CalendarYearResponse();
        year.setCalendarYear(2026);
        year.setYearStatus("ACTIVE");
        CalendarDayResponse day = new CalendarDayResponse();
        day.setCalendarDate(LocalDate.of(2026, 8, 18));
        day.setDayType("WORKDAY");
        CalendarMonthResponse source = new CalendarMonthResponse();
        source.setYear(year);
        source.setDays(new ArrayList<>());
        source.getDays().add(day);
        RedisSerializer<Object> serializer = PaymentRedisSerializerFactory.create();

        Object restored = serializer.deserialize(serializer.serialize(source));

        assertThat(restored).usingRecursiveComparison().isEqualTo(source);
        log.info("节假日日历月视图 Redis 往返序列化测试通过，日期数量：{}", source.getDays().size());
    }
}
