package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scott.payment.admin.entity.system.HolidayCalendarEntities.CalendarDayDO;
import com.scott.payment.admin.entity.system.HolidayCalendarEntities.CalendarYearDO;
import com.scott.payment.admin.mapper.SettlementCalendarYearMapper;
import com.scott.payment.admin.mapper.SettlementHolidayCalendarMapper;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminHolidayCalendarServiceImplTests
 * @date : 2026-08-18 20:20
 * @email : scott_x@163.com
 * @description : 结算节假日日历服务测试，覆盖空月视图、闰年初始化、年度确认及缓存失效契约。
 * @status : create
 */
class AdminHolidayCalendarServiceImplTests {

    /** 初始化 MyBatis-Plus Lambda 查询所需的实体字段元数据。 */
    @BeforeEach
    void setUpTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName());
        TableInfoHelper.initTableInfo(assistant, CalendarYearDO.class);
        TableInfoHelper.initTableInfo(assistant, CalendarDayDO.class);
    }

    /** 未初始化年度必须返回可直接渲染的空月视图。 */
    @Test
    void shouldReturnEmptyMonthWhenYearIsNotInitialized() {
        Fixture fixture = new Fixture();
        when(fixture.yearMapper.selectOne(any())).thenReturn(null);

        var response = fixture.service.getMonth(2026, 8);

        assertThat(response.getYear()).isNull();
        assertThat(response.getDays()).isEmpty();
    }

    /** 闰年初始化必须生成完整 366 天，并按周末设置默认日期类型。 */
    @Test
    void shouldInitializeEveryDayOfLeapYear() {
        Fixture fixture = new Fixture();
        List<CalendarDayDO> insertedDays = new ArrayList<>();
        when(fixture.yearMapper.selectByYearForUpdate(2028)).thenReturn(null);
        doAnswer(invocation -> {
            CalendarYearDO row = invocation.getArgument(0);
            row.setId(28L);
            return 1;
        }).when(fixture.yearMapper).insert(any(CalendarYearDO.class));
        doAnswer(invocation -> {
            insertedDays.add(invocation.getArgument(0));
            return 1;
        }).when(fixture.dayMapper).insert(any(CalendarDayDO.class));

        var response = fixture.service.initializeYear(2028, "日历管理员");

        assertThat(response.getCalendarYear()).isEqualTo(2028);
        assertThat(response.getRegionCode()).isEqualTo("CN_MAINLAND");
        assertThat(response.getTimeZone()).isEqualTo("Asia/Shanghai");
        assertThat(response.getYearStatus()).isEqualTo("DRAFT");
        assertThat(response.getTotalDays()).isEqualTo(366);
        assertThat(insertedDays).hasSize(366);
        assertThat(insertedDays.get(0).getCalendarDate()).isEqualTo(LocalDate.of(2028, 1, 1));
        assertThat(insertedDays.get(365).getCalendarDate()).isEqualTo(LocalDate.of(2028, 12, 31));
        assertThat(insertedDays)
                .allSatisfy(day -> assertThat(day.getDayType()).isEqualTo(
                        day.getCalendarDate().getDayOfWeek() == DayOfWeek.SATURDAY
                                || day.getCalendarDate().getDayOfWeek() == DayOfWeek.SUNDAY
                                ? "HOLIDAY" : "WORKDAY"));
    }

    /** 日期完整的草稿年度确认后必须成为可供 T+N 使用的生效日历。 */
    @Test
    void shouldActivateCompleteCalendarYear() {
        Fixture fixture = new Fixture();
        CalendarYearDO calendarYear = calendarYear(2026, 365);
        when(fixture.yearMapper.selectByYearForUpdate(2026)).thenReturn(calendarYear);
        when(fixture.dayMapper.selectCount(any())).thenReturn(365L);

        var response = fixture.service.confirmYear(2026, "复核人");

        assertThat(response.getYearStatus()).isEqualTo("ACTIVE");
        assertThat(response.getConfirmedBy()).isEqualTo("复核人");
        assertThat(response.getConfirmedTime()).isNotNull();
        verify(fixture.yearMapper).updateById(calendarYear);
    }

    /** 月查询使用项目统一缓存，初始化、维护和确认必须清空所有月视图。 */
    @Test
    void shouldDeclareHolidayMonthCacheAndMutationEvictions() throws Exception {
        Method reader = AdminHolidayCalendarServiceImpl.class.getMethod("getMonth", int.class, int.class);
        Cacheable cacheable = AnnotatedElementUtils.findMergedAnnotation(reader, Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames()).containsExactly(PaymentCacheNames.SETTLEMENT_HOLIDAY_MONTH);
        assertThat(cacheable.key()).isEqualTo("#p0 + '-' + #p1");

        assertAllEntriesEviction("initializeYear", int.class, String.class);
        assertAllEntriesEviction("saveDays",
                com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarBatchSaveRequest.class,
                String.class);
        assertAllEntriesEviction("confirmYear", int.class, String.class);
    }

    private void assertAllEntriesEviction(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminHolidayCalendarServiceImpl.class.getMethod(methodName, parameterTypes);
        CacheEvict eviction = AnnotatedElementUtils.findMergedAnnotation(method, CacheEvict.class);
        assertThat(eviction).isNotNull();
        assertThat(eviction.cacheNames()).containsExactly(PaymentCacheNames.SETTLEMENT_HOLIDAY_MONTH);
        assertThat(eviction.allEntries()).isTrue();
    }

    private static CalendarYearDO calendarYear(int year, int totalDays) {
        CalendarYearDO row = new CalendarYearDO();
        row.setId(26L);
        row.setCalendarYear(year);
        row.setRegionCode("CN_MAINLAND");
        row.setTimeZone("Asia/Shanghai");
        row.setYearStatus("DRAFT");
        row.setTotalDays(totalDays);
        row.setCreateBy("初始化人");
        row.setUpdateBy("初始化人");
        row.setDeleted(0L);
        return row;
    }

    private static final class Fixture {
        private final SettlementCalendarYearMapper yearMapper = mock(SettlementCalendarYearMapper.class);
        private final SettlementHolidayCalendarMapper dayMapper = mock(SettlementHolidayCalendarMapper.class);
        private final AdminHolidayCalendarServiceImpl service =
                new AdminHolidayCalendarServiceImpl(yearMapper, dayMapper);
    }
}
