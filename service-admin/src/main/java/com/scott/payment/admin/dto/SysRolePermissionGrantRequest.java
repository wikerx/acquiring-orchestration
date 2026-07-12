package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRolePermissionGrantRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台角色权限授权保存请求 DTO
 * @status : create
 *
 * <p>用于提交角色权限授权结果，承载角色主键和本次授予的权限主键集合。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRolePermissionGrantRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Role Permission Grant 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysRolePermissionGrantRequest implements Serializable {

    /**
     * 系统管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @NotNull(message = "roleId")
    private Long roleId;

    private List<Long> permissionIds = Collections.emptyList();
}
