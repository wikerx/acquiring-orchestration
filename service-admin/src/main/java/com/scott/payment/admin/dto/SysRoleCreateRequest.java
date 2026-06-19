package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleCreateRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台角色新增请求 DTO
 * @status : create
 *
 * <p>用于新增后台角色，承载角色编码、角色名称、数据范围和排序等配置。</p>
 */
@Data
public class SysRoleCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "roleCode")
    @Size(max = 80, message = "roleCode length must be less than 80")
    private String roleCode;

    @NotBlank(message = "roleName")
    @Size(max = 100, message = "roleName length must be less than 100")
    private String roleName;

    private String dataScope;

    @Size(max = 500, message = "description length must be less than 500")
    private String description;

    private Integer sortNo;
}
