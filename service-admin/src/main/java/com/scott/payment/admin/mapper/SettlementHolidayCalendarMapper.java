package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.system.HolidayCalendarEntities.CalendarDayDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementHolidayCalendarMapper
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 全局结算日历单日数据访问组件。
 * @status : create
 */
@Mapper
public interface SettlementHolidayCalendarMapper extends BaseMapper<CalendarDayDO> {
}
