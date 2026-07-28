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
 * @description : Sys User Role Grant Request 传输模型，位于 运营后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
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
