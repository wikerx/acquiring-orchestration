package com.scott.payment.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 管理后台用户编辑请求 DTO。
 *
 * <p>用于更新用户资料、账号状态、部门和岗位关系，不包含登录账号与密码等受限字段。</p>
 */
@Data
public class SysUserAccountUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "accountId")
    private Long accountId;

    @Size(max = 100, message = "realName length must be less than 100")
    private String realName;

    private Long deptId;

    private List<Long> postIds;

    @Size(max = 30, message = "mobile length must be less than 30")
    private String mobile;

    @Email(message = "email format does not match")
    @Size(max = 150, message = "email length must be less than 150")
    private String email;

    private Integer status;
}
