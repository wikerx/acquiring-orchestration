package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.system.HolidayCalendarEntities.CalendarDayDO;
import org.apache.ibatis.annotations.Mapper;

/** 全局结算日历单日数据访问组件。 */
@Mapper
public interface SettlementHolidayCalendarMapper extends BaseMapper<CalendarDayDO> {
}
