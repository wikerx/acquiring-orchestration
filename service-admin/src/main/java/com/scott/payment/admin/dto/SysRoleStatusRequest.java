package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleStatusRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台角色状态更新请求 DTO
 * @status : create
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
