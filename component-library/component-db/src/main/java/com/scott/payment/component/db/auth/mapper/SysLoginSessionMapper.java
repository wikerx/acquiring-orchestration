package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysLoginSessionMapper
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 登录会话 Mapper
 * @status : create
 */
public interface SysLoginSessionMapper extends BaseMapper<SysLoginSessionDO> {

    /**
     * 注销指定账号全部未退出会话，用于密码或授权等安全变更后立即失效旧令牌。
     *
     * @param appId     应用 ID
     * @param accountId 账号 ID
     * @param logoutAt  注销时间
     * @return 更新行数
     */
    @Update("UPDATE sys_login_session SET logout = 1, logout_at = #{logoutAt}, updated_at = #{logoutAt} "
            + "WHERE app_id = #{appId} AND account_id = #{accountId} AND logout = 0")
    int logoutAllActive(@Param("appId") Long appId,
                        @Param("accountId") Long accountId,
                        @Param("logoutAt") LocalDateTime logoutAt);
}
