package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户账号岗位关联数据库实体。
 */
@Data
@TableName("sys_merchant_account_post")
public class SysMerchantAccountPostDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String merchantId;
    private Long accountId;
    private Long postId;
    private LocalDateTime createdAt;
    private Long createdBy;
}
