package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserRoleGrantRequest
 * @date : 2026-06-12 11:47
 * @email : scott_x@163.com
 * @description : sys用户角色授权请求模型，位于 运营后台服务，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
 * @status : create
 */
@Data
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
