package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.system.HolidayCalendarEntities.CalendarYearDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCalendarYearMapper
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 全局结算日历年度数据访问组件。
 * @status : create
 */
@Mapper
public interface SettlementCalendarYearMapper extends BaseMapper<CalendarYearDO> {
    /** 按自然年加锁，串行化批量维护与确认。 */
    @Select("SELECT * FROM settlement_calendar_year WHERE calendar_year = #{year} AND deleted = 0 FOR UPDATE")
    CalendarYearDO selectByYearForUpdate(@Param("year") int year);
}
