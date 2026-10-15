package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysAccountPasswordHistoryDO
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 账号历史密码哈希实体；只保存旧哈希材料，用于阻止最近密码复用，绝不保存密码明文。
 * @status : create
 */
@Data
@TableName("sys_account_password_history")
public class SysAccountPasswordHistoryDO {

    /** 历史记录主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属应用主键。 */
    private Long appId;
    /** 所属登录账号主键。 */
    private Long accountId;
    /** 旧密码单向哈希。 */
    private String passwordHash;
    /** 旧密码独立随机盐。 */
    private String passwordSalt;
    /** 哈希算法标识。 */
    private String passwordAlgo;
    /** 该密码被替换的时刻。 */
    private LocalDateTime changedAt;
    /** 历史记录创建时刻。 */
    private LocalDateTime createdAt;
}
