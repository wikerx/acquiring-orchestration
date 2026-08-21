package com.scott.payment.component.db.mcc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.mcc.entity.SharedMccCodeDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SharedMccCodeMapper
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 公共组件数据访问层的 MCC 编码只读 Mapper，不承载管理端写操作或缓存逻辑
 * @status : create
 */
public interface SharedMccCodeMapper extends BaseMapper<SharedMccCodeDO> {
}
