package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysAccountPasswordHistoryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账号历史密码哈希数据访问接口，仅承担最近历史查询和旧哈希写入。
 */
@Mapper
public interface SysAccountPasswordHistoryMapper extends BaseMapper<SysAccountPasswordHistoryDO> {
}
