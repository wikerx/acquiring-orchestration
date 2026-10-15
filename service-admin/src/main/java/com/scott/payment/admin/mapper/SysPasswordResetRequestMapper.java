package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.SysPasswordResetRequestDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysPasswordResetRequestMapper
 * @date : 2026-10-14 00:00
 * @email : scott_x@163.com
 * @description : 找回密码请求数据访问接口；对状态流转读取使用数据库行锁，防止令牌被并发重复消费。
 * @status : create
 */
public interface SysPasswordResetRequestMapper extends BaseMapper<SysPasswordResetRequestDO> {

    /**
     * 按请求号加行锁读取重置请求。
     *
     * @param requestNo 高熵请求号
     * @return 重置请求，不存在时为空
     */
    @Select("SELECT * FROM sys_password_reset_request WHERE request_no = #{requestNo} FOR UPDATE")
    SysPasswordResetRequestDO selectByRequestNoForUpdate(@Param("requestNo") String requestNo);
}
