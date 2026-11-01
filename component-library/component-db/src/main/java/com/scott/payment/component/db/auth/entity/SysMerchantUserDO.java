package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * sys商户用户持久化模型，位于 公共组件库，映射数据库中的业务事实、状态、版本和审计字段，不作为外部接口模型。
 */
@Data
@TableName("sys_merchant_user")
public class SysMerchantUserDO {


    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantInfoId;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;

    private Long userId;

    private Long accountId;
    /**
     * 持久化的登录账号，用于还原当前记录的业务事实。
     */
    private String loginAccount;

    private String realName;

    private Integer status;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createdAt;
    /**
     * 持久化的创建按，用于还原当前记录的业务事实。
     */
    private Long createdBy;
    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    private LocalDateTime updatedAt;
    /**
     * 持久化的更新按，用于还原当前记录的业务事实。
     */
    private Long updatedBy;
    /**
     * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
     */
    private Long deleted;
}
