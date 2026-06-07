package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleStatusRequest
 * @date : 2026-06-07 00:00
 * @description : 管理后台角色状态更新请求
 * @status : create
 */
@Data
public class SysRoleStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "roleId")
    private Long roleId;

    @NotNull(message = "status")
    private Integer status;
}
