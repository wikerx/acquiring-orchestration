package com.scott.payment.component.db.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.security.entity.SecurityInterceptEventDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SecurityInterceptEventMapper
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 安全拦截事件 Mapper，位于 component-db 数据访问层，仅负责脱敏安全事件的基础读写。
 * @status : create
 */
public interface SecurityInterceptEventMapper extends BaseMapper<SecurityInterceptEventDO> {
}
