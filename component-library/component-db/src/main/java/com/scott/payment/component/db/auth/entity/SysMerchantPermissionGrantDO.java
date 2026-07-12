package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMerchantPermissionGrantDO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Merchant Permission Grant 数据库实体，位于 component-library/component-db 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@TableName("sys_merchant_permission_grant")
public class SysMerchantPermissionGrantDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 系统应用ID，固定为商户系统应用。
     */
    private Long appId;

    /**
     * 权限ID。
     */
    private Long permissionId;

    /**
     * 授权来源：ADMIN 平台授权，SYSTEM 系统初始化。
     */
    private String grantSource;

    /**
     * 状态：1 启用，0 停用。
     */
    private Integer status;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 创建人ID。
     */
    private Long createdBy;

    /**
     * 修改时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 修改人ID。
     */
    private Long updatedBy;

    /**
     * 删除标识：0 未删除，非 0 已删除。
     */
    private Long deleted;
}
