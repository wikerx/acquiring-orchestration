package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeSimulationRecordDetailDO;

/**
 * 费用试算逐项审计快照数据访问，只负责明细记录的持久化与批量查询。
 */
public interface FeeSimulationRecordDetailMapper extends BaseMapper<FeeSimulationRecordDetailDO> {
}
