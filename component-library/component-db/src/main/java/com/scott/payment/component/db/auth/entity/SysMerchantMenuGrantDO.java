package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMerchantMenuGrantDO
 * @date : 2026-06-06 10:36
 * @email : scott_x@163.com
 * @description : sys商户菜单授权持久化模型，位于 公共组件库，映射数据库中的业务事实、状态、版本和审计字段，不作为外部接口模型。
 * @status : create
 */
@Data
@TableName("sys_merchant_menu_grant")
public class SysMerchantMenuGrantDO {

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
     * 菜单ID。
     */
    private Long menuId;

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
