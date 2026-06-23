package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户菜单授权数据库实体。
 *
 * <p>记录平台给商户开放的商户端菜单范围，是商户内部角色菜单授权的上限。</p>
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
