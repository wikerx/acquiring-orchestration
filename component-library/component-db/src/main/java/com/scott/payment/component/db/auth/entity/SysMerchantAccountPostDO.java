package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * sys商户账号岗位持久化模型，位于 公共组件库，映射数据库中的业务事实、状态、版本和审计字段，不作为外部接口模型。
 */
@Data
@TableName("sys_merchant_account_post")
public class SysMerchantAccountPostDO {


    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;

    private Long accountId;

    private Long postId;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createdAt;
    /**
     * 持久化的创建按，用于还原当前记录的业务事实。
     */
    private Long createdBy;
}
