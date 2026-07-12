package com.scott.payment.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountCreateRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 后台用户新增请求，位于 service-admin 接口传输层；只承载用户资料、账号状态和岗位关系，不暴露角色授权明细。
 * @status : create
 */
@Data
public class SysUserAccountCreateRequest implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 登录账号，创建后不支持改名。
     */
    @NotBlank(message = "请输入登录账号")
    @Size(max = 100, message = "登录账号长度不能超过100位")
    private String loginAccount;

    /**
     * 初始密码，日志和操作记录中不得明文输出。
     */
    @NotBlank(message = "请输入初始密码")
    @Size(min = 8, max = 64, message = "初始密码长度必须在8到64位之间")
    private String password;

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
     * 账号状态：1启用，0停用；为空时按启用处理。
     */
    private Integer status;

    /**
     * 备注信息，用于后台人工管理说明。
     */
    @Size(max = 500, message = "备注长度不能超过500位")
    private String remark;
}
