package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;


@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserRoleGrantRequest
 * @date : 2026-06-12 11:47
 * @email : scott_x@163.com
 * @description : SysUserRoleGrantRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class SysUserRoleGrantRequest {

    /**
     * 目标账号ID。
     */
    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    /**
     * 本次保存后的角色ID集合，允许为空表示清空角色。
     */
    private List<Long> roleIds;
}
