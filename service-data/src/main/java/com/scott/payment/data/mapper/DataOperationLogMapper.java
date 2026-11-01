package com.scott.payment.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.data.entity.DataOperationLogDO;

/**
 * sys_oper_log 数据访问入口，只负责 service-data 的操作日志插入。
 */
public interface DataOperationLogMapper extends BaseMapper<DataOperationLogDO> {
}
