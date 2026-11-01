package com.scott.payment.component.db.mcc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.mcc.entity.SharedMccCodeDO;

/**
 * 公共组件数据访问层的 MCC 编码只读 Mapper，不承载管理端写操作或缓存逻辑
 */
public interface SharedMccCodeMapper extends BaseMapper<SharedMccCodeDO> {
}
