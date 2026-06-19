package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleUpdateRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台角色编辑请求 DTO
 * @status : create
 *
 * <p>用于更新角色展示信息、数据范围、状态和排序等可变配置。</p>
 */
@Data
public class SysRoleUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "roleId")
    private Long roleId;

    @Size(max = 100, message = "roleName length must be less than 100")
    private String roleName;

    private String dataScope;

    @Size(max = 500, message = "description length must be less than 500")
    private String description;

    private Integer status;

    private Integer sortNo;
}
