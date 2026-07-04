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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleStatusRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Role Status 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysRoleStatusRequest implements Serializable {

    /**
     * 系统管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 系统管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    @NotNull(message = "roleId")
    private Long roleId;

    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @NotNull(message = "status")
    private Integer status;
}
