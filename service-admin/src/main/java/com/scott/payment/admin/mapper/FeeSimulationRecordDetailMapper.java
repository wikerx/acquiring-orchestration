package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeSimulationRecordDetailDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeSimulationRecordDetailMapper
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : 费用试算逐项审计快照数据访问，只负责明细记录的持久化与批量查询。
 * @status : create
 */
public interface FeeSimulationRecordDetailMapper extends BaseMapper<FeeSimulationRecordDetailDO> {
}
