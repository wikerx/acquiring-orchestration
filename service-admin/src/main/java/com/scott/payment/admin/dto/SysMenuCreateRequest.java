package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuCreateRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台菜单新增请求 DTO
 * @status : create
 *
 * <p>用于新增后台菜单、目录、按钮或外链资源，承载菜单层级、路由与展示配置。</p>
 */
@Data
public class SysMenuCreateRequest {

    @NotNull(message = "父级菜单ID不能为空")
    private Long parentId;

    @NotBlank(message = "菜单编码不能为空")
    private String menuCode;

    @NotBlank(message = "菜单名称不能为空")
    private String menuName;

    @NotBlank(message = "菜单类型不能为空")
    private String menuType;

    private String routePath;

    private String componentPath;

    private String permissionCode;

    private String icon;

    private String redirect;

    private Integer visible;

    private Integer keepAlive;

    private Integer externalLink;

    private Integer sortNo;

    private Integer status;
}
