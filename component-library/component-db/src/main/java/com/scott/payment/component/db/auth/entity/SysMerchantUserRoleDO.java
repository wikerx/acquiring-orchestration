package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户端用户角色关联数据库实体。
 */
@Data
@TableName("sys_merchant_user_role")
public class SysMerchantUserRoleDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appId;
    private Long merchantInfoId;
    private Long merchantUserId;
    private Long roleId;
    private LocalDateTime createdAt;
    private Long createdBy;
    private Long deleted;
}
