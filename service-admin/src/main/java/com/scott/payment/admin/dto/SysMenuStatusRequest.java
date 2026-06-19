package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuStatusRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台菜单状态更新请求 DTO
 * @status : create
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
