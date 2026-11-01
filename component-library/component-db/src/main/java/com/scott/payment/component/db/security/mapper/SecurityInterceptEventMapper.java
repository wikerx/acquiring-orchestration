package com.scott.payment.component.db.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.security.entity.SecurityInterceptEventDO;

/**
 * 安全拦截事件 Mapper，位于 component-db 数据访问层，仅负责脱敏安全事件的基础读写。
 */
public interface SecurityInterceptEventMapper extends BaseMapper<SecurityInterceptEventDO> {
}
