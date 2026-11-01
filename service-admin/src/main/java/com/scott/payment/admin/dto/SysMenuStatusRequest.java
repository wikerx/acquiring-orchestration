package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理后台菜单状态更新请求 DTO
 *
 * <p>用于菜单启停切换，仅承载目标菜单主键和目标状态值。</p>
 */
@Data
public class SysMenuStatusRequest {

    @NotNull(message = "菜单ID不能为空")
    private Long menuId;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
