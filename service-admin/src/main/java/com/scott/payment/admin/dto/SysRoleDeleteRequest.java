package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleDeleteRequest
 * @date : 2026-06-07 00:00
 * @description : 管理后台角色删除请求
 * @status : create
 */
@Data
public class SysRoleDeleteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "roleId")
    private Long roleId;
}
