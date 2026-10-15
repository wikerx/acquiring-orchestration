package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysAccountPasswordHistoryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysAccountPasswordHistoryMapper
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 账号历史密码哈希数据访问接口，仅承担最近历史查询和旧哈希写入。
 * @status : create
 */
@Mapper
public interface SysAccountPasswordHistoryMapper extends BaseMapper<SysAccountPasswordHistoryDO> {
}
