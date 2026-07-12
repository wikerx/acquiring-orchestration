package com.scott.payment.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountUpdateRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 后台用户编辑请求，位于 service-admin 接口传输层；用于维护用户资料、账号状态和岗位关系，不承载角色授权。
 * @status : create
 */
@Data
public class SysUserAccountUpdateRequest implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 账号ID。
     */
    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    /**
     * 用户真实姓名。
     */
    @NotBlank(message = "请输入姓名")
    @Size(max = 100, message = "姓名长度不能超过100位")
    private String realName;

    /**
     * 所属部门ID，允许为空。
     */
    private Long deptId;

    /**
     * 所属岗位ID集合，允许为空。
     */
    private List<Long> postIds;

    /**
     * 手机号，允许为空。
     */
    @Size(max = 30, message = "手机号长度不能超过30位")
    private String mobile;

    /**
     * 邮箱，后台用户必填并需满足邮箱格式。
     */
    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 150, message = "邮箱长度不能超过150位")
    private String email;

    /**
     * 账号状态：1启用，0停用。
     */
    private Integer status;

    /**
     * 备注信息，用于后台人工管理说明。
     */
    @Size(max = 500, message = "备注长度不能超过500位")
    private String remark;
}
