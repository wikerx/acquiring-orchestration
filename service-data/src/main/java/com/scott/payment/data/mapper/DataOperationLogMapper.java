package com.scott.payment.data.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.data.entity.DataOperationLogDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataOperationLogMapper
 * @date : 2026-08-01 14:40
 * @email : scott_x@163.com
 * @description : sys_oper_log 数据访问入口，只负责 service-data 的操作日志插入
 * @status : create
 */
public interface DataOperationLogMapper extends BaseMapper<DataOperationLogDO> {
}
