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
 * @description : SysUserRoleAuthDTO 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
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
