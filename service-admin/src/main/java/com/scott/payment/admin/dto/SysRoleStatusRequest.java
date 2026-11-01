package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理后台角色状态更新请求 DTO
 *
 * <p>用于角色启停切换，仅承载角色主键和目标状态值。</p>
 */
@Data
public class SysRoleStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "roleId")
    private Long roleId;

    @NotNull(message = "status")
    private Integer status;
}
