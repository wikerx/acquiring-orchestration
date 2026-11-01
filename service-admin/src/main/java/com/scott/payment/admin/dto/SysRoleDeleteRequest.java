package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理后台角色删除请求 DTO
 *
 * <p>用于删除指定角色，仅承载目标角色主键。</p>
 */
@Data
public class SysRoleDeleteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "roleId")
    private Long roleId;
}
