package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台菜单新增请求 DTO
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

    /**
     * 请求中的图标，用于限定本次操作的输入和校验范围。
     */
    private String icon;

    /**
     * 请求中的重定向地址，用于限定本次操作的输入和校验范围。
     */
    private String redirect;

    /**
     * 请求中的可见，用于限定本次操作的输入和校验范围。
     */
    private Integer visible;

    private Integer keepAlive;

    /**
     * 请求中的外部链接，用于限定本次操作的输入和校验范围。
     */
    private Integer externalLink;

    /**
     * 排序号，数值越小越优先展示或匹配。
     */
    private Integer sortNo;

    private Integer status;
}
