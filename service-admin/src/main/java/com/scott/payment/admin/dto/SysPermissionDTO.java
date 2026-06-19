package com.scott.payment.admin.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysPermissionDTO
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台权限响应 DTO
 * @status : create
 *
 * <p>用于角色权限授权和当前用户权限装配，承载权限编码、资源路径和状态信息。</p>
 */
@Data
public class SysPermissionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long permissionId;

    private Long menuId;

    private String permissionCode;

    private String permissionName;

    private String permissionType;

    private String resourceMethod;

    private String resourcePath;

    private Integer status;
}
