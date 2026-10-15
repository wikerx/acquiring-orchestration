package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountResetPasswordRequest
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台用户重置密码请求 DTO
 * @status : create
 *
 * <p>用于后台管理员为指定账号发送一次性重置邮件，接口不接收、生成或返回明文密码。</p>
 */
@Data
public class SysUserAccountResetPasswordRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 账号ID，用于定位 {@code SysUserAccountResetPasswordRequest} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    @NotNull(message = "accountId")
    private Long accountId;

}
