package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户端登录用户数据库实体。
 *
 * <p>用于把同一登录账号名隔离在商户号维度内，例如不同商户均可拥有 admin 用户。</p>
 */
@Data
@TableName("sys_merchant_user")
public class SysMerchantUserDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantInfoId;
    private String merchantId;
    private Long userId;
    private Long accountId;
    private String loginAccount;
    private String realName;
    private Integer status;
    private LocalDateTime createdAt;
    private Long createdBy;
    private LocalDateTime updatedAt;
    private Long updatedBy;
    private Long deleted;
}
