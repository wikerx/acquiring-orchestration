package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.SysEmailVerificationRequestDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysEmailVerificationRequestMapper
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 账号邮箱验证请求数据访问接口；消费验证码和动作令牌时使用数据库行锁保证一次性状态流转。
 * @status : create
 */
public interface SysEmailVerificationRequestMapper extends BaseMapper<SysEmailVerificationRequestDO> {

    /**
     * 按公开请求号加行锁读取验证请求。
     *
     * @param requestNo 高熵公开请求编号
     * @return 验证请求，不存在时为空
     */
    @Select("SELECT * FROM sys_email_verification_request WHERE request_no = #{requestNo} FOR UPDATE")
    SysEmailVerificationRequestDO selectByRequestNoForUpdate(@Param("requestNo") String requestNo);
}
