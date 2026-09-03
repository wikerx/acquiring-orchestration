package com.scott.payment.admin.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysRoleMenuAuthDTO
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台角色菜单授权响应 DTO
 * @status : create
 *
 * <p>用于角色菜单授权页面，返回角色基础信息、可选菜单树和当前已勾选菜单主键。</p>
 */
@Data
public class SysRoleMenuAuthDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色ID，用于定位 {@code SysRoleMenuAuthDTO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Long roleId;

    /**
     * 角色编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String roleCode;

    /**
     * 角色名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String roleName;

    /**
     * 菜单集合，承载 {@code SysRoleMenuAuthDTO} 当前请求或响应中的多值数据。
     * <p>
     * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
     * </p>
     */
    private List<SysMenuDTO> menus = Collections.emptyList();

    /**
     * {@code checkedMenuIds}集合，承载 {@code SysRoleMenuAuthDTO} 当前请求或响应中的多值数据。
     * <p>
     * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
     * </p>
     */
    private List<Long> checkedMenuIds = Collections.emptyList();
}
