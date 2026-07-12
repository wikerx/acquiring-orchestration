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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMenuStatusRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Menu Status 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysMenuStatusRequest {

    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @NotNull(message = "菜单ID不能为空")
    private Long menuId;

    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @NotNull(message = "状态不能为空")
    private Integer status;
}
