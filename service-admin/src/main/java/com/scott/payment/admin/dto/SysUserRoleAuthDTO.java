package com.scott.payment.admin.dto;

import lombok.Data;

import java.util.List;


@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserRoleAuthDTO
 * @date : 2026-06-12 11:47
 * @email : scott_x@163.com
 * @description : Sys User Role Auth DTO 传输模型，位于 运营后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
public class SysUserRoleAuthDTO {

    /**
     * 目标账号ID。
     */
    private Long accountId;

    /**
     * 当前操作人可见的角色清单；角色中的 assignable 标识是否可被当前操作人授权。
     */
    private List<SysRoleDTO> roles;

    /**
     * 目标账号当前已绑定角色ID集合。
     */
    private List<Long> checkedRoleIds;
}
