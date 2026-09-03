package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarBatchSaveRequest;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarDayResponse;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarDaySaveRequest;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarMonthResponse;
import com.scott.payment.admin.dto.system.HolidayCalendarDTOs.CalendarYearResponse;
import com.scott.payment.admin.entity.system.HolidayCalendarEntities.CalendarDayDO;
import com.scott.payment.admin.entity.system.HolidayCalendarEntities.CalendarYearDO;
import com.scott.payment.admin.mapper.SettlementCalendarYearMapper;
import com.scott.payment.admin.mapper.SettlementHolidayCalendarMapper;
import com.scott.payment.admin.service.AdminHolidayCalendarService;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminHolidayCalendarServiceImpl
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 结算节假日日历实现，以数据库为事实源并缓存只读月视图。
 * @status : create
 */
@Service
public class AdminHolidayCalendarServiceImpl implements AdminHolidayCalendarService {

    /**
     * {@code REGION_CODE}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String REGION_CODE = "CN_MAINLAND";
    /**
     * 时间时区常量，统一 {@code AdminHolidayCalendarServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String TIME_ZONE = "Asia/Shanghai";

    private final SettlementCalendarYearMapper yearMapper;
    private final SettlementHolidayCalendarMapper dayMapper;
    private final ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;
    private final HolidayCalendarCachePolicy cachePolicy;

    /**
     * 构造结算节假日日历服务。
     *
     * @param yearMapper 日历年度数据访问组件
     * @param dayMapper 日历日期数据访问组件
     * @param cacheInvalidationCoordinator 事务缓存可靠失效协调器
     * @param cachePolicy 月视图缓存读取策略
     */
    public AdminHolidayCalendarServiceImpl(SettlementCalendarYearMapper yearMapper,
                                           SettlementHolidayCalendarMapper dayMapper,
                                           ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator,
                                           HolidayCalendarCachePolicy cachePolicy) {
        this.yearMapper = yearMapper;
        this.dayMapper = dayMapper;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
        this.cachePolicy = cachePolicy;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Cacheable(cacheNames = PaymentCacheNames.SETTLEMENT_HOLIDAY_MONTH,
            key = "@holidayCalendarCachePolicy.monthKey(#p0, #p1)",
            condition = "@holidayCalendarCachePolicy.isCacheReadAllowed(#p0, #p1)")
    public CalendarMonthResponse getMonth(int year, int month) {
        validateYearMonth(year, month);
        CalendarYearDO calendarYear = findYear(year);
        CalendarMonthResponse response = new CalendarMonthResponse();
        if (calendarYear == null) {
            return response;
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        List<CalendarDayDO> days = dayMapper.selectList(Wrappers.<CalendarDayDO>lambdaQuery()
                .eq(CalendarDayDO::getCalendarYearId, calendarYear.getId())
                .between(CalendarDayDO::getCalendarDate, yearMonth.atDay(1), yearMonth.atEndOfMonth())
                .eq(CalendarDayDO::getDeleted, 0L)
                .orderByAsc(CalendarDayDO::getCalendarDate));
        response.setYear(toYear(calendarYear));
        response.setDays(days.stream()
                .map(this::toDay)
                .collect(Collectors.toCollection(ArrayList::new)));
        return response;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public CalendarYearResponse initializeYear(int year, String operatorName) {
        validateYear(year);
        CalendarYearDO existing = yearMapper.selectByYearForUpdate(year);
        if (existing != null) {
            return toYear(existing);
        }
        LocalDateTime now = LocalDateTime.now();
        CalendarYearDO calendarYear = new CalendarYearDO();
        calendarYear.setCalendarYear(year);
        calendarYear.setRegionCode(REGION_CODE);
        calendarYear.setTimeZone(TIME_ZONE);
        calendarYear.setYearStatus("DRAFT");
        calendarYear.setTotalDays(Year.isLeap(year) ? 366 : 365);
        calendarYear.setCreateBy(operatorName);
        calendarYear.setCreateTime(now);
        calendarYear.setUpdateBy(operatorName);
        calendarYear.setUpdateTime(now);
        calendarYear.setDeleted(0L);
        yearMapper.insert(calendarYear);
        LocalDate date = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        while (!date.isAfter(end)) {
            CalendarDayDO day = new CalendarDayDO();
            day.setCalendarYearId(calendarYear.getId());
            day.setCalendarDate(date);
            day.setDayOfWeek(date.getDayOfWeek().getValue());
            day.setDayType(isWeekend(date) ? "HOLIDAY" : "WORKDAY");
            day.setStatutoryHoliday(0);
            day.setAdjustedWorkday(0);
            day.setDataSource("SYSTEM_DEFAULT");
            day.setCreateBy(operatorName);
            day.setCreateTime(now);
            day.setUpdateBy(operatorName);
            day.setUpdateTime(now);
            day.setDeleted(0L);
            dayMapper.insert(day);
            date = date.plusDays(1);
        }
        invalidateYear(year);
        return toYear(calendarYear);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public CalendarMonthResponse saveDays(CalendarBatchSaveRequest request, String operatorName) {
        if (request == null || request.getDays() == null || request.getDays().isEmpty()) {
            throw invalid("日历批量维护数据不能为空");
        }
        Set<Integer> years = new HashSet<>();
        request.getDays().forEach(item -> years.add(item.getCalendarDate().getYear()));
        if (years.size() != 1) {
            throw invalid("一次批量维护只能包含同一个自然年");
        }
        int year = years.iterator().next();
        CalendarYearDO calendarYear = yearMapper.selectByYearForUpdate(year);
        if (calendarYear == null) {
            throw invalid("请先初始化 " + year + " 年日历");
        }
        boolean yearStatusChanged = !"DRAFT".equals(calendarYear.getYearStatus());
        Set<YearMonth> affectedMonths = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();
        for (CalendarDaySaveRequest item : request.getDays()) {
            affectedMonths.add(YearMonth.from(item.getCalendarDate()));
            String dayType = item.getDayType().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("WORKDAY", "HOLIDAY").contains(dayType)) {
                throw invalid("日期类型只允许 WORKDAY 或 HOLIDAY");
            }
            if (Boolean.TRUE.equals(item.getAdjustedWorkday()) && !"WORKDAY".equals(dayType)) {
                throw invalid("调休工作日必须标记为 WORKDAY");
            }
            CalendarDayDO day = dayMapper.selectOne(Wrappers.<CalendarDayDO>lambdaQuery()
                    .eq(CalendarDayDO::getCalendarYearId, calendarYear.getId())
                    .eq(CalendarDayDO::getCalendarDate, item.getCalendarDate())
                    .eq(CalendarDayDO::getDeleted, 0L)
                    .last("LIMIT 1"));
            if (day == null) {
                throw invalid("日历日期不存在：" + item.getCalendarDate());
            }
            day.setDayType(dayType);
            day.setHolidayName(trimToNull(item.getHolidayName()));
            day.setStatutoryHoliday(Boolean.TRUE.equals(item.getStatutoryHoliday()) ? 1 : 0);
            day.setAdjustedWorkday(Boolean.TRUE.equals(item.getAdjustedWorkday()) ? 1 : 0);
            day.setDataSource("MANUAL");
            day.setRemark(trimToNull(item.getRemark()));
            day.setUpdateBy(operatorName);
            day.setUpdateTime(now);
            dayMapper.updateById(day);
        }
        calendarYear.setYearStatus("DRAFT");
        calendarYear.setConfirmedBy(null);
        calendarYear.setConfirmedTime(null);
        calendarYear.setUpdateBy(operatorName);
        calendarYear.setUpdateTime(now);
        yearMapper.updateById(calendarYear);
        if (yearStatusChanged) {
            invalidateYear(year);
        } else {
            invalidateMonths(affectedMonths);
        }
        LocalDate firstDate = request.getDays().get(0).getCalendarDate();
        return uncachedMonth(firstDate.getYear(), firstDate.getMonthValue());
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public CalendarYearResponse confirmYear(int year, String operatorName) {
        validateYear(year);
        CalendarYearDO calendarYear = yearMapper.selectByYearForUpdate(year);
        if (calendarYear == null) {
            throw invalid("请先初始化 " + year + " 年日历");
        }
        Long dayCount = dayMapper.selectCount(Wrappers.<CalendarDayDO>lambdaQuery()
                .eq(CalendarDayDO::getCalendarYearId, calendarYear.getId())
                .eq(CalendarDayDO::getDeleted, 0L));
        if (dayCount == null || dayCount.intValue() != calendarYear.getTotalDays()) {
            throw invalid("年度日历日期不完整，不能确认");
        }
        LocalDateTime now = LocalDateTime.now();
        calendarYear.setYearStatus("ACTIVE");
        calendarYear.setConfirmedBy(operatorName);
        calendarYear.setConfirmedTime(now);
        calendarYear.setUpdateBy(operatorName);
        calendarYear.setUpdateTime(now);
        yearMapper.updateById(calendarYear);
        invalidateYear(year);
        return toYear(calendarYear);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public boolean isWorkingDay(LocalDate date) {
        if (date == null) throw invalid("结算日期不能为空");
        CalendarYearDO calendarYear = findYear(date.getYear());
        if (calendarYear == null || !"ACTIVE".equals(calendarYear.getYearStatus())) {
            throw invalid(date.getYear() + " 年节假日日历尚未确认");
        }
        CalendarDayDO day = dayMapper.selectOne(Wrappers.<CalendarDayDO>lambdaQuery()
                .eq(CalendarDayDO::getCalendarYearId, calendarYear.getId())
                .eq(CalendarDayDO::getCalendarDate, date)
                .eq(CalendarDayDO::getDeleted, 0L)
                .last("LIMIT 1"));
        if (day == null) throw invalid("节假日日历缺少日期：" + date);
        return "WORKDAY".equals(day.getDayType());
    }

    private CalendarMonthResponse uncachedMonth(int year, int month) {
        CalendarYearDO calendarYear = findYear(year);
        CalendarMonthResponse response = new CalendarMonthResponse();
        response.setYear(toYear(calendarYear));
        YearMonth yearMonth = YearMonth.of(year, month);
        response.setDays(dayMapper.selectList(Wrappers.<CalendarDayDO>lambdaQuery()
                        .eq(CalendarDayDO::getCalendarYearId, calendarYear.getId())
                        .between(CalendarDayDO::getCalendarDate, yearMonth.atDay(1), yearMonth.atEndOfMonth())
                        .eq(CalendarDayDO::getDeleted, 0L)
                        .orderByAsc(CalendarDayDO::getCalendarDate)).stream()
                .map(this::toDay)
                .collect(Collectors.toCollection(ArrayList::new)));
        return response;
    }

    /** 登记指定年度全部月视图的可靠失效。 */
    private void invalidateYear(int year) {
        for (int month = 1; month <= 12; month++) {
            invalidateMonth(YearMonth.of(year, month));
        }
    }

    /** 按顺序登记批量维护涉及月视图的可靠失效。 */
    private void invalidateMonths(Set<YearMonth> months) {
        months.forEach(this::invalidateMonth);
    }

    /** 在当前数据库事务内登记单个月视图的精确失效意图。 */
    private void invalidateMonth(YearMonth month) {
        cacheInvalidationCoordinator.prepare(
                PaymentCacheNames.SETTLEMENT_HOLIDAY_MONTH,
                cachePolicy.monthKey(month.getYear(), month.getMonthValue())
        );
    }

    private CalendarYearDO findYear(int year) {
        return yearMapper.selectOne(Wrappers.<CalendarYearDO>lambdaQuery()
                .eq(CalendarYearDO::getCalendarYear, year)
                .eq(CalendarYearDO::getDeleted, 0L)
                .last("LIMIT 1"));
    }

    private CalendarYearResponse toYear(CalendarYearDO row) {
        if (row == null) return null;
        CalendarYearResponse response = new CalendarYearResponse();
        response.setId(row.getId());
        response.setCalendarYear(row.getCalendarYear());
        response.setRegionCode(row.getRegionCode());
        response.setTimeZone(row.getTimeZone());
        response.setYearStatus(row.getYearStatus());
        response.setTotalDays(row.getTotalDays());
        response.setConfirmedBy(row.getConfirmedBy());
        response.setConfirmedTime(row.getConfirmedTime());
        response.setCreateBy(row.getCreateBy());
        response.setCreateTime(row.getCreateTime());
        response.setUpdateBy(row.getUpdateBy());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    private CalendarDayResponse toDay(CalendarDayDO row) {
        CalendarDayResponse response = new CalendarDayResponse();
        response.setId(row.getId());
        response.setCalendarDate(row.getCalendarDate());
        response.setDayOfWeek(row.getDayOfWeek());
        response.setDayType(row.getDayType());
        response.setHolidayName(row.getHolidayName());
        response.setStatutoryHoliday(row.getStatutoryHoliday() != null && row.getStatutoryHoliday() == 1);
        response.setAdjustedWorkday(row.getAdjustedWorkday() != null && row.getAdjustedWorkday() == 1);
        response.setDataSource(row.getDataSource());
        response.setRemark(row.getRemark());
        response.setUpdateBy(row.getUpdateBy());
        response.setUpdateTime(row.getUpdateTime());
        return response;
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private void validateYearMonth(int year, int month) {
        validateYear(year);
        if (month < 1 || month > 12) throw invalid("月份必须在 1 至 12 之间");
    }

    private void validateYear(int year) {
        if (year < 2000 || year > 2100) throw invalid("年份必须在 2000 至 2100 之间");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
