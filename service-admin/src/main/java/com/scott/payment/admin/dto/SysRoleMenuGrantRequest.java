package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleMenuGrantRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台角色菜单授权保存请求 DTO
 * @status : create
 *
 * <p>用于提交角色菜单授权结果，承载角色主键和本次授予的菜单主键集合。</p>
 */
@Data
public class SysRoleMenuGrantRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色ID，用于定位 {@code SysRoleMenuGrantRequest} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @NotNull(message = "roleId")
    private Long roleId;

    /**
     * {@code menuIds}集合，承载 {@code SysRoleMenuGrantRequest} 当前请求或响应中的多值数据。
     * <p>
     * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
     * </p>
     */
    private List<Long> menuIds = Collections.emptyList();
}
