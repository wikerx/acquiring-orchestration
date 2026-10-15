package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysAccountMapper
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 系统登录账号 Mapper
 * @status : create
 */
public interface SysAccountMapper extends BaseMapper<SysAccountDO> {

    /**
     * 按主键锁定账号行，串行化密码和敏感联系方式变更，避免并发请求绕过历史密码或 MFA 防重放校验。
     *
     * @param id 账号主键
     * @return 已锁定的账号，不存在时返回 null
     */
    @Select("SELECT * FROM sys_account WHERE id = #{id} FOR UPDATE")
    SysAccountDO selectByIdForUpdate(@Param("id") Long id);
}
